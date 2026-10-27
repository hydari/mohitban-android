package ir.mohitban.app.gasht

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class NamedPath(val name: String, val points: List<GeoPoint>)
data class NamedPoint(val name: String, val point: GeoPoint, val description: String = "")
data class GpsData(val paths: List<NamedPath>, val points: List<NamedPoint>) {
    val isEmpty: Boolean get() = paths.all { it.points.isEmpty() } && points.isEmpty()
}

/**
 * Reads the files handheld GPS units and mapping programs produce — GPX (Garmin and most units),
 * KML/KMZ (Google Earth), PLT/WPT (OziExplorer) — and writes GPX/KML back.
 */
object GpsFiles {
    val EXTENSIONS = listOf("gpx", "kml", "kmz", "plt", "wpt")

    fun parse(fileName: String, bytes: ByteArray): GpsData {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return when {
            ext == "kmz" || (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) -> parseKmz(bytes)
            ext == "plt" -> parsePlt(text(bytes), fileName)
            ext == "wpt" -> parseWpt(text(bytes))
            else -> {
                val t = text(bytes)
                if (t.contains("<kml", ignoreCase = true)) parseKml(bytes) else parseGpx(bytes)
            }
        }
    }

    /** UTF-8, falling back to Windows-1256 (Persian names in older Windows tools). */
    private fun text(bytes: ByteArray): String {
        val utf = String(bytes, Charsets.UTF_8)
        if (!utf.contains('�')) return utf.removePrefix("﻿")
        return runCatching { String(bytes, Charset.forName("windows-1256")) }.getOrDefault(utf)
    }

    private fun dom(bytes: ByteArray): Element {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = false
        f.isExpandEntityReferences = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        return f.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
    }

    private fun Element.all(tag: String): List<Element> {
        val nl = getElementsByTagName(tag)
        return (0 until nl.length).map { nl.item(it) as Element }
    }

    private fun Element.child(tag: String): Element? {
        var n: Node? = firstChild
        while (n != null) {
            if (n is Element && n.tagName.equals(tag, ignoreCase = true)) return n
            n = n.nextSibling
        }
        return null
    }

    private fun Element.childText(tag: String): String? = child(tag)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private val isoFormats = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss")

