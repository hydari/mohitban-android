package ir.mohitban.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mohitban.app.R
import ir.mohitban.app.core.text.fa

/**
 * The Department of Environment emblem in green on a white disc, with a light sweep that
 * crosses the emblem forever (glossy effect).
 */
@Composable
fun ShimmerLogo(size: Dp = 60.dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "logo")
    val sweep by t.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2600, delayMillis = 900, easing = LinearEasing)),
        label = "sweep",
    )
    GlassRing(size, modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, Color(0xFFF3F7F4)))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painterResource(R.drawable.doe_emblem), "سازمان حفاظت محیط زیست",
                modifier = Modifier
                    .padding(size * 0.07f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val w = this.size.width
                        val x = sweep * w
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.Transparent,
                                0.45f to Color.White.copy(alpha = 0.0f),
                                0.5f to Color.White.copy(alpha = 0.85f),
                                0.55f to Color.White.copy(alpha = 0.0f),
                                1f to Color.Transparent,
                                start = Offset(x - w * 0.6f, 0f),
                                end = Offset(x + w * 0.6f, this.size.height),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    },
            )
        }
    }
}

/**
 * Thin liquid-glass ring: translucent frosted rim with a specular highlight on top and a soft
 * shadow; [content] fills the inner disc.
 */
@Composable
fun GlassRing(size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(size)
            .shadow(14.dp, CircleShape, ambientColor = Color.Black, spotColor = Color(0x80000000))
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0x8CFFFFFF), Color(0x29FFFFFF), Color(0x52FFFFFF)), start = Offset.Zero, end = Offset.Infinite))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0xF2FFFFFF), Color(0x33FFFFFF))), CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
        // specular highlight across the upper part of the ring
        Canvas(Modifier.fillMaxSize()) {
            drawArc(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.Transparent), endY = this.size.height * 0.5f),
                startAngle = 200f, sweepAngle = 140f, useCenter = false,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/**
 * A skeuomorphic brass bell on a raised round button. When [ringing] is true it swings
 * left and right every few seconds; [count] shows a red badge.
 */
@Composable
fun SkeuoBell(ringing: Boolean, count: Int, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val t = rememberInfiniteTransition(label = "bell")
    val swing by t.animateFloat(
        initialValue = 0f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 2600
                0f at 0
                -22f at 120
                18f at 260
                -14f at 400
                10f at 540
                -6f at 660
                3f at 760
                0f at 860
                0f at 2600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "swing",
    )
    Box(modifier.size(size)) {
        GlassRing(size, onClick = onClick) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE6ECE7), Color(0xFFCAD3CC)), start = Offset.Zero, end = Offset.Infinite)),
                contentAlignment = Alignment.Center,
            ) {
            Canvas(
                Modifier
                    .size(size * 0.66f)
                    .graphicsLayer {
                        rotationZ = if (ringing) swing else 0f
                        transformOrigin = TransformOrigin(0.5f, 0.08f)
                    },
            ) {
                val w = this.size.width
                val h = this.size.height
                // clapper
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A6), Color(0xFF9A7414)), center = Offset(w * 0.47f, h * 0.86f), radius = w * 0.12f), radius = w * 0.1f, center = Offset(w * 0.5f, h * 0.87f))
                // bell body
                val body = Path().apply {
                    moveTo(w * 0.5f, h * 0.1f)
                    cubicTo(w * 0.22f, h * 0.12f, w * 0.22f, h * 0.42f, w * 0.2f, h * 0.6f)
                    cubicTo(w * 0.18f, h * 0.7f, w * 0.06f, h * 0.74f, w * 0.06f, h * 0.8f)
                    lineTo(w * 0.94f, h * 0.8f)
                    cubicTo(w * 0.94f, h * 0.74f, w * 0.82f, h * 0.7f, w * 0.8f, h * 0.6f)
                    cubicTo(w * 0.78f, h * 0.42f, w * 0.78f, h * 0.12f, w * 0.5f, h * 0.1f)
                    close()
                }
                drawPath(
                    body,
                    Brush.linearGradient(
                        listOf(Color(0xFFFFF0B3), Color(0xFFF2C94C), Color(0xFFC99A1E), Color(0xFF7E5E10)),
                        start = Offset(0f, 0f), end = Offset(w, h),
                    ),
                )
                drawPath(body, Color(0xFF6B4F0C), style = Stroke(width = w * 0.025f))
                // rim
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFFF6D365), Color(0xFF8C6A12)), startY = h * 0.76f, endY = h * 0.86f),
                    topLeft = Offset(w * 0.04f, h * 0.76f), size = Size(w * 0.92f, h * 0.08f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f),
                )
                // knob
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3C4), Color(0xFFB8890F))), radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.08f))
                // specular highlight
                val shine = Path().apply {
                    moveTo(w * 0.36f, h * 0.2f)
                    cubicTo(w * 0.3f, h * 0.3f, w * 0.29f, h * 0.48f, w * 0.28f, h * 0.62f)
                    lineTo(w * 0.34f, h * 0.62f)
                    cubicTo(w * 0.35f, h * 0.48f, w * 0.36f, h * 0.32f, w * 0.42f, h * 0.2f)
                    close()
                }
                drawPath(shine, Color.White.copy(alpha = 0.7f))
            }
            }
        }
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(19.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFF6B5E), Color(0xFFC62828))))
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(count.fa(), color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}
