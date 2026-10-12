package studio.miroir.tether

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import studio.miroir.tether.camera.CameraDriver
import studio.miroir.tether.camera.CameraListener
import studio.miroir.tether.camera.DownloadedFile
import studio.miroir.tether.camera.DriverOptions
import studio.miroir.tether.image.JpegExtractor
import studio.miroir.tether.ptp.Ptp
import studio.miroir.tether.ptp.PtpSession
import studio.miroir.tether.ptp.TetherLog
import kotlin.random.Random

class LiveViewTest {
    private val log = TetherLog { println("LOG $it") }
    private val noFiles = object : CameraListener {
        override fun onFile(file: DownloadedFile) {}
        override fun onProgress(filename: String, done: Long, total: Long) {}
    }

    /** Minimal valid JPEG: SOI, DQT, SOS, entropy bytes (no 0xFF), EOI. */
    private fun jpeg(n: Int, seed: Int): ByteArray {
        val body = Random(seed).nextBytes(n).map { if (it == 0xFF.toByte()) 0 else it }.toByteArray()
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDB.toByte(), 0, 3, 0,
            0xFF.toByte(), 0xDA.toByte(), 0, 2) + body + byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }

    @Test
    fun firstJpegSkipsVendorHeaderAndPadding() {
        val j = jpeg(5000, 1)
        val frame = ByteArray(384) { 0x11 } + j + ByteArray(200)
        assertArrayEquals(j, JpegExtractor.firstJpeg(frame))
        assertNull(JpegExtractor.firstJpeg(ByteArray(100)))
    }

    @Test
    fun nikonLiveViewStartsStreamsAndRestartsWhenDropped() {
        val j = jpeg(20000, 2)
        var inLive = false
        var starts = 0
        val cam = FakeCamera { cmd ->
            when (cmd.code) {
                Ptp.OP_NIKON_START_LIVE_VIEW -> { inLive = true; starts++; FakeReply() }
                Ptp.OP_NIKON_END_LIVE_VIEW -> { inLive = false; FakeReply() }
                Ptp.OP_NIKON_GET_LIVE_VIEW_IMG ->
                    if (inLive) FakeReply(data = ByteArray(512) + j) else FakeReply(Ptp.RC_NIKON_NOT_LIVE_VIEW)
                else -> FakeReply()
            }
        }.apply { dataOutOps = emptySet() }
        val session = PtpSession(cam, log)
        session.openSession()
        val d = CameraDriver.create(Ptp.USB_VENDOR_NIKON, session, log, noFiles, DriverOptions())
        d.startLiveView()
        assertArrayEquals(j, d.liveViewFrame())
        inLive = false // camera drops live view (e.g. after a shot)
        assertNull(d.liveViewFrame())
        assertEquals(2, starts)
        assertArrayEquals(j, d.liveViewFrame())
        d.stopLiveView()
    }

    @Test
    fun sonyLiveViewReadsSpecialHandle() {
        val j = jpeg(15000, 3)
        val cam = FakeCamera { cmd ->
            when (cmd.code) {
                Ptp.OP_GET_OBJECT ->
                    if (cmd.params[0] == Ptp.SONY_LIVE_VIEW_HANDLE) FakeReply(data = ByteArray(16) + j)
                    else FakeReply(Ptp.RC_INVALID_OBJECT_HANDLE)
                else -> FakeReply()
            }
        }
        val session = PtpSession(cam, log)
        session.openSession()
        val d = CameraDriver.create(Ptp.USB_VENDOR_SONY, session, log, noFiles, DriverOptions())
        d.startLiveView()
        assertArrayEquals(j, d.liveViewFrame())
    }
}
