package studio.miroir.tether.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import studio.miroir.tether.camera.CameraSetting
import studio.miroir.tether.camera.SettingId

/**
 * Bottom tether bar, like Lightroom's / Capture One's: exposure settings in one row,
 * then live view and the shutter at the right where the thumb rests.
 */
@Composable
fun TetherBar(
    connected: Boolean,
    settings: List<CameraSetting>,
    liveSupported: Boolean,
    liveOn: Boolean,
    receiving: Boolean,
    /** Average exposure vs mid-grey in stops, for the meter (null = no image yet). */
    meterEv: Float?,
    onStep: (SettingId, Int) -> Unit,
    onToggleLive: () -> Unit,
    onShutter: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(84.dp).background(C.Panel)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!connected) {
            Text("No camera connected", style = T.Body.copy(color = C.Muted), modifier = Modifier.weight(1f).padding(start = 8.dp))
            Text(
                "Connect", style = T.Body.copy(color = C.Ink),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(C.Gold).clickable(onClick = onConnect)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
            return@Row
        }

        Row(
            Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val mode = settings.firstOrNull { it.id == SettingId.Mode }
            if (mode != null) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, C.Gold, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(mode.display, style = T.Value.copy(color = C.Gold))
                }
                Spacer(Modifier.width(10.dp))
            }
            settings.filter { it.id != SettingId.Mode }.forEachIndexed { i, s ->
                if (i > 0) Box(Modifier.width(1.dp).height(40.dp).background(C.Line))
                SettingCell(s, onStep)
            }
        }

        Spacer(Modifier.width(12.dp))
        ExposureMeter(meterEv)
        Spacer(Modifier.width(16.dp))
        if (liveSupported) {
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (liveOn) C.Bad.copy(alpha = 0.18f) else C.Raised)
                    .border(1.dp, if (liveOn) C.Bad else C.Line, RoundedCornerShape(50))
                    .clickable(onClick = onToggleLive).padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Glyph.Live, if (liveOn) C.Bad else C.Cream, size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text("LIVE", style = T.Label.copy(color = if (liveOn) C.Bad else C.Cream))
            }
            Spacer(Modifier.width(14.dp))
        }
        ShutterButton(enabled = true, busy = receiving, onClick = onShutter)
    }
}

@Composable
private fun SettingCell(s: CameraSetting, onStep: (SettingId, Int) -> Unit) {
    Column(Modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            (s.lockedBy?.let { "${s.id.label} · $it" } ?: s.id.label).uppercase(),
            style = T.Label, maxLines = 1,
        )
        // Fixed height so locked and adjustable cells line up.
        Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.adjustable) {
                StepArrow(Glyph.ChevronLeft) { onStep(s.id, -1) }
            } else {
                Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) { Icon(Glyph.Lock, C.Faint, size = 14.dp) }
            }
            Text(
                s.display, style = T.Value.copy(color = if (s.adjustable) C.Cream else C.Muted),
                textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.widthIn(min = 64.dp),
            )
            if (s.adjustable) StepArrow(Glyph.ChevronRight) { onStep(s.id, 1) } else Spacer(Modifier.width(30.dp))
        }
    }
}

@Composable
private fun StepArrow(glyph: Glyph, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp, 40.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, C.Muted, size = 18.dp)
    }
}

/** Shutter: gold ring, light core; the ring dims while a file is being received. */
@Composable
fun ShutterButton(enabled: Boolean, busy: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(64.dp).clip(CircleShape)
            .border(3.dp, if (!enabled) C.Line else if (busy) C.Gold.copy(alpha = 0.4f) else C.Gold, CircleShape)
            .padding(6.dp).clip(CircleShape)
            .background(if (enabled) C.Cream else C.Raised)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/** −3 … +3 stop scale with a gold marker, like the meter in a camera viewfinder. */
@Composable
private fun ExposureMeter(ev: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (ev == null) "EXPOSURE" else "EXPOSURE  " + (if (ev >= 0.05f) "+" else if (ev <= -0.05f) "" else "±") + "%.1f".format(ev),
            style = T.Label,
        )
        androidx.compose.foundation.Canvas(Modifier.width(150.dp).height(30.dp)) {
            val mid = size.height * 0.45f
            val stop = size.width / 6.6f
            val cx = size.width / 2
            for (i in -9..9) {
                val x = cx + i * stop / 3
                val major = i % 3 == 0
                drawLine(
                    if (i == 0) C.Cream else if (major) C.Muted else C.Faint,
                    androidx.compose.ui.geometry.Offset(x, mid - if (major) 7.dp.toPx() else 4.dp.toPx()),
                    androidx.compose.ui.geometry.Offset(x, mid + if (major) 7.dp.toPx() else 4.dp.toPx()),
                    strokeWidth = if (i == 0) 2.dp.toPx() else 1.dp.toPx(),
                )
            }
            if (ev != null) {
                val clamped = ev.coerceIn(-3.3f, 3.3f)
                val x = cx + clamped * stop
                val color = if (kotlin.math.abs(ev) > 3f) C.Bad else C.Gold
                val tri = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x, mid + 9.dp.toPx())
                    lineTo(x - 6.dp.toPx(), size.height)
                    lineTo(x + 6.dp.toPx(), size.height)
                    close()
                }
                drawPath(tri, color)
            }
        }
    }
}
