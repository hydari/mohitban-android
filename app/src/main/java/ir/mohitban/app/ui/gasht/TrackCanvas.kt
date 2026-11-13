package ir.mohitban.app.ui.gasht

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.mohitban.app.gasht.Fix
import ir.mohitban.app.gasht.Geo
import ir.mohitban.app.gasht.GeoPoint
import ir.mohitban.app.ui.theme.AppFonts
import ir.mohitban.app.ui.theme.MColors
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** What the canvas shows at the moment: centre in Web Mercator metres and metres per pixel. */
data class Viewport(val cx: Double, val cy: Double, val metersPerPx: Double, val size: Size) {
    fun toScreen(mx: Double, my: Double) = Offset(
        (size.width / 2 + (mx - cx) / metersPerPx).toFloat(),
        (size.height / 2 - (my - cy) / metersPerPx).toFloat(),
    )

    /** Real ground metres per pixel at the centre (Mercator stretches with latitude). */
    fun groundMetersPerPx(): Double = metersPerPx * cos(Math.toRadians(Geo.fromMercator(cx, cy).lat))
}

/**
 * The layer under the tracks. Today it is a plain survey grid (works with no internet);
 * an online or offline tile map can implement this later without touching the rest.
 */
fun interface MapBackground {
    fun draw(scope: DrawScope, v: Viewport)
}

val GridBackground = MapBackground { scope, v -> with(scope) { drawGrid(v) } }

private fun DrawScope.drawGrid(v: Viewport) {
    drawRect(Color(0xFFF4F7F2))
    // grid step: a round ground distance that gives 60–150 px between lines
    val ground = v.groundMetersPerPx()
    val target = 90 * ground
    val pow10 = 10.0.pow(floor(log10(target)))
    val stepGround = listOf(1.0, 2.0, 5.0, 10.0).map { it * pow10 }.first { it >= target }
    val stepMerc = stepGround / ground * v.metersPerPx
    val left = v.cx - v.size.width / 2 * v.metersPerPx
    val bottom = v.cy - v.size.height / 2 * v.metersPerPx
    val line = Color(0xFFDDE6DA)
    var x = floor(left / stepMerc) * stepMerc
    while (x < left + v.size.width * v.metersPerPx) {
        val sx = v.toScreen(x, v.cy).x
        drawLine(line, Offset(sx, 0f), Offset(sx, v.size.height), 1f)
        x += stepMerc
    }
    var y = floor(bottom / stepMerc) * stepMerc
    while (y < bottom + v.size.height * v.metersPerPx) {
        val sy = v.toScreen(v.cx, y).y
        drawLine(line, Offset(0f, sy), Offset(v.size.width, sy), 1f)
        y += stepMerc
    }
}

class MapTrack(val id: Long, val points: List<GeoPoint>, val color: Color, val width: Float, val highlight: Boolean = false) {
    val xs = DoubleArray(points.size) { Geo.mercatorX(points[it].lon) }
    val ys = DoubleArray(points.size) { Geo.mercatorY(points[it].lat) }
}

/** A protected-area boundary prepared for drawing (Web Mercator rings, colour by category). */
class MapZone(val zone: ir.mohitban.app.gasht.Zone) {
    val color = Color(ir.mohitban.app.gasht.ZoneTypes.color(zone.type))
    val rings: List<Pair<DoubleArray, DoubleArray>> = zone.rings.map { r ->
        DoubleArray(r.size) { Geo.mercatorX(r[it].lon) } to DoubleArray(r.size) { Geo.mercatorY(r[it].lat) }
    }
    val cx = Geo.mercatorX(zone.center.lon)
    val cy = Geo.mercatorY(zone.center.lat)
    val minX = rings.minOf { it.first.min() }; val maxX = rings.maxOf { it.first.max() }
    val minY = rings.minOf { it.second.min() }; val maxY = rings.maxOf { it.second.max() }
}

/** A blinking alert on the map (a colleague in need, a poacher, a fire). */
class MapAlert(val point: GeoPoint, val color: Color, val label: String = "")

class MapPlace(val name: String, val point: GeoPoint, val color: Color, val highlight: Boolean = false)

