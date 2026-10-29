package ir.mohitban.app.gasht

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A protected area of the four DoE categories (park, natural monument, wildlife refuge,
 * protected area). [rings] holds every ring (outer and holes); inside = even-odd rule.
 */
class Zone(val name: String, val type: String, val hectares: Double?, val rings: List<List<GeoPoint>>) {
    val west = rings.minOf { r -> r.minOf { it.lon } }
    val east = rings.maxOf { r -> r.maxOf { it.lon } }
    val south = rings.minOf { r -> r.minOf { it.lat } }
    val north = rings.maxOf { r -> r.maxOf { it.lat } }
    val center: GeoPoint get() = GeoPoint((south + north) / 2, (west + east) / 2)
    val title: String get() = "$type $name"

    fun contains(p: GeoPoint): Boolean {
        if (p.lat < south || p.lat > north || p.lon < west || p.lon > east) return false
        var inside = false
        for (r in rings) {
            var j = r.size - 1
            for (i in r.indices) {
                val a = r[i]; val b = r[j]
                if ((a.lat > p.lat) != (b.lat > p.lat) && p.lon < (b.lon - a.lon) * (p.lat - a.lat) / (b.lat - a.lat) + a.lon) inside = !inside
                j = i
            }
        }
        return inside
    }

    /** Metres from [p] to the nearest point of the boundary. */
    fun distanceToBoundary(p: GeoPoint): Double = rings.minOf { r -> Geo.nearestOnPath(p, r + r.first())?.distance ?: Double.MAX_VALUE }

    /** Rough distance for sorting far-away zones without walking every vertex. */
    fun roughDistance(p: GeoPoint): Double {
        val lat = p.lat.coerceIn(south, north); val lon = p.lon.coerceIn(west, east)
        return Geo.distance(p, GeoPoint(lat, lon))
    }
}

/** Where the ranger is relative to the zones. */
data class ZoneStatus(val inside: Zone?, val nearest: Zone?, val distance: Double?)

object ZoneTypes {
    /** ARGB colours per category, shared by the map and the lists. */
    fun color(type: String): Long = when {
        type.contains("پارک") -> 0xFF1B7F3B
        type.contains("اثر") -> 0xFF7B4FB5
        type.contains("پناهگاه") -> 0xFFE08A1E
        else -> 0xFFC0392B
    }
}

/**
 * Zone boundaries imported by the ranger (GeoJSON Polygon / MultiPolygon with name, type and
 * hectares). Kept in private storage only; shared by the screens and the background service.
 */
object ZoneStore {
    private const val FILE = "zones.geojson"
    private const val BUNDLED = "zones/bundled.geojson"

    /** True when the boundaries come from a file the ranger imported (not the built-in ones). */
    fun isImported(context: Context) = file(context).exists()
    @Volatile private var cache: List<Zone>? = null

    private fun file(context: Context) = File(File(context.filesDir, "zones").apply { mkdirs() }, FILE)

    fun hasZones(context: Context) = file(context).exists()

    fun load(context: Context): List<Zone> {
        cache?.let { return it }
        val f = file(context)
        // a file the ranger imported wins; otherwise the boundaries built into the app
        val z = if (f.exists()) runCatching { parse(f.readText()) }.getOrDefault(emptyList())
        else runCatching { assetText(context, BUNDLED)?.let(::parse) }.getOrNull() ?: emptyList()
        cache = z
        return z
    }

    fun import(context: Context, uri: Uri): List<Zone> {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("read")
        val zones = parse(text)
        require(zones.isNotEmpty()) { "no zones" }
        file(context).writeText(text)
        cache = zones
        return zones
    }

    /** Removes an imported file; the built-in boundaries are used again. */
    fun delete(context: Context) {
        file(context).delete()
        cache = null
    }

    fun status(zones: List<Zone>, p: GeoPoint): ZoneStatus {
        if (zones.isEmpty()) return ZoneStatus(null, null, null)
        val inside = zones.filter { it.contains(p) }.minByOrNull { it.hectares ?: Double.MAX_VALUE } // the smallest = most specific
        if (inside != null) return ZoneStatus(inside, inside, inside.distanceToBoundary(p))
        val near = zones.sortedBy { it.roughDistance(p) }.take(3).minByOrNull { it.distanceToBoundary(p) }
        return ZoneStatus(null, near, near?.distanceToBoundary(p))
    }

    private fun fa(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').trim()

    fun parse(text: String): List<Zone> {
        val root = JSONObject(text)
        val features = root.optJSONArray("features") ?: JSONArray().put(root)
        val out = mutableListOf<Zone>()
        for (i in 0 until features.length()) {
            val f = features.getJSONObject(i)
            val props = f.optJSONObject("properties") ?: JSONObject()
            val g = f.optJSONObject("geometry") ?: continue
            val coords = g.optJSONArray("coordinates") ?: continue
            val polys: List<JSONArray> = when (g.optString("type")) {
                "Polygon" -> listOf(coords)
                "MultiPolygon" -> (0 until coords.length()).map { coords.getJSONArray(it) }
                else -> continue
            }
            val rings = polys.flatMap { poly ->
                (0 until poly.length()).map { k ->
                    val ring = poly.getJSONArray(k)
                    (0 until ring.length()).map { n -> ring.getJSONArray(n).let { c -> GeoPoint(c.getDouble(1), c.getDouble(0)) } }
                }
            }.filter { it.size >= 3 }
            if (rings.isEmpty()) continue
            val name = fa(props.optString("name").ifBlank { props.optString("NAME") }.ifBlank { "منطقه ${i + 1}" })
            val type = fa(props.optString("type").ifBlank { props.optString("TYPE") }.ifBlank { "منطقه حفاظت‌شده" })
            val ha = props.optDouble("hectares").takeIf { !it.isNaN() } ?: props.optDouble("HECTARES").takeIf { !it.isNaN() }
            out += Zone(name, type, ha, rings)
        }
        return out
    }
}
