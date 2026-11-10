package ir.mohitban.app.gasht

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.Closeable
import java.io.File

/** A named place shipped inside a map file (town, village, peak, spring). */
data class MapLabel(val name: String, val kind: String, val point: GeoPoint, val ele: Double?)

/** Anything that can hand out map tiles: one map file, or several of them together. */
interface TileSource {
    override val minZoom: Int
    override val maxZoom: Int
    fun tile(z: Int, x: Int, y: Int): ByteArray?
}

/**
 * An offline map in MBTiles form (an SQLite file of web-map tiles). Read-only; works with no
 * internet. Tiles use the TMS row order of the MBTiles standard.
 */
class OfflineMap(val file: File) : Closeable, TileSource {
    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    private val meta: Map<String, String> = runCatching {
        db.rawQuery("SELECT name, value FROM metadata", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1) ?: "") }
        }
    }.getOrDefault(emptyMap())

    val title: String = meta["name"]?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
    val attribution: String = meta["attribution"].orEmpty()
    val minZoom: Int
    val maxZoom: Int

    init {
        val range = runCatching {
            db.rawQuery("SELECT MIN(zoom_level), MAX(zoom_level) FROM tiles", null).use { c -> if (c.moveToFirst()) c.getInt(0) to c.getInt(1) else null }
        }.getOrNull()
        minZoom = meta["minzoom"]?.toIntOrNull() ?: range?.first ?: 0
        maxZoom = meta["maxzoom"]?.toIntOrNull() ?: range?.second ?: 14
    }

    /** West, south, east, north in degrees, when the file declares them. */
    val bounds: DoubleArray? = meta["bounds"]?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() }?.takeIf { it.size == 4 }?.toDoubleArray()

    fun contains(p: GeoPoint): Boolean = bounds?.let { p.lon in it[0]..it[2] && p.lat in it[1]..it[3] } ?: true

    val center: GeoPoint? get() = bounds?.let { GeoPoint((it[1] + it[3]) / 2, (it[0] + it[2]) / 2) }

    /** Tile bytes (JPEG/PNG) for XYZ coordinates, or null when the file has none there. */
    override fun tile(z: Int, x: Int, y: Int): ByteArray? = runCatching {
        val row = (1 shl z) - 1 - y
        db.rawQuery(
            "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
            arrayOf(z.toString(), x.toString(), row.toString()),
        ).use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
    }.getOrNull()

    /** True when the file has at least one tile. */
    fun isValid(): Boolean = runCatching {
        db.rawQuery("SELECT 1 FROM tiles LIMIT 1", null).use { it.moveToFirst() }
    }.getOrDefault(false)

    fun labels(): List<MapLabel> = runCatching {
        db.rawQuery("SELECT name, kind, lat, lon, ele FROM mohityar_places", null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(MapLabel(c.getString(0) ?: "", c.getString(1) ?: "", GeoPoint(c.getDouble(2), c.getDouble(3)), if (c.isNull(4)) null else c.getDouble(4)))
                }
            }
        }
    }.getOrDefault(emptyList())

    override fun close() = db.close()
}

/**
 * Every county map on the phone, drawn as one: a tile comes from the first map that covers it, so
 * downloading another county never hides the ones already there.
 */
class MapSet(val maps: List<OfflineMap>) : TileSource, Closeable {
    override val minZoom: Int = maps.minOfOrNull { it.minZoom } ?: 0
    override val maxZoom: Int = maps.maxOfOrNull { it.maxZoom } ?: 14

    override fun tile(z: Int, x: Int, y: Int): ByteArray? {
        val n = (1 shl z).toDouble()
        val west = x / n * 360.0 - 180.0
        val east = (x + 1) / n * 360.0 - 180.0
        val north = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2.0 * y / n))))
        val south = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2.0 * (y + 1) / n))))
        for (m in maps) {
            val b = m.bounds
            // skip maps whose area does not touch this tile at all
            if (b != null && (east < b[0] || west > b[2] || north < b[1] || south > b[3])) continue
            m.tile(z, x, y)?.let { return it }
        }
        return null
    }

    /** True when at least one of the maps covers [p]. */
    fun covers(p: GeoPoint): Boolean = maps.any { it.bounds != null && it.contains(p) }

    override fun close() { maps.forEach { runCatching { it.close() } } }
}

/** Map files kept in the app's private storage, and which one is shown. */
object MapStore {
    private const val PREFS = "gasht_maps"
    private const val KEY_ACTIVE = "active"

    fun dir(context: Context): File = File(context.filesDir, "maps").apply { mkdirs() }

    fun list(context: Context): List<File> = dir(context).listFiles { f -> f.name.endsWith(".mbtiles") }?.sortedBy { it.name }.orEmpty()

    fun active(context: Context): File? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE, null)?.let { File(it) }?.takeIf { it.exists() }

    fun setActive(context: Context, file: File?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACTIVE, file?.path).apply()
    }

    /** Copies a picked .mbtiles file into private storage and checks that it really is a map. */
    fun import(context: Context, uri: Uri, displayName: String): File {
        val base = displayName.substringBeforeLast('.').replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").ifBlank { "map" }
        val target = File(dir(context), "$base.mbtiles")
        val tmp = File(dir(context), "$base.part")
        context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
            ?: error("cannot read")
        // validate before replacing an older copy
        val ok = runCatching { OfflineMap(tmp).use { it.isValid() } }.getOrDefault(false)
        if (!ok) { tmp.delete(); error("not an MBTiles map") }
        if (target.exists()) target.delete()
        tmp.renameTo(target)
        return target
    }

    fun delete(context: Context, file: File) {
        if (active(context)?.path == file.path) setActive(context, null)
        file.delete()
    }
}
