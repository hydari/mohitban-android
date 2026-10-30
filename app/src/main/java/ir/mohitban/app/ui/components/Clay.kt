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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
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

/**
 * Raised clay surface. Put clickable/padding after it.
 * Light from above: a soft top band and rim, a soft shadow below. On saturated colours (green
 * buttons, red warnings) the light is kept faint so no white patch or halo appears.
 */
fun Modifier.clay(
    radius: Dp = Corners.R,
    color: Color = Clay.Surface,
    depth: Dp = 9.dp,
): Modifier {
    val light = color.luminance() > 0.5f
    val sheen = if (light) 0.55f else 0.16f
    return this
        .drawBehind {
            // faint white glow above light surfaces only (Android 9+; older phones simply skip it)
            if (light && Build.VERSION.SDK_INT >= 28) {
                val r = radius.toPx()
                drawIntoCanvas { c ->
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color.toArgb()
                        setShadowLayer(depth.toPx() * 0.9f, 0f, -depth.toPx() * 0.3f, Color.White.copy(alpha = 0.75f).toArgb())
                    }
                    c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
                }
            }
        }
        .shadow(depth, RoundedCornerShape(radius), ambientColor = Clay.Shadow, spotColor = Clay.Shadow)
        .clip(RoundedCornerShape(radius))
        .drawBehind {
            val r = radius.toPx()
            val h = size.height
            // even top-to-bottom volume, the same on narrow chips and wide buttons
            drawRect(Brush.verticalGradient(listOf(lerpColor(color, Color.White, if (light) 0.35f else 0.12f), color, lerpColor(color, Color.Black, if (light) 0.04f else 0.10f))))
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = sheen), 0.45f to Color.Transparent, endY = h))
            val s = 1.5.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = if (light) 0.9f else 0.35f),
                    0.5f to Color.White.copy(alpha = 0f),
                    1f to Color(0xFF1F3B28).copy(alpha = if (light) 0.07f else 0.15f),
                    endY = h,
                ),
                topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, h - s),
                cornerRadius = CornerRadius(r - s / 2), style = Stroke(s),
            )
        }
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
