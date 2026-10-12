package studio.miroir.tether.camera

import studio.miroir.tether.ptp.ByteWriter
import studio.miroir.tether.ptp.Ptp
import studio.miroir.tether.ptp.PtpException
import studio.miroir.tether.ptp.PtpSession
import studio.miroir.tether.ptp.SonyProperty
import studio.miroir.tether.ptp.SonyPropertyParser
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
    private var props: Map<Int, SonyProperty> = emptyMap()

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
            SonyPropertyParser.parse(r.data)
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

    override fun batteryLevel(): Int? {
        val v = props[Ptp.SONY_PROP_BATTERY]?.current ?: return null
        return v.toInt().takeIf { it in 0..100 }
    }
}
