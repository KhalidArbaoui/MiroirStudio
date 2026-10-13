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
     * Largest crop of this ratio inside a [width] × [height] image, centred as close to
     * ([cu], [cv]) as fits, as fractions (left, top, right, bottom) of the image; null when off.
     */
    fun frame(width: Float, height: Float, cu: Float = 0.5f, cv: Float = 0.5f): FloatArray? {
        if (this == Off || width <= 0f || height <= 0f) return null
        val target = w.toFloat() / h
        var fw = 1f
        var fh = 1f
        if (width / height > target) fw = height * target / width else fh = width / target / height
        val u = cu.coerceIn(fw / 2, 1 - fw / 2)
        val v = cv.coerceIn(fh / 2, 1 - fh / 2)
        return floatArrayOf(u - fw / 2, v - fh / 2, u + fw / 2, v + fh / 2)
    }
}
