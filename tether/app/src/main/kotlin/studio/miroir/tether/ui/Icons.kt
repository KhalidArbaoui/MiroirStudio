package studio.miroir.tether.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The app's line-icon set, drawn on a 24-unit grid with one stroke weight. */
enum class Glyph {
    ChevronLeft, ChevronRight, Clipping, Focus, Eyedropper, Grid, Background,
    Live, Info, Log, Plug, Unplug, Lock, Star, StarFilled, Heart, HeartFilled, Export, Close, Copy, Crop, Present,
}

@Composable
fun Icon(glyph: Glyph, tint: Color = C.Cream, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, tint) }
}

/** Square icon button; [active] shows the gold accent state. */
@Composable
fun IconButton(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    tint: Color = if (active) C.Gold else C.Cream,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp))
            .background(if (active) C.GoldDim else Color.Transparent)
            .then(if (active) Modifier.border(1.dp, C.Gold.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(glyph, if (enabled) tint else C.Faint, size = size * 0.5f)
    }
}

private fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val u = size.minDimension / 24f
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(c, p(x1, y1), p(x2, y2), strokeWidth = 1.8f * u, cap = StrokeCap.Round)
    fun path(vararg pts: Pair<Float, Float>, close: Boolean = false, fill: Boolean = false) {
        val path = Path().apply {
            moveTo(pts[0].first * u, pts[0].second * u)
            for (i in 1 until pts.size) lineTo(pts[i].first * u, pts[i].second * u)
            if (close) close()
        }
        if (fill) drawPath(path, c) else drawPath(path, c, style = stroke)
    }
    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float = 2f) =
        drawRoundRect(c, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = stroke)

    when (g) {
        Glyph.ChevronLeft -> path(15f to 5f, 8f to 12f, 15f to 19f)
        Glyph.ChevronRight -> path(9f to 5f, 16f to 12f, 9f to 19f)
        Glyph.Clipping -> {
            // Zebra square: blown highlights.
            rect(4f, 4f, 16f, 16f)
            line(4f, 12f, 12f, 4f); line(4f, 20f, 20f, 4f); line(12f, 20f, 20f, 12f)
        }
        Glyph.Focus -> {
            // Corner brackets + centre point.
            path(4f to 9f, 4f to 4f, 9f to 4f); path(15f to 4f, 20f to 4f, 20f to 9f)
            path(20f to 15f, 20f to 20f, 15f to 20f); path(9f to 20f, 4f to 20f, 4f to 15f)
            drawCircle(c, 1.6f * u, p(12f, 12f))
        }
        Glyph.Eyedropper -> {
            drawCircle(c, 3.2f * u, p(17f, 7f), style = stroke)
            line(14.7f, 9.3f, 5f, 19f); line(11f, 10f, 14f, 13f); line(5f, 19f, 4f, 20f)
        }
        Glyph.Grid -> {
            rect(4f, 4f, 16f, 16f, 1.5f)
            line(9.33f, 4f, 9.33f, 20f); line(14.66f, 4f, 14.66f, 20f)
            line(4f, 9.33f, 20f, 9.33f); line(4f, 14.66f, 20f, 14.66f)
        }
        Glyph.Background -> {
            drawCircle(c, 8f * u, p(12f, 12f), style = stroke)
            drawArc(c, 90f, 180f, true, p(4f, 4f), Size(16f * u, 16f * u))
        }
        Glyph.Live -> {
            drawCircle(c, 8f * u, p(12f, 12f), style = stroke)
            drawCircle(c, 3.5f * u, p(12f, 12f))
        }
        Glyph.Info -> {
            drawCircle(c, 8.5f * u, p(12f, 12f), style = stroke)
            line(12f, 11f, 12f, 16.5f)
            drawCircle(c, 1.1f * u, p(12f, 7.7f))
        }
        Glyph.Log -> {
            for (y in listOf(7f, 12f, 17f)) {
                drawCircle(c, 1.1f * u, p(5f, y)); line(9f, y, 20f, y)
            }
        }
        Glyph.Plug -> {
            line(9f, 3f, 9f, 7f); line(15f, 3f, 15f, 7f)
            path(6f to 7f, 18f to 7f, 18f to 11f, 15f to 15f, 9f to 15f, 6f to 11f, close = true)
            line(12f, 15f, 12f, 21f)
        }
        Glyph.Unplug -> {
            line(9f, 3f, 9f, 7f); line(15f, 3f, 15f, 7f)
            path(6f to 7f, 18f to 7f, 18f to 11f, 15f to 15f, 9f to 15f, 6f to 11f, close = true)
            line(4f, 4f, 20f, 20f)
        }
        Glyph.Star, Glyph.StarFilled -> {
            val pts = (0 until 10).map { i ->
                val r = if (i % 2 == 0) 9f else 4f
                val a = -PI / 2 + i * PI / 5
                (12f + r * cos(a).toFloat()) to (12.5f + r * sin(a).toFloat())
            }
            path(*pts.toTypedArray(), close = true, fill = g == Glyph.StarFilled)
        }
        Glyph.Heart, Glyph.HeartFilled -> {
            val h = Path().apply {
                moveTo(12f * u, 20f * u)
                cubicTo(4f * u, 14.5f * u, 2.5f * u, 10f * u, 4.5f * u, 7f * u)
                cubicTo(6.5f * u, 4f * u, 10.5f * u, 4.5f * u, 12f * u, 8f * u)
                cubicTo(13.5f * u, 4.5f * u, 17.5f * u, 4f * u, 19.5f * u, 7f * u)
                cubicTo(21.5f * u, 10f * u, 20f * u, 14.5f * u, 12f * u, 20f * u)
                close()
            }
            if (g == Glyph.HeartFilled) drawPath(h, c) else drawPath(h, c, style = stroke)
        }
        Glyph.Export -> {
            path(8f to 10f, 5f to 10f, 5f to 20f, 19f to 20f, 19f to 10f, 16f to 10f)
            line(12f, 3f, 12f, 14f); path(8f to 7f, 12f to 3f, 16f to 7f)
        }
        Glyph.Lock -> {
            rect(5f, 11f, 14f, 10f)
            val arc = Path().apply {
                moveTo(8f * u, 11f * u); lineTo(8f * u, 8f * u)
                cubicTo(8f * u, 3.5f * u, 16f * u, 3.5f * u, 16f * u, 8f * u); lineTo(16f * u, 11f * u)
            }
            drawPath(arc, c, style = stroke)
        }
        Glyph.Close -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        Glyph.Copy -> { rect(8f, 8f, 12f, 12f); path(16f to 5f, 16f to 4f, 4f to 4f, 4f to 16f, 5f to 16f) }
        Glyph.Crop -> { path(7f to 3f, 7f to 17f, 21f to 17f); path(3f to 7f, 17f to 7f, 17f to 21f) }
        Glyph.Present -> {
            // Screen on a stand: show the client.
            rect(3f, 4f, 18f, 12f, 1.5f)
            line(12f, 16f, 12f, 20f); line(8f, 20f, 16f, 20f)
        }
    }
}
