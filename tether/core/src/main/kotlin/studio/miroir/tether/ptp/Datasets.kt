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

/**
 * A device property descriptor (PTP DevicePropDesc), from GetDevicePropDesc (0x1014)
 * or one entry of Sony's GetAllDevicePropData (0x9209).
 */
class PropDesc(
    val code: Int,
    val dataType: Int,
    val writable: Boolean,
    val current: Long,
    val currentString: String?,
    /** Allowed values from an enumeration form, in camera order. */
    val enumValues: List<Long>,
    /** Range form (min, max, step), if the camera reports one. */
    val range: Triple<Long, Long, Long>?,
    /** Sony only: false when the camera currently locks this setting (lens, mode, menu). */
    val enabled: Boolean = true,
) {
    /** Allowed values: the enumeration, or the range expanded (when it's a sensible size). */
    val allowedValues: List<Long>
        get() {
            if (enumValues.isNotEmpty()) return enumValues
            val (min, max, step) = range ?: return emptyList()
            if (step <= 0 || (max - min) / step > 2000) return emptyList()
            return (min..max step step).toList()
        }

    override fun toString() = "${code.hex4()}=${currentString ?: current} type=${dataType.hex4()} set=$writable on=$enabled " +
        "(${enumValues.size} values${if (range != null) ", range" else ""})"
}

object PropDescParser {
    /** Standard DevicePropDesc: code, type, getSet, default, current, formFlag, form. */
    fun parse(data: ByteArray): PropDesc = parseOne(ByteReader(data), sony = false)

    /**
     * Sony 0x9209 dataset: u64 count, then descriptors that carry one extra byte
     * ("is enabled") after getSet.
     */
    fun parseSonyAll(data: ByteArray): Map<Int, PropDesc> {
        val r = ByteReader(data)
        val count = r.u64()
        val out = HashMap<Int, PropDesc>()
        var i = 0L
        while (i < count && r.remaining >= 6) {
            val d = parseOne(r, sony = true)
            out[d.code] = d
            i++
        }
        return out
    }

    private fun parseOne(r: ByteReader, sony: Boolean): PropDesc {
        val code = r.u16()
        val type = r.u16()
        val getSet = r.u8()
        val enabled = if (sony) r.u8() != 0 else true
        readValue(r, type) // factory default
        val (cur, curStr) = readValue(r, type)
        var enum = emptyList<Long>()
        var range: Triple<Long, Long, Long>? = null
        if (r.remaining > 0) {
            when (r.u8()) { // form flag
                1 -> range = Triple(readValue(r, type).first, readValue(r, type).first, readValue(r, type).first)
                2 -> { val n = r.u16(); enum = List(n) { readValue(r, type).first } }
            }
        }
        return PropDesc(code, type, getSet == 1, cur, curStr, enum, range, enabled)
    }

    fun scalarSize(type: Int): Int = when (type) {
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

    /** Encodes [value] as the little-endian bytes of PTP data [type] (for SetDevicePropValue). */
    fun encode(type: Int, value: Long): ByteArray {
        val w = ByteWriter(8)
        when (scalarSize(type)) {
            1 -> w.u8(value.toInt())
            2 -> w.u16(value.toInt())
            4 -> w.u32(value)
            8 -> { w.u32(value and 0xFFFFFFFFL); w.u32(value ushr 32) }
            else -> throw PtpException("Can't encode type ${type.hex4()}")
        }
        return w.toByteArray()
    }
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
