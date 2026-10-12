package studio.miroir.tether.ptp

/** Raw USB pipes to a PTP camera. Implemented on Android by UsbPtpTransport. */
interface PtpTransport {
    /** Max packet size of the bulk-in endpoint; reads are rounded up to a multiple of it. */
    val maxPacketSize: Int

    /** Bulk-out write. Throws on failure. */
    fun write(data: ByteArray, length: Int = data.size, timeoutMs: Int)

    /** Bulk-in read into [buf]. Returns bytes read, or -1 on timeout/error. */
    fun read(buf: ByteArray, length: Int, timeoutMs: Int): Int

    /** Interrupt-in read of one event container. Returns bytes read, or -1 if none. */
    fun readEvent(buf: ByteArray, timeoutMs: Int): Int

    /** PTP class "device reset" + clear halts. Best effort. */
    fun reset() {}

    fun close()
}

fun interface TetherLog {
    fun log(message: String)
}

class PtpResult(val code: Int, val params: LongArray, val data: ByteArray) {
    val ok: Boolean get() = code == Ptp.RC_OK
    fun param(i: Int): Long = if (i < params.size) params[i] else 0L
    override fun toString() = "${Ptp.responseName(code)} data=${data.size}B"
}

class PtpEvent(val code: Int, val params: LongArray) {
    fun param(i: Int): Long = if (i < params.size) params[i] else 0L
    override fun toString() = "event ${code.hex4()} ${params.joinToString { it.hex8() }}"

    companion object {
        fun parse(buf: ByteArray, length: Int): PtpEvent? {
            if (length < Ptp.HEADER_SIZE) return null
            val r = ByteReader(buf, 0, length)
            r.u32() // length
            if (r.u16() != Ptp.TYPE_EVENT) return null
            val code = r.u16()
            r.u32() // transaction id
            val params = LongArray(minOf(3, r.remaining / 4)) { r.u32() }
            return PtpEvent(code, params)
        }
    }
}

/**
 * One PTP session over a [PtpTransport]. Not thread-safe: all calls must come from one thread.
 * (The interrupt endpoint may be read from another thread via [PtpTransport.readEvent].)
 */
class PtpSession(private val transport: PtpTransport, private val log: TetherLog) {
    private var nextTransactionId = 1L
    private val readBuf = ByteArray(READ_CHUNK)

    /** Bytes received past the end of a data phase (the start of the response container). */
    private var leftover: ByteArray? = null

    var deviceInfo: DeviceInfo? = null
        private set

    fun readDeviceInfo(): DeviceInfo {
        val r = transaction(Ptp.OP_GET_DEVICE_INFO, sessionless = true)
        r.requireOk("GetDeviceInfo")
        return DeviceInfo.parse(r.data).also { deviceInfo = it }
    }

    fun openSession() {
        nextTransactionId = 0 // OpenSession must use transaction id 0
        var r = transaction(Ptp.OP_OPEN_SESSION, longArrayOf(1))
        if (r.code == Ptp.RC_SESSION_ALREADY_OPEN) {
            log.log("Session already open, closing and reopening")
            transaction(Ptp.OP_CLOSE_SESSION)
            nextTransactionId = 0
            r = transaction(Ptp.OP_OPEN_SESSION, longArrayOf(1))
            if (r.code == Ptp.RC_SESSION_ALREADY_OPEN) return
        }
        r.requireOk("OpenSession")
    }

    fun closeSession() {
        runCatching { transaction(Ptp.OP_CLOSE_SESSION, timeoutMs = 1000) }
    }

    fun getObjectInfo(handle: Long): ObjectInfo {
        val r = retryBusy { transaction(Ptp.OP_GET_OBJECT_INFO, longArrayOf(handle)) }
        r.requireOk("GetObjectInfo(${handle.hex8()})")
        return ObjectInfo.parse(r.data)
    }

    fun getObject(handle: Long, progress: ((Long, Long) -> Unit)? = null): ByteArray {
        val r = retryBusy {
            transaction(Ptp.OP_GET_OBJECT, longArrayOf(handle), timeoutMs = 20_000, progress = progress)
        }
        r.requireOk("GetObject(${handle.hex8()})")
        return r.data
    }

    fun <T : PtpResult> retryBusy(attempts: Int = 25, block: () -> T): T {
        var r = block()
        var n = 1
        while (r.code == Ptp.RC_DEVICE_BUSY && n < attempts) {
            Thread.sleep(100)
            r = block()
            n++
        }
        return r
    }

