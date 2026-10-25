package ir.mohitban.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Paint as APaint
import android.graphics.Path as APath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ir.mohitban.app.ui.theme.MColors
import kotlin.math.max
import kotlin.math.min

/** Holds what the user drew. An already saved signature can be shown via [existing]. */
@Stable
class SignatureState {
    val strokes = mutableStateListOf<List<Offset>>()
    private val current = mutableStateListOf<Offset>()
    var existing by mutableStateOf<ImageBitmap?>(null)
    var size by mutableStateOf(IntSize.Zero)
        internal set
    /** Increases on every change so Compose redraws while drawing. */
    var version by mutableStateOf(0)
        private set

    val hasNewDrawing: Boolean get() = strokes.isNotEmpty() || current.isNotEmpty()
    val isEmpty: Boolean get() = !hasNewDrawing && existing == null

    internal fun start(p: Offset) { current.clear(); current.add(p); version++ }
    internal fun move(p: Offset) { current.add(p); version++ }
    internal fun end() {
        if (current.isNotEmpty()) strokes.add(current.toList())
        current.clear(); version++
    }
    internal fun liveStroke(): List<Offset> = current

    fun clear() { strokes.clear(); current.clear(); existing = null; version++ }

    /**
     * Renders the strokes into a transparent bitmap, cropped to the drawing with a margin,
     * so it can be placed on the official form. Returns null when nothing was drawn.
     */
    fun toBitmap(strokeWidthPx: Float = 6f): Bitmap? {
        val all = strokes.flatten()
        if (all.isEmpty()) return null
        val pad = 16f
        val minX = all.minOf { it.x } - pad
        val minY = all.minOf { it.y } - pad
        val maxX = all.maxOf { it.x } + pad
        val maxY = all.maxOf { it.y } + pad
        val w = max(40, (maxX - minX).toInt())
        val h = max(30, (maxY - minY).toInt())
        val scale = min(2f, 900f / w)
        val bmp = Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = ACanvas(bmp)
        val paint = APaint(APaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(16, 32, 64)
            style = APaint.Style.STROKE
            strokeWidth = strokeWidthPx * scale
            strokeCap = APaint.Cap.ROUND
            strokeJoin = APaint.Join.ROUND
        }
        for (s in strokes) {
            val path = APath()
            s.forEachIndexed { i, p ->
                val x = (p.x - minX) * scale
                val y = (p.y - minY) * scale
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (s.size == 1) path.lineTo((s[0].x - minX) * scale + 0.5f, (s[0].y - minY) * scale + 0.5f)
            c.drawPath(path, paint)
        }
        return bmp
    }
}

@Composable
fun rememberSignatureState(): SignatureState = remember { SignatureState() }

@Composable
fun SignaturePad(
    state: SignatureState,
    title: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    error: String? = null,
    hint: String = "اینجا امضا کنید",
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (error != null) MColors.Error else Color(0xFFC9D1CB)
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(shape)
                .background(Color(0xFFFBFCFB))
                .drawBehind {
                    drawRoundRect(
                        color = borderColor,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                    )
                    val y = size.height - 24.dp.toPx()
                    drawLine(
                        Color(0xFFD9DFDB), Offset(20.dp.toPx(), y), Offset(size.width - 20.dp.toPx(), y),
                        strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                    )
                }
                .onSizeChanged { state.size = it }
                .pointerInput(state) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        state.start(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.positionChange() != Offset.Zero) {
                                state.move(change.position)
                            }
                            change.consume()
                        }
                        state.end()
                    }
                },
        ) {
            val existing = state.existing
            if (existing != null && !state.hasNewDrawing) {
                Image(
                    existing, null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
                )
            } else if (state.isEmpty) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MColors.Hint, modifier = Modifier.align(Alignment.Center))
            }
            Canvas(Modifier.fillMaxSize()) {
                if (state.version < 0) return@Canvas
                val stroke = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                fun draw(points: List<Offset>) {
                    if (points.isEmpty()) return
                    val p = Path()
                    p.moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) p.lineTo(points[i].x, points[i].y)
                    if (points.size == 1) p.lineTo(points[0].x + 0.5f, points[0].y + 0.5f)
                    drawPath(p, Color(0xFF102040), style = stroke)
                }
                state.strokes.forEach { draw(it) }
                draw(state.liveStroke())
            }
            Row(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 6.dp)) {
                Text(title, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MColors.Muted)
                if (required) Text(" *", style = MaterialTheme.typography.labelSmall, color = MColors.Error)
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { state.clear() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.DeleteSweep, "پاک کردن امضا", tint = Color(0xFF9AA29C), modifier = Modifier.size(20.dp)) }
        }
        FieldMessage(error = error, success = null)
    }
}
