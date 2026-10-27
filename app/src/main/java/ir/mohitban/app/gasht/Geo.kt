package ir.mohitban.app.gasht

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A WGS84 position; [ele] in metres, [time] in epoch milliseconds. */
data class GeoPoint(val lat: Double, val lon: Double, val ele: Double? = null, val time: Long? = null)

private fun Double.rad() = this * PI / 180.0
private fun Double.deg() = this * 180.0 / PI

object Geo {
    private const val R = 6_371_008.8

    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val dLat = (b.lat - a.lat).rad()
        val dLon = (b.lon - a.lon).rad()
        val h = sin(dLat / 2).pow(2) + cos(a.lat.rad()) * cos(b.lat.rad()) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from [a] to [b], 0..360 degrees clockwise from north. */
    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val y = sin((b.lon - a.lon).rad()) * cos(b.lat.rad())
        val x = cos(a.lat.rad()) * sin(b.lat.rad()) - sin(a.lat.rad()) * cos(b.lat.rad()) * cos((b.lon - a.lon).rad())
        return (atan2(y, x).deg() + 360.0) % 360.0
    }

    fun pathLength(points: List<GeoPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> distance(a, b) }

    /** Nearest place on a path: the closest point on any segment, its distance and how far along the path it lies. */
    data class Nearest(val segment: Int, val point: GeoPoint, val distance: Double, val along: Double)

    fun nearestOnPath(p: GeoPoint, path: List<GeoPoint>): Nearest? {
        if (path.isEmpty()) return null
        if (path.size == 1) return Nearest(0, path[0], distance(p, path[0]), 0.0)
        val k = cos(p.lat.rad())
        fun x(q: GeoPoint) = (q.lon - p.lon).rad() * R * k
        fun y(q: GeoPoint) = (q.lat - p.lat).rad() * R
        var best: Nearest? = null
        var walked = 0.0
        for (i in 0 until path.size - 1) {
            val a = path[i]; val b = path[i + 1]
            val ax = x(a); val ay = y(a); val bx = x(b); val by = y(b)
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / len2
            val tc = t.coerceIn(0.0, 1.0)
            val q = GeoPoint(a.lat + (b.lat - a.lat) * tc, a.lon + (b.lon - a.lon) * tc)
            val d = distance(p, q)
            val seg = distance(a, b)
            if (best == null || d < best.distance) best = Nearest(i, q, d, walked + seg * tc)
            walked += seg
        }
        return best
    }

    /** Area of a closed polygon in square metres (local projection; accurate for patrol-sized areas). */
    fun area(polygon: List<GeoPoint>): Double {
        if (polygon.size < 3) return 0.0
        val lat0 = polygon.map { it.lat }.average()
        val lon0 = polygon.map { it.lon }.average()
        val k = cos(lat0.rad())
        val xs = polygon.map { (it.lon - lon0).rad() * R * k }
        val ys = polygon.map { (it.lat - lat0).rad() * R }
        var s = 0.0
        for (i in xs.indices) {
            val j = (i + 1) % xs.size
            s += xs[i] * ys[j] - xs[j] * ys[i]
        }
        return abs(s) / 2.0
    }

    private val DIRS = listOf("شمال", "شمال‌شرق", "شرق", "جنوب‌شرق", "جنوب", "جنوب‌غرب", "غرب", "شمال‌غرب")

    fun direction(bearing: Double): String = DIRS[((bearing + 22.5) / 45.0).toInt() % 8]

    /** Web Mercator metres; used to draw tracks so a tile map can later sit under them unchanged. */
    fun mercatorX(lon: Double): Double = lon.rad() * 6_378_137.0
    fun mercatorY(lat: Double): Double {
        val l = lat.coerceIn(-85.0, 85.0).rad()
        return ln(tan(PI / 4 + l / 2)) * 6_378_137.0
    }
    fun fromMercator(x: Double, y: Double): GeoPoint =
        GeoPoint((2 * atan(kotlin.math.exp(y / 6_378_137.0)) - PI / 2).deg(), (x / 6_378_137.0).deg())
}

/** Universal Transverse Mercator on WGS84 (the grid most handheld GPS units show). */
object Utm {
    data class Coord(val zone: Int, val north: Boolean, val easting: Double, val northing: Double) {
        val band: Char get() = if (north) 'N' else 'S'
    }

    private const val A = 6_378_137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996
    private val E2 = F * (2 - F)
    private val EP2 = E2 / (1 - E2)

    fun from(lat: Double, lon: Double): Coord {
        val zone = (floor((lon + 180) / 6) + 1).toInt().coerceIn(1, 60)
        val lon0 = ((zone - 1) * 6 - 180 + 3).toDouble().rad()
        val phi = lat.rad()
        val n = A / sqrt(1 - E2 * sin(phi).pow(2))
        val t = tan(phi).pow(2)
        val c = EP2 * cos(phi).pow(2)
        val a = cos(phi) * (lon.rad() - lon0)
        val m = meridian(phi)
        val e = K0 * n * (a + (1 - t + c) * a.pow(3) / 6 + (5 - 18 * t + t * t + 72 * c - 58 * EP2) * a.pow(5) / 120) + 500_000.0
        var nn = K0 * (m + n * tan(phi) * (a * a / 2 + (5 - t + 9 * c + 4 * c * c) * a.pow(4) / 24 +
            (61 - 58 * t + t * t + 600 * c - 330 * EP2) * a.pow(6) / 720))
        if (lat < 0) nn += 10_000_000.0
        return Coord(zone, lat >= 0, e, nn)
    }

