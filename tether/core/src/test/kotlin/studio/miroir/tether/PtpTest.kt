package studio.miroir.tether

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import studio.miroir.tether.camera.CameraDriver
import studio.miroir.tether.camera.CameraListener
import studio.miroir.tether.camera.DownloadedFile
import studio.miroir.tether.camera.DriverOptions
import studio.miroir.tether.camera.GenericDriver
import studio.miroir.tether.camera.NikonDriver
import studio.miroir.tether.camera.SonyDriver
import studio.miroir.tether.image.Histogram
import studio.miroir.tether.image.JpegExtractor
import studio.miroir.tether.image.NeutralSample
import studio.miroir.tether.ptp.ByteWriter
import studio.miroir.tether.ptp.NikonEvents
import studio.miroir.tether.ptp.Ptp
import studio.miroir.tether.ptp.PtpSession
import studio.miroir.tether.ptp.PropDescParser
import studio.miroir.tether.ptp.TetherLog
import kotlin.random.Random

class PtpTest {
    private val log = TetherLog { println("LOG $it") }

    private class Collector : CameraListener {
        val files = mutableListOf<DownloadedFile>()
        var lastProgress = 0L to 0L
        override fun onFile(file: DownloadedFile) { files += file }
        override fun onProgress(filename: String, done: Long, total: Long) { lastProgress = done to total }
    }

    @Test
    fun deviceInfoParsesModelAndOperations() {
        val cam = FakeCamera { cmd ->
            when (cmd.code) {
                Ptp.OP_GET_DEVICE_INFO -> FakeReply(data = FakeCamera.deviceInfo("Z 8", "Nikon Corporation", listOf(0x1001, 0x90C7)))
                else -> FakeReply()
            }
        }
        val s = PtpSession(cam, log)
        val info = s.readDeviceInfo()
        assertEquals("Z 8", info.model)
        assertEquals("Nikon Corporation", info.manufacturer)
        assertTrue(info.supports(0x90C7))
        assertEquals(0L, cam.commands.size.toLong() - 1)
    }

    @Test
    fun largeObjectIsReassembledAcrossTransfers() {
        val big = Random(1).nextBytes(3_500_123)
        val cam = FakeCamera { cmd -> if (cmd.code == Ptp.OP_GET_OBJECT) FakeReply(data = big) else FakeReply() }
        val s = PtpSession(cam, log)
        s.openSession()
        var last = 0L
        val got = s.getObject(5) { done, total -> assertEquals(big.size.toLong(), total); last = done }
        assertArrayEquals(big, got)
        assertEquals(big.size.toLong(), last)
    }

    @Test
    fun smallReadChunksStillReassembleLargeObjects() {
        val big = Random(13).nextBytes(1_234_567)
        val cam = FakeCamera { cmd -> if (cmd.code == Ptp.OP_GET_OBJECT) FakeReply(data = big) else FakeReply() }
        val s = PtpSession(cam, log)
        s.readChunk = PtpSession.SMALL_READ_CHUNK
        s.drain()
        s.openSession()
        assertArrayEquals(big, s.getObject(9))
        assertTrue(s.transaction(Ptp.OP_CLOSE_SESSION).ok)
    }

    @Test
    fun exactPacketMultipleWithoutZlpStillFindsResponse() {
        // 12-byte header + 1012 bytes = 1024 = 2 packets, camera sends response in the same transfer.
        val payload = Random(2).nextBytes(1012)
        val cam = FakeCamera(mergeExactPackets = true) { cmd ->
            if (cmd.code == Ptp.OP_GET_OBJECT) FakeReply(data = payload) else FakeReply()
        }
        val s = PtpSession(cam, log)
        s.openSession()
        assertArrayEquals(payload, s.getObject(1))
        // Next transaction must still be in sync.
        assertTrue(s.transaction(Ptp.OP_CLOSE_SESSION).ok)
    }

    @Test
    fun exactPacketMultipleWithZlpWorks() {
        val payload = Random(3).nextBytes(512 * 4 - 12)
        val cam = FakeCamera { cmd -> if (cmd.code == Ptp.OP_GET_OBJECT) FakeReply(data = payload) else FakeReply() }
        val s = PtpSession(cam, log)
        s.openSession()
        assertArrayEquals(payload, s.getObject(1))
        assertTrue(s.transaction(Ptp.OP_CLOSE_SESSION).ok)
    }

