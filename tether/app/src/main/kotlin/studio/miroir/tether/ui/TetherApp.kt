package studio.miroir.tether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import studio.miroir.tether.tether.AppLog
import studio.miroir.tether.tether.LinkState
import studio.miroir.tether.tether.PreviewSource
import studio.miroir.tether.tether.TetherViewModel
import studio.miroir.tether.tether.Tool

@Composable
fun TetherApp(vm: TetherViewModel, onConnectRequest: () -> Unit) {
    MiroirTheme {
        val status by vm.status.collectAsStateWithLifecycle()
        val shots by vm.shots.collectAsStateWithLifecycle()
        val selectedId by vm.selectedId.collectAsStateWithLifecycle()
        val analysis by vm.analysis.collectAsStateWithLifecycle()
        val transfer by vm.transfer.collectAsStateWithLifecycle()
        val toast by vm.toast.collectAsStateWithLifecycle()
        val showClipping by vm.showClipping.collectAsStateWithLifecycle()
        val tool by vm.tool.collectAsStateWithLifecycle()
        val loupe by vm.loupe.collectAsStateWithLifecycle()
        val neutral by vm.neutral.collectAsStateWithLifecycle()
        val follow by vm.followNewest.collectAsStateWithLifecycle()
        val copyRaw by vm.copyRaw.collectAsStateWithLifecycle()
        val log by AppLog.text.collectAsStateWithLifecycle()

        var showGuide by rememberSaveable { mutableStateOf(false) }
        var showLog by rememberSaveable { mutableStateOf(false) }
        var bgIndex by rememberSaveable { mutableIntStateOf(1) }
        val selected = shots.firstOrNull { it.id == selectedId }
        val connected = status.state == LinkState.Connected

        val viewer: @Composable (Modifier) -> Unit = { m ->
            val a = analysis
            if (a != null) {
                Viewer(
                    a, C.ViewerBackgrounds[bgIndex], showClipping, tool, loupe, neutral,
                    onTap = vm::onImageTap, modifier = m,
                )
            } else {
                EmptyState(
                    connected = connected, model = status.model, error = status.state == LinkState.Error,
                    message = status.message, onConnect = onConnectRequest, onGuide = { showGuide = true }, modifier = m,
                )
            }
        }

        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HistogramView(analysis?.histogram)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolButton("Clipping", showClipping, { vm.showClipping.value = !showClipping }, Modifier.weight(1f))
                    ToolButton("Focus 100%", tool == Tool.Loupe, { vm.setTool(Tool.Loupe) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolButton("Grey card", tool == Tool.WhiteBalance, { vm.setTool(Tool.WhiteBalance) }, Modifier.weight(1f))
                    ToolButton(listOf("Black", "Grey", "Light")[bgIndex], false, { bgIndex = (bgIndex + 1) % 3 }, Modifier.weight(1f))
                }
                ToggleRow("Follow new shots", follow, { vm.followNewest.value = it })
                ToggleRow("Copy RAW to tablet", copyRaw, vm::setCopyRaw, hint = "Off = faster, RAW stays on the card")
            }
        }

        Box(Modifier.fillMaxSize().background(C.Ink).safeDrawingPadding()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > 840.dp
                Column(Modifier.fillMaxSize()) {
                    TopBar(status, onConnect = onConnectRequest, onDisconnect = vm::disconnect, onGuide = { showGuide = true }, onLog = { showLog = true })
                    if (wide) {
                        Row(Modifier.weight(1f)) {
                            Column(Modifier.weight(1f)) {
                                viewer(Modifier.weight(1f).fillMaxWidth())
                                InfoBar(selected?.baseName, selected?.source, analysis?.exif, onPrev = { vm.selectRelative(-1) }, onNext = { vm.selectRelative(1) })
                            }
                            Column(
                                Modifier.width(320.dp).fillMaxHeight().background(C.Panel).padding(16.dp),
                            ) {
                                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controls() }
                                TransferBar(transfer)
                                Spacer(Modifier.height(12.dp))
                                ShutterButton(connected, vm::capture, Modifier.align(Alignment.CenterHorizontally))
                            }
                        }
                    } else {
                        viewer(Modifier.weight(1f).fillMaxWidth())
                        InfoBar(selected?.baseName, selected?.source, analysis?.exif, onPrev = { vm.selectRelative(-1) }, onNext = { vm.selectRelative(1) })
                        Row(
                            Modifier.fillMaxWidth().background(C.Panel).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).height(260.dp).verticalScroll(rememberScrollState())) {
                                controls()
                                TransferBar(transfer)
                            }
                            Spacer(Modifier.width(12.dp))
                            ShutterButton(connected, vm::capture)
                        }
                    }
                    Filmstrip(shots, selectedId, vm::select)
                }
            }

            toast?.let { msg ->
                LaunchedEffect(msg) { delay(4000); vm.clearToast() }
                Text(
                    msg, color = C.Ink, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)
                        .background(C.Gold, RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }

        if (showGuide) GuideDialog { showGuide = false }
        if (showLog) LogDialog(log, vm.sessionPath) { showLog = false }
    }
}

