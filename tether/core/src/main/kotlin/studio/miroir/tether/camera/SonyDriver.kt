package studio.miroir.tether.camera

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
                return
            }
            Thread.sleep(100)
        }
        log.log("Sony did not return properties — is USB Connection set to PC Remote?")
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

    override fun readSettings(): List<CameraSetting> {
        val kelvinMode = props[Ptp.PROP_WHITE_BALANCE]?.current == 0x8012L
        return settingCodes.mapNotNull { (id, code) ->
            if (id == SettingId.ColorTemp && !kelvinMode) return@mapNotNull null
            props[code]?.let { toSetting(id, it, adjustable = true) }
        }
    }

    override fun stepSetting(id: SettingId, direction: Int) {
        val code = settingCodes[id] ?: return
        refreshProps(force = true)
        val d = props[code] ?: return
        val before = settingKey(id, code, d.current)
        val target = targetFor(id, d, direction)

        // 1) Absolute value, when the camera lists the allowed values.
        if (target != null && setAbsolute(code, d.dataType, target)) {
            log.log("${id.label} → ${SettingFormat.format(id, code, target)}")
            return
        }
        if (id !in dialSettings) {
            throw PtpException("Camera refused ${id.label} — check the camera's mode", Ptp.RC_GENERAL_ERROR)
        }

        // 2) Dial click, learning which way +1 turns.
        val sign = clickSign[code] ?: 1
        click(code, sign * direction)
        val after = current(id, code) ?: return
        val moved = after - before
        if (moved != 0.0 && (moved > 0) != (direction > 0)) {
            log.log("${id.label}: dial runs the other way, correcting")
            clickSign[code] = -sign
            click(code, -sign * direction)
            click(code, -sign * direction)
            current(id, code)
        } else if (moved != 0.0) {
            clickSign[code] = sign
        } else {
            throw PtpException("${id.label} didn't change — check the camera's mode (M/S/A)", Ptp.RC_GENERAL_ERROR)
        }
    }

    private fun setAbsolute(code: Int, type: Int, value: Long): Boolean {
        val r = session.transaction(
            Ptp.OP_SONY_SET_CONTROL_DEVICE_A, longArrayOf(code.toLong()),
            dataOut = PropDescParser.encode(type, value),
        )
        if (!r.ok) return false
        Thread.sleep(250)
        refreshProps(force = true)
        return props[code]?.current == value
    }

    private fun click(code: Int, direction: Int) {
        session.transaction(
            Ptp.OP_SONY_SET_CONTROL_DEVICE_B, longArrayOf(code.toLong()),
            dataOut = byteArrayOf(if (direction > 0) 0x01 else 0xFF.toByte()),
        )
        Thread.sleep(250)
    }

    private fun current(id: SettingId, code: Int): Double? {
        refreshProps(force = true)
        return props[code]?.let { settingKey(id, code, it.current) }
    }

    override fun batteryLevel(): Int? {
        val v = props[Ptp.SONY_PROP_BATTERY]?.current ?: return null
        return v.toInt().takeIf { it in 0..100 }
    }
}
