package studio.miroir.tether.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private class GuideSection(val title: String, val items: List<String>)

private val guide = listOf(
    GuideSection(
        "Nikon Z8",
        listOf(
            "Connect with a USB-C to USB-C cable straight into the tablet (no hub).",
            "Network menu → Airplane mode ON (a Wi-Fi/computer link blocks USB).",
            "Setup menu → USB power delivery OFF, so the camera doesn't drain the tablet.",
            "Image quality: RAW + JPEG fine, JPEG size L. Colour space: sRGB.",
            "Tone mode: SDR (HLG/HEIF won't match Lightroom).",
            "Active D-Lighting OFF — Lightroom ignores it, so the JPEG would look brighter than your RAW.",
            "Custom setting d8 'Apply settings to live view' ON, so live view, the live histogram and the meter show the real exposure.",
        ),
    ),
    GuideSection(
        "Sony A7 III",
        listOf(
            "Setup menu → USB Connection → PC Remote.",
            "Network → PC Remote Function → Still Img. Save Dest. → PC+Camera (keeps a copy on the card).",
            "Network → PC Remote Function → RAW+J PC Save Img → RAW & JPEG (if shooting RAW+JPEG).",
            "Airplane mode ON. Colour space sRGB.",
            "DRO / Auto HDR OFF — Lightroom ignores it.",
            "Use the USB-C port (faster than micro-USB).",
            "Live View Display → Setting Effect ON, otherwise live view looks bright even when the photo will be dark.",
            "USB Power Supply OFF, and lens aperture ring on A (e.g. Sigma 24-70) so the tablet can change aperture.",
        ),
    ),
    GuideSection(
        "Match Lightroom (no surprises)",
        listOf(
            "The tablet shows the camera's JPEG. Lightroom renders the RAW with its own profile — by default 'Adobe Color', which looks different.",
            "Lightroom Classic → Preferences → Presets → Raw Defaults → 'Camera Settings'. Lightroom then picks the Camera Matching profile for your in-camera Picture Control / Creative Style.",
            "Or in your import preset, set Profile → Camera Matching → Camera Standard (and set the camera to Standard).",
            "White balance: keep 'As Shot' on import. Use the grey-card tool here to fix WB in camera before shooting.",
            "Lens corrections: enable profile corrections in Lightroom (the camera JPEG already corrects distortion/vignetting).",
            "Expect Lightroom to be very close, not pixel-identical — check one test shot side by side at the start of each job.",
        ),
    ),
    GuideSection(
        "Samsung tablet screen",
        listOf(
            "Settings → Display → Screen mode → Natural (Vivid oversaturates everything).",
            "Turn OFF Eye comfort shield and Adaptive brightness. Fix brightness around 70–80% and keep it the same every shoot.",
            "Use the grey background button in the viewer — judging colour on warm or black backgrounds misleads you.",
            "Calibrate your PC/Mac monitor too; the tablet should be compared against a calibrated screen.",
        ),
    ),
)

@Composable
fun GuideDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.widthIn(max = 760.dp).fillMaxWidth(0.92f).padding(vertical = 24.dp)
                .background(C.Panel, RoundedCornerShape(16.dp)).padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Setup & colour guide", color = C.Gold, fontSize = 24.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(8.dp))
            for (s in guide) {
                Text(s.title.uppercase(), color = C.Gold, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                s.items.forEach { Text("•  $it", color = C.Cream, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Ink)) { Text("Got it") }
        }
    }
}

@Composable
fun LogDialog(text: String, sessionPath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().padding(24.dp).background(C.Panel, RoundedCornerShape(16.dp)).padding(20.dp)) {
            Text("Connection log", color = C.Gold, fontSize = 22.sp, fontWeight = FontWeight.Light)
            Text("Photos are saved in: $sessionPath", color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth().background(C.Ink, RoundedCornerShape(8.dp)).padding(12.dp)
                    .verticalScroll(rememberScrollState(), reverseScrolling = true)
                    .horizontalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(text.ifEmpty { "No log yet." }, color = C.Cream, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "MIROIR Tether log")
                            .putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, "Share log"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Ink),
                ) { Text("Share log") }
                OutlinedButton(onClick = onDismiss) { Text("Close", color = C.Cream) }
            }
        }
    }
}
