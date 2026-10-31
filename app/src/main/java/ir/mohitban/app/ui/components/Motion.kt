package ir.mohitban.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import ir.mohitban.app.gasht.SunTimes
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/* =====================================================================================
 * Motion toolkit: short, interruptible movements that explain what happened. Everything here
 * stays still when the phone's "remove animations" setting is on.
 * ===================================================================================== */

/** False when the user turned animations off in Android's settings (animator scale 0). */
@Composable
fun rememberMotionOn(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)
    }
}

/** Remembers which entrance groups and counters already played in this app session. */
private object MotionMemory {
    val played = mutableSetOf<String>()
    val counters = mutableMapOf<String, Long>()
}

/**
 * Entrance for a list/grid item: rises a little and fades in, [index] × 45 ms after the first.
 * A [group] plays once per app session, so coming back to a page does not replay it.
 */
fun Modifier.staggerIn(index: Int, group: String): Modifier = composed {
    val motion = rememberMotionOn()
    val play = remember { motion && group !in MotionMemory.played }
    val a = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!play) return@LaunchedEffect
        MotionMemory.played += group
        delay(45L * index.coerceAtMost(9))
        a.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
    }
    graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 26.dp.toPx()
    }
}

/** A number that counts up to [target] (from the last value shown under [key]). */
@Composable
fun rememberCountUp(target: Long, key: String): Long {
    val motion = rememberMotionOn()
    val from = remember { if (motion) MotionMemory.counters[key] ?: 0L else target }
    val a = remember { Animatable(from.toFloat()) }
    LaunchedEffect(target) {
        if (motion && a.value != target.toFloat()) a.animateTo(target.toFloat(), tween(950, easing = FastOutSlowInEasing))
        else a.snapTo(target.toFloat())
        MotionMemory.counters[key] = target
    }
    return if (a.isRunning) a.value.toLong() else target
}

/** Turns an angle smoothly along the short way round (no jump from 359° to 0°). */
@Composable
fun rememberSmoothAngle(target: Float): Float {
    val a = remember { Animatable(target) }
    LaunchedEffect(target) {
        val cur = a.value
        val d = ((target - cur) % 360f + 540f) % 360f - 180f
        a.animateTo(cur + d, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow))
    }
    return a.value
}

/** A status dot that sends out a soft ring every 1.6 s while [active]. */
@Composable
fun PulseDot(color: Color, modifier: Modifier = Modifier, size: Dp = 12.dp, active: Boolean = true) {
    val motion = rememberMotionOn() && active
    val t = rememberInfiniteTransition(label = "pulse")
    val p = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "p")
    Box(modifier.size(size * 2.4f), contentAlignment = Alignment.Center) {
        if (motion) {
            Canvas(Modifier.fillMaxSize()) {
                val v = p.value
                val r0 = size.toPx() / 2
                drawCircle(color.copy(alpha = 0.45f * (1f - v)), radius = r0 + (this.size.minDimension / 2 - r0) * v)
            }
        }
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

/** Light or dark status-bar icons while this screen is shown (restored when it leaves). */
@Composable
fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(darkIcons) {
        val window = (view.context.findActivityCompat())?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = darkIcons
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }
}

private tailrec fun Context.findActivityCompat(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivityCompat()
    else -> null
}

/* ------------------------------ living sky ------------------------------ */

/** Where the sun is computed for: the phone's last known position, else [fallback], else central Iran. */
private fun skyLocation(context: Context, fallback: Pair<Double, Double>?): Pair<Double, Double> {
    val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (ok) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val best = runCatching {
            lm?.getProviders(true)?.mapNotNull { lm.getLastKnownLocation(it) }?.maxByOrNull { it.time }
        }.getOrNull()
        if (best != null) return best.latitude to best.longitude
    }
    return fallback ?: (35.7 to 51.4)
}

/**
 * A photographic colour grade for one moment of the day: per-channel gain and lift, plus how
 * much night (stars, darkness) there is.
 */
private class Grade(val r: Float, val g: Float, val b: Float, val sat: Float, val lift: Float, val night: Float) {
    fun mix(o: Grade, f: Float) = Grade(
        r + (o.r - r) * f, g + (o.g - g) * f, b + (o.b - b) * f, sat + (o.sat - sat) * f, lift + (o.lift - lift) * f, night + (o.night - night) * f,
    )

