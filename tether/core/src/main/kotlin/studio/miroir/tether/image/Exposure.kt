package studio.miroir.tether.image

/** RGB + luminance histogram and clipping stats computed from ARGB pixels (sRGB, 8-bit). */
class Histogram(
    val red: IntArray,
    val green: IntArray,
    val blue: IntArray,
    val luma: IntArray,
    val pixelCount: Int,
    /** Percentage of pixels with any channel at or above [HIGHLIGHT_LEVEL]. */
    val highlightClipPct: Float,
    /** Percentage of pixels with all channels at or below [SHADOW_LEVEL]. */
    val shadowClipPct: Float,
    /** Mean luminance 0..255. */
    val meanLuma: Float,
) {
    /** Largest bucket across channels, ignoring the two end buckets so clipping spikes don't flatten the graph. */
    val peak: Int
        get() = maxOf(red.max(1), green.max(1), blue.max(1), luma.max(1)).coerceAtLeast(1)

    private fun IntArray.max(skipEnds: Int): Int {
        var m = 0
        for (i in skipEnds until size - skipEnds) if (this[i] > m) m = this[i]
        return m
    }

    companion object {
        const val HIGHLIGHT_LEVEL = 250
        const val SHADOW_LEVEL = 4

        fun compute(pixels: IntArray): Histogram {
            val r = IntArray(256)
            val g = IntArray(256)
            val b = IntArray(256)
            val y = IntArray(256)
            var hi = 0
            var lo = 0
            var sum = 0L
            for (p in pixels) {
                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF
                r[pr]++; g[pg]++; b[pb]++
                // Rec. 709 luma weights (same primaries as sRGB).
                val l = (pr * 54 + pg * 183 + pb * 19) shr 8
                y[l]++
                sum += l
                if (pr >= HIGHLIGHT_LEVEL || pg >= HIGHLIGHT_LEVEL || pb >= HIGHLIGHT_LEVEL) hi++
                else if (pr <= SHADOW_LEVEL && pg <= SHADOW_LEVEL && pb <= SHADOW_LEVEL) lo++
            }
            val n = pixels.size.coerceAtLeast(1)
            return Histogram(
                r, g, b, y, pixels.size,
                highlightClipPct = hi * 100f / n,
                shadowClipPct = lo * 100f / n,
                meanLuma = sum.toFloat() / n,
            )
        }

        /**
         * Clipping overlay: clipped highlights painted red, crushed shadows blue, everything else transparent.
         * Returns ARGB pixels the same size as [pixels].
         */
        fun clippingMask(pixels: IntArray): IntArray = IntArray(pixels.size) { i ->
            val p = pixels[i]
            val pr = (p shr 16) and 0xFF
            val pg = (p shr 8) and 0xFF
            val pb = p and 0xFF
            when {
                pr >= HIGHLIGHT_LEVEL || pg >= HIGHLIGHT_LEVEL || pb >= HIGHLIGHT_LEVEL -> HIGHLIGHT_COLOR
                pr <= SHADOW_LEVEL && pg <= SHADOW_LEVEL && pb <= SHADOW_LEVEL -> SHADOW_COLOR
                else -> 0
            }
        }

        const val HIGHLIGHT_COLOR = 0xFFFF2D2D.toInt()
        const val SHADOW_COLOR = 0xFF2D6BFF.toInt()
    }
}

/**
 * Neutral check: average colour of a sample (e.g. a grey card) and how far it is from neutral.
 * Used by the eyedropper so the photographer can verify white balance before Lightroom.
 */
class NeutralSample(val r: Int, val g: Int, val b: Int) {
    /** Approx. luminance in % (0..100). */
    val brightnessPct: Int get() = ((r * 0.2126 + g * 0.7152 + b * 0.0722) / 255.0 * 100).toInt()

    /** Positive = warm (more red than blue), negative = cool. */
    val warmth: Int get() = r - b

    /** Positive = magenta, negative = green. */
    val tint: Int get() = (r + b) / 2 - g

    val isNeutral: Boolean get() = kotlin.math.abs(warmth) <= 6 && kotlin.math.abs(tint) <= 6

    fun describe(): String {
        if (isNeutral) return "Neutral ✓"
        val parts = mutableListOf<String>()
        if (kotlin.math.abs(warmth) > 6) parts += if (warmth > 0) "warm +$warmth" else "cool $warmth"
        if (kotlin.math.abs(tint) > 6) parts += if (tint > 0) "magenta +$tint" else "green $tint"
        return parts.joinToString(", ")
    }

    companion object {
        fun average(pixels: IntArray): NeutralSample {
            var r = 0L; var g = 0L; var b = 0L
            for (p in pixels) { r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF }
            val n = pixels.size.coerceAtLeast(1)
            return NeutralSample((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        }
    }
}
