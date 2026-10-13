package studio.miroir.tether.camera

import studio.miroir.tether.ptp.ObjectInfo
import studio.miroir.tether.ptp.PropDesc
import studio.miroir.tether.ptp.PropDescParser
import studio.miroir.tether.ptp.Ptp
import studio.miroir.tether.ptp.PtpEvent
import studio.miroir.tether.ptp.PtpException
import studio.miroir.tether.ptp.PtpSession
import studio.miroir.tether.ptp.TetherLog
import studio.miroir.tether.ptp.hex8
import java.util.concurrent.ConcurrentLinkedQueue

/** A file received from the camera. */
class DownloadedFile(
    val info: ObjectInfo,
    val data: ByteArray,
    /** False when the bytes are only a preview (e.g. a RAW we chose not to copy in full). */
    val isFullFile: Boolean,
)

interface CameraListener {
    fun onFile(file: DownloadedFile)
    fun onProgress(filename: String, done: Long, total: Long)
}

class DriverOptions(
    /** Copy RAW files to the tablet. When false, only a preview is taken for RAW-only shots. */
    @Volatile var copyRaw: Boolean = false,
)

/**
 * Brand-specific camera control. All methods run on the single camera thread,
 * except [onInterruptEvent] which is called from the interrupt reader thread.
 */
abstract class CameraDriver(
    protected val session: PtpSession,
    protected val log: TetherLog,
    protected val listener: CameraListener,
    protected val options: DriverOptions,
) {
    abstract val brand: String
    val model: String get() = session.deviceInfo?.model ?: brand

    protected val interruptEvents = ConcurrentLinkedQueue<PtpEvent>()

    fun onInterruptEvent(e: PtpEvent) {
        interruptEvents.add(e)
    }

    /** Called after OpenSession. Perform vendor handshakes here. */
    open fun connect() {}

    /** Fire the shutter from the tablet. */
    abstract fun capture()

    /** Check for new images and download them. Called every ~150 ms. */
    abstract fun poll()

    /** True when this camera can stream live view to the tablet. */
    open val supportsLiveView: Boolean = false

    open fun startLiveView() {}

    /** One live-view frame as JPEG bytes, or null if none is ready yet. */
    open fun liveViewFrame(): ByteArray? = null

    open fun stopLiveView() {}

    /** Run autofocus once (at the camera's current AF point). */
    open fun autofocus() {}

    /** Current exposure settings, for the tablet's camera panel. */
    open fun readSettings(): List<CameraSetting> = emptyList()

    /** Moves a setting one step: +1 = faster shutter / higher f-number / higher ISO / more EV / warmer K. */
    open fun stepSetting(id: SettingId, direction: Int) {}

    /** Sort key so "+1" always means the same thing whatever order the camera lists values in. */
    protected fun settingKey(id: SettingId, code: Int, v: Long): Double = when (id) {
        SettingId.Shutter -> {
            val seconds = if (code == Ptp.PROP_EXPOSURE_TIME) {
                if (v == 0xFFFFFFFFL) 1e9 else v / 10000.0
            } else {
                val den = v and 0xFFFF
                if (v == 0L || v >= 0xFFFFFFFEL || den == 0L) 1e9 else ((v ushr 16) and 0xFFFF).toDouble() / den
            }
            -seconds
        }
        SettingId.Iso -> if ((v and 0xFFFFFF) == 0xFFFFFFL) 1e9 else (v and 0xFFFFFF).toDouble()
        SettingId.ExposureComp -> v.toInt().toShort().toDouble()
        SettingId.Mode, SettingId.WhiteBalance -> 0.0 // keep camera order
        else -> v.toDouble()
    }

    protected fun orderedValues(id: SettingId, d: PropDesc): List<Long> {
        val values = d.allowedValues
        return if (id == SettingId.Mode || id == SettingId.WhiteBalance) values
        else values.sortedBy { settingKey(id, d.code, it) }
    }

    protected fun targetFor(id: SettingId, d: PropDesc, direction: Int): Long? =
        if (id == SettingId.ColorTemp) SettingFormat.kelvinNeighbor(d, direction)
        else SettingFormat.neighbor(orderedValues(id, d), d.current, direction)

    protected fun toSetting(id: SettingId, d: PropDesc, adjustable: Boolean = d.writable) =
        CameraSetting(id, SettingFormat.format(id, d.code, d.current), adjustable && id != SettingId.Mode)

    /** Battery percentage if the camera reports it. */
    open fun batteryLevel(): Int? = runCatching {
        val r = session.transaction(Ptp.OP_GET_DEVICE_PROP_VALUE, longArrayOf(0x5001))
        if (r.ok && r.data.isNotEmpty()) r.data[0].toInt() and 0xFF else null
    }.getOrNull()

    protected fun drainInterruptEvents(): List<PtpEvent> {
        val out = ArrayList<PtpEvent>()
        while (true) out.add(interruptEvents.poll() ?: break)
        return out
    }

    protected fun download(handle: Long, info: ObjectInfo) {
        val data = session.getObject(handle) { done, total -> listener.onProgress(info.filename, done, total) }
        log.log("Downloaded ${info.filename} (${data.size / 1024} KB)")
        listener.onFile(DownloadedFile(info, data, isFullFile = true))
    }

    companion object {
        fun create(
            usbVendorId: Int,
            session: PtpSession,
            log: TetherLog,
            listener: CameraListener,
            options: DriverOptions,
        ): CameraDriver = when (usbVendorId) {
            Ptp.USB_VENDOR_NIKON -> NikonDriver(session, log, listener, options)
            Ptp.USB_VENDOR_SONY -> SonyDriver(session, log, listener, options)
            else -> GenericDriver(session, log, listener, options)
        }
    }
}