    private fun parseTime(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        for (p in isoFormats) {
            val f = SimpleDateFormat(p, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            runCatching { return f.parse(s)?.time }
        }
        return null
    }

    // ------------------------------------------------------------------ GPX

    fun parseGpx(bytes: ByteArray): GpsData {
        val root = dom(bytes)
        fun pt(e: Element): GeoPoint? {
            val lat = e.getAttribute("lat").toDoubleOrNull() ?: return null
            val lon = e.getAttribute("lon").toDoubleOrNull() ?: return null
            return GeoPoint(lat, lon, e.childText("ele")?.toDoubleOrNull(), parseTime(e.childText("time")))
        }
        val paths = mutableListOf<NamedPath>()
        root.all("trk").forEachIndexed { i, trk ->
            paths += NamedPath(trk.childText("name") ?: "مسیر ${i + 1}", trk.all("trkpt").mapNotNull(::pt))
        }
        root.all("rte").forEachIndexed { i, rte ->
            paths += NamedPath(rte.childText("name") ?: "مسیر برنامه‌ریزی ${i + 1}", rte.all("rtept").mapNotNull(::pt))
        }
        val points = root.all("wpt").mapIndexedNotNull { i, w ->
            pt(w)?.let { NamedPoint(w.childText("name") ?: "نقطه ${i + 1}", it, w.childText("desc") ?: w.childText("cmt") ?: "") }
        }
        return GpsData(paths.filter { it.points.isNotEmpty() }, points)
    }

    // ------------------------------------------------------------------ KML / KMZ

    private fun coords(text: String): List<GeoPoint> =
        text.trim().split(Regex("\\s+")).mapNotNull { tuple ->
            val p = tuple.split(',')
            val lon = p.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val lat = p.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            GeoPoint(lat, lon, p.getOrNull(2)?.toDoubleOrNull())
        }

    fun parseKml(bytes: ByteArray): GpsData {
        val root = dom(bytes)
        val paths = mutableListOf<NamedPath>()
        val points = mutableListOf<NamedPoint>()
        root.all("Placemark").forEachIndexed { i, pm ->
            val name = pm.childText("name") ?: "مورد ${i + 1}"
            val desc = pm.childText("description").orEmpty()
            pm.all("LineString").forEach { ls -> ls.all("coordinates").firstOrNull()?.let { paths += NamedPath(name, coords(it.textContent)) } }
            pm.all("LinearRing").forEach { lr -> lr.all("coordinates").firstOrNull()?.let { paths += NamedPath(name, coords(it.textContent)) } }
            pm.all("gx:Track").forEach { tr ->
                val times = tr.all("when").map { parseTime(it.textContent.trim()) }
                val pts = tr.all("gx:coord").mapIndexedNotNull { k, c ->
                    val p = c.textContent.trim().split(Regex("\\s+"))
                    val lon = p.getOrNull(0)?.toDoubleOrNull() ?: return@mapIndexedNotNull null
                    val lat = p.getOrNull(1)?.toDoubleOrNull() ?: return@mapIndexedNotNull null
                    GeoPoint(lat, lon, p.getOrNull(2)?.toDoubleOrNull(), times.getOrNull(k))
                }
                paths += NamedPath(name, pts)
            }
            pm.all("Point").forEach { p ->
                p.all("coordinates").firstOrNull()?.let { c -> coords(c.textContent).firstOrNull()?.let { points += NamedPoint(name, it, desc) } }
            }
        }
        return GpsData(paths.filter { it.points.isNotEmpty() }, points)
    }

    fun parseKmz(bytes: ByteArray): GpsData {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.lowercase(Locale.US).endsWith(".kml")) return parseKml(zip.readBytes())
                e = zip.nextEntry
            }
        }
        return GpsData(emptyList(), emptyList())
    }

    // ------------------------------------------------------------------ OziExplorer

    private const val FEET = 0.3048

    /** OziExplorer track: 6 header lines, then "lat,lon,break,altitude(ft),..." (a break of 1 starts a new segment). */
    fun parsePlt(text: String, fileName: String): GpsData {
        val lines = text.lines()
        val name = lines.getOrNull(4)?.split(',')?.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.substringBeforeLast('.')
        val segments = mutableListOf<MutableList<GeoPoint>>()
        for (line in lines.drop(6)) {
            val f = line.split(',').map { it.trim() }
            val lat = f.getOrNull(0)?.toDoubleOrNull() ?: continue
            val lon = f.getOrNull(1)?.toDoubleOrNull() ?: continue
            val alt = f.getOrNull(3)?.toDoubleOrNull()?.takeIf { it > -777 }?.times(FEET)
            val days = f.getOrNull(4)?.toDoubleOrNull()
            // Ozi time = days since 30 Dec 1899
            val time = days?.takeIf { it > 1 }?.let { ((it - 25569.0) * 86_400_000.0).toLong() }
            if (segments.isEmpty() || f.getOrNull(2) == "1") segments += mutableListOf<GeoPoint>()
            segments.last() += GeoPoint(lat, lon, alt, time)
        }
        val paths = segments.filter { it.isNotEmpty() }.mapIndexed { i, s ->
            NamedPath(if (segments.size > 1) "$name (${i + 1})" else name, s)
        }
        return GpsData(paths, emptyList())
    }

    /** OziExplorer waypoints: 4 header lines, then "no,name,lat,lon,...,description(11th),...,altitude ft(15th)". */
    fun parseWpt(text: String): GpsData {
        val points = text.lines().drop(4).mapNotNull { line ->
            val f = line.split(',').map { it.trim() }
            val lat = f.getOrNull(2)?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = f.getOrNull(3)?.toDoubleOrNull() ?: return@mapNotNull null
            val alt = f.getOrNull(14)?.toDoubleOrNull()?.takeIf { it > -777 }?.times(FEET)
            NamedPoint(f.getOrNull(1).orEmpty().ifEmpty { "نقطه" }, GeoPoint(lat, lon, alt), f.getOrNull(10).orEmpty())
        }
        return GpsData(emptyList(), points)
    }

    // ------------------------------------------------------------------ export

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun iso(t: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(t)

    private fun n(v: Double) = String.format(Locale.US, "%.7f", v)

    fun toGpx(paths: List<NamedPath>, points: List<NamedPoint>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<gpx version=\"1.1\" creator=\"Mohityar\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        for (w in points) {
            append("  <wpt lat=\"${n(w.point.lat)}\" lon=\"${n(w.point.lon)}\">")
            w.point.ele?.let { append("<ele>${String.format(Locale.US, "%.1f", it)}</ele>") }
            append("<name>${esc(w.name)}</name>")
            if (w.description.isNotBlank()) append("<desc>${esc(w.description)}</desc>")
            append("</wpt>\n")
        }
        for (p in paths) {
            append("  <trk><name>${esc(p.name)}</name><trkseg>\n")
            for (q in p.points) {
                append("    <trkpt lat=\"${n(q.lat)}\" lon=\"${n(q.lon)}\">")
                q.ele?.let { append("<ele>${String.format(Locale.US, "%.1f", it)}</ele>") }
                q.time?.let { append("<time>${iso(it)}</time>") }
                append("</trkpt>\n")
            }
            append("  </trkseg></trk>\n")
        }
        append("</gpx>\n")
    }

    fun toKml(title: String, paths: List<NamedPath>, points: List<NamedPoint>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<kml xmlns=\"http://www.opengis.net/kml/2.2\"><Document><name>${esc(title)}</name>\n")
        append("<Style id=\"trk\"><LineStyle><color>ff2e8b4e</color><width>4</width></LineStyle></Style>\n")
        for (p in paths) {
            append("<Placemark><name>${esc(p.name)}</name><styleUrl>#trk</styleUrl><LineString><tessellate>1</tessellate><coordinates>\n")
            p.points.forEach { append("${n(it.lon)},${n(it.lat)},${String.format(Locale.US, "%.1f", it.ele ?: 0.0)}\n") }
            append("</coordinates></LineString></Placemark>\n")
        }
        for (w in points) {
            append("<Placemark><name>${esc(w.name)}</name>")
            if (w.description.isNotBlank()) append("<description>${esc(w.description)}</description>")
            append("<Point><coordinates>${n(w.point.lon)},${n(w.point.lat)},${String.format(Locale.US, "%.1f", w.point.ele ?: 0.0)}</coordinates></Point></Placemark>\n")
        }
        append("</Document></kml>\n")
    }
}
