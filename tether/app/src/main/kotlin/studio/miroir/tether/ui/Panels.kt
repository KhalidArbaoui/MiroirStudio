package studio.miroir.tether.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.miroir.tether.image.Histogram
import studio.miroir.tether.tether.CameraStatus
import studio.miroir.tether.tether.ExifSummary
import studio.miroir.tether.tether.LinkState
import studio.miroir.tether.tether.PreviewSource
import studio.miroir.tether.tether.Shot
import studio.miroir.tether.tether.ShotFilter
import studio.miroir.tether.tether.Transfer
import studio.miroir.tether.tether.matches

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.Label, modifier = modifier.padding(bottom = 8.dp))
}

@Composable
fun HistogramView(h: Histogram?, height: Dp = 150.dp, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0B0B0C))
                .border(1.dp, C.Line, RoundedCornerShape(8.dp)).padding(6.dp),
        ) {
            Canvas(Modifier.matchParentSize()) {
                // Quarter guides.
                for (q in 1..3) {
                    val x = size.width * q / 4f
                    drawLine(Color(0xFF232326), Offset(x, 0f), Offset(x, size.height), 1f)
                }
                if (h == null) return@Canvas
                val peak = h.peak.toFloat()
                fun path(bins: IntArray): Path = Path().apply {
                    moveTo(0f, size.height)
                    for (i in 0..255) {
                        lineTo(i / 255f * size.width, size.height - (bins[i] / peak).coerceAtMost(1f) * size.height)
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path(h.red), Color(0x66FF4040))
                drawPath(path(h.green), Color(0x6640FF60))
                drawPath(path(h.blue), Color(0x664080FF))
                drawPath(path(h.luma), Color(0xCCE7E7E7), style = Stroke(1.5f))
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
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            // Red only when clipping is meaningful; a few pure-black pixels are normal.
            Stat("Shadows", h?.let { "%.1f%%".format(it.shadowClipPct) } ?: "–", (h?.shadowClipPct ?: 0f) > 2f)
            Stat("Average", h?.let { "${(it.meanLuma / 255f * 100).toInt()}%" } ?: "–", false)
            Stat("Highlights", h?.let { "%.1f%%".format(it.highlightClipPct) } ?: "–", (h?.highlightClipPct ?: 0f) > 1f)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, alert: Boolean) {
    Column {
        Text(label.uppercase(), style = T.Label)
        Text(value, style = T.Value.copy(fontSize = 15.sp, color = if (alert) C.Bad else C.Cream))
    }
}

/** Name, source, exposure, lens, rating for the selected shot. */
@Composable
fun ShotInfo(
    shot: Shot?,
    onRate: (Int) -> Unit,
    onPick: () -> Unit,
    title: String = "Shot",
    showThumb: Boolean = false,
) {
    Column {
        SectionLabel(title)
        if (shot == null) {
            Text("Take a photo — its details appear here.", style = T.Small)
            return@Column
        }
        val exif: ExifSummary = shot.exif
        if (showThumb) {
            shot.thumb?.let {
                Image(
                    it.asImageBitmap(), shot.baseName,
                    Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(8.dp)).background(C.Ink),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(10.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(shot.baseName, style = T.Body.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f), maxLines = 1)
            Tag(shot.source.label, if (shot.source == PreviewSource.Jpeg) C.Ok else C.Warn)
        }
        run {
            if (exif.exposureLine.isNotEmpty()) Text(exif.exposureLine, style = T.Small.copy(color = C.Cream), modifier = Modifier.padding(top = 6.dp))
            listOfNotNull(exif.whiteBalance, exif.lens).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString("  ·  "), style = T.Small, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            if (exif.adobeRgb) Tag("Adobe RGB — set camera to sRGB", C.Warn, Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        RatingControls(shot.rating, shot.pick, onRate, onPick)
    }
}

@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, style = T.Small.copy(color = color, fontSize = 11.sp),
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun RatingControls(rating: Int, pick: Boolean, onRate: (Int) -> Unit, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) {
            Box(Modifier.size(34.dp).clip(CircleShape).clickable { onRate(i) }, contentAlignment = Alignment.Center) {
                Icon(if (i <= rating) Glyph.StarFilled else Glyph.Star, if (i <= rating) C.Gold else C.Faint, size = 22.dp)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(if (pick) C.GoldDim else C.Raised)
                .border(1.dp, if (pick) C.Gold else C.Line, RoundedCornerShape(50))
                .clickable(onClick = onPick).padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (pick) Glyph.HeartFilled else Glyph.Heart, if (pick) C.Gold else C.Cream, size = 16.dp)
            Spacer(Modifier.width(6.dp))
            Text("Pick", style = T.Body.copy(color = if (pick) C.Gold else C.Cream, fontSize = 13.sp))
        }
    }
}

@Composable
fun Filmstrip(shots: List<Shot>, selectedId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val index = shots.indexOfFirst { it.id == selectedId }
    LaunchedEffect(index, shots.size) { if (index >= 0) state.animateScrollToItem(index) }
    LazyRow(
        modifier.fillMaxWidth().height(104.dp).background(C.Ink),
        state = state,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(shots, key = { it.id }) { shot ->
            val selected = shot.id == selectedId
            Box(
                Modifier.width(126.dp).height(84.dp).clip(RoundedCornerShape(6.dp))
                    .background(C.Raised)
                    .border(if (selected) 2.dp else 1.dp, if (selected) C.Gold else C.Line, RoundedCornerShape(6.dp))
                    .clickable { onSelect(shot.id) },
            ) {
                shot.thumb?.let {
                    Image(it.asImageBitmap(), shot.baseName, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                }
                // Bottom scrim with rating + flags.
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x99000000))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (i in 1..shot.rating) Icon(Glyph.StarFilled, C.Gold, size = 10.dp)
                    Spacer(Modifier.weight(1f))
                    if (shot.source != PreviewSource.Jpeg) Box(Modifier.size(6.dp).background(C.Warn, CircleShape))
                    if (shot.rawFile != null) Text(" RAW", style = T.Label.copy(fontSize = 8.sp, color = C.Cream))
                    if (shot.pick) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Glyph.HeartFilled, C.Gold, size = 11.dp)
                    }
                }
            }
        }
    }
}

