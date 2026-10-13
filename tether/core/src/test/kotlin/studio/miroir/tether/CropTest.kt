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

    @Test fun cyclesBackToOff() {
        var r = CropRatio.Off
        repeat(CropRatio.entries.size) { r = r.next() }
        assertEquals(CropRatio.Off, r)
    }
}
