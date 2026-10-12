package studio.miroir.tether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.miroir.tether.camera.CameraDriver
import studio.miroir.tether.camera.CameraListener
import studio.miroir.tether.camera.DownloadedFile
import studio.miroir.tether.camera.DriverOptions
import studio.miroir.tether.camera.SettingFormat
import studio.miroir.tether.camera.SettingId
import studio.miroir.tether.ptp.ByteReader
import studio.miroir.tether.ptp.ByteWriter
import studio.miroir.tether.ptp.Ptp
import studio.miroir.tether.ptp.PtpSession
import studio.miroir.tether.ptp.TetherLog

class SettingsTest {
    private val log = TetherLog { println("LOG $it") }
    private val noFiles = object : CameraListener {
        override fun onFile(file: DownloadedFile) {}
        override fun onProgress(filename: String, done: Long, total: Long) {}
    }

    @Test
    fun formatsExposureValues() {
        assertEquals("1/125", SettingFormat.format(SettingId.Shutter, Ptp.NIKON_PROP_EXPOSURE_TIME, (1L shl 16) or 125))
        assertEquals("2s", SettingFormat.format(SettingId.Shutter, Ptp.SONY_PROP_SHUTTER, (20L shl 16) or 10))
        assertEquals("1/250", SettingFormat.format(SettingId.Shutter, Ptp.PROP_EXPOSURE_TIME, 40))
        assertEquals("f/5.6", SettingFormat.format(SettingId.Aperture, Ptp.PROP_F_NUMBER, 560))
        assertEquals("f/8", SettingFormat.format(SettingId.Aperture, Ptp.PROP_F_NUMBER, 800))
        assertEquals("ISO 400", SettingFormat.format(SettingId.Iso, Ptp.SONY_PROP_ISO, 400))
        assertEquals("ISO AUTO", SettingFormat.format(SettingId.Iso, Ptp.SONY_PROP_ISO, 0xFFFFFF))
        assertEquals("-0.7 EV", SettingFormat.format(SettingId.ExposureComp, Ptp.PROP_EXPOSURE_BIAS, (-700L) and 0xFFFF))
        assertEquals("+1.0 EV", SettingFormat.format(SettingId.ExposureComp, Ptp.PROP_EXPOSURE_BIAS, 1000))
        assertEquals("Cloudy", SettingFormat.format(SettingId.WhiteBalance, Ptp.PROP_WHITE_BALANCE, 0x8010))
        assertEquals("M", SettingFormat.format(SettingId.Mode, Ptp.PROP_EXPOSURE_PROGRAM, 1))
    }

    @Test
    fun neighborStepsAndStopsAtEnds() {
        val v = listOf(100L, 200L, 400L)
        assertEquals(400L, SettingFormat.neighbor(v, 200, 1))
        assertEquals(100L, SettingFormat.neighbor(v, 200, -1))
        assertNull(SettingFormat.neighbor(v, 400, 1))
        assertEquals(400L, SettingFormat.neighbor(v, 300, 1)) // unlisted current
    }

    /** Standard PropDesc: u16 enum form. */
    private fun desc(code: Int, type: Int, current: Long, values: List<Long>, writable: Boolean = true): ByteArray {
        val w = ByteWriter()
        w.u16(code).u16(type).u8(if (writable) 1 else 0)
        fun v(x: Long) { if (type == 6) w.u32(x) else w.u16(x.toInt()) }
        v(values.first()); v(current)
        w.u8(2).u16(values.size); values.forEach { v(it) }
        return w.toByteArray()
    }

    @Test
    fun nikonStepsShutterFasterWhateverTheCameraOrder() {
        // Camera lists slowest first: 1/60, 1/125, 1/250 (as num<<16|den).
        val shutters = listOf((1L shl 16) or 60, (1L shl 16) or 125, (1L shl 16) or 250)
        var current = shutters[1]
        val cam = FakeCamera { cmd ->
            when (cmd.code) {
                Ptp.OP_GET_DEVICE_PROP_DESC -> when (cmd.params[0].toInt()) {
                    Ptp.NIKON_PROP_EXPOSURE_TIME -> FakeReply(data = desc(Ptp.NIKON_PROP_EXPOSURE_TIME, 6, current, shutters))
                    Ptp.PROP_F_NUMBER -> FakeReply(data = desc(Ptp.PROP_F_NUMBER, 4, 560, listOf(400L, 560L, 800L)))
                    else -> FakeReply(Ptp.RC_OPERATION_NOT_SUPPORTED + 0x0E) // DevicePropNotSupported
                }
                Ptp.OP_SET_DEVICE_PROP_VALUE -> {
                    current = ByteReader(cmd.dataOut!!).u32(); FakeReply()
                }
                else -> FakeReply()
            }
        }.apply { dataOutOps = setOf(Ptp.OP_SET_DEVICE_PROP_VALUE) }
        val session = PtpSession(cam, log)
        session.openSession()
        val d = CameraDriver.create(Ptp.USB_VENDOR_NIKON, session, log, noFiles, DriverOptions())
        val settings = d.readSettings()
        assertEquals(listOf(SettingId.Shutter, SettingId.Aperture), settings.map { it.id })
        assertEquals("1/125", settings[0].display)
        d.stepSetting(SettingId.Shutter, +1)
        assertEquals(shutters[2], current) // faster = 1/250
        d.stepSetting(SettingId.Shutter, -1)
        d.stepSetting(SettingId.Shutter, -1)
        assertEquals(shutters[0], current)
    }

    @Test
    fun sonyLearnsDialDirection() {
        val apertures = listOf(400L, 560L, 800L)
        var idx = 1
        fun props(): ByteArray {
            val w = ByteWriter()
            w.u32(1L).u32(0L)
            // f-number with no enum form: Sony only accepts dial clicks.
            w.u16(Ptp.PROP_F_NUMBER).u16(4).u8(1).u8(1).u16(0).u16(apertures[idx].toInt()).u8(0)
            return w.toByteArray()
        }
        val cam = FakeCamera { cmd ->
            when (cmd.code) {
                Ptp.OP_SONY_SDIO_CONNECT, Ptp.OP_SONY_GET_EXT_DEVICE_INFO -> FakeReply(data = ByteArray(8))
                Ptp.OP_SONY_GET_ALL_DEVICE_PROP_DATA -> FakeReply(data = props())
                Ptp.OP_SONY_SET_CONTROL_DEVICE_B -> {
                    // This body turns the "wrong" way: +1 click = wider aperture.
                    val click = cmd.dataOut!![0].toInt()
                    idx = (idx + if (click == 1) -1 else 1).coerceIn(0, 2)
                    FakeReply()
                }
                else -> FakeReply()
            }
        }
        val session = PtpSession(cam, log)
        session.openSession()
        val d = CameraDriver.create(Ptp.USB_VENDOR_SONY, session, log, noFiles, DriverOptions())
        d.connect()
        assertEquals("f/5.6", d.readSettings().first { it.id == SettingId.Aperture }.display)
        d.stepSetting(SettingId.Aperture, +1)
        assertEquals("f/8", d.readSettings().first { it.id == SettingId.Aperture }.display)
        d.stepSetting(SettingId.Aperture, -1)
        assertEquals("f/5.6", d.readSettings().first { it.id == SettingId.Aperture }.display)
    }
}