    @Test
    fun sessionAlreadyOpenIsRecovered() {
        var opens = 0
        val cam = FakeCamera { cmd ->
            if (cmd.code == Ptp.OP_OPEN_SESSION && opens++ == 0) FakeReply(Ptp.RC_SESSION_ALREADY_OPEN) else FakeReply()
        }
        PtpSession(cam, log).openSession()
        assertEquals(listOf(Ptp.OP_OPEN_SESSION, Ptp.OP_CLOSE_SESSION, Ptp.OP_OPEN_SESSION), cam.commands.map { it.code })
    }

    @Test
    fun nikonEventsParse() {
        val d = ByteWriter().u16(2).u16(Ptp.EV_OBJECT_ADDED).u32(0x1234L).u16(Ptp.EV_DEVICE_PROP_CHANGED).u32(0x5001L).toByteArray()
        val ev = NikonEvents.parse(d)
        assertEquals(2, ev.size)
        assertEquals(Ptp.EV_OBJECT_ADDED, ev[0].code)
        assertEquals(0x1234L, ev[0].param(0))
    }

    private fun sonyProps(objectInMemory: Int): ByteArray {
        val w = ByteWriter()
        w.u32(3L).u32(0L) // u64 count = 3
        // u16 enum property (e.g. white balance) with enum form
        w.u16(0x5005).u16(4).u8(1).u8(1).u16(2).u16(4).u8(2).u16(3).u16(2).u16(4).u16(0x8001)
        // string property, no form
        w.u16(0xD2FF).u16(0xFFFF).u8(0).u8(1)
        FakeCamera.str(w, "a"); FakeCamera.str(w, "abc"); w.u8(0)
        // object in memory, u16 range form
        w.u16(Ptp.SONY_PROP_OBJECT_IN_MEMORY).u16(4).u8(0).u8(1).u16(0).u16(objectInMemory).u8(1).u16(0).u16(0xFFFF).u16(1)
        return w.toByteArray()
    }

    @Test
    fun sonyPropertiesParse() {
        val p = PropDescParser.parseSonyAll(sonyProps(0x8001))
        assertEquals(3, p.size)
        assertEquals(4L, p[0x5005]!!.current)
        assertEquals("abc", p[0xD2FF]!!.currentString)
        assertEquals(0x8001L, p[Ptp.SONY_PROP_OBJECT_IN_MEMORY]!!.current)
    }

    @Test
    fun sonyDriverHandshakeCaptureAndDownloadsQueuedFiles() {
        val queue = ArrayDeque(listOf("DSC00001.ARW" to Random(4).nextBytes(200_000), "DSC00001.JPG" to Random(5).nextBytes(90_000)))
        val cam = FakeCamera { cmd ->
            when (cmd.code) {
                Ptp.OP_SONY_SDIO_CONNECT, Ptp.OP_SONY_GET_EXT_DEVICE_INFO -> FakeReply(data = ByteArray(8))
                Ptp.OP_SONY_GET_ALL_DEVICE_PROP_DATA -> FakeReply(data = sonyProps(if (queue.isEmpty()) 0 else 0x8001))
                Ptp.OP_GET_OBJECT_INFO -> queue.firstOrNull()?.let {
                    FakeReply(data = FakeCamera.objectInfo(it.first, if (it.first.endsWith("ARW")) Ptp.FMT_SONY_ARW else Ptp.FMT_JPEG, it.second.size))
                } ?: FakeReply(Ptp.RC_INVALID_OBJECT_HANDLE)
                Ptp.OP_GET_OBJECT -> FakeReply(data = queue.removeFirst().second)
                else -> FakeReply()
            }
        }
        val session = PtpSession(cam, log)
        session.openSession()
        val out = Collector()
        val d = CameraDriver.create(Ptp.USB_VENDOR_SONY, session, log, out, DriverOptions())
        assertTrue(d is SonyDriver)
        d.connect()
        d.capture()
        val controls = cam.commands.filter { it.code == Ptp.OP_SONY_SET_CONTROL_DEVICE_B }
        assertEquals(listOf(0xD2C1L, 0xD2C2L, 0xD2C2L, 0xD2C1L), controls.map { it.params[0] })
        assertEquals(listOf(2, 2, 1, 1), controls.map { (it.dataOut!![0].toInt()) })

        Thread.sleep(300)
        d.poll()
        assertEquals(listOf("DSC00001.ARW", "DSC00001.JPG"), out.files.map { it.info.filename })
        assertTrue(out.files[0].info.isRaw)
        assertTrue(out.files[1].info.isJpeg)
    }

