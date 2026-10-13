package studio.miroir.tether.image

/**
 * Orientation of a TIFF-based RAW (Nikon NEF, Sony ARW) from tag 0x0112 in its first IFD.
 * The JPEG preview inside a RAW usually has no orientation of its own, so without this a
 * vertical RAW-only shot shows sideways and live view can't tell which way the camera turned.
 */
object RawOrientation {
    /** Clockwise degrees to display the image upright, or null if not found. */
    fun degrees(d: ByteArray): Int? {
        if (d.size < 16) return null
        val little = when {
            d[0] == 'I'.code.toByte() && d[1] == 'I'.code.toByte() -> true
            d[0] == 'M'.code.toByte() && d[1] == 'M'.code.toByte() -> false
            else -> return null
        }
        fun u16(o: Int): Int = if (little) (d[o].toInt() and 0xFF) or ((d[o + 1].toInt() and 0xFF) shl 8)
            else ((d[o].toInt() and 0xFF) shl 8) or (d[o + 1].toInt() and 0xFF)
        fun u32(o: Int): Long = if (little) (u16(o).toLong() or (u16(o + 2).toLong() shl 16))
            else ((u16(o).toLong() shl 16) or u16(o + 2).toLong())
        if (u16(2) != 42) return null
        val ifd = u32(4)
        if (ifd < 8 || ifd + 2 > d.size) return null
        val count = u16(ifd.toInt())
        for (i in 0 until count) {
            val e = ifd.toInt() + 2 + i * 12
            if (e + 12 > d.size) return null
            if (u16(e) == 0x0112) {
                return when (u16(e + 8)) {
                    1 -> 0; 3 -> 180; 6 -> 90; 8 -> 270
                    else -> null
                }
            }
        }
        return null
    }
}
