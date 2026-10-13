package studio.miroir.tether.tether

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
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
import studio.miroir.tether.camera.CameraSetting
import studio.miroir.tether.camera.SettingId
import studio.miroir.tether.camera.DownloadedFile
import studio.miroir.tether.camera.DriverOptions
import studio.miroir.tether.camera.FocusResult
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
    val rating: Int = 0,
    val pick: Boolean = false,
)

enum class ShotFilter(val label: String) { All("All"), Picks("Picks"), Rated("★3+") }

fun ShotFilter.matches(s: Shot) = when (this) {
    ShotFilter.All -> true
    ShotFilter.Picks -> s.pick
    ShotFilter.Rated -> s.rating >= 3
}

data class Transfer(val filename: String, val done: Long, val total: Long) {
    val fraction: Float get() = if (total > 0) done.toFloat() / total else 0f
}

enum class Tool { None, Loupe, WhiteBalance }

enum class AfState { Busy, Locked, Done, Failed }

/** Where the user last asked the camera to focus, in live-view fractions, and how it went. */
data class AfMark(val u: Float, val v: Float, val state: AfState)

enum class RotationLock(val label: String, val toast: String) {
    Auto("AUTO", "Rotation follows the tablet"),
    Landscape("LAND", "Locked to landscape"),
    Portrait("PORT", "Locked to portrait"),
}

/** A short message for the top of the screen. Each instance is distinct so repeats re-show. */
class Toast(val text: String, val error: Boolean)