    /**
     * Runs one PTP transaction: command, optional data-out, optional data-in, response.
     * The data-in phase is detected from the container type, so callers don't declare it.
     */
    fun transaction(
        code: Int,
        params: LongArray = LongArray(0),
        dataOut: ByteArray? = null,
        timeoutMs: Int = 5000,
        sessionless: Boolean = false,
        progress: ((Long, Long) -> Unit)? = null,
    ): PtpResult {
        val tid = if (sessionless && code == Ptp.OP_GET_DEVICE_INFO) 0L else nextTransactionId++
        leftover = null

        val cmd = ByteWriter(Ptp.HEADER_SIZE + params.size * 4)
            .u32(Ptp.HEADER_SIZE + params.size * 4)
            .u16(Ptp.TYPE_COMMAND).u16(code).u32(tid)
        params.forEach { cmd.u32(it) }
        writeAll(cmd.toByteArray(), timeoutMs)

        if (dataOut != null) {
            val data = ByteWriter(Ptp.HEADER_SIZE + dataOut.size)
                .u32(Ptp.HEADER_SIZE + dataOut.size)
                .u16(Ptp.TYPE_DATA).u16(code).u32(tid)
                .bytes(dataOut)
            writeAll(data.toByteArray(), timeoutMs)
        }

        var first = readContainerStart(timeoutMs)
        var type = u16At(first, 4)
        var dataIn = EMPTY

        if (type == Ptp.TYPE_DATA) {
            dataIn = readDataPhase(first, timeoutMs, progress)
            first = readContainerStart(timeoutMs)
            type = u16At(first, 4)
        }
        if (type != Ptp.TYPE_RESPONSE) {
            throw PtpException("Expected response for ${code.hex4()}, got container type $type")
        }
        val len = minOf(u32At(first, 0).toInt(), first.size)
        val r = ByteReader(first, 8, len)
        r.u32() // transaction id
        val respParams = LongArray(minOf(5, r.remaining / 4)) { r.u32() }
        return PtpResult(u16At(first, 6), respParams, dataIn)
    }

    private fun writeAll(bytes: ByteArray, timeoutMs: Int) {
        transport.write(bytes, bytes.size, timeoutMs)
        // A transfer that ends exactly on a packet boundary needs a zero-length packet.
        if (bytes.size % transport.maxPacketSize == 0) transport.write(EMPTY, 0, timeoutMs)
    }

    /** Returns the bytes of the next container start (at least a full header). */
    private fun readContainerStart(timeoutMs: Int): ByteArray {
        leftover?.let { left ->
            leftover = null
            if (left.size >= Ptp.HEADER_SIZE) return left
            // Rare: header split across transfers.
            val more = readOnce(timeoutMs)
            return left + more
        }
        var attempts = 0
        while (true) {
            val chunk = readOnce(timeoutMs)
            if (chunk.isNotEmpty()) {
                if (chunk.size < Ptp.HEADER_SIZE) throw PtpException("Short container (${chunk.size} bytes)")
                return chunk
            }
            // Zero-length packet terminating the previous phase — read again.
            if (++attempts > 3) throw PtpException("No response from camera")
        }
    }

    private fun readOnce(timeoutMs: Int, wanted: Int = READ_CHUNK): ByteArray {
        val len = roundUpToPacket(minOf(wanted, READ_CHUNK))
        val n = transport.read(readBuf, len, timeoutMs)
        if (n < 0) throw PtpException("USB read timed out")
        return readBuf.copyOf(n)
    }

    private fun readDataPhase(first: ByteArray, timeoutMs: Int, progress: ((Long, Long) -> Unit)?): ByteArray {
        val containerLen = u32At(first, 0)
        if (containerLen == 0xFFFFFFFFL) throw PtpException("Objects over 4 GB are not supported")
        val total = (containerLen - Ptp.HEADER_SIZE).toInt()
        if (total < 0) throw PtpException("Bad data container length $containerLen")
        val out = ByteArray(total)
        var got = minOf(total, first.size - Ptp.HEADER_SIZE)
        first.copyInto(out, 0, Ptp.HEADER_SIZE, Ptp.HEADER_SIZE + got)
        stashExcess(first, Ptp.HEADER_SIZE + got)
        progress?.invoke(got.toLong(), total.toLong())

        while (got < total) {
            val n = transport.read(readBuf, roundUpToPacket(minOf(total - got, READ_CHUNK)), timeoutMs)
            if (n < 0) throw PtpException("USB read timed out during data phase ($got/$total)")
            if (n == 0) continue
            val take = minOf(n, total - got)
            readBuf.copyInto(out, got, 0, take)
            got += take
            if (n > take) stashExcess(readBuf.copyOf(n), take)
            progress?.invoke(got.toLong(), total.toLong())
        }
        return out
    }

    private fun stashExcess(chunk: ByteArray, from: Int) {
        if (from < chunk.size) leftover = chunk.copyOfRange(from, chunk.size)
    }

    private fun roundUpToPacket(n: Int): Int {
        val p = transport.maxPacketSize
        return ((n + p - 1) / p) * p
    }

    companion object {
        const val READ_CHUNK = 1 shl 20 // 1 MB per USB transfer
        private val EMPTY = ByteArray(0)

        private fun u16At(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
        private fun u32At(b: ByteArray, o: Int): Long {
            var v = 0L
            for (i in 0 until 4) v = v or ((b[o + i].toLong() and 0xFF) shl (8 * i))
            return v
        }
    }
}

fun PtpResult.requireOk(what: String): PtpResult {
    if (!ok) throw PtpException("$what failed: ${Ptp.responseName(code)}", code)
    return this
}