/** Standard PTP: InitiateCapture + ObjectAdded events. Used for Nikon and unknown brands. */
open class GenericDriver(
    session: PtpSession, log: TetherLog, listener: CameraListener, options: DriverOptions,
) : CameraDriver(session, log, listener, options) {
    override val brand = "Camera"
    private val seen = HashSet<Long>()

    /** File names (without extension) whose JPEG we already have — their RAW isn't needed for preview. */
    private val jpegBases = HashSet<String>()

    /** RAWs waiting briefly in case a matching JPEG (RAW+JPEG shooting) follows. */
    private val pendingRaw = LinkedHashMap<Long, Pair<ObjectInfo, Long>>()

    override fun capture() {
        val r = session.retryBusy { session.transaction(Ptp.OP_INITIATE_CAPTURE, longArrayOf(0, 0)) }
        if (!r.ok) throw PtpException("Capture failed: ${Ptp.responseName(r.code)}", r.code)
    }

    override fun poll() {
        handleEvents(drainInterruptEvents() + pollVendorEvents())
        processPendingRaw()
    }

    protected open fun pollVendorEvents(): List<PtpEvent> = emptyList()

    protected fun handleEvents(events: List<PtpEvent>) {
        val added = ArrayList<Pair<Long, ObjectInfo>>()
        for (e in events) {
            val handle = when (e.code) {
                Ptp.EV_OBJECT_ADDED -> e.param(0).takeIf { seen.add(it) }
                Ptp.EV_NIKON_OBJECT_ADDED_IN_SDRAM -> e.param(0).takeIf { it != 0L } ?: Ptp.NIKON_SDRAM_HANDLE
                // Property changes arrive every second or so and would bury useful lines.
                Ptp.EV_DEVICE_PROP_CHANGED -> null
                else -> { log.log("Ignored $e"); null }
            } ?: continue
            val info = try {
                session.getObjectInfo(handle)
            } catch (ex: PtpException) {
                log.log("ObjectInfo ${handle.hex8()} failed: ${ex.message}")
                continue
            }
            log.log("New object: $info")
            if (!info.isFolder) added += handle to info
        }
        // JPEGs first so the picture appears before a large RAW transfer.
        for ((handle, info) in added.sortedBy { if (it.second.isRaw) 1 else 0 }) fetch(handle, info)
    }

    private fun base(info: ObjectInfo) = info.filename.substringBeforeLast('.').uppercase()

    private fun fetch(handle: Long, info: ObjectInfo) {
        if (info.isRaw) {
            when {
                options.copyRaw -> download(handle, info)
                base(info) in jpegBases -> log.log("Skipping RAW ${info.filename}: JPEG already shown")
                else -> pendingRaw[handle] = info to System.currentTimeMillis()
            }
            return
        }
        if (info.isJpeg) jpegBases += base(info)
        download(handle, info)
    }

    private fun processPendingRaw() {
        if (pendingRaw.isEmpty()) return
        val now = System.currentTimeMillis()
        val due = pendingRaw.entries.filter { now - it.value.second >= RAW_WAIT_MS }
        for (entry in due) {
            pendingRaw.remove(entry.key)
            val info = entry.value.first
            if (base(info) in jpegBases) {
                log.log("Skipping RAW ${info.filename}: JPEG already shown")
            } else {
                fetchRawPreview(entry.key, info)
            }
        }
    }

    /**
     * RAW-only shot with Copy RAW off: we still need a sharp preview, so download the RAW once —
     * the app extracts the full-size JPEG embedded in it and doesn't keep the RAW file.
     */
    protected open fun fetchRawPreview(handle: Long, info: ObjectInfo) {
        log.log("RAW-only shot ${info.filename}: downloading for its full-size embedded preview")
        download(handle, info)
    }

    // ---------------------------------------------------------------- settings (standard PTP)

    /** Property codes per setting, best first. Nikon vendor codes are tried before standard ones. */
    protected open val settingCodes: Map<SettingId, List<Int>> = mapOf(
        SettingId.Mode to listOf(Ptp.PROP_EXPOSURE_PROGRAM),
        SettingId.Shutter to listOf(Ptp.NIKON_PROP_EXPOSURE_TIME, Ptp.PROP_EXPOSURE_TIME),
        SettingId.Aperture to listOf(Ptp.PROP_F_NUMBER),
        SettingId.Iso to listOf(Ptp.PROP_EXPOSURE_INDEX),
        SettingId.ExposureComp to listOf(Ptp.PROP_EXPOSURE_BIAS),
        SettingId.WhiteBalance to listOf(Ptp.PROP_WHITE_BALANCE),
        SettingId.ColorTemp to listOf(Ptp.NIKON_PROP_COLOR_TEMPERATURE),
    )
    private val resolvedCodes = HashMap<SettingId, Int?>()
    private val descs = LinkedHashMap<SettingId, PropDesc>()

    private fun readDesc(code: Int): PropDesc? {
        val r = session.retryBusy(attempts = 5) { session.transaction(Ptp.OP_GET_DEVICE_PROP_DESC, longArrayOf(code.toLong())) }
        return if (r.ok) runCatching { PropDescParser.parse(r.data) }.getOrNull() else null
    }

    private fun codeFor(id: SettingId): Int? {
        if (resolvedCodes.containsKey(id)) return resolvedCodes[id]
        val code = settingCodes[id].orEmpty().firstOrNull { readDesc(it) != null }
        log.log("Setting ${id.label}: ${code?.let { "0x%04X".format(it) } ?: "not available"}")
        resolvedCodes[id] = code
        return code
    }

    override fun readSettings(): List<CameraSetting> {
        for (id in SettingId.entries) {
            val code = codeFor(id) ?: continue
            readDesc(code)?.let { descs[id] = it }
        }
        val kelvinMode = descs[SettingId.WhiteBalance]?.current == 0x8012L
        return descs.filterKeys { it != SettingId.ColorTemp || kelvinMode }.map { (id, d) -> toSetting(id, d) }
    }

    override fun stepSetting(id: SettingId, direction: Int) {
        val code = codeFor(id) ?: return
        val d = readDesc(code) ?: return
        if (!d.writable) throw PtpException("${id.label} can't be changed now — check the camera's mode", Ptp.RC_GENERAL_ERROR)
        val target = targetFor(id, d, direction) ?: return
        val r = session.retryBusy {
            session.transaction(Ptp.OP_SET_DEVICE_PROP_VALUE, longArrayOf(code.toLong()), dataOut = PropDescParser.encode(d.dataType, target))
        }
        if (!r.ok) throw PtpException("Camera refused ${id.label} (${Ptp.responseName(r.code)}) — check the camera's mode", r.code)
        readDesc(code)?.let { descs[id] = it }
        log.log("${id.label} → ${SettingFormat.format(id, code, target)}")
    }

    companion object {
        /** How long to wait for the JPEG half of a RAW+JPEG pair before treating a RAW as RAW-only. */
        const val RAW_WAIT_MS = 1500L
    }
}
