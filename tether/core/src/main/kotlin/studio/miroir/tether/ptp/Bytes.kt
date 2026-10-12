package studio.miroir.tether.ptp

/** Little-endian reader for PTP datasets. */
class ByteReader(private val buf: ByteArray, start: Int = 0, private val end: Int = buf.size) {
    var pos: Int = start
        private set

    val remaining: Int get() = end - pos

    private fun need(n: Int) {
        if (pos + n > end) throw PtpException("Dataset truncated (need $n bytes at $pos, end $end)")
    }

    fun u8(): Int { need(1); return buf[pos++].toInt() and 0xFF }

    fun u16(): Int {
        need(2)
        val v = (buf[pos].toInt() and 0xFF) or ((buf[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        return v
    }

    fun u32(): Long {
        need(4)
        var v = 0L
        for (i in 0 until 4) v = v or ((buf[pos + i].toLong() and 0xFF) shl (8 * i))
        pos += 4
        return v
    }

    fun u64(): Long {
        need(8)
        var v = 0L
        for (i in 0 until 8) v = v or ((buf[pos + i].toLong() and 0xFF) shl (8 * i))
        pos += 8
        return v
    }

    fun skip(n: Int) { need(n); pos += n }

    /** PTP string: u8 char count (including NUL), then UTF-16LE chars. */
    fun string(): String {
        val count = u8()
        if (count == 0) return ""
        need(count * 2)
        val sb = StringBuilder(count)
        for (i in 0 until count) {
            val c = u16()
            if (c != 0) sb.append(c.toChar())
        }
        return sb.toString()
    }

    fun u16Array(): IntArray {
        val n = u32().toInt()
        if (n < 0 || n * 2 > remaining) throw PtpException("Bad array length $n")
        return IntArray(n) { u16() }
    }

    fun u32Array(): LongArray {
        val n = u32().toInt()
        if (n < 0 || n * 4L > remaining) throw PtpException("Bad array length $n")
        return LongArray(n) { u32() }
    }
}

class ByteWriter(initial: Int = 32) {
    private var buf = ByteArray(initial)
    var size = 0
        private set

    private fun ensure(n: Int) {
        if (size + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + n))
    }

    fun u8(v: Int) = apply { ensure(1); buf[size++] = v.toByte() }
    fun u16(v: Int) = apply { ensure(2); buf[size++] = v.toByte(); buf[size++] = (v shr 8).toByte() }
    fun u32(v: Long) = apply { ensure(4); for (i in 0 until 4) buf[size++] = (v shr (8 * i)).toByte() }
    fun u32(v: Int) = u32(v.toLong() and 0xFFFFFFFFL)
    fun bytes(b: ByteArray) = apply { ensure(b.size); b.copyInto(buf, size); size += b.size }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

class PtpException(message: String, val responseCode: Int = 0) : Exception(message)

fun Int.hex4(): String = "0x%04X".format(this)
fun Long.hex8(): String = "0x%08X".format(this)
