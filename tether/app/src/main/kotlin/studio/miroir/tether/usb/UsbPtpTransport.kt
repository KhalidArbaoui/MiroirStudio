package studio.miroir.tether.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import studio.miroir.tether.ptp.PtpException
import studio.miroir.tether.ptp.PtpTransport

/** PTP over Android USB host: one bulk-in, one bulk-out and one interrupt-in endpoint. */
class UsbPtpTransport private constructor(
    private val connection: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val bulkIn: UsbEndpoint,
    private val bulkOut: UsbEndpoint,
    private val interruptIn: UsbEndpoint?,
) : PtpTransport {

    override val maxPacketSize: Int = bulkIn.maxPacketSize.coerceAtLeast(64)

    override fun write(data: ByteArray, length: Int, timeoutMs: Int) {
        var off = 0
        if (length == 0) {
            connection.bulkTransfer(bulkOut, data, 0, timeoutMs)
            return
        }
        while (off < length) {
            val n = connection.bulkTransfer(bulkOut, data, off, length - off, timeoutMs)
            if (n <= 0) throw PtpException("USB write failed ($n) — check the cable")
            off += n
        }
    }

    override fun read(buf: ByteArray, length: Int, timeoutMs: Int): Int =
        connection.bulkTransfer(bulkIn, buf, 0, minOf(length, buf.size), timeoutMs)

    override fun readEvent(buf: ByteArray, timeoutMs: Int): Int {
        val ep = interruptIn ?: return -1
        return connection.bulkTransfer(ep, buf, 0, minOf(buf.size, ep.maxPacketSize.coerceAtLeast(64)), timeoutMs)
    }

    override fun reset() {
        // PTP class request "Device Reset" (0x66), then clear endpoint halts.
        connection.controlTransfer(0x21, 0x66, 0, iface.id, null, 0, 1000)
        for (ep in listOfNotNull(bulkIn, bulkOut, interruptIn)) {
            connection.controlTransfer(0x02, 0x01, 0, ep.address, null, 0, 1000)
        }
    }

    /** USB endpoints, for the log. */
    fun describe(): String =
        "iface ${iface.id} class ${iface.interfaceClass}, bulk-in 0x%02X (%d B), bulk-out 0x%02X (%d B), int-in %s".format(
            bulkIn.address, bulkIn.maxPacketSize, bulkOut.address, bulkOut.maxPacketSize,
            interruptIn?.let { "0x%02X".format(it.address) } ?: "none",
        )

    /** PTP class request "Get Device Status" (0x67): 0x2001 = OK, 0x2019 = busy. */
    fun deviceStatus(): String {
        val buf = ByteArray(32)
        val n = connection.controlTransfer(0xA1, 0x67, 0, iface.id, buf, buf.size, 1000)
        if (n < 4) return "no answer ($n)"
        val code = (buf[2].toInt() and 0xFF) or ((buf[3].toInt() and 0xFF) shl 8)
        return "0x%04X".format(code)
    }

    override fun close() {
        runCatching { connection.releaseInterface(iface) }
        runCatching { connection.close() }
    }

    companion object {
        fun findPtpInterface(device: UsbDevice): UsbInterface? {
            val candidates = (0 until device.interfaceCount).map { device.getInterface(it) }
            return candidates.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE && hasBulkPair(it) }
                ?: candidates.firstOrNull { hasBulkPair(it) }
        }

        private fun hasBulkPair(i: UsbInterface): Boolean {
            val eps = (0 until i.endpointCount).map { i.getEndpoint(it) }
            return eps.any { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN } &&
                eps.any { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
        }

        fun open(manager: UsbManager, device: UsbDevice): UsbPtpTransport {
            val iface = findPtpInterface(device) ?: throw PtpException("This USB device is not a PTP camera")
            val eps = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
            val bulkIn = eps.first { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN }
            val bulkOut = eps.first { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
            val intIn = eps.firstOrNull { it.type == UsbConstants.USB_ENDPOINT_XFER_INT && it.direction == UsbConstants.USB_DIR_IN }
            val conn = manager.openDevice(device) ?: throw PtpException("Could not open the camera (USB permission?)")
            if (!conn.claimInterface(iface, true)) {
                conn.close()
                throw PtpException("Could not claim the camera — close other apps using it (Gallery import, Snapbridge…)")
            }
            return UsbPtpTransport(conn, iface, bulkIn, bulkOut, intIn)
        }
    }
}
