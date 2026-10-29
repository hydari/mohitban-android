package ir.mohitban.app.gasht

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.Closeable
import java.io.File

/** A named place shipped inside a map file (town, village, peak, spring). */
data class MapLabel(val name: String, val kind: String, val point: GeoPoint, val ele: Double?)

/**
 * An offline map in MBTiles form (an SQLite file of web-map tiles). Read-only; works with no
 * internet. Tiles use the TMS row order of the MBTiles standard.
 */
class OfflineMap(val file: File) : Closeable {
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
    fun tile(z: Int, x: Int, y: Int): ByteArray? = runCatching {
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

/** Map files kept in the app's private storage, and which one is shown. */
object MapStore {
    private const val PREFS = "gasht_maps"
    private const val KEY_ACTIVE = "active"

    /** Where the East Azerbaijan topographic map can be downloaded (opened in the browser, not by the app). */
    const val DOWNLOAD_URL = "https://github.com/hydari/mohitban-android/releases/download/map-azarbaijan-sharghi/azarbaijan-sharghi.mbtiles"

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
