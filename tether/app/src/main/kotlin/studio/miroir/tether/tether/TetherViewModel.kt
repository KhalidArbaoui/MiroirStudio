package studio.miroir.tether.tether

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import studio.miroir.tether.camera.CameraDriver
import studio.miroir.tether.camera.CameraListener
import studio.miroir.tether.camera.DownloadedFile
import studio.miroir.tether.camera.DriverOptions
import studio.miroir.tether.image.JpegExtractor
import studio.miroir.tether.image.NeutralSample
import studio.miroir.tether.ptp.PtpEvent
import studio.miroir.tether.ptp.PtpException
import studio.miroir.tether.ptp.PtpSession
import studio.miroir.tether.usb.UsbPtpTransport
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class LinkState { Disconnected, Connecting, Connected, Error }

data class CameraStatus(
    val state: LinkState = LinkState.Disconnected,
    val model: String = "",
    val brand: String = "",
    val battery: Int? = null,
    val message: String = "",
)

enum class PreviewSource(val label: String) {
    Jpeg("JPEG"),
    RawEmbedded("RAW preview"),
    CameraPreview("Camera preview"),
}

data class Shot(
    val id: Long,
    val baseName: String,
    val previewFile: File,
    val source: PreviewSource,
    val jpegFile: File? = null,
    val rawFile: File? = null,
    val thumb: Bitmap? = null,
    val exif: ExifSummary = ExifSummary(),
    val version: Int = 0,
)

data class Transfer(val filename: String, val done: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
}

enum class Tool { None, Loupe, WhiteBalance }

class TetherViewModel(app: Application) : AndroidViewModel(app), CameraListener {
    private val prefs = app.getSharedPreferences("tether", Context.MODE_PRIVATE)

    private val _status = MutableStateFlow(CameraStatus())
    val status: StateFlow<CameraStatus> = _status

    private val _shots = MutableStateFlow<List<Shot>>(emptyList())
    val shots: StateFlow<List<Shot>> = _shots

    private val _selectedId = MutableStateFlow<Long?>(null)
    val selectedId: StateFlow<Long?> = _selectedId

    private val _analysis = MutableStateFlow<Analysis?>(null)
    val analysis: StateFlow<Analysis?> = _analysis

    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer: StateFlow<Transfer?> = _transfer

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    val followNewest = MutableStateFlow(true)
    val showClipping = MutableStateFlow(true)
    val tool = MutableStateFlow(Tool.None)
    val copyRaw = MutableStateFlow(prefs.getBoolean("copyRaw", false))

    private val _loupe = MutableStateFlow<Bitmap?>(null)
    val loupe: StateFlow<Bitmap?> = _loupe

    private val _neutral = MutableStateFlow<NeutralSample?>(null)
    val neutral: StateFlow<NeutralSample?> = _neutral

    private val options = DriverOptions(copyRaw.value)
    private val cameraThread = Executors.newSingleThreadExecutor { Thread(it, "camera") }
    private val ioThread = Executors.newSingleThreadExecutor { Thread(it, "image-io") }
    private val running = AtomicBoolean(false)
    private val captureRequests = ConcurrentLinkedQueue<Unit>()
    private var analysisJob: Job? = null
    private var nextId = 1L

    private val sessionDir: File by lazy {
        val root = app.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: app.filesDir
        val name = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        File(root, "MIROIR Tether/$name").apply { mkdirs() }
    }
    private val previewDir: File by lazy { File(sessionDir, ".previews").apply { mkdirs() } }

    val sessionPath: String get() = sessionDir.absolutePath

    // ---------------------------------------------------------------- connection

    fun connect(manager: UsbManager, device: UsbDevice) {
        if (!running.compareAndSet(false, true)) return
        _status.value = CameraStatus(LinkState.Connecting, message = "Connecting to ${device.productName ?: "camera"}…")
        cameraThread.execute { runCamera(manager, device) }
    }

    fun disconnect() {
        running.set(false)
    }