    @Test
    fun nikonDriverCapturesToCardAndDownloadsFromCheckEvent() {
        val jpeg = Random(6).nextBytes(150_000)
        var events = ByteWriter().u16(0).toByteArray()
        val cam = FakeCamera { cmd ->
            when (cmd.code) {
                Ptp.OP_NIKON_CAPTURE_REC_IN_MEDIA -> {
                    events = ByteWriter().u16(1).u16(Ptp.EV_OBJECT_ADDED).u32(0x77L).toByteArray()
                    FakeReply()
                }
                Ptp.OP_NIKON_CHECK_EVENT -> FakeReply(data = events).also { events = ByteWriter().u16(0).toByteArray() }
                Ptp.OP_GET_OBJECT_INFO -> FakeReply(data = FakeCamera.objectInfo("DSC_0001.JPG", Ptp.FMT_JPEG, jpeg.size))
                Ptp.OP_GET_OBJECT -> FakeReply(data = jpeg)
                else -> FakeReply()
            }
        }
        cam.dataOutOps = emptySet() // 0x9207 is a plain command on Nikon
        val session = PtpSession(cam, log)
        session.openSession()
        val out = Collector()
        val d = CameraDriver.create(Ptp.USB_VENDOR_NIKON, session, log, out, DriverOptions())
        assertTrue(d is NikonDriver)
        d.connect()
        d.capture()
        assertEquals(listOf(0xFFFFFFFFL, 0L), cam.commands.first { it.code == Ptp.OP_NIKON_CAPTURE_REC_IN_MEDIA }.params.toList())
        d.poll()
        assertEquals(1, out.files.size)
        assertArrayEquals(jpeg, out.files[0].data)
        // Same handle reported again must not download twice.
        events = ByteWriter().u16(1).u16(Ptp.EV_OBJECT_ADDED).u32(0x77L).toByteArray()
        Thread.sleep(250)
        d.poll()
        assertEquals(1, out.files.size)
    }

    private fun nikonCamera(
        files: Map<Long, Pair<String, ByteArray>>,
        events: () -> ByteArray,
        downloaded: MutableList<Long>,
    ) = FakeCamera { cmd ->
        when (cmd.code) {
            Ptp.OP_NIKON_CHECK_EVENT -> FakeReply(data = events())
            Ptp.OP_GET_OBJECT_INFO -> files[cmd.params[0]]!!.let {
                FakeReply(data = FakeCamera.objectInfo(it.first, if (it.first.endsWith("JPG")) Ptp.FMT_JPEG else 0x3000, it.second.size))
            }
            Ptp.OP_GET_OBJECT -> { downloaded += cmd.params[0]; FakeReply(data = files[cmd.params[0]]!!.second) }
            Ptp.OP_NIKON_GET_LARGE_THUMB -> error("should use the full RAW, not the small preview")
            else -> FakeReply()
        }
    }.apply { dataOutOps = emptySet() }

    private fun nikonEvents(vararg handles: Long): ByteArray {
        val w = ByteWriter().u16(handles.size)
        handles.forEach { w.u16(Ptp.EV_OBJECT_ADDED).u32(it) }
        return w.toByteArray()
    }

    @Test
    fun nikonRawOnlyDownloadsFullRawForSharpPreview() {
        val nef = Random(7).nextBytes(600_000)
        var ev = nikonEvents(0x88)
        val downloaded = mutableListOf<Long>()
        val cam = nikonCamera(mapOf(0x88L to ("_DSC0002.NEF" to nef)), { ev.also { ev = nikonEvents() } }, downloaded)
        val session = PtpSession(cam, log)
        session.openSession()
        val out = Collector()
        val d = CameraDriver.create(Ptp.USB_VENDOR_NIKON, session, log, out, DriverOptions(copyRaw = false))
        d.poll()
        assertTrue("RAW waits for a possible JPEG", out.files.isEmpty())
        Thread.sleep(GenericDriver.RAW_WAIT_MS + 100)
        d.poll()
        assertEquals(1, out.files.size)
        assertTrue(out.files[0].isFullFile)
        assertArrayEquals(nef, out.files[0].data)
    }