    /** Saturation, then channel gains, then lift — one colour matrix for the photo. */
    fun matrix(): ColorMatrix {
        val lr = 0.2126f; val lg = 0.7152f; val lb = 0.0722f
        val sr = (1 - sat) * lr; val sg = (1 - sat) * lg; val sb = (1 - sat) * lb
        val l = lift * 255f
        return ColorMatrix(
            floatArrayOf(
                (sr + sat) * r, sg * r, sb * r, 0f, l,
                sr * g, (sg + sat) * g, sb * g, 0f, l,
                sr * b, sg * b, (sb + sat) * b, 0f, l * 1.4f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}

// the photo itself is a golden-hour scene; the other moments are graded from it
private val NIGHT = Grade(0.30f, 0.40f, 0.68f, 0.25f, 0.0f, 1f)
private val BLUE_HOUR = Grade(0.62f, 0.66f, 0.90f, 0.55f, 0.01f, 0.45f)
private val GOLDEN = Grade(1.0f, 1.0f, 1.0f, 1.0f, 0f, 0f)
private val DAY = Grade(0.94f, 1.02f, 1.12f, 0.88f, 0.02f, 0f)
private val DUSK = Grade(0.95f, 0.70f, 0.78f, 0.9f, 0f, 0.25f)

private fun gradeAt(minute: Float, sunrise: Float, sunset: Float): Grade {
    val keys = listOf(
        sunrise - 90f to NIGHT, sunrise - 30f to BLUE_HOUR, sunrise + 15f to GOLDEN, sunrise + 90f to DAY,
        sunset - 90f to DAY, sunset - 10f to GOLDEN, sunset + 20f to DUSK, sunset + 50f to BLUE_HOUR, sunset + 100f to NIGHT,
    )
    if (minute <= keys.first().first || minute >= keys.last().first) return NIGHT
    val i = keys.indexOfLast { it.first <= minute }
    val (m0, k0) = keys[i]; val (m1, k1) = keys[i + 1]
    return k0.mix(k1, ((minute - m0) / (m1 - m0)).coerceIn(0f, 1f))
}

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float)

/**
 * The dashboard's header: a real mountain photograph whose light follows the actual time of day
 * (cool daylight, golden sunrise and sunset, rosy dusk, blue night with faint stars), computed
 * from today's sunrise and sunset. It breathes with a very slow zoom and melts into the page.
 * [parallax] = scroll offset in pixels; [fallback] = place used when the phone has no position.
 */
@Composable
fun SkyHeader(
    modifier: Modifier = Modifier,
    parallax: () -> Int = { 0 },
    fallback: Pair<Double, Double>? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val motion = rememberMotionOn()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val place = remember(fallback) { skyLocation(context, fallback) }
    val grade = remember(now / 60_000, place) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val offset = TimeZone.getDefault().getOffset(now) / 60_000
        val sun = SunTimes.compute(cal.get(Calendar.DAY_OF_YEAR), place.first, place.second, offset)
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60f + cal.get(Calendar.MINUTE)
        gradeAt(minute, (sun.sunrise ?: 360).toFloat(), (sun.sunset ?: 1080).toFloat())
    }
    // white status-bar icons over the photo (a soft shade at the top keeps them readable)
    StatusBarIcons(darkIcons = false)
    val filter = remember(grade) { ColorFilter.colorMatrix(grade.matrix()) }
    val stars = remember { val r = Random(7); List(60) { Star(r.nextFloat(), r.nextFloat() * 0.34f, 0.4f + r.nextFloat() * 0.8f, r.nextFloat()) } }
    val t = rememberInfiniteTransition(label = "sky")
    val twinkle = t.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "tw")
    val breathe = t.animateFloat(0f, 1f, infiniteRepeatable(tween(28_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "kb")
    val appear = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }

    Box(modifier.clipToBounds()) {
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(ir.mohitban.app.R.drawable.sky_header), null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alignment = Alignment.TopCenter,
            colorFilter = filter,
            modifier = Modifier.matchParentSize().graphicsLayer {
                translationY = parallax() * 0.35f
                val z = 1.04f + (if (motion) 0.05f * breathe.value else 0f)
                scaleX = z; scaleY = z
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.62f, 0.3f)
                alpha = appear.value
            },
        )
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            // faint stars only high in a dark sky
            if (grade.night > 0.3f) {
                val k = ((grade.night - 0.3f) / 0.7f) * appear.value
                for (s in stars) {
                    val tw = if (motion) 0.6f + 0.4f * sin(((twinkle.value + s.phase) * 2 * PI).toFloat()) else 0.8f
                    drawCircle(Color.White.copy(alpha = (0.75f * k * tw).coerceIn(0f, 1f)), s.r.dp.toPx(), Offset(s.x * w, s.y * h))
                }
            }
            // a soft shade under the status bar and the fade into the page
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent), endY = h * 0.25f))
            drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 0.86f to Clay.Bg.copy(alpha = 0.85f), 1f to Clay.Bg, startY = 0f, endY = h))
        }
        content()
    }
}