    fun onUsbDetached() {
        if (running.get()) AppLog.log("Camera unplugged")
        running.set(false)
    }

    private fun runCamera(manager: UsbManager, device: UsbDevice) {
        var transport: UsbPtpTransport? = null
        try {
            AppLog.log("Opening ${device.manufacturerName} ${device.productName} (VID %04X PID %04X)".format(device.vendorId, device.productId))
            transport = UsbPtpTransport.open(manager, device)
            val session = PtpSession(transport, AppLog)
            val info = try {
                session.readDeviceInfo()
            } catch (e: PtpException) {
                AppLog.log("DeviceInfo failed (${e.message}); resetting USB and retrying")
                transport.reset()
                Thread.sleep(500)
                session.readDeviceInfo()
            }
            AppLog.log("Camera: $info")
            session.openSession()

            val driver = CameraDriver.create(device.vendorId, session, AppLog, this, options)
            driver.connect()
            _status.value = CameraStatus(LinkState.Connected, info.model, driver.brand, message = "Ready")
            AppLog.log("Connected to ${driver.brand} ${info.model}")

            val events = startEventReader(transport, driver)
            var lastBattery = 0L
            var failures = 0
            while (running.get()) {
                try {
                    if (captureRequests.poll() != null) {
                        AppLog.log("Capture requested")
                        driver.capture()
                    }
                    driver.poll()
                    val now = System.currentTimeMillis()
                    if (now - lastBattery > 30_000) {
                        lastBattery = now
                        val level = driver.batteryLevel()
                        _status.update { it.copy(battery = level) }
                    }
                    failures = 0
                } catch (e: PtpException) {
                    failures++
                    AppLog.log("Camera error: ${e.message}")
                    if (e.responseCode != 0) showToast(e.message ?: "Camera error")
                    if (failures >= 4) throw e
                    Thread.sleep(300)
                }
                Thread.sleep(120)
            }
            events.interrupt()
            session.closeSession()
            _status.value = CameraStatus(LinkState.Disconnected, message = "Disconnected")
        } catch (t: Throwable) {
            AppLog.error("Connection lost", t)
            _status.value = CameraStatus(LinkState.Error, message = t.message ?: "Connection failed")
        } finally {
            running.set(false)
            _transfer.value = null
            transport?.close()
        }
    }

    private fun startEventReader(transport: UsbPtpTransport, driver: CameraDriver): Thread =
        Thread({
            val buf = ByteArray(64)
            while (running.get() && !Thread.currentThread().isInterrupted) {
                val n = transport.readEvent(buf, 500)
                if (n > 0) PtpEvent.parse(buf, n)?.let {
                    AppLog.log("USB $it")
                    driver.onInterruptEvent(it)
                }
            }
        }, "camera-events").apply { isDaemon = true; start() }

    fun capture() {
        if (_status.value.state == LinkState.Connected) captureRequests.add(Unit)
    }

    fun setCopyRaw(on: Boolean) {
        copyRaw.value = on
        options.copyRaw = on
        prefs.edit().putBoolean("copyRaw", on).apply()
    }

    private fun showToast(msg: String) {
        _toast.value = msg
    }

    fun clearToast() { _toast.value = null }

    // ---------------------------------------------------------------- files

    override fun onProgress(filename: String, done: Long, total: Long) {
        _transfer.value = if (done >= total) null else Transfer(filename, done, total)
    }

    override fun onFile(file: DownloadedFile) {
        _transfer.value = null
        // Hand off so the camera thread can keep polling while we write and decode.
        ioThread.execute {
            try {
                storeFile(file)
            } catch (t: Throwable) {
                AppLog.error("Saving ${file.info.filename}", t)
            }
        }
    }