    @Test
    fun nikonRawPlusJpegSkipsTheRaw() {
        val nef = Random(8).nextBytes(600_000)
        val jpg = Random(9).nextBytes(100_000)
        var ev = nikonEvents(0x90, 0x91)
        val downloaded = mutableListOf<Long>()
        val cam = nikonCamera(
            mapOf(0x90L to ("_DSC0003.NEF" to nef), 0x91L to ("_DSC0003.JPG" to jpg)),
            { ev.also { ev = nikonEvents() } }, downloaded,
        )
        val session = PtpSession(cam, log)
        session.openSession()
        val out = Collector()
        val d = CameraDriver.create(Ptp.USB_VENDOR_NIKON, session, log, out, DriverOptions(copyRaw = false))
        d.poll()
        Thread.sleep(GenericDriver.RAW_WAIT_MS + 100)
        d.poll()
        assertEquals(listOf(0x91L), downloaded)
        assertEquals(listOf("_DSC0003.JPG"), out.files.map { it.info.filename })
    }

    @Test
    fun copyRawDownloadsJpegBeforeRaw() {
        val nef = Random(10).nextBytes(300_000)
        val jpg = Random(11).nextBytes(50_000)
        var ev = nikonEvents(0xA0, 0xA1)
        val downloaded = mutableListOf<Long>()
        val cam = nikonCamera(
            mapOf(0xA0L to ("_DSC0004.NEF" to nef), 0xA1L to ("_DSC0004.JPG" to jpg)),
            { ev.also { ev = nikonEvents() } }, downloaded,
        )
        val session = PtpSession(cam, log)
        session.openSession()
        val d = CameraDriver.create(Ptp.USB_VENDOR_NIKON, session, log, Collector(), DriverOptions(copyRaw = true))
        d.poll()
        assertEquals(listOf(0xA1L, 0xA0L), downloaded)
    }

    private fun fakeJpeg(scanBytes: Int, seed: Int, exifThumb: ByteArray? = null): ByteArray {
        val w = ByteWriter()
        w.u8(0xFF).u8(0xD8)
        if (exifThumb != null) {
            val len = exifThumb.size + 2 + 6
            w.u8(0xFF).u8(0xE1).u8(len shr 8).u8(len and 0xFF).bytes("Exif\u0000\u0000".toByteArray()).bytes(exifThumb)
        }
        w.u8(0xFF).u8(0xDB).u8(0).u8(4).u8(0).u8(1) // tiny DQT
        w.u8(0xFF).u8(0xDA).u8(0).u8(2) // SOS (minimal)
        val rnd = Random(seed)
        repeat(scanBytes) {
            val b = rnd.nextInt(256)
            w.u8(b)
            if (b == 0xFF) w.u8(0x00) // byte stuffing
        }
        w.u8(0xFF).u8(0xD9)
        return w.toByteArray()
    }

    @Test
    fun extractsLargestEmbeddedJpegFromRaw() {
        val thumb = fakeJpeg(40_000, 1)
        val big = fakeJpeg(900_000, 2, exifThumb = fakeJpeg(5_000, 3))
        val raw = Random(9).nextBytes(10_000).also { it[0] = 0 } + thumb + Random(10).nextBytes(50_000) + big + Random(11).nextBytes(2_000_000)
        val found = JpegExtractor.largestJpeg(raw)
        assertNotNull(found)
        assertArrayEquals(big, found)
        assertNull(JpegExtractor.largestJpeg(Random(12).nextBytes(1000).map { if (it == 0xFF.toByte()) 0 else it }.toByteArray()))
    }

    @Test
    fun histogramReportsClipping() {
        val px = IntArray(100) { i ->
            when {
                i < 10 -> 0xFFFFFFFF.toInt() // white
                i < 30 -> 0xFF000000.toInt() // black
                else -> 0xFF808080.toInt()
            }
        }
        val h = Histogram.compute(px)
        assertEquals(10f, h.highlightClipPct, 0.01f)
        assertEquals(20f, h.shadowClipPct, 0.01f)
        assertEquals(70, h.luma[128])
        val mask = Histogram.clippingMask(px)
        assertEquals(Histogram.HIGHLIGHT_COLOR, mask[0])
        assertEquals(Histogram.SHADOW_COLOR, mask[15])
        assertEquals(0, mask[50])
    }

    @Test
    fun neutralSampleDetectsWarmCast() {
        assertTrue(NeutralSample(128, 128, 128).isNeutral)
        val warm = NeutralSample(150, 128, 110)
        assertTrue(warm.warmth > 0)
        assertTrue(warm.describe().startsWith("warm"))
    }
}
