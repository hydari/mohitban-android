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
import ir.mohitban.app.core.jalali.fmod
import ir.mohitban.app.domain.model.ShiftPattern
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

/* ------------------------------ haptics-free night vision ------------------------------ */

/**
 * Night patrol view: everything is drawn in dim red on black (dark adaptation of the eyes is
 * kept, as with a red torch). Light colours turn dark and dark text turns red.
 */
fun Modifier.nightVision(on: Boolean): Modifier = if (!on) this else this.drawWithContent {
    val k = 0.9f
    val paint = Paint().apply {
        colorFilter = ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -0.30f * k, -0.59f * k, -0.11f * k, 0f, 255f * k,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
    drawIntoCanvas { c ->
        c.saveLayer(Rect(Offset.Zero, size), paint)
        drawContent()
        c.restore()
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

/* ------------------------------ shift cycle ring ------------------------------ */

/**
 * The photo inside a ring of the whole shift cycle: one segment per day (green = shift,
 * grey = rest). Past days are solid, coming days faint, and today's segment breathes.
 */
@Composable
fun ShiftCycleRing(
    size: Dp,
    pattern: ShiftPattern?,
    todayJdn: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val motion = rememberMotionOn()
    val t = rememberInfiniteTransition(label = "breath")
    val breath = t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val grow = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(pattern?.cycle) { if (motion) { grow.snapTo(0f); grow.animateTo(1f, tween(900, delayMillis = 200, easing = FastOutSlowInEasing)) } }
    val on = Color(0xFF239B56)
    val off = Color(0xFFAEB8B2)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.2.dp.toPx()
            val r = this.size.minDimension / 2 - stroke / 2 - 1.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val box = Size(r * 2, r * 2)
            if (pattern == null) {
                drawCircle(Color.White, r, style = Stroke(stroke))
                return@Canvas
            }
            val n = pattern.cycle.coerceIn(1, 60)
            val first = todayJdn - fmod(todayJdn - pattern.startJdn, n)
            val gap = if (n > 20) 2f else 5f
            val seg = 360f / n
            val visible = 360f * grow.value
            for (i in 0 until n) {
                val day = first + i
                val start = -90f + i * seg + gap / 2
                if (i * seg > visible) break
                val sweep = (seg - gap).coerceAtMost(visible - i * seg)
                val c = if (pattern.isShift(day)) on else off
                when {
                    day < todayJdn -> drawArc(c.copy(alpha = 0.75f), start, sweep, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Butt))
                    day > todayJdn -> drawArc(c.copy(alpha = 0.28f), start, sweep, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Butt))
                    else -> {
                        val b = if (motion) breath.value else 0.5f
                        drawArc(c.copy(alpha = 0.22f + 0.25f * b), start - 1f, sweep + 2f, false, tl, box, style = Stroke(stroke * (1.6f + 0.6f * b)))
                        drawArc(c, start, sweep, false, tl, box, style = Stroke(stroke * 1.15f))
                    }
                }
            }
        }
        Box(
            Modifier
                .padding(7.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.White)
                .padding(1.5.dp),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/* ------------------------------ living sky ------------------------------ */

/** Where the sun is computed for: the phone's last known position, else central Iran. */
private fun skyLocation(context: Context): Pair<Double, Double> {
    val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (ok) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val best = runCatching {
            lm?.getProviders(true)?.mapNotNull { lm.getLastKnownLocation(it) }?.maxByOrNull { it.time }
        }.getOrNull()
        if (best != null) return best.latitude to best.longitude
    }
    return 35.7 to 51.4
}

private class SkyKey(val minute: Float, val top: Color, val bottom: Color, val stars: Float, val glow: Color)

/** Sky colours for the current time between sunrise and sunset. */
private class SkyLook(val top: Color, val bottom: Color, val stars: Float, val glow: Color, val sun: Float?, val dark: Boolean)

private fun skyLook(minute: Float, sunrise: Float, sunset: Float): SkyLook {
    val night = SkyKey(0f, Color(0xFF0D1A33), Color(0xFF2C4266), 1f, Color(0x00000000))
    val dawn = SkyKey(0f, Color(0xFF3A4A7A), Color(0xFFF0A86E), 0.25f, Color(0x66FFB36B))
    val day = SkyKey(0f, Color(0xFF4FA3DD), Color(0xFFCFE9F6), 0f, Color(0x33FFFFFF))
    val golden = SkyKey(0f, Color(0xFF5E92C8), Color(0xFFF7C77E), 0f, Color(0x66FFC46B))
    val dusk = SkyKey(0f, Color(0xFF2A2D5E), Color(0xFFE2785E), 0.35f, Color(0x55FF8A5C))
    val keys = listOf(
        sunrise - 80f to night, sunrise - 10f to dawn, sunrise + 50f to day,
        sunset - 80f to day, sunset - 8f to golden, sunset + 30f to dusk, sunset + 85f to night,
    )
    fun mix(a: SkyKey, b: SkyKey, f: Float) = SkyLook(
        lerpColor(a.top, b.top, f), lerpColor(a.bottom, b.bottom, f), a.stars + (b.stars - a.stars) * f,
        lerpColor(a.glow, b.glow, f), null, false,
    )
    val look = when {
        minute <= keys.first().first || minute >= keys.last().first -> mix(night, night, 0f)
        else -> {
            val i = keys.indexOfLast { it.first <= minute }
            val (m0, k0) = keys[i]; val (m1, k1) = keys[i + 1]
            mix(k0, k1, ((minute - m0) / (m1 - m0)).coerceIn(0f, 1f))
        }
    }
    val sun = if (minute in sunrise..sunset) (minute - sunrise) / (sunset - sunrise) else null
    return SkyLook(look.top, look.bottom, look.stars, look.glow, sun, look.top.luminanceApprox() < 0.32f)
}

private fun Color.luminanceApprox() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float)

