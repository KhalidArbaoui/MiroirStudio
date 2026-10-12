package studio.miroir.tether.ptp

class DeviceInfo(
    val standardVersion: Int,
    val vendorExtensionId: Long,
    val vendorExtensionDesc: String,
    val operations: Set<Int>,
    val events: Set<Int>,
    val properties: Set<Int>,
    val manufacturer: String,
    val model: String,
    val deviceVersion: String,
    val serialNumber: String,
) {
    fun supports(op: Int) = op in operations

    override fun toString() =
        "$manufacturer $model fw $deviceVersion (vendorExt ${vendorExtensionId.hex8()}, ${operations.size} ops)"

    companion object {
        fun parse(data: ByteArray): DeviceInfo {
            val r = ByteReader(data)
            val std = r.u16()
            val vendorExt = r.u32()
            r.u16() // vendor extension version
            val vendorDesc = r.string()
            r.u16() // functional mode
            val ops = r.u16Array().toSet()
            val events = r.u16Array().toSet()
            val props = r.u16Array().toSet()
            r.u16Array() // capture formats
            r.u16Array() // image formats
            return DeviceInfo(
                std, vendorExt, vendorDesc, ops, events, props,
                manufacturer = r.string(),
                model = r.string(),
                deviceVersion = r.string(),
                serialNumber = if (r.remaining > 0) r.string() else "",
            )
        }
    }
}

class ObjectInfo(
    val storageId: Long,
    val format: Int,
    val compressedSize: Long,
    val imageWidth: Long,
    val imageHeight: Long,
    val parent: Long,
    val filename: String,
    val captureDate: String,
) {
    val isFolder: Boolean get() = format == Ptp.FMT_ASSOCIATION
    val extension: String get() = filename.substringAfterLast('.', "").uppercase()
    val isJpeg: Boolean get() = format == Ptp.FMT_JPEG || extension in setOf("JPG", "JPEG")
    val isRaw: Boolean get() = extension in RAW_EXTENSIONS || format == Ptp.FMT_SONY_ARW
    val isHeif: Boolean get() = extension in setOf("HIF", "HEIF", "HEIC")

    override fun toString() = "$filename (${format.hex4()}, ${compressedSize / 1024} KB, ${imageWidth}x$imageHeight)"

    companion object {
        val RAW_EXTENSIONS = setOf("NEF", "NRW", "ARW", "SRF", "SR2", "DNG", "CR2", "CR3", "RAF", "ORF", "RW2")

        fun parse(data: ByteArray): ObjectInfo {
            val r = ByteReader(data)
            val storage = r.u32()
            val format = r.u16()
            r.u16() // protection status
            val size = r.u32()
            r.u16() // thumb format
            r.u32() // thumb size
            r.u32() // thumb width
            r.u32() // thumb height
            val w = r.u32()
            val h = r.u32()
            r.u32() // bit depth
            val parent = r.u32()
            r.u16() // association type
            r.u32() // association desc
            r.u32() // sequence number
            val name = r.string()
            val date = if (r.remaining > 0) r.string() else ""
            return ObjectInfo(storage, format, size, w, h, parent, name, date)
        }
    }
}

/** Device property value as reported by Sony's GetAllDevicePropData (0x9209). */
class SonyProperty(val code: Int, val dataType: Int, val current: Long, val currentString: String?)

object SonyPropertyParser {
    /**
     * Parses the 0x9209 dataset: u64 count, then Sony device property descriptors:
     * code u16, type u16, getSet u8, isEnabled u8, default value, current value, form flag u8, form.
     */
    fun parse(data: ByteArray): Map<Int, SonyProperty> {
        val r = ByteReader(data)
        val count = r.u64()
        val out = HashMap<Int, SonyProperty>()
        var i = 0L
        while (i < count && r.remaining >= 6) {
            val code = r.u16()
            val type = r.u16()
            r.u8() // get/set
            r.u8() // is enabled
            skipValue(r, type) // factory default
            val (cur, curStr) = readValue(r, type)
            when (r.u8()) { // form flag
                1 -> repeat(3) { skipValue(r, type) } // range: min, max, step
                2 -> { val n = r.u16(); repeat(n) { skipValue(r, type) } } // enum
            }
            out[code] = SonyProperty(code, type, cur, curStr)
            i++
        }
        return out
    }

    private fun scalarSize(type: Int): Int = when (type) {
        1, 2 -> 1
        3, 4 -> 2
        5, 6 -> 4
        7, 8 -> 8
        9, 10 -> 16
        else -> throw PtpException("Unknown PTP data type ${type.hex4()}")
    }

    private fun readValue(r: ByteReader, type: Int): Pair<Long, String?> {
        if (type == 0xFFFF) return 0L to r.string()
        if (type and 0x4000 != 0) {
            val n = r.u32().toInt()
            r.skip(n * scalarSize(type and 0x3FFF))
            return 0L to null
        }
        val v = when (scalarSize(type)) {
            1 -> r.u8().toLong().let { if (type == 1) it.toByte().toLong() else it }
            2 -> r.u16().toLong().let { if (type == 3) it.toShort().toLong() else it }
            4 -> r.u32().let { if (type == 5) it.toInt().toLong() else it }
            8 -> r.u64()
            else -> { r.skip(16); 0L }
        }
        return v to null
    }

    private fun skipValue(r: ByteReader, type: Int) { readValue(r, type) }
}

object NikonEvents {
    /** Parses Nikon CheckEvent (0x90C7): u16 count, then (u16 code, u32 param) per event. */
    fun parse(data: ByteArray): List<PtpEvent> {
        if (data.size < 2) return emptyList()
        val r = ByteReader(data)
        val n = r.u16()
        val out = ArrayList<PtpEvent>(n)
        repeat(n) {
            if (r.remaining < 6) return out
            val code = r.u16()
            val p = r.u32()
            out.add(PtpEvent(code, longArrayOf(p)))
        }
        return out
    }
}