@Composable
fun TrackCanvas(
    tracks: List<MapTrack>,
    places: List<MapPlace>,
    fix: Fix?,
    heading: Float?,
    modifier: Modifier = Modifier,
    background: MapBackground = GridBackground,
    fitKey: Any? = null,
    labels: List<ir.mohitban.app.gasht.MapLabel> = emptyList(),
    attribution: String = "",
    initialCenter: GeoPoint? = null,
    zones: List<MapZone> = emptyList(),
    fitPoints: List<GeoPoint> = emptyList(),
    alerts: List<MapAlert> = emptyList(),
    /** Room the screen keeps free on top / at the bottom (panels laid over the map): the compass, scale and buttons stay clear of them. */
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    onLongPress: ((GeoPoint) -> Unit)? = null,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var cx by remember { mutableDoubleStateOf(Double.NaN) }
    var cy by remember { mutableDoubleStateOf(Double.NaN) }
    var mpp by remember { mutableDoubleStateOf(3.0) }
    var follow by remember { mutableStateOf(true) }
    var size by remember { mutableStateOf(Size.Zero) }
    val measurer = rememberTextMeasurer()
    val smoothHeading = heading?.let { ir.mohitban.app.ui.components.rememberSmoothAngle(it) }
    val motion = ir.mohitban.app.ui.components.rememberMotionOn()
    // a newly shown track draws itself along its way (the live recording is never hidden)
    val shownIds = tracks.filter { it.id != -1L }.map { it.id }
    val reveal = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(shownIds) {
        if (motion && shownIds.isNotEmpty()) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    val pulseT = androidx.compose.animation.core.rememberInfiniteTransition(label = "me")
    val pulse = pulseT.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable<Float>(androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing)), label = "mp")

    fun fitAll() {
        if (fitPoints.isNotEmpty() && size != Size.Zero) {
            val fx = fitPoints.map { Geo.mercatorX(it.lon) }; val fy = fitPoints.map { Geo.mercatorY(it.lat) }
            cx = (fx.min() + fx.max()) / 2; cy = (fy.min() + fy.max()) / 2
            mpp = (maxOf((fx.max() - fx.min()) / size.width, (fy.max() - fy.min()) / size.height) * 1.2).coerceIn(0.3, 2000.0)
            follow = false
            return
        }
        val xs = tracks.flatMap { it.xs.asList() } + places.map { Geo.mercatorX(it.point.lon) } + listOfNotNull(fix?.let { Geo.mercatorX(it.point.lon) })
        val ys = tracks.flatMap { it.ys.asList() } + places.map { Geo.mercatorY(it.point.lat) } + listOfNotNull(fix?.let { Geo.mercatorY(it.point.lat) })
        if (xs.isEmpty() || size == Size.Zero) return
        cx = (xs.min() + xs.max()) / 2
        cy = (ys.min() + ys.max()) / 2
        val span = max((xs.max() - xs.min()) / size.width, (ys.max() - ys.min()) / size.height)
        mpp = (span * 1.25).coerceIn(0.3, 2000.0)
        follow = false
    }

    // first view: the selected tracks; otherwise the ranger's position
    LaunchedEffect(fitKey, size) {
        if (size == Size.Zero) return@LaunchedEffect
        if (tracks.isNotEmpty() || places.isNotEmpty() || fitPoints.isNotEmpty()) fitAll()
    }
    // with a map but no GPS fix yet, open on the map's area
    LaunchedEffect(initialCenter) {
        val c = initialCenter ?: return@LaunchedEffect
        if (cx.isNaN() && fix == null) { cx = Geo.mercatorX(c.lon); cy = Geo.mercatorY(c.lat); mpp = 150.0; follow = false }
    }
    LaunchedEffect(fix, follow) {
        val f = fix ?: return@LaunchedEffect
        if (follow || cx.isNaN()) {
            cx = Geo.mercatorX(f.point.lon); cy = Geo.mercatorY(f.point.lat)
        }
    }

    Box(modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, MColors.Line, RoundedCornerShape(12.dp))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { size = it.toSize() }
                .pointerInput(onLongPress != null) {
                    if (onLongPress == null) return@pointerInput
                    detectTapGestures(onLongPress = { o ->
                        if (cx.isNaN()) return@detectTapGestures
                        val mx = cx + (o.x - size.width / 2) * mpp
                        val my = cy - (o.y - size.height / 2) * mpp
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onLongPress(Geo.fromMercator(mx, my))
                    })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (cx.isNaN()) return@detectTransformGestures
                        follow = false
                        mpp = (mpp / zoom).coerceIn(0.2, 5000.0)
                        cx -= pan.x * mpp
                        cy += pan.y * mpp
                    }
                },
        ) {
            if (cx.isNaN()) {
                drawRect(Color(0xFFF4F7F2))
                return@Canvas
            }
            val v = Viewport(cx, cy, mpp, this.size)
            background.draw(this, v)

            // protected-area boundaries: light fill, solid edge, name in the middle
            val vl = v.cx - v.size.width / 2 * v.metersPerPx; val vr = v.cx + v.size.width / 2 * v.metersPerPx
            val vb = v.cy - v.size.height / 2 * v.metersPerPx; val vt = v.cy + v.size.height / 2 * v.metersPerPx
            for (mz in zones) {
                if (mz.maxX < vl || mz.minX > vr || mz.maxY < vb || mz.minY > vt) continue
                val path = Path().apply { fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd }
                val step = if (v.metersPerPx > 60) 4 else if (v.metersPerPx > 15) 2 else 1
                for ((xs, ys) in mz.rings) {
                    var i = 0
                    while (i < xs.size) {
                        val p = v.toScreen(xs[i], ys[i])
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                        i += step
                    }
                    path.close()
                }
                drawPath(path, mz.color.copy(alpha = 0.10f))
                drawPath(path, Color.White.copy(alpha = 0.8f), style = Stroke(6f, join = StrokeJoin.Round))
                drawPath(path, mz.color, style = Stroke(3f, join = StrokeJoin.Round))
                if (zoomOf(v.metersPerPx) >= 8.5) {
                    val c = v.toScreen(mz.cx, mz.cy)
                    val layout = measurer.measure(
                        mz.zone.title,
                        TextStyle(fontSize = 11.sp, fontFamily = AppFonts.family(), fontWeight = FontWeight.Black, color = mz.color,
                            shadow = androidx.compose.ui.graphics.Shadow(Color.White, Offset(0f, 0f), 8f)),
                    )
                    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2, c.y - layout.size.height / 2))
                }
            }

            // tracks
            for (t in tracks) {
                if (t.xs.isEmpty()) continue
                val path = Path()
                for (i in t.xs.indices) {
                    val p = v.toScreen(t.xs[i], t.ys[i])
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                val r = if (t.id == -1L) 1f else reveal.value
                val drawn = if (r >= 1f) path else Path().also { out ->
                    val pm = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
                    pm.getSegment(0f, pm.length * r, out, true)
                }
                if (t.highlight) drawPath(drawn, Color.White, style = Stroke(t.width + 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(drawn, t.color, style = Stroke(t.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val s = v.toScreen(t.xs.first(), t.ys.first())
                // start: a green disc with a play mark; end: a red disc with a stop mark
                drawCircle(Color.White, 11f, s); drawCircle(Color(0xFF239B56), 8.5f, s)
                drawPath(Path().apply { moveTo(s.x - 3f, s.y - 4.5f); lineTo(s.x + 4.5f, s.y); lineTo(s.x - 3f, s.y + 4.5f); close() }, Color.White)
                if (t.xs.size > 1) {
                    val e = v.toScreen(t.xs.last(), t.ys.last())
                    drawCircle(Color.White, 11f, e); drawCircle(Color(0xFFC62828), 8.5f, e)
                    drawRect(Color.White, Offset(e.x - 3.5f, e.y - 3.5f), Size(7f, 7f))
                }
            }

            // places: map pins (the tip is the place)
            for (pl in places) {
                val p = v.toScreen(Geo.mercatorX(pl.point.lon), Geo.mercatorY(pl.point.lat))
                if (p.x < -50 || p.y < -90 || p.x > this.size.width + 50 || p.y > this.size.height + 50) continue
                drawPin(p, pl.color, if (pl.highlight) 12f else 9f)
                val layout = measurer.measure(
                    pl.name,
                    TextStyle(
                        fontSize = 10.sp, fontFamily = AppFonts.family(), fontWeight = FontWeight.Bold, color = MColors.Ink,
                        shadow = androidx.compose.ui.graphics.Shadow(Color.White, Offset(0f, 0f), 6f),
                    ),
                )
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y + 3))
            }

            // a colleague in need: a red pin (it blinks on the layer above)
            for (al in alerts) {
                val p = v.toScreen(Geo.mercatorX(al.point.lon), Geo.mercatorY(al.point.lat))
                drawCircle(Color(0x44000000), 16f, p + Offset(0f, 3f))
                drawCircle(Color.White, 15f, p)
                drawCircle(al.color, 11f, p)
                drawCircle(Color.White, 4f, p)
                if (al.label.isNotBlank()) {
                    val layout = measurer.measure(al.label, TextStyle(fontSize = 11.sp, fontFamily = AppFonts.family(), fontWeight = FontWeight.Black, color = al.color, shadow = androidx.compose.ui.graphics.Shadow(Color.White, Offset(0f, 0f), 6f)))
                    drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y + 18f))
                }
            }

            // the ranger: accuracy circle, heading cone, blue dot
            fix?.let { f ->
                val p = v.toScreen(Geo.mercatorX(f.point.lon), Geo.mercatorY(f.point.lat))
                f.accuracy?.let { acc ->
                    val rr = (acc / v.groundMetersPerPx()).toFloat()
                    if (rr > 6) {
                        drawCircle(Color(0x332F7FC1), rr, p)
                        drawCircle(Color(0x662F7FC1), rr, p, style = Stroke(1.5f))
                    }
                }
                smoothHeading?.let { h ->
                    rotate(h, p) {
                        val cone = Path().apply {
                            moveTo(p.x, p.y)
                            lineTo(p.x - 26f, p.y - 62f)
                            quadraticTo(p.x, p.y - 74f, p.x + 26f, p.y - 62f)
                            close()
                        }
                        drawPath(cone, androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x882F7FC1), Color(0x002F7FC1)), center = p, radius = 74f))
                    }
                }
                drawCircle(Color(0x33000000), 16f, p + Offset(0f, 2f))
                drawCircle(Color.White, 15f, p)
                drawCircle(Color(0xFF1E6FD9), 11f, p)
                drawCircle(Color.White, 3.5f, p)
            }

            // named places from the map file (towns first; villages, peaks, springs when zoomed in)
            if (labels.isNotEmpty()) {
                val z = zoomOf(v.metersPerPx)
                var shownLabels = 0
                for (l in labels) {
                    val (color, minZ) = labelStyle(l.kind) ?: continue
                    if (z < minZ) continue
                    val p = v.toScreen(Geo.mercatorX(l.point.lon), Geo.mercatorY(l.point.lat))
                    if (p.x < -40 || p.y < -20 || p.x > this.size.width + 40 || p.y > this.size.height + 20) continue
                    if (++shownLabels > 160) break
                    when (l.kind) {
                        "peak" -> {
                            val tri = Path().apply { moveTo(p.x, p.y - 6); lineTo(p.x + 6, p.y + 4); lineTo(p.x - 6, p.y + 4); close() }
                            drawPath(tri, color)
                        }
                        "spring" -> { drawCircle(Color.White, 6f, p); drawCircle(color, 4.5f, p) }
                        else -> { drawCircle(Color.White, if (l.kind == "town") 6f else 4.5f, p); drawCircle(color, if (l.kind == "town") 4.5f else 3f, p) }
                    }
                    val text = if (l.kind == "peak" && l.ele != null) "${l.name} ${l.ele.toInt()}".trim() else l.name
                    if (text.isBlank()) continue
                    val layout = measurer.measure(
                        text,
                        TextStyle(
                            fontSize = if (l.kind == "town") 12.sp else 10.sp, fontFamily = AppFonts.family(), fontWeight = FontWeight.Bold, color = color,
                            shadow = androidx.compose.ui.graphics.Shadow(Color.White, Offset(0f, 0f), 6f),
                        ),
                    )
                    drawText(layout, topLeft = Offset(p.x - layout.size.width / 2, p.y + 5))
                }
            }

            val bottomPx = bottomInset.toPx()
            drawScale(v, measurer, bottomPx)
            if (attribution.isNotBlank()) {
                val layout = measurer.measure(attribution, TextStyle(fontSize = 7.sp, color = Color(0xCC3D4A42), fontFamily = AppFonts.family()), constraints = androidx.compose.ui.unit.Constraints(maxWidth = (this.size.width * 0.62f).toInt()))
                val ax = this.size.width - layout.size.width - 8f
                val ay = this.size.height - bottomPx - layout.size.height - 4f
                drawRect(Color(0xB3FFFFFF), topLeft = Offset(ax - 4f, ay - 2f), size = Size(layout.size.width + 8f, layout.size.height + 4f))
                drawText(layout, topLeft = Offset(ax, ay))
            }
            drawNorth(measurer, topInset.toPx())
        }

        // the "you are here" pulse on its own layer, so the map itself is not redrawn every frame
        if ((motion && fix != null) || alerts.isNotEmpty()) {
            Canvas(Modifier.fillMaxSize()) {
                if (cx.isNaN()) return@Canvas
                val v = Viewport(cx, cy, mpp, this.size)
                val k = pulse.value
                if (motion && fix != null) {
                    val p = v.toScreen(Geo.mercatorX(fix.point.lon), Geo.mercatorY(fix.point.lat))
                    drawCircle(Color(0xFF1E6FD9).copy(alpha = 0.35f * (1f - k)), 13f + 34f * k, p)
                    drawCircle(Color(0xFF1E6FD9).copy(alpha = 0.5f * (1f - k)), 13f + 34f * k, p, style = Stroke(2f))
                }
                for (al in alerts) {
                    // two rings spreading out and a blinking core
                    val p = v.toScreen(Geo.mercatorX(al.point.lon), Geo.mercatorY(al.point.lat))
                    for (j in 0..1) {
                        val q = (k + j * 0.5f) % 1f
                        drawCircle(al.color.copy(alpha = 0.40f * (1f - q)), 15f + 70f * q, p)
                        drawCircle(al.color.copy(alpha = 0.8f * (1f - q)), 15f + 70f * q, p, style = Stroke(3f))
                    }
                    val blink = if ((k * 4).toInt() % 2 == 0) 1f else 0.25f
                    drawCircle(al.color.copy(alpha = blink), 11f, p)
                    drawCircle(Color.White.copy(alpha = blink), 4f, p)
                }
            }
        }
        Column(Modifier.align(Alignment.CenterEnd).padding(top = topInset, bottom = bottomInset).padding(8.dp)) {
            MapButton(Icons.Outlined.Add) { mpp = (mpp / 1.6).coerceAtLeast(0.2) }
            MapButton(Icons.Outlined.Remove) { mpp = (mpp * 1.6).coerceAtMost(5000.0) }
            MapButton(Icons.Outlined.ZoomOutMap) { fitAll() }
            // the location icon: green while my position is found and the map follows it, red otherwise
            MapButton(Icons.Outlined.MyLocation, tint = if (fix != null && follow) Color(0xFF1F8A4C) else Color(0xFFC62828)) {
                follow = true
                fix?.let { cx = Geo.mercatorX(it.point.lon); cy = Geo.mercatorY(it.point.lat) }
                if (mpp > 20) mpp = 3.0
            }
        }
    }
}

