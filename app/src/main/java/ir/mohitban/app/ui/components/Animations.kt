package ir.mohitban.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.mohitban.app.ui.theme.MColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Green circle that pops in with a spring, then the check mark draws itself, with a soft ring pulse. */
@Composable
fun AnimatedCheck(size: Dp = 84.dp, color: Color = MColors.Green2) {
    val circle = remember { Animatable(0f) }
    val tick = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { circle.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { delay(120); ring.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        delay(220)
        tick.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Box(Modifier.size(size * 1.35f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * 1.35f)) {
            val r = this.size.minDimension / 2
            drawCircle(color.copy(alpha = 0.25f * (1f - ring.value)), radius = r * (0.7f + 0.3f * ring.value))
        }
        Canvas(Modifier.size(size).scale(circle.value)) {
            drawCircle(color)
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * 0.28f, h * 0.53f)
                lineTo(w * 0.44f, h * 0.68f)
                lineTo(w * 0.73f, h * 0.37f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * tick.value, partial, true)
            drawPath(partial, Color.White, style = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Short animated confirmation that closes itself (used after every successful save). */
@Composable
fun SuccessDialog(message: String, onFinished: () -> Unit, durationMs: Long = 1400) {
    val finish = rememberUpdatedState(onFinished)
    val appear = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) {
        launch { appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        delay(durationMs)
        finish.value()
    }
    Dialog(onDismissRequest = { finish.value() }) {
        Column(
            Modifier
                .graphicsLayer { scaleX = appear.value; scaleY = appear.value }
                .clip(RoundedCornerShape(26.dp))
                .background(Color.White)
                .widthIn(min = 220.dp)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedCheck()
            Text(
                message, style = MaterialTheme.typography.titleMedium, color = MColors.Ink, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** Filled green button with white text, for dialog confirmations. */
@Composable
fun GreenButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MColors.Green,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFA9C2B1),
            disabledContentColor = Color.White,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp)) }
}