private const val LIVE_VIEW_GIVE_UP_MS = 4000L

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

    private val _toast = MutableStateFlow<Toast?>(null)
    val toast: StateFlow<Toast?> = _toast

    val followNewest = MutableStateFlow(true)
    val showClipping = MutableStateFlow(true)
    val tool = MutableStateFlow(Tool.None)
    val copyRaw = MutableStateFlow(prefs.getBoolean("copyRaw", false))

    val filter = MutableStateFlow(ShotFilter.All)

    private val _settings = MutableStateFlow<List<CameraSetting>>(emptyList())
    val settings: StateFlow<List<CameraSetting>> = _settings
    private val settingRequests = ConcurrentLinkedQueue<Pair<SettingId, Int>>()
    private val exported = HashSet<Long>()

    /** Live view the user asked for; the camera thread starts/stops it to match. */
    val liveViewWanted = MutableStateFlow(false)
    private val _liveFrame = MutableStateFlow<Bitmap?>(null)
    val liveFrame: StateFlow<Bitmap?> = _liveFrame
    private val _liveHistogram = MutableStateFlow<studio.miroir.tether.image.Histogram?>(null)
    val liveHistogram: StateFlow<studio.miroir.tether.image.Histogram?> = _liveHistogram
    private val _liveSupported = MutableStateFlow(false)
    val liveSupported: StateFlow<Boolean> = _liveSupported
    /** (u, v) to move the AF point there, or empty to focus at the current point. */
    private val focusRequests = ConcurrentLinkedQueue<FloatArray>()
    private val _afMark = MutableStateFlow<AfMark?>(null)
    val afMark: StateFlow<AfMark?> = _afMark

    val rotation = MutableStateFlow(
        runCatching { RotationLock.valueOf(prefs.getString("rotation", null) ?: "Auto") }.getOrDefault(RotationLock.Auto),
    )

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
            AppLog.log("USB: ${transport.describe()}, status ${transport.deviceStatus()}")
            val session = PtpSession(transport, AppLog)
            val info = handshake(transport, session)
            AppLog.log("Camera: $info")
            session.openSession()

            val driver = CameraDriver.create(device.vendorId, session, AppLog, this, options)
            driver.connect()
            _status.value = CameraStatus(LinkState.Connected, info.model, driver.brand, message = "Ready")
            AppLog.log("Connected to ${driver.brand} ${info.model}")

            val events = startEventReader(transport, driver)
            _liveSupported.value = driver.supportsLiveView
            var liveActive = false
            // Time live view has been failing continuously (0 = fine). Cameras pause frames while
            // focusing or shooting, so only give up after a long stretch.
            var liveFailingSince = 0L
            var lastHistogram = 0L
            var lastBattery = 0L
            var lastSettings = 0L
            var failures = 0
            while (running.get()) {
                try {
                    if (captureRequests.poll() != null) {
                        AppLog.log("Capture requested")
                        driver.capture()
                    }
                    val step = settingRequests.poll()
                    if (step != null) {
                        try {
                            driver.stepSetting(step.first, step.second)
                        } catch (e: PtpException) {
                            AppLog.log("Setting ${step.first.label}: ${e.message}")
                            showToast(e.message ?: "Camera refused the change")
                        }
                        lastSettings = 0L
                    }
                    var focus = focusRequests.poll()
                    if (focus != null) {
                        // Only the latest tap matters.
                        while (true) focus = focusRequests.poll() ?: break
                        val req = focus!!
                        try {
                            val result = if (req.size == 2) driver.focusAt(req[0], req[1]) else { driver.autofocus(); FocusResult.Done }
                            _afMark.update { it?.copy(state = if (result == FocusResult.Locked) AfState.Locked else AfState.Done) }
                        } catch (e: PtpException) {
                            _afMark.update { it?.copy(state = AfState.Failed) }
                            showToast(e.message ?: "Couldn't focus — tap an edge, the label or a texture")
                        }
                    }

                    // Live view: follow the toggle, then pull one frame per loop.
                    val wantLive = liveViewWanted.value && driver.supportsLiveView
                    if (wantLive != liveActive) {
                        if (wantLive) {
                            try {
                                driver.startLiveView()
                                liveActive = true
                                liveFailingSince = 0L
                            } catch (e: PtpException) {
                                liveViewWanted.value = false
                                showToast(e.message ?: "Live view didn't start")
                            }
                        } else {
                            driver.stopLiveView()
                            liveActive = false
                            _liveFrame.value = null
                            _liveHistogram.value = null
                        }
                    }
                    if (liveActive) {
                        try {
                            driver.liveViewFrame()?.let { jpeg ->
                                android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let { bmp ->
                                    _liveFrame.value = bmp
                                    val now = System.currentTimeMillis()
                                    if (now - lastHistogram > 400) {
                                        lastHistogram = now
                                        _liveHistogram.value = liveHistogram(bmp)
                                    }
                                }
                            }
                            liveFailingSince = 0L
                        } catch (e: PtpException) {
                            val now = System.currentTimeMillis()
                            if (liveFailingSince == 0L) {
                                liveFailingSince = now
                                AppLog.log("Live view paused: ${e.message}")
                            }
                            if (now - liveFailingSince > LIVE_VIEW_GIVE_UP_MS) {
                                AppLog.log("Live view stopped after ${now - liveFailingSince} ms: ${e.message}")
                                showToast("Live view stopped — tap Live view to restart")
                                liveViewWanted.value = false
                                liveFailingSince = 0L
                            } else {
                                Thread.sleep(100) // let the camera finish focusing
                            }
                        }
                    }

                    driver.poll()
                    val now = System.currentTimeMillis()
                    if (now - lastSettings > 1500) {
                        lastSettings = now
                        _settings.value = driver.readSettings()
                    }
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
                Thread.sleep(if (liveActive) 15 else 120)
            }
            if (liveActive) driver.stopLiveView()
            _liveFrame.value = null
            _liveHistogram.value = null
            events.interrupt()
            _settings.value = emptyList()
            session.closeSession()
            _status.value = CameraStatus(LinkState.Disconnected, message = "Disconnected")
        } catch (t: Throwable) {
            AppLog.error("Connection lost", t)
            _status.value = CameraStatus(LinkState.Error, message = t.message ?: "Connection failed")
        } finally {
            running.set(false)
            liveViewWanted.value = false
            _afMark.value = null
            _liveFrame.value = null
            _transfer.value = null
            transport?.close()
        }
    }

    /**
     * GetDeviceInfo with fallbacks for the usual Android problems: stale data from another app,
     * a USB stack that rejects large reads, or a camera stuck in an old session.
     */
    private fun handshake(transport: UsbPtpTransport, session: PtpSession): studio.miroir.tether.ptp.DeviceInfo {
        // Samsung's USB stack rejects 1 MB bulk reads, so start with 16 KB (still fast enough).
        session.readChunk = PtpSession.SMALL_READ_CHUNK
        session.drain()
        val attempts = listOf<Pair<String, () -> Unit>>(
            "16 KB reads" to {},
            "large reads" to { session.readChunk = PtpSession.READ_CHUNK },
            "USB reset" to {
                transport.reset()
                Thread.sleep(800)
                AppLog.log("Status after reset: ${transport.deviceStatus()}")
            },
        )
        var last: Exception? = null
        for ((name, prepare) in attempts) {
            try {
                prepare()
                session.drain()
                return session.readDeviceInfo().also { AppLog.log("Handshake OK ($name)") }
            } catch (e: PtpException) {
                AppLog.log("Handshake ($name) failed: ${e.message}")
                last = e
            }
        }
        throw PtpException(
            "Camera doesn't answer over USB. Check: camera ON and awake, airplane mode ON, " +
                "Z8 USB power delivery OFF / Sony USB Connection = PC Remote, and no other app is using the camera. " +
                "(${last?.message})",
        )
    }

    private fun startEventReader(transport: UsbPtpTransport, driver: CameraDriver): Thread =
        Thread({
            val buf = ByteArray(64)
            while (running.get() && !Thread.currentThread().isInterrupted) {
                val n = transport.readEvent(buf, 500)
                if (n > 0) PtpEvent.parse(buf, n)?.let {
                    if (it.code != studio.miroir.tether.ptp.Ptp.EV_DEVICE_PROP_CHANGED) AppLog.log("USB $it")
                    driver.onInterruptEvent(it)
                }
            }
        }, "camera-events").apply { isDaemon = true; start() }

    fun capture() {
        if (_status.value.state == LinkState.Connected) captureRequests.add(Unit)
    }

    private var focusHintShown = false

    fun toggleLiveView() {
        liveViewWanted.value = !liveViewWanted.value && _status.value.state == LinkState.Connected
        if (liveViewWanted.value && !focusHintShown) {
            focusHintShown = true
            showToast("Tap the image to focus there · AF focuses again at the same spot", error = false)
        }
    }

    /** AF button: focus again at the last tapped point, or at the camera's own point. */
    fun autofocus() {
        if (_status.value.state != LinkState.Connected) return
        val m = _afMark.value
        if (m != null) focusAt(m.u, m.v) else focusRequests.add(FloatArray(0))
    }

    /** Tap on live view at (u, v): move the AF point there and focus. */
    fun focusAt(u: Float, v: Float) {
        if (_status.value.state != LinkState.Connected) return
        _afMark.value = AfMark(u, v, AfState.Busy)
        focusRequests.add(floatArrayOf(u, v))
    }

    fun cycleRotation() {
        val next = RotationLock.entries[(rotation.value.ordinal + 1) % RotationLock.entries.size]
        rotation.value = next
        prefs.edit().putString("rotation", next.name).apply()
        showToast(next.toast, error = false)
    }

    private fun liveHistogram(bmp: Bitmap): studio.miroir.tether.image.Histogram {
        // Every 4th pixel each way is plenty for a live histogram.
        val w = bmp.width / 4
        val h = bmp.height / 4
        val small = Bitmap.createScaledBitmap(bmp, w.coerceAtLeast(1), h.coerceAtLeast(1), false)
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        if (small !== bmp) small.recycle()
        return studio.miroir.tether.image.Histogram.compute(px)
    }

    fun stepSetting(id: SettingId, direction: Int) {
        if (_status.value.state == LinkState.Connected) settingRequests.add(id to direction)
    }

    // ---------------------------------------------------------------- ratings & picks

    fun setRating(id: Long, rating: Int) {
        update(id) { it.copy(rating = if (it.rating == rating) 0 else rating) }
        saveRatings()
    }

    fun togglePick(id: Long) {
        update(id) { it.copy(pick = !it.pick) }
        saveRatings()
    }

    private fun saveRatings() {
        val lines = _shots.value.filter { it.rating > 0 || it.pick }
            .joinToString("\n") { "${it.baseName}\t${it.rating}\t${if (it.pick) 1 else 0}" }
        ioThread.execute { runCatching { File(sessionDir, "ratings.txt").writeText(lines) } }
    }

    /** File names of the picks, space-separated — paste into Lightroom's Library Filter (Text → Filename → Contains). */
    fun pickList(): String = _shots.value.filter { it.pick }.joinToString(" ") { it.baseName }

    /** Copies the picked JPEGs into the Samsung Gallery (Pictures/MIROIR Tether/<session>). */
    fun exportPicks() {
        val picks = _shots.value.filter { it.pick && it.id !in exported }
        if (picks.isEmpty()) {
            showToast(if (_shots.value.any { it.pick }) "All picks are already in the Gallery" else "No picks yet — tap ♥ on your best shots", error = false)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            showToast("Saving to Gallery needs Android 10 or newer")
            return
        }
        ioThread.execute {
            val resolver = getApplication<Application>().contentResolver
            var n = 0
            for (shot in picks) {
                try {
                    val src = shot.jpegFile ?: shot.previewFile
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, shot.baseName + ".jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MIROIR Tether/${sessionDir.name}")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: continue
                    resolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    synchronized(exported) { exported += shot.id }
                    n++
                } catch (t: Throwable) {
                    AppLog.error("Export ${shot.baseName}", t)
                }
            }
            showToast("$n pick${if (n == 1) "" else "s"} saved to Gallery › MIROIR Tether", error = n == 0)
        }
    }

    fun setCopyRaw(on: Boolean) {
        copyRaw.value = on
        options.copyRaw = on
        prefs.edit().putBoolean("copyRaw", on).apply()
    }

    /** Errors get a red accent; [error] = false for confirmations. */
    private fun showToast(msg: String, error: Boolean = true) {
        _toast.value = Toast(msg, error)
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
