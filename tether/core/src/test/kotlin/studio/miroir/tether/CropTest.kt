package studio.miroir.tether

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import studio.miroir.tether.image.CropRatio

class CropTest {
    private val eps = 1e-4f

    @Test fun offHasNoFrame() = assertNull(CropRatio.Off.frame(6000f, 4000f))

    @Test fun portraitOnLandscapeUsesFullHeight() {
        // 3:2 landscape, 4:5 crop: width = 4000 * 0.8 = 3200 of 6000.
        val f = CropRatio.Portrait45.frame(6000f, 4000f)!!
        assertArrayEquals(floatArrayOf(1400f / 6000, 0f, 4600f / 6000, 1f), f, eps)
    }

    @Test fun wideOnLandscapeUsesFullWidth() {
        // 3:2 landscape, 16:9 crop: height = 6000 * 9 / 16 = 3375 of 4000.
        val f = CropRatio.Wide169.frame(6000f, 4000f)!!
        assertArrayEquals(floatArrayOf(0f, 312.5f / 4000, 1f, 3687.5f / 4000), f, eps)
    }

    @Test fun squareOnPortrait() {
        val f = CropRatio.Square.frame(4000f, 6000f)!!
        assertArrayEquals(floatArrayOf(0f, 1000f / 6000, 1f, 5000f / 6000), f, eps)
    }

    @Test fun movedFrameStaysInsideImage() {
        // 4:5 on 3:2 is 0.533 wide; asking for the right edge pins it there.
        val f = CropRatio.Portrait45.frame(6000f, 4000f, cu = 0.95f, cv = 0.1f)!!
        assertArrayEquals(floatArrayOf(1 - 3200f / 6000, 0f, 1f, 1f), f, eps)
        val g = CropRatio.Portrait45.frame(6000f, 4000f, cu = 0.4f)!!
        assertEquals(0.4f, (g[0] + g[2]) / 2, eps)
    }

    @Test fun nikonLiveHeader() {
        val d = ByteArray(64)
        fun put(i: Int, v: Int) { d[i] = (v shr 8).toByte(); d[i + 1] = v.toByte() }
        put(0, 1024); put(2, 683); put(4, 8256); put(6, 5504); put(20, 4128); put(22, 2752)
        val h = studio.miroir.tether.camera.NikonLiveHeader.parse(d)!!
        assertEquals(8256, h.wholeWidth); assertEquals(5504, h.wholeHeight); assertEquals(4128, h.afCenterX)
        assertNull(studio.miroir.tether.camera.NikonLiveHeader.parse(byteArrayOf(-1, -40) + ByteArray(40)))
    }

    @Test fun storageFreeImages() {
        val w = studio.miroir.tether.ptp.ByteWriter().u16(4).u16(3).u16(0).u32(0L).u32(16L).u32(0L).u32(8L).u32(1234L)
        assertEquals(1234L, studio.miroir.tether.camera.StorageSpace.freeImages(w.toByteArray()))
    }

    @Test fun batteryAlarmFiresOncePerThreshold() {
        val a = studio.miroir.tether.camera.LowLevelAlarm(listOf(20, 10))
        assertNull(a.check(50)); assertEquals(20, a.check(19)); assertNull(a.check(18))
        assertEquals(10, a.check(9)); assertNull(a.check(8))
        assertNull(a.check(100)); assertEquals(10, a.check(5)) // swapped battery, then a flat one
    }

    @Test fun cyclesBackToOff() {
        var r = CropRatio.Off
        repeat(CropRatio.entries.size) { r = r.next() }
        assertEquals(CropRatio.Off, r)
    }
}
