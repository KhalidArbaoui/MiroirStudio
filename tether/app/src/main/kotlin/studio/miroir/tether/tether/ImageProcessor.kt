package studio.miroir.tether.tether

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import studio.miroir.tether.image.Histogram
import studio.miroir.tether.image.NeutralSample
import java.io.File
import kotlin.math.roundToInt

/** Camera settings read from EXIF, shown under the image. */
data class ExifSummary(
    val shutter: String? = null,
    val aperture: String? = null,
    val iso: String? = null,
    val focal: String? = null,
    val exposureComp: String? = null,
    val whiteBalance: String? = null,
    val lens: String? = null,
    val camera: String? = null,
    val rotation: Int = 0,
    /** JPEG is tagged Adobe RGB (or "uncalibrated"), not sRGB. */
    val adobeRgb: Boolean = false,
) {
    val exposureLine: String
        get() = listOfNotNull(shutter, aperture, iso, exposureComp, focal).joinToString("   ")
}

/** Everything the viewer needs for one image. */
class Analysis(
    val file: File,
    val display: Bitmap,
    /** Small sRGB copy used for histogram, clipping and white-balance sampling. */
    val small: Bitmap,
    val smallPixels: IntArray,
    val clipMask: Bitmap,
    val histogram: Histogram,
    /** Size of the original image after rotation. */
    val fullWidth: Int,
    val fullHeight: Int,
    val exif: ExifSummary,
)

object ImageProcessor {
    private const val DISPLAY_MAX = 3200
    private const val ANALYSIS_MAX = 1200

    fun readExif(file: File): ExifSummary = runCatching {
        val e = ExifInterface(file)
        val exposure = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        val fNumber = e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
        val iso = e.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
        val focal = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        val ev = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0.0)
        val wb = e.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1)
        val colorSpace = e.getAttributeInt(ExifInterface.TAG_COLOR_SPACE, 1)
        ExifSummary(
            shutter = exposure.takeIf { it > 0 }?.let { formatShutter(it) },
            aperture = fNumber.takeIf { it > 0 }?.let { "f/" + trim(it) },
            iso = iso.takeIf { it > 0 }?.let { "ISO $it" },
            focal = focal.takeIf { it > 0 }?.let { "${it.roundToInt()} mm" },
            exposureComp = ev.takeIf { kotlin.math.abs(it) > 0.01 }?.let { (if (it > 0) "+" else "") + trim(it) + " EV" },
            whiteBalance = when (wb) { 0 -> "WB Auto"; 1 -> "WB Manual"; else -> null },
            lens = e.getAttribute(ExifInterface.TAG_LENS_MODEL),
            camera = e.getAttribute(ExifInterface.TAG_MODEL),
            rotation = e.rotationDegrees,
            adobeRgb = colorSpace == 0xFFFF,
        )
    }.getOrDefault(ExifSummary())

    private fun formatShutter(s: Double): String =
        if (s >= 0.3) trim(s) + "s" else "1/" + (1.0 / s).roundToInt()

    private fun trim(v: Double): String =
        if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else "%.1f".format(v)

    private fun boundsOf(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        return o.outWidth to o.outHeight
    }

    private fun sampleFor(w: Int, h: Int, max: Int): Int {
        var s = 1
        while (maxOf(w, h) / (s * 2) >= max) s *= 2
        return s
    }

    private fun decode(file: File, max: Int, srgb: Boolean): Bitmap? {
        val (w, h) = boundsOf(file)
        if (w <= 0 || h <= 0) return null
        val o = BitmapFactory.Options().apply {
            inSampleSize = sampleFor(w, h, max)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (srgb) inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val bmp = BitmapFactory.decodeFile(file.path, o) ?: return null
        // Downscale further if the power-of-two sample left it larger than needed.
        val scale = max.toFloat() / maxOf(bmp.width, bmp.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true)
                .also { if (it !== bmp) bmp.recycle() }
        } else bmp
    }

    private fun rotate(b: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return b
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true).also { if (it !== b) b.recycle() }
    }

    fun thumbnail(file: File, exifRotation: Int, max: Int = 260): Bitmap? =
        decode(file, max, srgb = true)?.let { rotate(it, exifRotation) }

    fun analyze(file: File): Analysis? {
        val exif = readExif(file)
        val display = decode(file, DISPLAY_MAX, srgb = false)?.let { rotate(it, exif.rotation) } ?: return null
        val small = decode(file, ANALYSIS_MAX, srgb = true)?.let { rotate(it, exif.rotation) } ?: return null
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        val hist = Histogram.compute(px)
        val maskPx = Histogram.clippingMask(px)
        val mask = Bitmap.createBitmap(maskPx, small.width, small.height, Bitmap.Config.ARGB_8888)
        val (w, h) = boundsOf(file)
        val rotated = exif.rotation == 90 || exif.rotation == 270
        return Analysis(
            file, display, small, px, mask, hist,
            fullWidth = if (rotated) h else w,
            fullHeight = if (rotated) w else h,
            exif = exif,
        )
    }

    /** Average colour of a small square around normalised point (u, v) — for grey-card checks. */
    fun sampleNeutral(a: Analysis, u: Float, v: Float, radius: Int = 6): NeutralSample {
        val cx = (u * a.small.width).roundToInt().coerceIn(0, a.small.width - 1)
        val cy = (v * a.small.height).roundToInt().coerceIn(0, a.small.height - 1)
        val out = ArrayList<Int>()
        for (y in (cy - radius)..(cy + radius)) for (x in (cx - radius)..(cx + radius)) {
            if (x in 0 until a.small.width && y in 0 until a.small.height) out += a.smallPixels[y * a.small.width + x]
        }
        return NeutralSample.average(out.toIntArray())
    }

    /**
     * 1:1 crop around normalised point (u, v) of the *rotated* image, decoded at full resolution
     * from the file — this is the focus check.
     */
    fun loupe(a: Analysis, u: Float, v: Float, sizePx: Int = 900): Bitmap? = runCatching {
        @Suppress("DEPRECATION")
        val decoder = BitmapRegionDecoder.newInstance(a.file.path, false) ?: return null
        try {
            val fw = decoder.width
            val fh = decoder.height
            // Map the point from rotated (display) space back to the file's pixel space.
            val (ou, ov) = when (a.exif.rotation) {
                90 -> v to 1f - u
                180 -> 1f - u to 1f - v
                270 -> 1f - v to u
                else -> u to v
            }
            val cx = (ou * fw).roundToInt()
            val cy = (ov * fh).roundToInt()
            val half = sizePx / 2
            val left = (cx - half).coerceIn(0, maxOf(0, fw - sizePx))
            val top = (cy - half).coerceIn(0, maxOf(0, fh - sizePx))
            val rect = Rect(left, top, minOf(fw, left + sizePx), minOf(fh, top + sizePx))
            val crop = decoder.decodeRegion(rect, BitmapFactory.Options()) ?: return null
            rotate(crop, a.exif.rotation)
        } finally {
            decoder.recycle()
        }
    }.getOrNull()
}
