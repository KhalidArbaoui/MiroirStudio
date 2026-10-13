package studio.miroir.tether.camera

import studio.miroir.tether.image.JpegExtractor
import studio.miroir.tether.ptp.ByteWriter
import studio.miroir.tether.ptp.Ptp
import studio.miroir.tether.ptp.PtpException
import studio.miroir.tether.ptp.PtpSession
import studio.miroir.tether.ptp.PropDesc
import studio.miroir.tether.ptp.PropDescParser
import studio.miroir.tether.ptp.TetherLog
import studio.miroir.tether.ptp.hex4

/**
 * Sony Alpha in "PC Remote" USB mode (A7 III and similar).
 * Images are handed over one at a time through the special handle 0xFFFFC001; the camera
 * signals a waiting image through property 0xD215 ("object in memory") >= 0x8000.
 */
class SonyDriver(
    session: PtpSession, log: TetherLog, listener: CameraListener, options: DriverOptions,
) : CameraDriver(session, log, listener, options) {
    override val brand = "Sony"

    private var lastPropPoll = 0L
    private var props: Map<Int, PropDesc> = emptyMap()

    override fun connect() {
        sdioConnect(1)
        sdioConnect(2)
        val ext = session.transaction(Ptp.OP_SONY_GET_EXT_DEVICE_INFO, longArrayOf(0xC8))
        log.log("Sony ext device info: $ext")
        sdioConnect(3)

        // The camera needs a moment after the handshake before it answers property queries.
        repeat(30) {
            if (refreshProps(force = true)) {
                log.log("Sony ready, ${props.size} properties")
                takeSettingsPriority()
                return
            }
            Thread.sleep(100)
        }
        log.log("Sony did not return properties — is USB Connection set to PC Remote?")
    }

    /**
     * "Priority Key Settings": by default the camera's own dials win and remote changes are
     * ignored. Switch it to PC Remote so the tablet can change exposure.
     */
    private fun takeSettingsPriority() {
        val d = props[Ptp.SONY_PROP_PRIORITY_KEY] ?: run {
            log.log("Sony: no priority-key property, skipping")
            return
        }
        log.log("Sony priority key: $d")
        if (d.current == Ptp.SONY_PRIORITY_PC_REMOTE) return
        val r = session.transaction(
            Ptp.OP_SONY_SET_CONTROL_DEVICE_A, longArrayOf(Ptp.SONY_PROP_PRIORITY_KEY.toLong()),
            dataOut = PropDescParser.encode(d.dataType, Ptp.SONY_PRIORITY_PC_REMOTE),
        )
        log.log("Sony priority → PC Remote: $r")
        waitFor(Ptp.SONY_PROP_PRIORITY_KEY, 1500) { it == Ptp.SONY_PRIORITY_PC_REMOTE }
    }

    private fun sdioConnect(phase: Long) {
        val r = session.transaction(Ptp.OP_SONY_SDIO_CONNECT, longArrayOf(phase, 0, 0))
        log.log("Sony SDIOConnect($phase): $r")
    }

    private fun control(code: Int, value: Int) {
        val r = session.transaction(
            Ptp.OP_SONY_SET_CONTROL_DEVICE_B, longArrayOf(code.toLong()),
            dataOut = ByteWriter(2).u16(value).toByteArray(),
        )
        if (!r.ok) throw PtpException("Sony control ${code.hex4()} failed: ${Ptp.responseName(r.code)}", r.code)
    }

    override fun capture() {
        control(Ptp.SONY_CTRL_S1_HALF_PRESS, Ptp.SONY_PRESS)
        Thread.sleep(200)
        control(Ptp.SONY_CTRL_S2_RELEASE, Ptp.SONY_PRESS)
        Thread.sleep(100)
        control(Ptp.SONY_CTRL_S2_RELEASE, Ptp.SONY_RELEASE)
        control(Ptp.SONY_CTRL_S1_HALF_PRESS, Ptp.SONY_RELEASE)
    }

    // ---------------------------------------------------------------- live view

    override val supportsLiveView = true

    /** Sony streams live view through the special object handle 0xFFFFC002 in PC Remote mode. */
    override fun liveViewFrame(): ByteArray? {
        val r = session.transaction(Ptp.OP_GET_OBJECT, longArrayOf(Ptp.SONY_LIVE_VIEW_HANDLE), timeoutMs = 3000)
        return when {
            r.ok -> JpegExtractor.firstJpeg(r.data)
            r.code == Ptp.RC_DEVICE_BUSY || r.code == Ptp.RC_INVALID_OBJECT_HANDLE -> null
            else -> throw PtpException("Live view frame failed: ${Ptp.responseName(r.code)}", r.code)
        }
    }

    override fun focusAt(u: Float, v: Float): FocusOutcome {
        val x = (u.coerceIn(0f, 1f) * 639).toLong()
        val y = (v.coerceIn(0f, 1f) * 479).toLong()
        val value = (x shl 16) or y
        val r = session.transaction(
            Ptp.OP_SONY_SET_CONTROL_DEVICE_A, longArrayOf(Ptp.SONY_PROP_AF_AREA_POSITION.toLong()),
            dataOut = ByteWriter(4).u32(value).toByteArray(),
        )
        // Only trust the move if the camera reads the new position back (older bodies say OK and ignore it).
        val readBack = if (r.ok) waitFor(Ptp.SONY_PROP_AF_AREA_POSITION, 600) { it == value } else null
        log.log("Sony AF point → $x,$y: ${Ptp.responseName(r.code)}, read back ${props[Ptp.SONY_PROP_AF_AREA_POSITION]?.current?.let { "0x%08X".format(it) } ?: "none"}")
        val locked = halfPressFocus()
        if (locked == false) throw PtpException("Camera couldn't focus — tap an edge, the label or a texture", Ptp.RC_GENERAL_ERROR)
        if (readBack == null) {
            return FocusOutcome(
                false, locked != null,
                "The A7 III can't take a focus point over USB (newer Sonys can). Set Focus Area to Flexible Spot " +
                    "and move the point on the camera; the tablet's AF then focuses there",
            )
        }
        return FocusOutcome(true, locked != null)
    }

    override fun autofocus() {
        val locked = halfPressFocus()
        if (locked == false) throw PtpException("Camera couldn't focus — tap an edge, the label or a texture", Ptp.RC_GENERAL_ERROR)
    }

    /**
     * Half-press until the camera reports focus (prop 0xD213 = 2) or ~1.5 s pass.
     * Returns true = locked, false = camera said it failed, null = camera doesn't report it.
     */
    private fun halfPressFocus(): Boolean? {
        control(Ptp.SONY_CTRL_S1_HALF_PRESS, Ptp.SONY_PRESS)
        try {
            val v = waitFor(Ptp.SONY_PROP_FOCUS_FOUND, 1500) { it == 2L || it == 3L }
            log.log("Sony focus indicator: ${v ?: props[Ptp.SONY_PROP_FOCUS_FOUND]?.current ?: "not reported"}")
            return when (v) { 2L -> true; 3L -> false; else -> null }
        } finally {
            control(Ptp.SONY_CTRL_S1_HALF_PRESS, Ptp.SONY_RELEASE)
        }
    }

    private fun refreshProps(force: Boolean = false): Boolean {
        val now = System.currentTimeMillis()
        if (!force && now - lastPropPoll < 250) return false
        lastPropPoll = now
        val r = session.transaction(Ptp.OP_SONY_GET_ALL_DEVICE_PROP_DATA)
        if (!r.ok) return false
        props = try {
            PropDescParser.parseSonyAll(r.data)
        } catch (e: PtpException) {
            log.log("Sony property parse error: ${e.message}")
            return false
        }
        return true
    }

    private fun objectInMemory(): Int = (props[Ptp.SONY_PROP_OBJECT_IN_MEMORY]?.current ?: 0L).toInt()

    override fun poll() {
        val announced = drainInterruptEvents().any { it.code == Ptp.EV_SONY_OBJECT_ADDED }
        val refreshed = refreshProps(force = announced)
        if (!refreshed && !announced) return

        var guard = 0
        while ((objectInMemory() >= 0x8000 || (announced && guard == 0)) && guard < 8) {
            guard++
            val info = try {
                session.getObjectInfo(Ptp.SONY_IN_MEMORY_HANDLE)
            } catch (e: PtpException) {
                log.log("Sony: no object ready (${e.message})")
                break
            }
            log.log("New object: $info")
            // Sony must hand over every file (RAW too) before the next one is available.
            val data = session.getObject(Ptp.SONY_IN_MEMORY_HANDLE) { done, total ->
                listener.onProgress(info.filename, done, total)
            }
            log.log("Downloaded ${info.filename} (${data.size / 1024} KB)")
            listener.onFile(DownloadedFile(info, data, isFullFile = true))
            Thread.sleep(50)
            refreshProps(force = true)
        }
    }

    // ---------------------------------------------------------------- settings

    private val settingCodes = linkedMapOf(
        SettingId.Mode to Ptp.PROP_EXPOSURE_PROGRAM,
        SettingId.Shutter to Ptp.SONY_PROP_SHUTTER,
        SettingId.Aperture to Ptp.PROP_F_NUMBER,
        SettingId.Iso to Ptp.SONY_PROP_ISO,
        SettingId.ExposureComp to Ptp.PROP_EXPOSURE_BIAS,
        SettingId.WhiteBalance to Ptp.PROP_WHITE_BALANCE,
        SettingId.ColorTemp to Ptp.SONY_PROP_COLOR_TEMPERATURE,
    )

    /** Settings Sony changes by "dial clicks" rather than absolute values on older bodies. */
    private val dialSettings = setOf(SettingId.Shutter, SettingId.Aperture, SettingId.ExposureComp)

    /** Learned direction of a +1 dial click per property (cameras differ). */
    private val clickSign = HashMap<Int, Int>()

    /** false once the camera has ignored an absolute set for a property: go straight to clicks. */
    private val absoluteWorks = HashMap<Int, Boolean>()

    /** Values for the pick list: the camera's own list, or Sony's standard stops when it sends none. */
    private fun pickValues(id: SettingId, code: Int, d: PropDesc): List<Long> {
        val own = d.allowedValues
        val values = if (own.size > 1) own else SonyStops.forSetting(id)
        return if (id == SettingId.WhiteBalance) values else values.sortedBy { settingKey(id, code, it) }
    }

    override fun readSettings(): List<CameraSetting> {
        val kelvinMode = props[Ptp.PROP_WHITE_BALANCE]?.current == 0x8012L
        return settingCodes.mapNotNull { (id, code) ->
            if (id == SettingId.ColorTemp && !kelvinMode) return@mapNotNull null
            props[code]?.let { d ->
                val values = SettingFormat.pickList(id, pickValues(id, code, d))
                CameraSetting(
                    id, SettingFormat.format(id, code, d.current), adjustable = id != SettingId.Mode,
                    // Aperture read-only usually means the lens ring sets it; we still try the dial.
                    lockedBy = if (id == SettingId.Aperture && !d.writable) "Ring on A?" else null,
                    options = values.map { SettingOption(it, SettingFormat.format(id, code, it)) },
                    current = d.current,
                )
            }
        }
    }

    override fun stepSetting(id: SettingId, direction: Int) {
        val code = settingCodes[id] ?: return
        refreshProps(force = true)
        val d = props[code] ?: return
        log.log("Step ${id.label} ${if (direction > 0) "+" else "-"}: now $d")
        // Exposure dials (shutter, aperture, EV) are moved by clicks, like libgphoto2 does on Sony.
        if (id !in dialSettings && absoluteWorks[code] != false) {
            val target = targetFor(id, d, direction)
            if (target != null) {
                val r = session.transaction(
                    Ptp.OP_SONY_SET_CONTROL_DEVICE_A, longArrayOf(code.toLong()),
                    dataOut = PropDescParser.encode(d.dataType, target),
                )
                log.log("Set ${id.label} = ${SettingFormat.format(id, code, target)}: $r")
                if (r.ok && waitFor(code, 1200) { it == target } != null) { absoluteWorks[code] = true; return }
                absoluteWorks[code] = false
                log.log("${id.label}: absolute set ignored, using dial clicks from now on")
            }
        }
        try {
            stepByClick(id, code, d.current, direction)
        } catch (e: PtpException) {
            if (d.enabled) throw e
            throw PtpException(
                if (id == SettingId.Aperture) "The camera has aperture locked — use an E-mount AF lens with its aperture ring on A"
                else "The camera has ${id.label} locked right now (mode, custom key or open menu)",
                e.responseCode,
            )
        }
    }

    /**
     * Jump to [value]. Absolute set where the camera takes it; otherwise one click to learn the
     * dial's direction, a quick burst of the remaining clicks, then fine-tuning one at a time.
     */
    override fun setSetting(id: SettingId, value: Long) {
        val code = settingCodes[id] ?: return
        refreshProps(force = true)
        val d = props[code] ?: return
        val goal = settingKey(id, code, value)
        fun now() = props[code]?.current
        fun left(): Double = now()?.let { goal - settingKey(id, code, it) } ?: 0.0
        if (left() == 0.0) return
        log.log("Jump ${id.label}: ${SettingFormat.format(id, code, d.current)} → ${SettingFormat.format(id, code, value)}")

        if (id !in dialSettings && absoluteWorks[code] != false) {
            val r = session.transaction(
                Ptp.OP_SONY_SET_CONTROL_DEVICE_A, longArrayOf(code.toLong()),
                dataOut = PropDescParser.encode(d.dataType, value),
            )
            if (r.ok && waitFor(code, 1200) { it == value } != null) { absoluteWorks[code] = true; return }
            absoluteWorks[code] = false
            log.log("${id.label}: absolute set ignored, using dial clicks from now on")
        }

        // How many clicks: distance between the two values in the pick list.
        val list = pickValues(id, code, d)
        fun index(key: Double) = list.indices.minByOrNull { kotlin.math.abs(settingKey(id, code, list[it]) - key) } ?: 0
        val firstDir = if (left() > 0) 1 else -1
        stepSetting(id, firstDir) // also learns which way the dial runs
        var remaining = index(goal) - index(goal - left())
        if (kotlin.math.abs(remaining) > 1 && (remaining > 0) == (firstDir > 0)) {
            val sign = clickSign[code] ?: 1
            val dir = if (remaining > 0) 1 else -1
            repeat(kotlin.math.abs(remaining) - 1) { click(code, sign * dir); Thread.sleep(70) }
            waitSettled(code)
        }
        repeat(6) {
            val l = left()
            if (l == 0.0) return
            val before = now()
            stepSetting(id, if (l > 0) 1 else -1)
            val after = left()
            if (now() == before || (after != 0.0 && (after > 0) != (l > 0))) return // at the end, or the goal isn't offered
        }
    }

    /** Waits until property [code] stops changing (a burst of clicks has landed). */
    private fun waitSettled(code: Int) {
        var last = props[code]?.current
        var stableSince = System.currentTimeMillis()
        val end = stableSince + 3000
        while (System.currentTimeMillis() < end) {
            Thread.sleep(120)
            if (!refreshProps(force = true)) continue
            val v = props[code]?.current
            if (v != last) { last = v; stableSince = System.currentTimeMillis() }
            else if (System.currentTimeMillis() - stableSince > 400) return
        }
    }

    private fun stepByClick(id: SettingId, code: Int, start: Long, direction: Int) {
        val before = settingKey(id, code, start)
        val sign = clickSign[code] ?: 1
        click(code, sign * direction)
        val now = waitFor(code, 2000) { it != start }
        if (now == null) {
            throw PtpException(
                "${id.label} didn't change. Check: mode dial (M = all, A = aperture, S = shutter), " +
                    "lens aperture ring on A, ISO not on a custom key lock, and no menu open on the camera.",
                Ptp.RC_GENERAL_ERROR,
            )
        }
        val moved = settingKey(id, code, now) - before
        if (moved != 0.0 && (moved > 0) != (direction > 0)) {
            log.log("${id.label}: dial runs the other way, correcting")
            clickSign[code] = -sign
            click(code, -sign * direction)
            waitFor(code, 2000) { it != now }
            val after = props[code]?.current ?: return
            click(code, -sign * direction)
            waitFor(code, 2000) { it != after }
        } else {
            clickSign[code] = sign
        }
        log.log("${id.label} → ${props[code]?.let { SettingFormat.format(id, code, it.current) }}")
    }

    private fun click(code: Int, direction: Int) {
        val r = session.transaction(
            Ptp.OP_SONY_SET_CONTROL_DEVICE_B, longArrayOf(code.toLong()),
            dataOut = byteArrayOf(if (direction > 0) 0x01 else 0xFF.toByte()),
        )
        if (!r.ok) log.log("Sony click ${"0x%04X".format(code)}: $r")
    }

    /** Polls the camera until property [code] satisfies [done]; returns the value, or null on timeout. */
    private fun waitFor(code: Int, timeoutMs: Long, done: (Long) -> Boolean): Long? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            Thread.sleep(120)
            if (refreshProps(force = true)) {
                val v = props[code]?.current
                if (v != null && done(v)) return v
            }
        }
        return null
    }

    override fun batteryLevel(): Int? {
        val v = props[Ptp.SONY_PROP_BATTERY]?.current ?: return null
        return v.toInt().takeIf { it in 0..100 }
    }
}