/**
 * The dashboard's header: the real sky of this moment (dawn, day, golden hour, dusk, night with
 * stars) from today's sunrise and sunset, the sun on its arc, drifting clouds and layered
 * mountains melting into the page. [parallax] = scroll offset in pixels.
 */
@Composable
fun SkyHeader(modifier: Modifier = Modifier, parallax: () -> Int = { 0 }, content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    val motion = rememberMotionOn()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }
    val place = remember { skyLocation(context) }
    val look = remember(now / 60_000) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val offset = TimeZone.getDefault().getOffset(now) / 60_000
        val sun = SunTimes.compute(cal.get(Calendar.DAY_OF_YEAR), place.first, place.second, offset)
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60f + cal.get(Calendar.MINUTE)
        skyLook(minute, (sun.sunrise ?: 360).toFloat(), (sun.sunset ?: 1080).toFloat())
    }
    StatusBarIcons(darkIcons = !look.dark)
    val stars = remember { val r = Random(7); List(42) { Star(r.nextFloat(), r.nextFloat() * 0.62f, 0.6f + r.nextFloat() * 1.3f, r.nextFloat()) } }
    val t = rememberInfiniteTransition(label = "sky")
    val twinkle = t.animateFloat(0f, 1f, infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "tw")
    val drift = t.animateFloat(0f, 1f, infiniteRepeatable(tween(70_000, easing = LinearEasing)), label = "dr")
    val appear = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    Box(modifier.clipToBounds()) {
        Canvas(Modifier.matchParentSize().graphicsLayer { translationY = parallax() * 0.35f }) {
            val w = size.width; val h = size.height
            drawRect(Brush.verticalGradient(listOf(look.top, look.bottom), endY = h * 0.85f))
            // stars
            if (look.stars > 0.02f) {
                for (s in stars) {
                    val tw = if (motion) 0.55f + 0.45f * sin(((twinkle.value + s.phase) * 2 * PI).toFloat()) else 0.8f
                    drawCircle(Color.White.copy(alpha = (look.stars * tw * appear.value).coerceIn(0f, 1f)), s.r.dp.toPx(), Offset(s.x * w, s.y * h))
                }
                // a quiet crescent moon
                val mc = Offset(w * 0.8f, h * 0.24f); val mr = 11.dp.toPx()
                drawCircle(Color(0xFFFFF4D6).copy(alpha = look.stars * 0.25f), mr * 2.2f, mc)
                drawCircle(Color(0xFFFFF4D6).copy(alpha = look.stars), mr, mc)
                drawCircle(look.top.copy(alpha = look.stars), mr * 0.86f, Offset(mc.x + mr * 0.45f, mc.y - mr * 0.25f))
            }
            // the sun on its arc, east (left) to west (right)
            look.sun?.let { f ->
                val sx = w * (0.1f + 0.8f * f)
                val sy = h * 0.68f - sin((f * PI).toFloat()) * h * 0.5f
                val sr = 13.dp.toPx()
                drawCircle(Brush.radialGradient(listOf(Color(0x99FFE7A3), Color(0x00FFE7A3)), Offset(sx, sy), sr * 4f), sr * 4f, Offset(sx, sy))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF6D8), Color(0xFFFFCF5C)), Offset(sx, sy), sr), sr, Offset(sx, sy))
            }
            // horizon glow
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, look.glow, Color.Transparent), startY = h * 0.45f, endY = h * 0.85f))
            // drifting clouds (daylight only)
            if (look.stars < 0.5f) {
                val a = (1f - look.stars * 2f) * 0.55f * appear.value
                val d = if (motion) drift.value else 0.3f
                cloud(w * ((d * 1.4f + 0.05f) % 1.4f - 0.2f), h * 0.22f, 26.dp.toPx(), Color.White.copy(alpha = a))
                cloud(w * ((d * 1.4f + 0.75f) % 1.4f - 0.2f), h * 0.38f, 19.dp.toPx(), Color.White.copy(alpha = a * 0.8f))
            }
            // mountains: far, middle, near (the near one is the page colour)
            val nightMix = look.stars.coerceIn(0f, 1f)
            val far = lerpColor(lerpColor(look.bottom, Color(0xFF6E8F7C), 0.55f), Color(0xFF243447), nightMix * 0.7f)
            val mid = lerpColor(Color(0xFF3F7454), Color(0xFF1C2E2A), nightMix * 0.75f)
            ridge(h * 0.62f, h * 0.10f, 1.3f, 0.2f, far.copy(alpha = appear.value))
            ridge(h * 0.74f, h * 0.08f, 2.1f, 1.7f, mid.copy(alpha = appear.value))
            ridge(h * 0.88f, h * 0.05f, 3.0f, 3.1f, Clay.Bg)
            drawRect(Clay.Bg, topLeft = Offset(0f, h * 0.97f), size = Size(w, h * 0.03f + 1f))
        }
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.ridge(base: Float, amp: Float, freq: Float, phase: Float, color: Color) {
    val w = size.width; val h = size.height
    val path = Path().apply {
        moveTo(0f, h)
        var x = 0f
        while (x <= w + 8f) {
            val u = x / w * 2 * PI.toFloat()
            val y = base - amp * (0.6f * sin(u * freq + phase) + 0.3f * sin(u * freq * 2.3f + phase * 1.7f) + 0.1f * sin(u * freq * 5.1f))
            lineTo(x, y)
            x += 6f
        }
        lineTo(w, h); close()
    }
    drawPath(path, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.cloud(x: Float, y: Float, r: Float, color: Color) {
    // one path for the whole cloud, so overlapping puffs keep a single even transparency
    val p = Path().apply {
        addOval(Rect(Offset(x, y), r))
        addOval(Rect(Offset(x - r * 0.95f, y + r * 0.25f), r * 0.75f))
        addOval(Rect(Offset(x + r * 1.0f, y + r * 0.2f), r * 0.8f))
        addRoundRect(androidx.compose.ui.geometry.RoundRect(Rect(Offset(x - r * 1.6f, y + r * 0.1f), Size(r * 3.3f, r * 0.85f)), androidx.compose.ui.geometry.CornerRadius(r * 0.45f)))
    }
    drawPath(p, color)
}
