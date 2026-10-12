package studio.miroir.tether.camera

import studio.miroir.tether.image.JpegExtractor
import studio.miroir.tether.ptp.NikonEvents
import studio.miroir.tether.ptp.ObjectInfo
import studio.miroir.tether.ptp.Ptp
import studio.miroir.tether.ptp.PtpEvent
import studio.miroir.tether.ptp.PtpException
import studio.miroir.tether.ptp.PtpSession
import studio.miroir.tether.ptp.TetherLog

/**
 * Nikon Z (Z8, Z9, Z6/Z7 series…). Captures to the memory card so the card always keeps a copy,
 * and polls Nikon CheckEvent because Nikon bodies don't reliably use the interrupt pipe.
 */
class NikonDriver(
    session: PtpSession, log: TetherLog, listener: CameraListener, options: DriverOptions,
) : GenericDriver(session, log, listener, options) {
    override val brand = "Nikon"

    private var useCheckEvent = true
    private var useRecInMedia = true
    private var lastCheck = 0L

    override fun connect() {
        val info = session.deviceInfo
        if (info != null && info.operations.isNotEmpty() && !info.supports(Ptp.OP_NIKON_CHECK_EVENT)) {
            log.log("Camera doesn't list CheckEvent; will try it anyway")
        }
    }

    override fun capture() {
        if (useRecInMedia) {
            // Params: AF per camera setting (0xFFFFFFFF), record to card (0).
            val r = session.retryBusy {
                session.transaction(Ptp.OP_NIKON_CAPTURE_REC_IN_MEDIA, longArrayOf(0xFFFFFFFFL, 0))
            }
            when {
                r.ok -> { waitReady(); return }
                r.code == Ptp.RC_OPERATION_NOT_SUPPORTED -> {
                    log.log("InitiateCaptureRecInMedia not supported, using standard capture")
                    useRecInMedia = false
                }
                r.code == Ptp.RC_NIKON_OUT_OF_FOCUS ->
                    throw PtpException("Camera couldn't focus — check AF or switch to MF", r.code)
                else -> throw PtpException("Capture failed: ${Ptp.responseName(r.code)}", r.code)
            }
        }
        super.capture()
        waitReady()
    }

    private fun waitReady() {
        repeat(80) {
            val r = session.transaction(Ptp.OP_NIKON_DEVICE_READY)
            when (r.code) {
                Ptp.RC_DEVICE_BUSY -> Thread.sleep(100)
                Ptp.RC_NIKON_OUT_OF_FOCUS -> throw PtpException("Camera couldn't focus", r.code)
                else -> return
            }
        }
    }

    // ---------------------------------------------------------------- live view

    override val supportsLiveView = true
    private var liveRestarts = 0

    override fun startLiveView() {
        val r = session.retryBusy { session.transaction(Ptp.OP_NIKON_START_LIVE_VIEW) }
        if (!r.ok) throw PtpException("Live view didn't start: ${Ptp.responseName(r.code)} — exit video mode / close menus", r.code)
        waitReady()
        liveRestarts = 0
        log.log("Nikon live view on")
    }

    override fun liveViewFrame(): ByteArray? {
        val r = session.transaction(Ptp.OP_NIKON_GET_LIVE_VIEW_IMG, timeoutMs = 3000)
        return when {
            r.ok -> JpegExtractor.firstJpeg(r.data)
            r.code == Ptp.RC_DEVICE_BUSY -> null
            r.code == Ptp.RC_NIKON_NOT_LIVE_VIEW && liveRestarts < 3 -> {
                // The camera drops live view after a capture or when its own screen takes over.
                liveRestarts++
                log.log("Nikon left live view, restarting")
                startLiveView()
                null
            }
            else -> throw PtpException("Live view frame failed: ${Ptp.responseName(r.code)}", r.code)
        }
    }

    override fun stopLiveView() {
        runCatching { session.transaction(Ptp.OP_NIKON_END_LIVE_VIEW) }
        log.log("Nikon live view off")
    }

    override fun autofocus() {
        val r = session.retryBusy { session.transaction(Ptp.OP_NIKON_AF_DRIVE) }
        if (!r.ok) throw PtpException("Autofocus failed: ${Ptp.responseName(r.code)}", r.code)
        waitReady()
    }

    override fun pollVendorEvents(): List<PtpEvent> {
        if (!useCheckEvent) return emptyList()
        val now = System.currentTimeMillis()
        if (now - lastCheck < 200) return emptyList()
        lastCheck = now
        val r = session.transaction(Ptp.OP_NIKON_CHECK_EVENT)
        return when {
            r.ok -> NikonEvents.parse(r.data)
            r.code == Ptp.RC_OPERATION_NOT_SUPPORTED -> {
                log.log("CheckEvent not supported; relying on USB interrupt events")
                useCheckEvent = false
                emptyList()
            }
            else -> emptyList()
        }
    }

    override fun fetchRawPreview(handle: Long, info: ObjectInfo) {
        try {
            super.fetchRawPreview(handle, info)
            return
        } catch (e: PtpException) {
            log.log("RAW download failed (${e.message}); falling back to the camera's preview")
        }
        val r = session.retryBusy {
            session.transaction(Ptp.OP_NIKON_GET_LARGE_THUMB, longArrayOf(handle), timeoutMs = 10_000)
        }
        if (r.ok && r.data.size > 1000) {
            log.log("Got small NEF preview for ${info.filename} (${r.data.size / 1024} KB)")
            listener.onFile(DownloadedFile(info, r.data, isFullFile = false))
        }
    }
}