@Composable
private fun TopBar(
    status: studio.miroir.tether.tether.CameraStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onGuide: () -> Unit,
    onLog: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(C.Ink).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("MIROIR", color = C.Gold, fontSize = 20.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Light)
        Text("  TETHER", color = C.Muted, fontSize = 12.sp, letterSpacing = 3.sp)
        Spacer(Modifier.weight(1f))
        StatusChip(status)
        Spacer(Modifier.width(8.dp))
        when (status.state) {
            LinkState.Connected -> TextButton(onClick = onDisconnect) { Text("Disconnect", color = C.Muted) }
            LinkState.Connecting -> {}
            else -> TextButton(onClick = onConnect) { Text("Connect", color = C.Gold) }
        }
        TextButton(onClick = onGuide) { Text("Guide", color = C.Cream) }
        TextButton(onClick = onLog) { Text("Log", color = C.Cream) }
    }
}

@Composable
private fun InfoBar(
    name: String?,
    source: PreviewSource?,
    exif: studio.miroir.tether.tether.ExifSummary?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(C.Ink).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPrev) { Text("‹", color = C.Cream, fontSize = 22.sp) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name ?: "—", color = C.Cream, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (source != null) {
                    Spacer(Modifier.width(8.dp))
                    Pill(source.label, color = if (source == PreviewSource.Jpeg) C.Ok else C.Warn)
                }
                if (exif?.adobeRgb == true) {
                    Spacer(Modifier.width(6.dp))
                    Pill("Adobe RGB — set camera to sRGB", color = C.Warn)
                }
            }
            if (exif != null) {
                Text(
                    listOfNotNull(exif.exposureLine.ifEmpty { null }, exif.whiteBalance, exif.lens).joinToString("   ·   "),
                    color = C.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onNext) { Text("›", color = C.Cream, fontSize = 22.sp) }
    }
}

@Composable
private fun EmptyState(
    connected: Boolean,
    model: String,
    error: Boolean,
    message: String,
    onConnect: () -> Unit,
    onGuide: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.background(C.ViewerBackgrounds[0]), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (connected) {
                Text("$model connected", color = C.Gold, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Text(
                    "Take a photo with the camera or the shutter button. It appears here in a few seconds.",
                    color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                Text("Connect your camera", color = C.Gold, fontSize = 26.sp, fontWeight = FontWeight.Light)
                Text(
                    "Plug the Nikon Z8 or Sony A7 III into the tablet with a USB-C cable, turn the camera on, and allow access when Android asks.",
                    color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
                )
                if (error && message.isNotEmpty()) {
                    Text(message, color = C.Bad, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                }
                Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = C.Gold, contentColor = C.Ink)) {
                        Text("Connect")
                    }
                    OutlinedButton(onClick = onGuide) { Text("Camera setup guide", color = C.Cream) }
                }
            }
        }
    }
}
