package studio.miroir.tether.image

/** Delivery formats to frame for: the shot is cropped to one of these in Lightroom later. */
enum class CropRatio(val label: String, val w: Int, val h: Int, val use: String) {
    Off("Crop", 0, 0, ""),
    Portrait45("4:5", 4, 5, "Instagram feed"),
    Square("1:1", 1, 1, "Square / menu"),
    Story916("9:16", 9, 16, "Stories / Reels"),
    Wide169("16:9", 16, 9, "Web banner / YouTube");

    fun next(): CropRatio = entries[(ordinal + 1) % entries.size]

    /**
     * Largest centred crop of this ratio inside a [width] × [height] image, as fractions
     * (left, top, right, bottom) of the image, or null when off.
     */
    fun frame(width: Float, height: Float): FloatArray? {
        if (this == Off || width <= 0f || height <= 0f) return null
        val target = w.toFloat() / h
        return if (width / height > target) {
            val f = height * target / width
            floatArrayOf((1 - f) / 2, 0f, (1 + f) / 2, 1f)
        } else {
            val f = width / target / height
            floatArrayOf(0f, (1 - f) / 2, 1f, (1 + f) / 2)
        }
    }
}
