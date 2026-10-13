package studio.miroir.tether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import studio.miroir.tether.image.CropRatio
import studio.miroir.tether.tether.AppLog
import studio.miroir.tether.tether.CameraStatus
import studio.miroir.tether.tether.LinkState
import studio.miroir.tether.tether.TetherViewModel
import studio.miroir.tether.tether.Tool
import studio.miroir.tether.tether.matches

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
        val filter by vm.filter.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val liveWanted by vm.liveViewWanted.collectAsStateWithLifecycle()
        val liveFrame by vm.liveFrame.collectAsStateWithLifecycle()
        val liveHistogram by vm.liveHistogram.collectAsStateWithLifecycle()
        val liveSupported by vm.liveSupported.collectAsStateWithLifecycle()
        val context = androidx.compose.ui.platform.LocalContext.current

        var showGrid by rememberSaveable { mutableStateOf(false) }
        var showGuide by rememberSaveable { mutableStateOf(false) }
        var showLog by rememberSaveable { mutableStateOf(false) }
        var bgIndex by rememberSaveable { mutableIntStateOf(1) }
        var crop by rememberSaveable { mutableStateOf(CropRatio.Off) }
        var clientMode by rememberSaveable { mutableStateOf(false) }

        val visibleShots = shots.filter { filter.matches(it) }
        val selected = shots.firstOrNull { it.id == selectedId }
        val connected = status.state == LinkState.Connected
        val live = liveWanted && connected

        val shareList = {
            val list = vm.pickList()
            if (list.isEmpty()) {
                android.widget.Toast.makeText(context, "No picks yet", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, list)
                context.startActivity(android.content.Intent.createChooser(send, "Pick list for Lightroom"))
            }
        }

        val stage: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                val a = analysis
                when {
                    live -> LiveViewPane(liveFrame, C.ViewerBackgrounds[bgIndex], showGrid, Modifier.fillMaxSize(), crop)
                    a != null -> Viewer(
                        a, C.ViewerBackgrounds[bgIndex], showClipping, showGrid, tool, loupe, neutral,
                        onTap = vm::onImageTap, modifier = Modifier.fillMaxSize(), crop = crop,
                    )
                    else -> EmptyState(
                        connected = connected, model = status.model, error = status.state == LinkState.Error,
                        message = status.message, onConnect = onConnectRequest, onGuide = { showGuide = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (live || a != null) {
                    ToolRail(
                        live = live, showClipping = showClipping, tool = tool, showGrid = showGrid, bgIndex = bgIndex, crop = crop,
                        onClipping = { vm.showClipping.value = !showClipping },
                        onLoupe = { vm.setTool(Tool.Loupe) },
                        onGreyCard = { vm.setTool(Tool.WhiteBalance) },
                        onGrid = { showGrid = !showGrid },
                        onCrop = { crop = crop.next() },
                        onBackground = { bgIndex = (bgIndex + 1) % C.ViewerBackgrounds.size },
                        onAutofocus = vm::autofocus,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                    )
                }
                if (!live && shots.isNotEmpty()) {
                    val index = shots.indexOfFirst { it.id == selectedId }
                    FrameCounter(
                        index + 1, shots.size, onPrev = { vm.selectRelative(-1) }, onNext = { vm.selectRelative(1) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    )
                }
            }
        }

        val inspector: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column {
                    SectionLabel(if (live) "Live histogram" else "Histogram")
                    HistogramView(if (live) liveHistogram else analysis?.histogram)
                }
                // In live view, show the last shot so you can compare while adjusting.
                val infoShot = if (live) shots.lastOrNull() else selected
                ShotInfo(
                    infoShot,
                    onRate = { r -> infoShot?.let { vm.setRating(it.id, r) } },
                    onPick = { infoShot?.let { vm.togglePick(it.id) } },
                    title = if (live) "Last shot" else "Shot",
                    showThumb = live,
                )
                Column {
                    SectionLabel("Session")
                    ToggleRow("Follow new shots", follow, { vm.followNewest.value = it })
                    ToggleRow("Copy RAW to tablet", copyRaw, vm::setCopyRaw, hint = "Off = faster, RAW stays on the card")
                }
            }
        }

        val tetherBar: @Composable () -> Unit = {
            TetherBar(
                connected = connected, settings = settings, liveSupported = liveSupported, liveOn = live,
                receiving = transfer != null,
                meterEv = (if (live) liveHistogram else analysis?.histogram)?.evFromMidGrey,
                onStep = vm::stepSetting, onToggleLive = vm::toggleLiveView,
                onShutter = vm::capture, onConnect = onConnectRequest,
            )
        }

        if (clientMode) {
            ClientView(
                analysis = analysis, selected = selected, index = shots.indexOfFirst { it.id == selectedId },
                total = shots.size, live = live, liveFrame = liveFrame, crop = crop,
                onPick = { selected?.let { vm.togglePick(it.id) } },
                onPrev = { vm.selectRelative(-1) }, onNext = { vm.selectRelative(1) },
                onExit = { clientMode = false },
            )
            toast?.let { t -> LaunchedEffect(t) { delay(3500); vm.clearToast() } }
        } else Box(Modifier.fillMaxSize().background(C.Ink).safeDrawingPadding()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > 840.dp
                Column(Modifier.fillMaxSize()) {
                    TopBar(
                        status, onConnect = onConnectRequest, onDisconnect = vm::disconnect,
                        onGuide = { showGuide = true }, onLog = { showLog = true },
                        onClient = { clientMode = true },
                    )
                    if (wide) {
                        Row(Modifier.weight(1f)) {
                            stage(Modifier.weight(1f).fillMaxHeight())
                            Column(
                                Modifier.width(320.dp).fillMaxHeight().background(C.Panel)
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                            ) {
                                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { inspector() }
                                TransferBar(transfer)
                            }
                        }
                        tetherBar()
                    } else {
                        stage(Modifier.weight(1f).fillMaxWidth())
                        tetherBar()
                        Column(
                            Modifier.fillMaxWidth().height(240.dp).background(C.Panel)
                                .verticalScroll(rememberScrollState()).padding(16.dp),
                        ) {
                            inspector()
                            TransferBar(transfer)
                        }
                    }
                    // The filmstrip appears with the first shot, so the image gets the space until then.
                    if (shots.isNotEmpty()) {
                        FilterBar(shots, filter, { vm.filter.value = it }, vm::exportPicks, shareList)
                        Filmstrip(visibleShots, selectedId, { id ->
                            // Tapping a shot leaves live view so you can review it.
                            if (liveWanted) vm.toggleLiveView()
                            vm.select(id)
                        })
                    }
                }
            }

            toast?.let { t ->
                LaunchedEffect(t) { delay(3500); vm.clearToast() }
                ToastPill(t.text, t.error, Modifier.align(Alignment.TopCenter).padding(top = 72.dp))
            }
        }

        if (showGuide) GuideDialog { showGuide = false }
        if (showLog) LogDialog(log, vm.sessionPath) { showLog = false }
    }
}

