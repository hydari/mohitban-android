package ir.mohitban.app.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.mohitban.app.ui.theme.Corners

/**
 * Formal claymorphism: soft, slightly puffy surfaces in the organisation's greens.
 * A raised clay surface = warm light from the top-left (white glow outside, highlight inside)
 * and a soft green-grey shadow to the bottom-right; inset clay (fields) is the reverse.
 */
object Clay {
    val Bg = Color(0xFFE8EEE9)
    val Surface = Color(0xFFF5F8F5)
    val Inset = Color(0xFFEAF0EB)
    val Shadow = Color(0xFF6F8A76)
}

/** Raised clay surface. Put clickable/padding after it. */
fun Modifier.clay(
    radius: Dp = Corners.R,
    color: Color = Clay.Surface,
    depth: Dp = 9.dp,
): Modifier = this
    .drawBehind {
        // white glow up-left (Android 9+ draws shadow layers for shapes; older phones simply skip it)
        if (Build.VERSION.SDK_INT >= 28) {
            val r = radius.toPx()
            drawIntoCanvas { c ->
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color.toArgb()
                    setShadowLayer(depth.toPx() * 1.1f, -depth.toPx() * 0.45f, -depth.toPx() * 0.45f, Color.White.copy(alpha = 0.95f).toArgb())
                }
                c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
            }
        }
    }
    .shadow(depth, RoundedCornerShape(radius), ambientColor = Clay.Shadow, spotColor = Clay.Shadow)
    .clip(RoundedCornerShape(radius))
    .drawBehind {
        val r = radius.toPx()
        drawRect(Brush.linearGradient(listOf(lerpColor(color, Color.White, 0.55f), color, lerpColor(color, Color.Black, 0.035f)), start = Offset.Zero, end = Offset(size.width, size.height)))
        // inner highlight top-left and inner shade bottom-right: the "puffy" clay volume
        drawRect(Brush.linearGradient(0f to Color.White.copy(alpha = 0.7f), 0.38f to Color.Transparent, start = Offset.Zero, end = Offset(size.width * 0.5f, size.height * 0.9f)))
        drawRect(Brush.linearGradient(0.6f to Color.Transparent, 1f to Color(0xFF1F3B28).copy(alpha = 0.07f), start = Offset.Zero, end = Offset(size.width, size.height)))
        val s = 2.5.dp.toPx()
        drawRoundRect(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0f), Color(0xFF1F3B28).copy(alpha = 0.06f)), start = Offset.Zero, end = Offset(size.width, size.height)),
            topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s),
            cornerRadius = CornerRadius(r - s / 2), style = Stroke(s),
        )
    }

/** Pressed-in clay well, for text fields and selected states. */
fun Modifier.clayInset(radius: Dp = Corners.R, color: Color = Clay.Inset): Modifier = this
    .clip(RoundedCornerShape(radius))
    .drawBehind {
        drawRect(color)
        val w = size.width; val h = size.height
        val d = 7.dp.toPx()
        // shadow falls in from the top-left edges, light from the bottom-right
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1F3B28).copy(alpha = 0.10f), Color.Transparent), startY = 0f, endY = d))
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF1F3B28).copy(alpha = 0.07f), Color.Transparent), startX = 0f, endX = d))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.9f)), startY = h - d, endY = h), topLeft = Offset(0f, h - d), size = Size(w, d))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.7f)), startX = w - d, endX = w), topLeft = Offset(w - d, 0f), size = Size(d, h))
    }

/** Soft clay press: the element sinks a little while touched. Pass the same source to clickable. */
fun Modifier.clayPress(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(if (pressed) 90 else 200), label = "clayPress")
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Convenience: a remembered interaction source for [clayPress] + clickable. */
@androidx.compose.runtime.Composable
fun rememberPress(): MutableInteractionSource = remember { MutableInteractionSource() }
