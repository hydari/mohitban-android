package ir.mohitban.app.gasht

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.GZIPInputStream

/**
 * Names the ranger's location — province, county and the nearest city or village — from the
 * GPS position alone, using OpenStreetMap data built into the app (no internet).
 */
object Whereabouts {
    private class Area(val name: String, val level: Int, val rings: List<DoubleArray>) {
        // each ring = lat0, lon0, lat1, lon1, …
        val south = rings.minOf { r -> (r.indices step 2).minOf { r[it] } }
        val north = rings.maxOf { r -> (r.indices step 2).maxOf { r[it] } }
        val west = rings.minOf { r -> (1 until r.size step 2).minOf { r[it] } }
        val east = rings.maxOf { r -> (1 until r.size step 2).maxOf { r[it] } }

        fun contains(lat: Double, lon: Double): Boolean {
            if (lat < south || lat > north || lon < west || lon > east) return false
            var inside = false
            for (r in rings) {
                val n = r.size / 2
                var j = n - 1
                for (i in 0 until n) {
                    val ay = r[2 * i]; val ax = r[2 * i + 1]; val by = r[2 * j]; val bx = r[2 * j + 1]
                    if ((ay > lat) != (by > lat) && lon < (bx - ax) * (lat - ay) / (by - ay) + ax) inside = !inside
                    j = i
                }
            }
            return inside
        }
    }

    class Settlement(val kind: Char, val lat: Double, val lon: Double, val name: String) {
        val label: String get() = when (kind) {
            'c', 't' -> "شهر $name"
            's' -> "محله‌ی $name"
            else -> "روستای $name"
        }
    }

    data class Result(val province: String?, val county: String?, val place: Settlement?, val placeDistance: Double?) {
        val isEmpty get() = province == null && county == null && place == null
    }

    @Volatile private var areas: List<Area>? = null
    @Volatile private var places: List<Settlement>? = null

    fun available(context: Context): Boolean = runCatching { context.assets.list("geo")?.any { it.startsWith("admin.geojson") } == true }.getOrDefault(false)

    /** The build tool unpacks ".gz" assets and drops the extension; accept either form. */
    private fun text(context: Context, name: String): String? = assetText(context, "geo/$name")

    private fun load(context: Context) {
        if (areas == null) {
            areas = text(context, "admin.geojson")?.let { parseAreas(it) } ?: emptyList()
        }
        if (places == null) {
            places = text(context, "places.tsv")?.lineSequence()?.mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 4) null else Settlement(f[0].firstOrNull() ?: 'v', f[1].toDoubleOrNull() ?: return@mapNotNull null, f[2].toDoubleOrNull() ?: return@mapNotNull null, f[3])
            }?.toList() ?: emptyList()
        }
    }

    private fun parseAreas(json: String): List<Area> {
        val features = JSONObject(json).getJSONArray("features")
        val out = ArrayList<Area>(features.length())
        for (i in 0 until features.length()) {
            val f = features.getJSONObject(i)
            val props = f.getJSONObject("properties")
            val g = f.optJSONObject("geometry") ?: continue
            val c = g.getJSONArray("coordinates")
            val polys: List<JSONArray> = when (g.optString("type")) {
                "Polygon" -> listOf(c)
                "MultiPolygon" -> (0 until c.length()).map { c.getJSONArray(it) }
                else -> continue
            }
            val rings = polys.flatMap { poly ->
                (0 until poly.length()).map { k ->
                    val ring = poly.getJSONArray(k)
                    DoubleArray(ring.length() * 2).also { arr ->
                        for (n in 0 until ring.length()) {
                            val pt = ring.getJSONArray(n)
                            arr[2 * n] = pt.getDouble(1); arr[2 * n + 1] = pt.getDouble(0)
                        }
                    }
                }
            }.filter { it.size >= 6 }
            if (rings.isNotEmpty()) out += Area(props.optString("n"), props.optInt("l"), rings)
        }
        return out
    }

    private fun clean(name: String, prefix: String) = name.trim().removePrefix(prefix).trim()

    /**
     * Middle of a county by its name (the county chosen at registration), preferring one inside
     * [province]. Call off the main thread. Null when the name is not in the built-in data.
     */
    fun countyCenter(context: Context, county: String, province: String? = null): GeoPoint? {
        load(context)
        val want = norm(clean(county, "شهرستان"))
        if (want.isEmpty()) return null
        val a = areas.orEmpty()
        val matches = a.filter { it.level == 5 && norm(clean(it.name, "شهرستان")) == want }
        if (matches.isEmpty()) return null
        val prov = province?.let { p -> a.firstOrNull { it.level == 4 && norm(clean(it.name, "استان")) == norm(clean(p, "استان")) } }
        val pick = prov?.let { pv -> matches.firstOrNull { m -> pv.contains((m.south + m.north) / 2, (m.west + m.east) / 2) } } ?: matches.first()
        // the average of the outer ring's points sits well inside most counties
        val r = pick.rings.maxByOrNull { it.size } ?: return null
        var lat = 0.0; var lon = 0.0
        for (i in r.indices step 2) { lat += r[i]; lon += r[i + 1] }
        val n = r.size / 2
        return GeoPoint(lat / n, lon / n)
    }

    private fun norm(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", "").replace(" ", "").trim()

    /** Call off the main thread (first call reads the data, about a second). */
    fun locate(context: Context, p: GeoPoint): Result {
        load(context)
        val a = areas.orEmpty()
        val province = a.firstOrNull { it.level == 4 && it.contains(p.lat, p.lon) }?.name?.let { clean(it, "استان") }
        val county = a.firstOrNull { it.level == 5 && it.contains(p.lat, p.lon) }?.name?.let { clean(it, "شهرستان") }
        var best: Settlement? = null
        var bestD = Double.MAX_VALUE
        for (s in places.orEmpty()) {
            if (kotlin.math.abs(s.lat - p.lat) > 0.12 || kotlin.math.abs(s.lon - p.lon) > 0.15) continue
            // towns count as "nearer" so a village on a city's edge does not hide the city
            val d = Geo.distance(p, GeoPoint(s.lat, s.lon)) * (if (s.kind == 'c') 0.5 else if (s.kind == 't') 0.75 else 1.0)
            if (d < bestD) { bestD = d; best = s }
        }
        val realD = best?.let { Geo.distance(p, GeoPoint(it.lat, it.lon)) }
        return Result(province, county, best, realD)
    }
}

/** Reads a text asset stored either plain or gzip-compressed (with a ".gz" name). */
internal fun assetText(context: Context, path: String): String? =
    runCatching { context.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        ?: runCatching { context.assets.open("$path.gz").use { GZIPInputStream(it).readBytes().toString(Charsets.UTF_8) } }.getOrNull()