    private fun meridian(phi: Double): Double {
        val e4 = E2 * E2; val e6 = e4 * E2
        return A * ((1 - E2 / 4 - 3 * e4 / 64 - 5 * e6 / 256) * phi -
            (3 * E2 / 8 + 3 * e4 / 32 + 45 * e6 / 1024) * sin(2 * phi) +
            (15 * e4 / 256 + 45 * e6 / 1024) * sin(4 * phi) -
            (35 * e6 / 3072) * sin(6 * phi))
    }

    fun toLatLon(c: Coord): GeoPoint {
        val e1 = (1 - sqrt(1 - E2)) / (1 + sqrt(1 - E2))
        val x = c.easting - 500_000.0
        val y = if (c.north) c.northing else c.northing - 10_000_000.0
        val lon0 = ((c.zone - 1) * 6 - 180 + 3).toDouble().rad()
        val m = y / K0
        val mu = m / (A * (1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256))
        val phi1 = mu + (3 * e1 / 2 - 27 * e1.pow(3) / 32) * sin(2 * mu) +
            (21 * e1 * e1 / 16 - 55 * e1.pow(4) / 32) * sin(4 * mu) +
            (151 * e1.pow(3) / 96) * sin(6 * mu) + (1097 * e1.pow(4) / 512) * sin(8 * mu)
        val n1 = A / sqrt(1 - E2 * sin(phi1).pow(2))
        val t1 = tan(phi1).pow(2)
        val c1 = EP2 * cos(phi1).pow(2)
        val r1 = A * (1 - E2) / (1 - E2 * sin(phi1).pow(2)).pow(1.5)
        val d = x / (n1 * K0)
        val lat = phi1 - (n1 * tan(phi1) / r1) * (d * d / 2 - (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * EP2) * d.pow(4) / 24 +
            (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * EP2 - 3 * c1 * c1) * d.pow(6) / 720)
        val lon = lon0 + (d - (1 + 2 * t1 + c1) * d.pow(3) / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * EP2 + 24 * t1 * t1) * d.pow(5) / 120) / cos(phi1)
        return GeoPoint(lat.deg(), lon.deg())
    }
}

/** Text forms of a position. Latin digits so they can be typed into a GPS unit or sent as-is. */
object CoordFormat {
    fun decimal(p: GeoPoint): String = String.format(java.util.Locale.US, "%.6f, %.6f", p.lat, p.lon)

    private fun dms(v: Double, pos: Char, neg: Char): String {
        val a = abs(v)
        var d = floor(a).toInt()
        var m = floor((a - d) * 60).toInt()
        var s = ((a - d - m / 60.0) * 3600 * 10).roundToInt() / 10.0
        if (s >= 60.0) { s = 0.0; m += 1 }
        if (m >= 60) { m = 0; d += 1 }
        return String.format(java.util.Locale.US, "%c %d°%02d'%04.1f\"", if (v >= 0) pos else neg, d, m, s)
    }

    fun dms(p: GeoPoint): String = "${dms(p.lat, 'N', 'S')}  ${dms(p.lon, 'E', 'W')}"

    fun utm(p: GeoPoint): String {
        val u = Utm.from(p.lat, p.lon)
        return String.format(java.util.Locale.US, "%d%c  %.0f E  %.0f N", u.zone, u.band, u.easting, u.northing)
    }

    /** Reads "35.7, 51.4", "35.7 51.4" or a DMS pair like N 35°41'22" E 51°23'14". */
    fun parse(text: String): GeoPoint? {
        val t = text.trim().replace('،', ',')
        val nums = Regex("""-?\d+(?:\.\d+)?""").findAll(t).map { it.value.toDouble() }.toList()
        val south = t.contains('S', ignoreCase = false)
        val west = t.contains('W', ignoreCase = false)
        val p = when (nums.size) {
            2 -> GeoPoint(nums[0], nums[1])
            4 -> GeoPoint(nums[0] + nums[1] / 60, nums[2] + nums[3] / 60)
            6 -> GeoPoint(nums[0] + nums[1] / 60 + nums[2] / 3600, nums[3] + nums[4] / 60 + nums[5] / 3600)
            else -> return null
        }
        val q = GeoPoint(if (south) -abs(p.lat) else p.lat, if (west) -abs(p.lon) else p.lon)
        return q.takeIf { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }
    }

    fun meters(m: Double): String = when {
        m < 1000 -> "${m.roundToInt()} متر"
        else -> String.format(java.util.Locale.US, "%.2f کیلومتر", m / 1000)
    }
}

/** Sunrise and sunset (NOAA approximation, ±2 min), offline. Minutes after local midnight; null = no event. */
object SunTimes {
    data class Result(val sunrise: Int?, val noon: Int, val sunset: Int?)

    fun compute(dayOfYear: Int, lat: Double, lon: Double, utcOffsetMinutes: Int): Result {
        val g = 2 * PI / 365.0 * (dayOfYear - 1)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val phi = lat.rad()
        val noon = 720 - 4 * lon - eq + utcOffsetMinutes
        val cosHa = cos(90.833.rad()) / (cos(phi) * cos(decl)) - tan(phi) * tan(decl)
        if (cosHa > 1 || cosHa < -1) return Result(null, noon.roundToInt(), null)
        val ha = acos(cosHa).deg()
        return Result((noon - 4 * ha).roundToInt(), noon.roundToInt(), (noon + 4 * ha).roundToInt())
    }
}
