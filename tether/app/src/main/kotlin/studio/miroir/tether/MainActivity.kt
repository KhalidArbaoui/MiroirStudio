package studio.miroir.tether

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import studio.miroir.tether.tether.AppLog
import studio.miroir.tether.tether.TetherViewModel
import studio.miroir.tether.ui.TetherApp
import studio.miroir.tether.usb.UsbPtpTransport

class MainActivity : ComponentActivity() {
    private val vm: TetherViewModel by viewModels()
    private val usb by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.usbDevice() ?: return
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        vm.connect(usb, device)
                    } else {
                        AppLog.log("USB permission denied for ${device.productName}")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> vm.onUsbDetached()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Let Android colour-manage Adobe RGB / P3 images on wide-gamut Samsung panels.
        if (resources.configuration.isScreenWideColorGamut) {
            window.colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        setContent { TetherApp(vm, onConnectRequest = { connectToAttachedCamera() }) }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Don't ask for permission here: the permission dialog itself pauses/resumes the activity.
        connectToAttachedCamera(askPermission = false)
    }

    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            // Launched by plugging in a camera: permission is already granted for this device.
            intent.usbDevice()?.let { request(it) }
        }
    }

    /** Finds a camera on the USB port and connects (asking permission if needed). */
    fun connectToAttachedCamera(askPermission: Boolean = true) {
        val camera = usb.deviceList.values.firstOrNull { UsbPtpTransport.findPtpInterface(it) != null }
        if (camera == null) {
            if (askPermission) AppLog.log("No camera found on USB")
            return
        }
        if (askPermission || usb.hasPermission(camera)) request(camera)
    }

    private fun request(device: UsbDevice) {
        if (usb.hasPermission(device)) {
            vm.connect(usb, device)
            return
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), flags,
        )
        usb.requestPermission(device, pi)
    }

    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION") getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    companion object {
        const val ACTION_USB_PERMISSION = "studio.miroir.tether.USB_PERMISSION"
    }
}
