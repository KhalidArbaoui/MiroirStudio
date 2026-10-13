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

    /** Live-view coordinate space for ChangeAfArea, read from the frame header. */
    private var liveHeader: NikonLiveHeader? = null
    private var headerLogged = false
    /** After moving the AF point, log where the camera says it is, to check the coordinates. */
    @Volatile private var logAfCentre = false

    override fun liveViewFrame(): ByteArray? {
        val r = session.transaction(Ptp.OP_NIKON_GET_LIVE_VIEW_IMG, timeoutMs = 3000)
        return when {
            r.ok -> {
                liveRestarts = 0
                NikonLiveHeader.parse(r.data)?.let { liveHeader = it }
                if (!headerLogged) {
                    headerLogged = true
                    val raw = r.data.take(32).joinToString(" ") { "%02X".format(it) }
                    log.log("Nikon live view header: ${liveHeader ?: "not recognised"} (${r.data.size} bytes) [$raw]")
                }
                if (logAfCentre) {
                    logAfCentre = false
                    liveHeader?.let { log.log("Nikon AF area now centred at ${it.afCenterX},${it.afCenterY}") }
                }
                JpegExtractor.firstJpeg(r.data)
            }
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

    override fun focusAt(u: Float, v: Float): FocusOutcome {
        val space = liveHeader?.let { it.wholeWidth to it.wholeHeight } ?: lastImageSize
        if (space == null) {
            log.log("Nikon AF point not moved: no coordinate space yet (no live-view header, no photo)")
            autofocus()
            return FocusOutcome(false, true, "Take one photo first, then tap to focus — the app needs the camera's frame size")
        }
        val x = (u.coerceIn(0f, 1f) * space.first).toLong()
        val y = (v.coerceIn(0f, 1f) * space.second).toLong()
        val r = session.retryBusy { session.transaction(Ptp.OP_NIKON_CHANGE_AF_AREA, longArrayOf(x, y)) }
        log.log("Nikon AF point → $x,$y of ${space.first}x${space.second}${if (liveHeader == null) " (from photo size)" else ""}: ${Ptp.responseName(r.code)}")
        if (!r.ok) {
            autofocus()
            return FocusOutcome(
                false, true,
                "The Z8 didn't move its focus point (${Ptp.responseName(r.code)}). On the camera set Focus mode AF-S " +
                    "and AF-area mode Single-point or Wide-area — not Auto-area",
            )
        }
        waitReady()
        logAfCentre = true
        autofocus()
        return FocusOutcome(true, true)
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

/**
 * Start of a Nikon live-view object (big-endian): JPEG size, then the "whole" area that
 * ChangeAfArea coordinates refer to, then the displayed area and the current AF area.
 */
data class NikonLiveHeader(
    val jpegWidth: Int, val jpegHeight: Int,
    val wholeWidth: Int, val wholeHeight: Int,
    val afCenterX: Int, val afCenterY: Int,
) {
    companion object {
        /** Nikon documents this header as big-endian; accept little-endian too in case a body differs. */
        fun parse(d: ByteArray): NikonLiveHeader? {
            if (d.size < 24 || (d[0].toInt() == -1 && d[1].toInt() == -40)) return null // starts with JPEG: no header
            fun be(i: Int) = ((d[i].toInt() and 0xFF) shl 8) or (d[i + 1].toInt() and 0xFF)
            fun le(i: Int) = (d[i].toInt() and 0xFF) or ((d[i + 1].toInt() and 0xFF) shl 8)
            for (read in listOf(::be, ::le)) {
                val h = NikonLiveHeader(read(0), read(2), read(4), read(6), read(20), read(22))
                val sane = h.jpegWidth in 160..4000 && h.jpegHeight in 120..4000 &&
                    h.wholeWidth in h.jpegWidth..20000 && h.wholeHeight in h.jpegHeight..20000
                if (sane) return h
            }
            return null
        }
    }
}
