package ir.mohitban.app.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import ir.mohitban.app.gasht.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A recognised living thing the ranger chose to keep: photo, answer, when and (if the phone knew) where. */
data class Observation(
    val id: Long = 0,
    val createdAt: Long,
    val category: String,
    val scientific: String,
    val fa: String,
    val english: String,
    val probability: Float,
    /** Relative path of the photo in the app's media folder. */
    val photoPath: String?,
    val lat: Double? = null,
    val lon: Double? = null,
    val note: String = "",
    /** The model had no class for the exact species: the name stands for the nearest group. */
    val approx: Boolean = false,
) {
    val categoryTitle: String get() = Category.of(category)?.shortTitle.orEmpty()
}

/**
 * The saved observations, in one JSON file in the app's private storage (the photos are in the media folder, so the
 * backup file carries them too).
 */
object ObservationStore {
    private const val FILE = "observations.json"

    /** Bumped on every change, so screens reload. */
    val version = MutableStateFlow(0)

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    @Synchronized
    fun all(context: Context): List<Observation> = runCatching {
        val f = file(context)
        if (!f.exists()) return emptyList()
        fromJsonArray(JSONArray(f.readText(Charsets.UTF_8)))
    }.getOrDefault(emptyList()).sortedByDescending { it.createdAt }

    @Synchronized
    fun save(context: Context, o: Observation): Long {
        val list = all(context).toMutableList()
        val id = if (o.id != 0L) o.id else (list.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(System.currentTimeMillis()) + 1
        val r = o.copy(id = id)
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list[i] = r else list += r
        write(context, list)
        return id
    }

    @Synchronized
    fun delete(context: Context, id: Long): Observation? {
        val list = all(context)
        val gone = list.firstOrNull { it.id == id } ?: return null
        write(context, list.filter { it.id != id })
        return gone
    }

    /** Used by the backup restore. */
    @Synchronized
    fun replaceAll(context: Context, list: List<Observation>) = write(context, list)

    private fun write(context: Context, list: List<Observation>) {
        val f = file(context)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(toJsonArray(list).toString(), Charsets.UTF_8)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        version.value++
    }

    fun toJsonArray(list: List<Observation>): JSONArray = JSONArray().apply {
        list.forEach { o ->
            put(JSONObject().apply {
                put("id", o.id); put("createdAt", o.createdAt); put("category", o.category)
                put("scientific", o.scientific); put("fa", o.fa); put("english", o.english)
                put("probability", o.probability.toDouble()); put("photoPath", o.photoPath ?: JSONObject.NULL)
                put("lat", o.lat ?: JSONObject.NULL); put("lon", o.lon ?: JSONObject.NULL)
                put("note", o.note); put("approx", o.approx)
            })
        }
    }

    fun fromJsonArray(arr: JSONArray): List<Observation> = (0 until arr.length()).mapNotNull { i ->
        runCatching {
            val o = arr.getJSONObject(i)
            Observation(
                id = o.getLong("id"), createdAt = o.optLong("createdAt"), category = o.optString("category"),
                scientific = o.optString("scientific"), fa = o.optString("fa"), english = o.optString("english"),
                probability = o.optDouble("probability").toFloat(),
                photoPath = if (o.isNull("photoPath")) null else o.optString("photoPath"),
                lat = if (o.isNull("lat")) null else o.optDouble("lat"), lon = if (o.isNull("lon")) null else o.optDouble("lon"),
                note = o.optString("note"), approx = o.optBoolean("approx"),
            )
        }.getOrNull()
    }

    /** The phone's last known position, if location is allowed (never asks, never turns anything on). */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): GeoPoint? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val best = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            // not older than a day
            .filter { System.currentTimeMillis() - it.time < 24 * 3_600_000L }
            .maxByOrNull { it.time } ?: return null
        return GeoPoint(best.latitude, best.longitude)
    }
}

/**
 * Species the ranger sent from the recognition screen to the daily report: kept until the report form opens and
 * takes them, so the wildlife list needs no typing.
 */
object PendingWildlife {
    private const val PREFS = "pending_wildlife"
    private const val KEY = "names"

    @Synchronized
    fun add(context: Context, name: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = JSONArray(p.getString(KEY, "[]"))
        list.put(name.trim())
        p.edit().putString(KEY, list.toString()).apply()
    }

    fun count(context: Context): Int = runCatching { JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")).length() }.getOrDefault(0)

    /** Returns what was waiting and clears it. */
    @Synchronized
    fun take(context: Context): List<String> {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val list = runCatching { JSONArray(p.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        p.edit().remove(KEY).apply()
        return (0 until list.length()).map { list.getString(it) }.filter { it.isNotBlank() }
    }
}
