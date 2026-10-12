package studio.miroir.tether.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.miroir.tether.camera.CameraSetting
import studio.miroir.tether.camera.SettingId
import studio.miroir.tether.image.Histogram
import studio.miroir.tether.tether.ShotFilter
import studio.miroir.tether.tether.matches
import studio.miroir.tether.tether.CameraStatus
import studio.miroir.tether.tether.LinkState
import studio.miroir.tether.tether.PreviewSource
import studio.miroir.tether.tether.Shot
import studio.miroir.tether.tether.Transfer

@Composable
fun HistogramView(h: Histogram?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(130.dp).background(Color(0xFF0E0E0E), RoundedCornerShape(8.dp))
                .border(1.dp, C.Line, RoundedCornerShape(8.dp)).padding(6.dp),
        ) {
            if (h != null) {
                Canvas(Modifier.matchParentSize()) {
                    val peak = h.peak.toFloat()
                    fun path(bins: IntArray): Path = Path().apply {
                        moveTo(0f, size.height)
                        for (i in 0..255) {
                            val x = i / 255f * size.width
                            val y = size.height - (bins[i] / peak).coerceAtMost(1f) * size.height
                            lineTo(x, y)
                        }
                        lineTo(size.width, size.height)
                        close()
                    }
                    // Quarter guide lines.
                    for (q in 1..3) {
                        val x = size.width * q / 4f
                        drawLine(Color(0xFF262626), Offset(x, 0f), Offset(x, size.height), 1f)
                    }
                    drawPath(path(h.red), Color(0x66FF4040))
                    drawPath(path(h.green), Color(0x6640FF60))
                    drawPath(path(h.blue), Color(0x664080FF))
                    drawPath(path(h.luma), Color(0xCCE8E2D6), style = Stroke(1.5f))
                    // Clipping indicators, like Lightroom's corner triangles.
                    val t = 9.dp.toPx()
                    if (h.shadowClipPct > 0.1f) drawPath(
                        Path().apply { moveTo(0f, 0f); lineTo(t, 0f); lineTo(0f, t); close() }, Color(0xFF2D6BFF),
                    )
                    if (h.highlightClipPct > 0.1f) drawPath(
                        Path().apply { moveTo(size.width, 0f); lineTo(size.width - t, 0f); lineTo(size.width, t); close() }, Color(0xFFFF2D2D),
                    )
                }
            }
        }
        if (h != null) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("Shadows", "%.1f%%".format(h.shadowClipPct), h.shadowClipPct > 0.5f)
                Stat("Average", "${(h.meanLuma / 255f * 100).toInt()}%", false)
                Stat("Highlights", "%.1f%%".format(h.highlightClipPct), h.highlightClipPct > 0.5f)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, alert: Boolean) {
    Column {
        Text(label.uppercase(), color = C.Muted, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = if (alert) C.Bad else C.Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Filmstrip(shots: List<Shot>, selectedId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val index = shots.indexOfFirst { it.id == selectedId }
    LaunchedEffect(index, shots.size) { if (index >= 0) state.animateScrollToItem(index) }
    LazyRow(
        modifier.fillMaxWidth().height(92.dp).background(C.Panel),
        state = state,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(shots, key = { it.id }) { shot ->
            val selected = shot.id == selectedId
            Box(
                Modifier.width(112.dp).height(76.dp).clip(RoundedCornerShape(6.dp))
                    .background(C.Raised)
                    .border(if (selected) 2.dp else 1.dp, if (selected) C.Gold else C.Line, RoundedCornerShape(6.dp))
                    .clickable { onSelect(shot.id) },
            ) {
                shot.thumb?.let {
                    Image(it.asImageBitmap(), shot.baseName, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                }
                Row(Modifier.align(Alignment.TopEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (shot.pick) Badge("♥", C.Gold)
                    if (shot.rating > 0) Badge("★${shot.rating}", C.Gold)
                }
                Row(Modifier.align(Alignment.BottomStart).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (shot.jpegFile != null) Badge("J")
                    if (shot.rawFile != null) Badge("RAW")
                    if (shot.source != PreviewSource.Jpeg) Badge("P", C.Warn)
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color = C.Cream) {
    Text(
        text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(Color(0xCC000000), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
fun StatusChip(status: CameraStatus) {
    val (dot, label) = when (status.state) {
        LinkState.Connected -> C.Ok to "${status.brand} ${status.model}".trim()
        LinkState.Connecting -> C.Warn to "Connecting…"
        LinkState.Error -> C.Bad to "Not connected"
        LinkState.Disconnected -> C.Muted to "No camera"
    }
    Row(
        Modifier.background(C.Raised, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, color = C.Cream, fontSize = 13.sp, maxLines = 1)
        status.battery?.let {
            Spacer(Modifier.width(10.dp))
            Text("🔋 $it%", color = if (it < 20) C.Bad else C.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun ShutterButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(88.dp).border(3.dp, if (enabled) C.Gold else C.Line, CircleShape).padding(7.dp)
            .clip(CircleShape)
            .background(if (enabled) C.Cream else C.Raised)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
fun TransferBar(t: Transfer?) {
    if (t == null) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Receiving ${t.filename}  ${t.done / 1_048_576}/${t.total / 1_048_576} MB",
            color = C.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        LinearProgressIndicator(
            progress = { t.fraction }, color = C.Gold, trackColor = C.Line,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = C.Cream, fontSize = 14.sp)
            if (hint != null) Text(hint, color = C.Muted, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = C.Ink, checkedTrackColor = C.Gold),
        )
    }
}

@Composable
fun ToolButton(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick, modifier = modifier,
        border = BorderStroke(1.dp, if (active) C.Gold else C.Line),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (active) C.Gold else C.Cream, fontSize = 13.sp, maxLines = 1)
    }
}

/** Filter chips and pick actions above the filmstrip. */
@Composable
fun FilterBar(
    shots: List<Shot>,
    filter: ShotFilter,
    onFilter: (ShotFilter) -> Unit,
    onExport: () -> Unit,
    onShareList: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(C.Panel).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (f in ShotFilter.entries) {
            val n = shots.count { f.matches(it) }
            val on = f == filter
            Text(
                "${f.label} $n", color = if (on) C.Ink else C.Cream, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) C.Gold else C.Raised)
                    .clickable { onFilter(f) }.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Copy pick list", color = C.Cream, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onShareList).padding(horizontal = 10.dp, vertical = 5.dp),
        )
        Text(
            "Save picks to Gallery", color = C.Gold, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, C.Gold, RoundedCornerShape(50))
                .clickable(onClick = onExport).padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** ★★★★★ and ♥ for the selected shot. */
@Composable
fun RatingControls(rating: Int, pick: Boolean, onRate: (Int) -> Unit, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            Text(
                if (i <= rating) "★" else "☆", color = if (i <= rating) C.Gold else C.Muted, fontSize = 22.sp,
                modifier = Modifier.clip(CircleShape).clickable { onRate(i) }.padding(horizontal = 3.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (pick) "♥ Pick" else "♡ Pick", color = if (pick) C.Ink else C.Cream, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (pick) C.Gold else C.Raised)
                .clickable(onClick = onPick).padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

/** Exposure settings with ‹ › steppers. */
@Composable
fun CameraPanel(settings: List<CameraSetting>, onStep: (SettingId, Int) -> Unit) {
    if (settings.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().background(C.Raised, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("CAMERA", color = C.Muted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(vertical = 4.dp))
        for (s in settings) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.id.label, color = C.Muted, fontSize = 12.sp, modifier = Modifier.width(72.dp))
                if (s.adjustable) StepButton("‹") { onStep(s.id, -1) }
                Text(
                    s.display, color = C.Cream, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1,
                )
                if (s.adjustable) StepButton("›") { onStep(s.id, 1) }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(C.Panel).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = C.Gold, fontSize = 22.sp)
    }
}