@Composable
private fun MapButton(icon: ImageVector, tint: Color = MColors.Ink, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 6.dp)
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.96f))
            .border(1.dp, MColors.Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
}

/** A map pin: [tip] is the place and the round head sits above it. */
private fun DrawScope.drawPin(tip: Offset, color: Color, r: Float) {
    val head = Offset(tip.x, tip.y - 2f * r)
    val drop = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(head.x - r * 0.866f, head.y + r * 0.5f)
        arcTo(androidx.compose.ui.geometry.Rect(head.x - r, head.y - r, head.x + r, head.y + r), 150f, 240f, false)
        close()
    }
    drawPath(drop, Color(0x33000000), style = Stroke(r * 0.7f, join = StrokeJoin.Round))
    drawPath(drop, Color.White, style = Stroke(r * 0.45f, join = StrokeJoin.Round))
    drawPath(drop, color)
    drawCircle(Color.White, r * 0.42f, head)
}

private fun DrawScope.drawScale(v: Viewport, measurer: androidx.compose.ui.text.TextMeasurer, bottomPx: Float = 0f) {
    val ground = v.groundMetersPerPx()
    val target = 110 * ground
    val pow10 = 10.0.pow(floor(log10(target)))
    val meters = listOf(1.0, 2.0, 5.0).map { it * pow10 }.lastOrNull { it <= target } ?: pow10
    val px = (meters / ground).toFloat()
    val x0 = 16f
    val y0 = size.height - 18f - bottomPx
    drawLine(Color.Black, Offset(x0, y0), Offset(x0 + px, y0), 3f)
    drawLine(Color.Black, Offset(x0, y0 - 7), Offset(x0, y0 + 1), 3f)
    drawLine(Color.Black, Offset(x0 + px, y0 - 7), Offset(x0 + px, y0 + 1), 3f)
    val label = if (meters >= 1000) "${(meters / 1000).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }} km" else "${meters.toInt()} m"
    val layout = measurer.measure(label, TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black))
    drawText(layout, topLeft = Offset(x0, y0 - 8 - layout.size.height))
}