    private fun storeFile(file: DownloadedFile) {
        val info = file.info
        val base = info.filename.substringBeforeLast('.')
        val existing = _shots.value.firstOrNull { it.baseName == base }

        when {
            info.isJpeg -> {
                val f = File(sessionDir, info.filename).apply { writeBytes(file.data) }
                upsert(base, existing, f, PreviewSource.Jpeg) { it.copy(jpegFile = f) }
            }
            info.isRaw -> {
                val rawFile = if (file.isFullFile && options.copyRaw) {
                    File(sessionDir, info.filename).apply { writeBytes(file.data) }
                } else null
                if (existing?.source == PreviewSource.Jpeg) {
                    // The JPEG already gives the best preview; just remember the RAW.
                    if (rawFile != null) update(existing.id) { it.copy(rawFile = rawFile) }
                    return
                }
                val jpeg = if (file.isFullFile) JpegExtractor.largestJpeg(file.data) else file.data
                if (jpeg == null) {
                    AppLog.log("No embedded preview found in ${info.filename}")
                    return
                }
                val p = File(previewDir, "$base.jpg").apply { writeBytes(jpeg) }
                val src = if (file.isFullFile) PreviewSource.RawEmbedded else PreviewSource.CameraPreview
                upsert(base, existing, p, src) { it.copy(rawFile = rawFile ?: it.rawFile) }
            }
            info.isHeif -> {
                val f = File(sessionDir, info.filename).apply { writeBytes(file.data) }
                upsert(base, existing, f, PreviewSource.Jpeg) { it }
                showToast("HEIF/HLG shot: colours won't match Lightroom SDR. Use JPEG for tethering.")
            }
            else -> {
                File(sessionDir, info.filename).writeBytes(file.data)
                AppLog.log("Saved ${info.filename} (no preview for this format)")
            }
        }
    }

    private fun upsert(base: String, existing: Shot?, preview: File, source: PreviewSource, extra: (Shot) -> Shot) {
        val exif = ImageProcessor.readExif(preview)
        val thumb = ImageProcessor.thumbnail(preview, exif.rotation)
        if (existing == null) {
            val shot = extra(Shot(nextId++, base, preview, source, thumb = thumb, exif = exif))
            _shots.update { it + shot }
            if (followNewest.value || _selectedId.value == null) select(shot.id)
        } else {
            update(existing.id) { extra(it.copy(previewFile = preview, source = source, thumb = thumb, exif = exif, version = it.version + 1)) }
            if (_selectedId.value == existing.id) loadAnalysis(existing.id)
        }
    }

    private fun update(id: Long, f: (Shot) -> Shot) {
        _shots.update { list -> list.map { if (it.id == id) f(it) else it } }
    }

    // ---------------------------------------------------------------- viewer

    fun select(id: Long) {
        _selectedId.value = id
        _loupe.value = null
        _neutral.value = null
        loadAnalysis(id)
    }

    fun selectRelative(step: Int) {
        val list = _shots.value
        val i = list.indexOfFirst { it.id == _selectedId.value }
        if (i < 0) return
        val j = (i + step).coerceIn(0, list.lastIndex)
        if (j != i) {
            followNewest.value = j == list.lastIndex
            select(list[j].id)
        }
    }

    private fun loadAnalysis(id: Long) {
        val shot = _shots.value.firstOrNull { it.id == id } ?: return
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            val a = withContext(Dispatchers.Default) { ImageProcessor.analyze(shot.previewFile) }
            if (_selectedId.value == id) _analysis.value = a
        }
    }

    /** Tap on the image at normalised (u, v), honouring the active tool. */
    fun onImageTap(u: Float, v: Float) {
        val a = _analysis.value ?: return
        when (tool.value) {
            Tool.Loupe -> viewModelScope.launch {
                _loupe.value = withContext(Dispatchers.IO) { ImageProcessor.loupe(a, u, v) }
            }
            Tool.WhiteBalance -> _neutral.value = ImageProcessor.sampleNeutral(a, u, v)
            Tool.None -> {}
        }
    }

    fun setTool(t: Tool) {
        tool.value = if (tool.value == t) Tool.None else t
        _loupe.value = null
        _neutral.value = null
    }

    override fun onCleared() {
        running.set(false)
        cameraThread.shutdown()
        ioThread.shutdown()
    }
}