@Composable
private fun TopBar(
    status: CameraStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onGuide: () -> Unit,
    onLog: () -> Unit,
    onClient: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).background(C.Ink).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("MIROIR", color = C.Gold, fontSize = 19.sp, letterSpacing = 5.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(10.dp))
        Text("TETHER", style = T.Label.copy(letterSpacing = 3.sp))
        Spacer(Modifier.weight(1f))
        StatusChip(status, onConnect, onDisconnect)
        Spacer(Modifier.width(10.dp))
        IconButton(Glyph.Present, onClient, size = 40.dp)
        IconButton(Glyph.Info, onGuide, size = 40.dp)
        IconButton(Glyph.Log, onLog, size = 40.dp)
    }
}

/** Vertical icon toolbar floating on the viewer's left edge. */
@Composable
private fun ToolRail(
    live: Boolean,
    showClipping: Boolean,
    tool: Tool,
    showGrid: Boolean,
    bgIndex: Int,
    crop: CropRatio,
    onClipping: () -> Unit,
    onLoupe: () -> Unit,
    onGreyCard: () -> Unit,
    onGrid: () -> Unit,
    onCrop: () -> Unit,
    onBackground: () -> Unit,
    onAutofocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xD917171A))
            .border(1.dp, C.Line, RoundedCornerShape(14.dp)).padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (live) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(C.Gold).clickable(onClick = onAutofocus),
                contentAlignment = Alignment.Center,
            ) { Text("AF", style = T.Body.copy(color = C.Ink, fontWeight = FontWeight.Bold)) }
        } else {
            IconButton(Glyph.Clipping, onClipping, active = showClipping)
            IconButton(Glyph.Focus, onLoupe, active = tool == Tool.Loupe)
            IconButton(Glyph.Eyedropper, onGreyCard, active = tool == Tool.WhiteBalance)
        }
        IconButton(Glyph.Grid, onGrid, active = showGrid)
        // Tap to cycle 4:5 → 1:1 → 9:16 → 16:9 → off.
        Box(contentAlignment = Alignment.BottomCenter) {
            IconButton(Glyph.Crop, onCrop, active = crop != CropRatio.Off)
            if (crop != CropRatio.Off) Text(crop.label, style = T.Label.copy(fontSize = 8.sp, color = C.Gold))
        }
        Box(contentAlignment = Alignment.BottomCenter) {
            IconButton(Glyph.Background, onBackground)
            Text(listOf("K", "G", "L")[bgIndex.coerceIn(0, 2)], style = T.Label.copy(fontSize = 8.sp, color = C.Muted))
        }
    }
}

@Composable
internal fun FrameCounter(current: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0xCC000000)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(Glyph.ChevronLeft, onPrev, size = 36.dp)
        Text("${current.coerceAtLeast(0)} / $total", style = T.Small.copy(color = C.Cream), modifier = Modifier.padding(horizontal = 6.dp))
        IconButton(Glyph.ChevronRight, onNext, size = 36.dp)
    }
}

@Composable
private fun ToastPill(text: String, error: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.widthIn(max = 620.dp).clip(RoundedCornerShape(12.dp)).background(C.Raised)
            .border(1.dp, C.Line, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(44.dp).background(if (error) C.Bad else C.Gold))
        Text(text, style = T.Body.copy(fontSize = 13.sp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
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
                Text("$model connected", style = T.Title)
                Text(
                    "Take a photo with the camera or the shutter button. It appears here in a few seconds.",
                    color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                Text("Connect your camera", style = T.Title)
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