private fun DrawScope.drawNorth(measurer: androidx.compose.ui.text.TextMeasurer, topPx: Float = 0f) {
    val c = Offset(size.width - 26f, 34f + topPx)
    val tri = Path().apply { moveTo(c.x, c.y - 16); lineTo(c.x + 9, c.y + 10); lineTo(c.x, c.y + 4); lineTo(c.x - 9, c.y + 10); close() }
    drawCircle(Color.White, 22f, c)
    drawPath(tri, Color(0xFFC62828))
    val layout = measurer.measure("N", TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MColors.Ink))
    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2, c.y + 10))
}

/** An arrow pointing [angle] degrees clockwise from screen-up. */
@Composable
fun GuideArrow(angle: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = min(size.width, size.height) / 2
        drawCircle(Color.White, r, c)
        drawCircle(color.copy(alpha = 0.15f), r, c)
        drawCircle(color, r, c, style = Stroke(3f))
        rotate(angle, c) {
            val p = Path().apply {
                moveTo(c.x, c.y - r * 0.78f)
                lineTo(c.x + r * 0.42f, c.y + r * 0.45f)
                lineTo(c.x, c.y + r * 0.2f)
                lineTo(c.x - r * 0.42f, c.y + r * 0.45f)
                close()
            }
            drawPath(p, color)
        }
    }
}

