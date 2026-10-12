package studio.miroir.tether

import studio.miroir.tether.ptp.ByteReader
import studio.miroir.tether.ptp.ByteWriter
import studio.miroir.tether.ptp.Ptp
import studio.miroir.tether.ptp.PtpTransport
import java.util.ArrayDeque

/** What the fake camera answers for one command. */
class FakeReply(val code: Int = Ptp.RC_OK, val data: ByteArray? = null, val params: LongArray = LongArray(0))

class FakeCommand(val code: Int, val params: LongArray, var dataOut: ByteArray? = null)

/**
 * Simulates a PTP camera on the USB bulk pipes. Each container is one USB transfer; a read
 * returns at most the remainder of the current transfer, like a real short-packet-terminated read.
 * With [mergeExactPackets], a data phase that is an exact multiple of the packet size is NOT
 * terminated by a zero-length packet, so a read can run into the response (real-world quirk).
 */
class FakeCamera(
    override val maxPacketSize: Int = 512,
    private val mergeExactPackets: Boolean = false,
    private val handler: (FakeCommand) -> FakeReply,
) : PtpTransport {
    val commands = mutableListOf<FakeCommand>()
    private val transfers = ArrayDeque<ByteArray>()
    private var current: ByteArray? = null
    private var currentPos = 0
    private var pendingCommand: FakeCommand? = null
    private var pendingTid = 0L

    override fun write(data: ByteArray, length: Int, timeoutMs: Int) {
        if (length == 0) return // ZLP
        val r = ByteReader(data, 0, length)
        r.u32()
        val type = r.u16()
        val code = r.u16()
        val tid = r.u32()
        when (type) {
            Ptp.TYPE_COMMAND -> {
                val params = LongArray(r.remaining / 4) { r.u32() }
                val cmd = FakeCommand(code, params)
                commands += cmd
                if (expectsDataOut(code)) {
                    pendingCommand = cmd; pendingTid = tid
                } else {
                    respond(cmd, tid)
                }
            }
            Ptp.TYPE_DATA -> {
                val cmd = pendingCommand ?: error("data without command")
                cmd.dataOut = data.copyOfRange(Ptp.HEADER_SIZE, length)
                pendingCommand = null
                respond(cmd, pendingTid)
            }
        }
    }

    /** Commands in these tests that carry a data-out phase. */
    var dataOutOps = setOf(Ptp.OP_SONY_SET_CONTROL_DEVICE_B, Ptp.OP_SONY_SET_CONTROL_DEVICE_A)
    private fun expectsDataOut(code: Int) = code in dataOutOps

    private fun respond(cmd: FakeCommand, tid: Long) {
        val reply = handler(cmd)
        val resp = ByteWriter().u32(Ptp.HEADER_SIZE + reply.params.size * 4)
            .u16(Ptp.TYPE_RESPONSE).u16(reply.code).u32(tid)
        reply.params.forEach { resp.u32(it) }
        val respBytes = resp.toByteArray()
        val data = reply.data
        if (data != null) {
            val d = ByteWriter(data.size + Ptp.HEADER_SIZE).u32(Ptp.HEADER_SIZE + data.size)
                .u16(Ptp.TYPE_DATA).u16(cmd.code).u32(tid).bytes(data).toByteArray()
            if (mergeExactPackets && d.size % maxPacketSize == 0) {
                transfers.add(d + respBytes)
                return
            }
            transfers.add(d)
            if (d.size % maxPacketSize == 0) transfers.add(ByteArray(0)) // ZLP
        }
        transfers.add(respBytes)
    }

    override fun read(buf: ByteArray, length: Int, timeoutMs: Int): Int {
        require(length % maxPacketSize == 0) { "read length $length not packet-aligned" }
        val cur = current ?: (transfers.pollFirst() ?: return -1).also { current = it; currentPos = 0 }
        val n = minOf(length, cur.size - currentPos)
        cur.copyInto(buf, 0, currentPos, currentPos + n)
        currentPos += n
        if (currentPos >= cur.size) current = null
        return n
    }

    override fun readEvent(buf: ByteArray, timeoutMs: Int): Int = -1
    override fun close() {}

    companion object {
        fun deviceInfo(model: String, manufacturer: String, ops: List<Int> = emptyList()): ByteArray {
            val w = ByteWriter()
            w.u16(100).u32(0x0AL).u16(100)
            str(w, "microsoft.com: 1.0")
            w.u16(0)
            w.u32(ops.size); ops.forEach { w.u16(it) }
            w.u32(1); w.u16(Ptp.EV_OBJECT_ADDED)
            w.u32(0) // props
            w.u32(0) // capture formats
            w.u32(1); w.u16(Ptp.FMT_JPEG)
            str(w, manufacturer); str(w, model); str(w, "1.00"); str(w, "123456")
            return w.toByteArray()
        }

        fun objectInfo(filename: String, format: Int, size: Int): ByteArray {
            val w = ByteWriter()
            w.u32(0x00010001L).u16(format).u16(0).u32(size.toLong())
            w.u16(Ptp.FMT_JPEG).u32(0).u32(0).u32(0)
            w.u32(8256).u32(5504).u32(24).u32(0).u16(0).u32(0).u32(0)
            str(w, filename); str(w, "20261012T120000")
            str(w, ""); str(w, "")
            return w.toByteArray()
        }

        fun str(w: ByteWriter, s: String) {
            if (s.isEmpty()) { w.u8(0); return }
            w.u8(s.length + 1)
            s.forEach { w.u16(it.code) }
            w.u16(0)
        }
    }
}
