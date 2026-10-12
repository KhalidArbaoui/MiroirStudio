package studio.miroir.tether.image

/**
 * Finds the largest complete JPEG embedded in a RAW file (NEF, ARW, DNG, CR2/CR3…).
 * Nikon Z and Sony Alpha RAWs carry a camera-rendered preview JPEG; for RAW-only shooting
 * that preview is what we show on the tablet.
 */
object JpegExtractor {
    fun largestJpeg(data: ByteArray, minSize: Int = 32 * 1024): ByteArray? {
        var best: IntRange? = null
        var i = 0
        val n = data.size
        while (i < n - 3) {
            if (data[i] == FF && data[i + 1] == SOI && data[i + 2] == FF) {
                val end = jpegEnd(data, i)
                if (end > 0) {
                    val len = end - i
                    if (len >= minSize && (best == null || len > best.last - best.first + 1)) best = i until end
                    // Skip past this JPEG so we don't re-scan its embedded EXIF thumbnail.
                    i = end
                    continue
                }
            }
            i++
        }
        return best?.let { data.copyOfRange(it.first, it.last + 1) }
    }

    /** Returns the offset just past EOI for the JPEG starting at [start], or -1 if malformed. */
    fun jpegEnd(d: ByteArray, start: Int): Int {
        var p = start + 2
        val n = d.size
        // Marker segments up to Start Of Scan.
        while (p + 4 <= n) {
            if (d[p] != FF) return -1
            val marker = d[p + 1].toInt() and 0xFF
            when {
                marker == 0xFF -> { p++; continue } // fill byte
                marker == 0xD9 -> return p + 2 // EOI with no scan (thumbnail-only)
                marker in 0xD0..0xD7 || marker == 0x01 -> { p += 2; continue }
            }
            val segLen = ((d[p + 2].toInt() and 0xFF) shl 8) or (d[p + 3].toInt() and 0xFF)
            if (segLen < 2) return -1
            p += 2 + segLen
            if (marker == 0xDA) return scanToEoi(d, p)
        }
        return -1
    }

    private fun scanToEoi(d: ByteArray, from: Int): Int {
        var p = from
        val n = d.size
        while (p < n - 1) {
            if (d[p] == FF) {
                val m = d[p + 1].toInt() and 0xFF
                when {
                    m == 0x00 || m in 0xD0..0xD7 || m == 0xFF -> p += if (m == 0xFF) 1 else 2
                    m == 0xD9 -> return p + 2
                    else -> {
                        // Another marker inside entropy data (e.g. DHT/SOS in progressive JPEGs).
                        if (p + 4 > n) return -1
                        val segLen = ((d[p + 2].toInt() and 0xFF) shl 8) or (d[p + 3].toInt() and 0xFF)
                        if (segLen < 2) return -1
                        p += 2 + segLen
                    }
                }
            } else {
                p++
            }
        }
        return -1
    }

    private const val FF = 0xFF.toByte()
    private const val SOI = 0xD8.toByte()
}
