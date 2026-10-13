package studio.miroir.tether.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import studio.miroir.tether.image.CropRatio
import studio.miroir.tether.tether.Analysis
import studio.miroir.tether.tether.Shot
import studio.miroir.tether.tether.Tool

/**
 * Full-screen view for the client on set: just the photo, new shots appear as they're taken,
 * and the client can tap Pick to approve. No histograms or camera settings.
 */
@Composable
fun ClientView(
    analysis: Analysis?,
    selected: Shot?,
    index: Int,
    total: Int,
    live: Boolean,
    liveFrame: android.graphics.Bitmap?,
    crop: CropRatio,
    cropCenter: androidx.compose.ui.geometry.Offset,
    onPick: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onExit: () -> Unit,
) {
    ImmersiveMode()
    BackHandler(onBack = onExit)
    val bg = Color(0xFF0B0B0B)
    Box(Modifier.fillMaxSize().background(bg)) {
        when {
            live -> LiveViewPane(liveFrame, bg, showGrid = false, modifier = Modifier.fillMaxSize(), crop = crop, cropCenter = cropCenter)
            analysis != null -> Viewer(
                analysis, bg, showClipping = false, showGrid = false, tool = Tool.None, loupe = null, neutral = null,
                onTap = { _, _ -> }, modifier = Modifier.fillMaxSize(), crop = crop, cropCenter = cropCenter,
            )
            else -> Text(
                "Waiting for the first photo…", color = C.Muted, fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(studio.miroir.tether.R.drawable.miroir_logo), contentDescription = "MIROIR Studio",
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 26.dp)
                .height(30.dp).alpha(0.75f),
        )

        IconButton(
            Glyph.Close, onExit, tint = C.Muted,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0x99000000)),
        )

        if (!live && selected != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FrameCounter(index + 1, total, onPrev, onNext)
                ClientPick(selected.pick, onPick)
            }
        }
    }
}

@Composable
private fun ClientPick(pick: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (pick) C.Accent else Color(0xCC000000))
            .border(1.dp, C.Accent, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (pick) Glyph.HeartFilled else Glyph.Heart, if (pick) C.Ink else C.Accent, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(if (pick) "Approved" else "Approve", style = T.Body.copy(color = if (pick) C.Ink else C.Accent, fontSize = 15.sp))
    }
}

/** Hides Android's status and navigation bars while shown; a swipe from the edge brings them back briefly. */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