/** A compass rose turning with the phone. */
@Composable
fun CompassRose(heading: Float, modifier: Modifier = Modifier, qibla: Float? = null) {
    val measurer = rememberTextMeasurer()
    // the card turns smoothly the short way round instead of jumping with every sensor reading
    @Suppress("NAME_SHADOWING") val heading = ir.mohitban.app.ui.components.rememberSmoothAngle(heading)
    Canvas(modifier) {
        val c = center
        val r = min(size.width, size.height) / 2 - 4
        drawCircle(Color.White, r, c)
        drawCircle(MColors.Line, r, c, style = Stroke(3f))
        rotate(-heading, c) {
            for (d in 0 until 360 step 10) {
                val a = Math.toRadians(d.toDouble())
                val inner = if (d % 30 == 0) r * 0.84f else r * 0.9f
                drawLine(
                    if (d == 0) Color(0xFFC62828) else MColors.Muted,
                    Offset(c.x + (inner * sin(a)).toFloat(), c.y - (inner * cos(a)).toFloat()),
                    Offset(c.x + (r * 0.97f * sin(a)).toFloat(), c.y - (r * 0.97f * cos(a)).toFloat()),
                    if (d % 30 == 0) 3f else 1.5f,
                )
            }
            listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (t, d) ->
                val a = Math.toRadians(d.toDouble())
                val layout = measurer.measure(t, TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Black, color = if (d == 0) Color(0xFFC62828) else MColors.Ink))
                val pos = Offset(c.x + (r * 0.68f * sin(a)).toFloat(), c.y - (r * 0.68f * cos(a)).toFloat())
                drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2, pos.y - layout.size.height / 2))
            }
            val needle = Path().apply { moveTo(c.x, c.y - r * 0.5f); lineTo(c.x + 10, c.y); lineTo(c.x - 10, c.y); close() }
            drawPath(needle, Color(0xFFC62828))
            val tail = Path().apply { moveTo(c.x, c.y + r * 0.5f); lineTo(c.x + 10, c.y); lineTo(c.x - 10, c.y); close() }
            drawPath(tail, MColors.Muted)
            // qibla: a green line to the Kaaba mark at its bearing from north
            qibla?.let { q ->
                rotate(q, c) {
                    val green = Color(0xFF1F7A4A)
                    drawLine(green.copy(alpha = 0.85f), c, Offset(c.x, c.y - r * 0.62f), 5f, cap = StrokeCap.Round)
                    val k = r * 0.11f
                    val kc = Offset(c.x, c.y - r * 0.74f)
                    drawCircle(green, k * 1.25f, kc)
                    drawRect(Color(0xFF151515), Offset(kc.x - k * 0.62f, kc.y - k * 0.62f), Size(k * 1.24f, k * 1.24f))
                    drawRect(Color(0xFFE2B54A), Offset(kc.x - k * 0.62f, kc.y - k * 0.32f), Size(k * 1.24f, k * 0.2f))
                }
            }
        }
        // fixed index at the top = the direction the phone points
        val idx = Path().apply { moveTo(c.x, c.y - r - 2); lineTo(c.x - 9, c.y - r - 16); lineTo(c.x + 9, c.y - r - 16); close() }
        drawPath(idx, MColors.Green)
        drawCircle(MColors.Ink, 6f, c)
    }
}
