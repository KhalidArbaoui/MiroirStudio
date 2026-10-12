package studio.miroir.tether.camera

import studio.miroir.tether.ptp.ObjectInfo
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

    override fun capture() {
        val r = session.retryBusy { session.transaction(Ptp.OP_INITIATE_CAPTURE, longArrayOf(0, 0)) }
        if (!r.ok) throw PtpException("Capture failed: ${Ptp.responseName(r.code)}", r.code)
    }

    override fun poll() {
        handleEvents(drainInterruptEvents() + pollVendorEvents())
    }

    protected open fun pollVendorEvents(): List<PtpEvent> = emptyList()

    protected fun handleEvents(events: List<PtpEvent>) {
        for (e in events) {
            when (e.code) {
                Ptp.EV_OBJECT_ADDED -> {
                    val h = e.param(0)
                    if (seen.add(h)) fetch(h)
                }
                Ptp.EV_NIKON_OBJECT_ADDED_IN_SDRAM -> {
                    val h = e.param(0).takeIf { it != 0L } ?: Ptp.NIKON_SDRAM_HANDLE
                    fetch(h)
                }
                else -> log.log("Ignored $e")
            }
        }
    }

    private fun fetch(handle: Long) {
        val info = try {
            session.getObjectInfo(handle)
        } catch (e: PtpException) {
            log.log("ObjectInfo ${handle.hex8()} failed: ${e.message}")
            return
        }
        log.log("New object: $info")
        if (info.isFolder) return
        if (info.isRaw && !options.copyRaw) {
            fetchRawPreview(handle, info)
            return
        }
        download(handle, info)
    }

    /** RAW not copied in full: get the camera's large preview instead, if it offers one. */
    protected open fun fetchRawPreview(handle: Long, info: ObjectInfo) {
        log.log("Skipping RAW ${info.filename} (Copy RAW is off)")
    }
}
