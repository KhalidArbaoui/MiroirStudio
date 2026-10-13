package studio.miroir.tether.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.miroir.tether.image.CropRatio
import studio.miroir.tether.image.NeutralSample
import studio.miroir.tether.tether.AfMark
import studio.miroir.tether.tether.AfState
import studio.miroir.tether.tether.Analysis
import studio.miroir.tether.tether.Tool

/** Image viewer with pinch-zoom, clipping overlay, focus loupe and grey-card sampling. */
@Composable
fun Viewer(
    analysis: Analysis,
    background: Color,
    showClipping: Boolean,
    showGrid: Boolean,
    tool: Tool,
    loupe: Bitmap?,
    neutral: NeutralSample?,
    onTap: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    crop: CropRatio = CropRatio.Off,
    /** Crop frame centre as fractions of the image. */
    cropCenter: Offset = Offset(0.5f, 0.5f),
    onCropCenter: (Offset) -> Unit = {},
) {
    val image = remember(analysis) { analysis.display.asImageBitmap() }
    val cropNow by rememberUpdatedState(crop)
    val centerNow by rememberUpdatedState(cropCenter)
    val onCenter by rememberUpdatedState(onCropCenter)
    val mask = remember(analysis) { analysis.clipMask.asImageBitmap() }
    var scale by remember(analysis.file) { mutableFloatStateOf(1f) }
    var offset by remember(analysis.file) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var marker by remember(analysis, tool) { mutableStateOf<Offset?>(null) }

    fun fitRect(): Rect {
        val bw = box.width.toFloat()
        val bh = box.height.toFloat()
        if (bw == 0f || bh == 0f) return Rect.Zero
        val s = minOf(bw / image.width, bh / image.height)
        val w = image.width * s
        val h = image.height * s
        return Rect(Offset((bw - w) / 2, (bh - h) / 2), Size(w, h))
    }

    fun shownRect(): Rect {
        val fit = fitRect()
        val c = Offset(box.width / 2f, box.height / 2f) + offset
        val w = fit.width * scale
        val h = fit.height * scale
        return Rect(Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
    }

    fun clamp(o: Offset, s: Float): Offset {
        val fit = fitRect()
        val mx = ((fit.width * s - box.width) / 2).coerceAtLeast(0f)
        val my = ((fit.height * s - box.height) / 2).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    fun zoomAround(p: Offset, newScale: Float) {
        val center = Offset(box.width / 2f, box.height / 2f)
        val rel = p - center - offset
        val ns = newScale.coerceIn(1f, 10f)
        offset = clamp(offset - rel * (ns / scale - 1f), ns)
        scale = ns
    }

    Box(
        modifier
            .background(background)
            .onSizeChanged { box = it }
            .pointerInput(analysis) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (cropNow != CropRatio.Off && scale <= 1.01f && zoom == 1f) {
                        // With a crop on and the image not zoomed, dragging moves the crop frame.
                        val r = shownRect()
                        if (r.width > 0f) onCenter(moveCrop(cropNow, image.width, image.height, centerNow, pan.x / r.width, pan.y / r.height))
                        return@detectTransformGestures
                    }
                    if (zoom != 1f) zoomAround(centroid, scale * zoom)
                    offset = clamp(offset + pan, scale)
                }
            }
            .pointerInput(analysis, tool) {
                detectTapGestures(
                    onDoubleTap = { p ->
                        val c = cropRect(shownRect(), cropNow, centerNow, image.width, image.height)
                        when {
                            scale <= 1.01f && c != null && c.contains(p) -> onCenter(Offset(0.5f, 0.5f))
                            scale > 1.01f -> { scale = 1f; offset = Offset.Zero }
                            else -> zoomAround(p, 3f)
                        }
                    },
                    onTap = { p ->
                        val r = shownRect()
                        val u = (p.x - r.left) / r.width
                        val v = (p.y - r.top) / r.height
                        if (u in 0f..1f && v in 0f..1f) {
                            if (tool != Tool.None) marker = Offset(u, v)
                            onTap(u, v)
                        }
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = shownRect()
            if (r.width <= 0f) return@Canvas
            val dstOffset = IntOffset(r.left.toInt(), r.top.toInt())
            val dstSize = IntSize(r.width.toInt(), r.height.toInt())
            drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), dstOffset, dstSize, filterQuality = FilterQuality.High)
            if (showClipping) {
                drawImage(mask, IntOffset.Zero, IntSize(mask.width, mask.height), dstOffset, dstSize, alpha = 0.9f, filterQuality = FilterQuality.None)
            }
            val cr = cropRect(r, crop, cropCenter, image.width, image.height)
            cr?.let { drawCropMask(r, it) }
            if (showGrid) drawThirds(cr ?: r)
            marker?.let { m ->
                val p = Offset(r.left + m.x * r.width, r.top + m.y * r.height)
                drawCircle(Color.Black, 15.dp.toPx(), p, style = Stroke(4.dp.toPx()))
                drawCircle(C.Accent, 15.dp.toPx(), p, style = Stroke(2.dp.toPx()))
            }
        }

        if (scale > 1.01f) {
            Pill("${(scale * 100 / (analysis.fullWidth / fitRect().width.coerceAtLeast(1f))).toInt()}%", Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }

        if (tool == Tool.Loupe) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(12.dp).size(300.dp)
                    .background(Color.Black, RoundedCornerShape(10.dp))
                    .border(1.dp, C.Accent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (loupe != null) {
                    Image(
                        loupe.asImageBitmap(), contentDescription = "Focus check at 100%",
                        modifier = Modifier.fillMaxSize().padding(1.dp), contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.None,
                    )
                    Pill("100%", Modifier.align(Alignment.BottomStart).padding(8.dp))
                } else {
                    Text("Tap the image to check focus at 100%", color = C.Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
                }
            }
        }

        if (tool == Tool.WhiteBalance) {
            Column(
                Modifier.align(Alignment.TopStart).padding(start = 76.dp, top = 12.dp)
                    .background(Color(0xE6000000), RoundedCornerShape(10.dp)).padding(12.dp),
            ) {
                if (neutral == null) {
                    Text("Tap a grey card or white plate", color = C.Cream, fontSize = 13.sp)
                } else {
                    Box(Modifier.size(28.dp).background(Color(neutral.r, neutral.g, neutral.b), RoundedCornerShape(6.dp)))
                    Text(
                        neutral.describe(), color = if (neutral.isNeutral) C.Ok else C.Warn,
                        fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp),
                    )
                    Text("R ${neutral.r}  G ${neutral.g}  B ${neutral.b}   ${neutral.brightnessPct}%", color = C.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = C.Cream) {
    Text(
        text, color = color, fontSize = 12.sp,
        modifier = modifier.background(Color(0xCC000000), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Live view from the camera: tap to focus there; with a crop on, drag to move the frame. */
@Composable
fun LiveViewPane(
    frame: Bitmap?,
    background: Color,
    showGrid: Boolean,
    modifier: Modifier = Modifier,
    crop: CropRatio = CropRatio.Off,
    cropCenter: Offset = Offset(0.5f, 0.5f),
    onCropCenter: (Offset) -> Unit = {},
    afMark: AfMark? = null,
    onFocusAt: ((Float, Float) -> Unit)? = null,
    /** Reference shot drawn over the live image at [overlayAlpha]. */
    overlay: Bitmap? = null,
    overlayAlpha: Float = 0.4f,
) {
    val overlayImg = remember(overlay) { overlay?.asImageBitmap() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val img = remember(frame) { frame?.asImageBitmap() }
    val imgNow by rememberUpdatedState(img)
    val cropNow by rememberUpdatedState(crop)
    val centerNow by rememberUpdatedState(cropCenter)
    val onCenter by rememberUpdatedState(onCropCenter)
    val onFocus by rememberUpdatedState(onFocusAt)

    fun fitted(): Rect {
        val i = imgNow ?: return Rect.Zero
        if (box.width == 0 || box.height == 0) return Rect.Zero
        val sc = minOf(box.width.toFloat() / i.width, box.height.toFloat() / i.height)
        val w = i.width * sc
        val h = i.height * sc
        return Rect(Offset((box.width - w) / 2, (box.height - h) / 2), Size(w, h))
    }

    Box(
        modifier.background(background).onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { p ->
                        val r = fitted()
                        if (r.width > 0f && r.contains(p)) onFocus?.invoke((p.x - r.left) / r.width, (p.y - r.top) / r.height)
                    },
                    onDoubleTap = { p ->
                        val i = imgNow ?: return@detectTapGestures
                        val c = cropRect(fitted(), cropNow, centerNow, i.width, i.height)
                        if (c != null && c.contains(p)) onCenter(Offset(0.5f, 0.5f))
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    val i = imgNow ?: return@detectDragGestures
                    val r = fitted()
                    if (cropNow == CropRatio.Off || r.width <= 0f) return@detectDragGestures
                    change.consume()
                    onCenter(moveCrop(cropNow, i.width, i.height, centerNow, drag.x / r.width, drag.y / r.height))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (img == null) {
            Text("Starting live view…", color = C.Muted, fontSize = 15.sp)
        } else {
            Image(img, "Live view", Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.Low)
            overlayImg?.let {
                Image(
                    it, "Reference overlay", Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    alpha = overlayAlpha, filterQuality = FilterQuality.Low,
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                val sc = minOf(size.width / img.width, size.height / img.height)
                val w = img.width * sc
                val h = img.height * sc
                val r = Rect(Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h))
                val cr = cropRect(r, crop, cropCenter, img.width, img.height)
                cr?.let { drawCropMask(r, it) }
                if (showGrid) drawThirds(cr ?: r)
                afMark?.let { drawAfMark(r, it) }
            }
        }
    }
}

/** Focus box: white while focusing, green when the camera confirms, red when it couldn't. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAfMark(r: Rect, m: AfMark) {
    val color = when (m.state) {
        AfState.Busy -> Color.White
        AfState.Locked -> C.Ok
        AfState.Done -> C.Cream
        AfState.Failed -> C.Bad
    }
    val c = Offset(r.left + m.u * r.width, r.top + m.v * r.height)
    val half = maxOf(18.dp.toPx(), r.width * 0.035f)
    val arm = half * 0.45f
    for ((stroke, col) in listOf(4.dp.toPx() to Color(0x8C000000), 2.dp.toPx() to color)) {
        for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
            val corner = Offset(c.x + sx * half, c.y + sy * half)
            drawLine(col, corner, Offset(corner.x, corner.y - sy * arm), stroke)
            drawLine(col, corner, Offset(corner.x - sx * arm, corner.y), stroke)
        }
    }
    drawCircle(color, 2.dp.toPx(), c)
}

/** New crop centre after dragging by (du, dv) fractions, kept so the frame stays inside the image. */
internal fun moveCrop(crop: CropRatio, w: Int, h: Int, center: Offset, du: Float, dv: Float): Offset {
    val f = crop.frame(w.toFloat(), h.toFloat(), center.x + du, center.y + dv) ?: return center
    return Offset((f[0] + f[2]) / 2, (f[1] + f[3]) / 2)
}

/** The [crop] frame inside the image ([w] × [h] px) drawn at [r], or null when cropping is off. */
internal fun cropRect(r: Rect, crop: CropRatio, center: Offset, w: Int, h: Int): Rect? =
    crop.frame(w.toFloat(), h.toFloat(), center.x, center.y)?.let { f ->
        Rect(r.left + f[0] * r.width, r.top + f[1] * r.height, r.left + f[2] * r.width, r.top + f[3] * r.height)
    }

/** Darkens what the crop cuts away from [r] and outlines the kept frame [c]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropMask(r: Rect, c: Rect) {
    val shade = Color(0xB3000000)
    drawRect(shade, r.topLeft, Size(r.width, c.top - r.top))
    drawRect(shade, Offset(r.left, c.bottom), Size(r.width, r.bottom - c.bottom))
    drawRect(shade, Offset(r.left, c.top), Size(c.left - r.left, c.height))
    drawRect(shade, Offset(c.right, c.top), Size(r.right - c.right, c.height))
    drawRect(C.Accent.copy(alpha = 0.8f), c.topLeft, c.size, style = Stroke(1.dp.toPx()))
}

/** Rule-of-thirds lines over [r]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawThirds(r: Rect) {
    val line = Color(0x80FFFFFF)
    for (i in 1..2) {
        val x = r.left + r.width * i / 3
        val y = r.top + r.height * i / 3
        drawLine(line, Offset(x, r.top), Offset(x, r.bottom), 1.dp.toPx())
        drawLine(line, Offset(r.left, y), Offset(r.right, y), 1.dp.toPx())
    }
}