/** Camera status; tap to connect, or (when connected) to open a Disconnect menu. */
@Composable
fun StatusChip(status: CameraStatus, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        StatusChipBody(status, Modifier.clip(RoundedCornerShape(50)).clickable {
            if (status.state == LinkState.Connected) menu = true else if (status.state != LinkState.Connecting) onConnect()
        })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = C.Raised) {
            DropdownMenuItem(
                text = { Text("Disconnect camera", style = T.Body) },
                onClick = { menu = false; onDisconnect() },
            )
        }
    }
}

@Composable
private fun StatusChipBody(status: CameraStatus, modifier: Modifier) {
    val (dot, label) = when (status.state) {
        LinkState.Connected -> C.Ok to "${status.brand} ${status.model}".trim()
        LinkState.Connecting -> C.Warn to "Connecting…"
        LinkState.Error -> C.Bad to "Not connected · tap to connect"
        LinkState.Disconnected -> C.Faint to "No camera · tap to connect"
    }
    Row(
        modifier.background(C.Raised).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, style = T.Body.copy(fontSize = 13.sp), maxLines = 1)
        status.battery?.let {
            Spacer(Modifier.width(12.dp))
            Battery(it)
            Spacer(Modifier.width(5.dp))
            Text("$it%", style = T.Small.copy(color = if (it < 20) C.Bad else C.Muted))
        }
    }
}

@Composable
private fun Battery(level: Int) {
    val fill = if (level < 20) C.Bad else C.Cream
    Canvas(Modifier.size(20.dp, 10.dp)) {
        val body = Size(size.width - 2.dp.toPx(), size.height)
        drawRoundRect(C.Muted, Offset.Zero, body, CornerRadius(2.dp.toPx()), style = Stroke(1.2.dp.toPx()))
        drawRect(C.Muted, Offset(body.width, size.height * 0.3f), Size(2.dp.toPx(), size.height * 0.4f))
        val inset = 2.dp.toPx()
        drawRect(fill, Offset(inset, inset), Size((body.width - 2 * inset) * level / 100f, body.height - 2 * inset))
    }
}

@Composable
fun TransferBar(t: Transfer?) {
    if (t == null) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Receiving ${t.filename}  ${t.done / 1_048_576} / ${t.total / 1_048_576} MB",
            style = T.Small, maxLines = 1, overflow = TextOverflow.Ellipsis,
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
            Text(label, style = T.Body)
            if (hint != null) Text(hint, style = T.Small.copy(fontSize = 11.sp))
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.Ink, checkedTrackColor = C.Gold,
                uncheckedThumbColor = C.Muted, uncheckedTrackColor = C.Raised, uncheckedBorderColor = C.Line,
            ),
        )
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
        Modifier.fillMaxWidth().background(C.Ink).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (f in ShotFilter.entries) {
            val n = shots.count { f.matches(it) }
            val on = f == filter
            Text(
                "${f.label}  $n", style = T.Small.copy(color = if (on) C.Ink else C.Cream),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) C.Gold else C.Raised)
                    .clickable { onFilter(f) }.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        ActionChip(Glyph.Copy, "Pick list", onShareList)
        ActionChip(Glyph.Export, "Save picks to Gallery", onExport, accent = true)
    }
}

@Composable
private fun ActionChip(glyph: Glyph, label: String, onClick: () -> Unit, accent: Boolean = false) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .then(if (accent) Modifier.border(1.dp, C.Gold, RoundedCornerShape(50)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(glyph, if (accent) C.Gold else C.Cream, size = 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = T.Small.copy(color = if (accent) C.Gold else C.Cream))
    }
}
