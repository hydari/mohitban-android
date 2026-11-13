package ir.mohitban.app.gasht

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class WeatherNow(
    val time: String, val temp: Double, val feels: Double?, val humidity: Int?, val code: Int, val isDay: Boolean,
    val wind: Double?, val windDir: Double?, val gust: Double?, val pressure: Double?, val cloud: Int?, val precip: Double?,
)

data class WeatherHour(val time: String, val temp: Double, val humidity: Int?, val code: Int, val wind: Double?, val windDir: Double?, val pop: Int?, val isDay: Boolean)

data class WeatherDay(val date: String, val code: Int, val max: Double, val min: Double, val pop: Int?, val windMax: Double?)

/** One forecast as received, with when and where it was fetched. */
class Weather(
    val fetchedAt: Long,
    val point: GeoPoint,
    val place: String,
    val utcOffsetSec: Int,
    val now: WeatherNow,
    val hours: List<WeatherHour>,
    val days: List<WeatherDay>,
) {
    /** Local "yyyy-MM-ddTHH:00" of the forecast place for [millis]. */
    fun hourKey(millis: Long): String {
        val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis + utcOffsetSec * 1000L }
        return String.format(
            Locale.US, "%04d-%02d-%02dT%02d:00",
            c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.HOUR_OF_DAY),
        )
    }

    /** The saved forecast for this hour (used when the phone is offline). */
    fun hourAt(millis: Long): WeatherHour? = hourKey(millis).let { k -> hours.firstOrNull { it.time == k } }

    /** The hours from now on. */
    fun hoursFrom(millis: Long, count: Int): List<WeatherHour> {
        val k = hourKey(millis)
        return hours.filter { it.time >= k }.take(count)
    }

    fun daysFrom(millis: Long): List<WeatherDay> {
        val today = hourKey(millis).substring(0, 10)
        return days.filter { it.date >= today }
    }
}

/**
 * Weather from Open-Meteo (free, no key). If that address is not reachable from the phone's
 * network, the same answer is asked through the app's own host (muhitban.ir/app/weather.php),
 * which works on networks that reach Iranian hosts more easily. The last answer is kept on the
 * phone, so with no internet the ranger still sees the latest forecast and its time.
 */
object WeatherService {
    private const val PREFS = "weather"
    private const val FIELDS =
        "current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
            "&hourly=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,precipitation_probability,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max" +
            "&timezone=auto&forecast_days=5&wind_speed_unit=kmh"

    private fun sources(p: GeoPoint): List<String> {
        val ll = String.format(Locale.US, "latitude=%.2f&longitude=%.2f", p.lat, p.lon) // ~1 km: enough for a forecast, not a ranger's exact spot
        return listOf(
            "https://api.open-meteo.com/v1/forecast?$ll&$FIELDS",
            "https://muhitban.ir/app/weather.php?$ll",
        )
    }

    private fun get(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 7_000; c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "Mohityar/1.0 (Android)")
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }.getOrNull()

    /** Fetches a fresh forecast and saves it; null when there is no internet (or no answer). */
    fun fetch(context: Context, p: GeoPoint, place: String): Weather? {
        for (u in sources(p)) {
            val body = get(u) ?: continue
            val w = runCatching { parse(JSONObject(body), System.currentTimeMillis(), p, place) }.getOrNull() ?: continue
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("json", body).putLong("at", w.fetchedAt)
                .putFloat("lat", p.lat.toFloat()).putFloat("lon", p.lon.toFloat()).putString("place", place)
                .apply()
            return w
        }
        return null
    }

    /** The last saved forecast, if any. */
    fun saved(context: Context): Weather? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val body = sp.getString("json", null) ?: return null
        return runCatching {
            parse(JSONObject(body), sp.getLong("at", 0L), GeoPoint(sp.getFloat("lat", 0f).toDouble(), sp.getFloat("lon", 0f).toDouble()), sp.getString("place", "").orEmpty())
        }.getOrNull()
    }

    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null
    private fun org.json.JSONArray?.dbl(i: Int): Double? = this?.let { if (i < length() && !isNull(i)) optDouble(i).takeIf { d -> !d.isNaN() } else null }

    private fun parse(o: JSONObject, at: Long, p: GeoPoint, place: String): Weather {
        val cur = o.getJSONObject("current")
        val now = WeatherNow(
            time = cur.optString("time"), temp = cur.getDouble("temperature_2m"), feels = cur.dbl("apparent_temperature"),
            humidity = cur.dbl("relative_humidity_2m")?.toInt(), code = cur.optInt("weather_code"), isDay = cur.optInt("is_day", 1) == 1,
            wind = cur.dbl("wind_speed_10m"), windDir = cur.dbl("wind_direction_10m"), gust = cur.dbl("wind_gusts_10m"),
            pressure = cur.dbl("pressure_msl"), cloud = cur.dbl("cloud_cover")?.toInt(), precip = cur.dbl("precipitation"),
        )
        val h = o.optJSONObject("hourly")
        val hours = buildList {
            val t = h?.optJSONArray("time") ?: return@buildList
            for (i in 0 until t.length()) {
                val temp = h.optJSONArray("temperature_2m").dbl(i) ?: continue
                add(
                    WeatherHour(
                        t.getString(i), temp, h.optJSONArray("relative_humidity_2m").dbl(i)?.toInt(), h.optJSONArray("weather_code").dbl(i)?.toInt() ?: 0,
                        h.optJSONArray("wind_speed_10m").dbl(i), h.optJSONArray("wind_direction_10m").dbl(i),
                        h.optJSONArray("precipitation_probability").dbl(i)?.toInt(), (h.optJSONArray("is_day").dbl(i) ?: 1.0) > 0.5,
                    ),
                )
            }
        }
        val d = o.optJSONObject("daily")
        val days = buildList {
            val t = d?.optJSONArray("time") ?: return@buildList
            for (i in 0 until t.length()) {
                val mx = d.optJSONArray("temperature_2m_max").dbl(i) ?: continue
                val mn = d.optJSONArray("temperature_2m_min").dbl(i) ?: continue
                add(
                    WeatherDay(
                        t.getString(i), d.optJSONArray("weather_code").dbl(i)?.toInt() ?: 0, mx, mn,
                        d.optJSONArray("precipitation_probability_max").dbl(i)?.toInt(), d.optJSONArray("wind_speed_10m_max").dbl(i),
                    ),
                )
            }
        }
        return Weather(at, p, place, o.optInt("utc_offset_seconds", 12_600), now, hours, days)
    }

    /** Persian name of a WMO weather code. */
    fun describe(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "آفتابی" else "صاف"
        1 -> if (isDay) "عمدتاً آفتابی" else "عمدتاً صاف"
        2 -> "نیمه‌ابری"
        3 -> "ابری"
        45, 48 -> "مه"
        51, 53 -> "نم‌نم باران"
        55 -> "نم‌نم باران پیوسته"
        56, 57 -> "نم‌نم باران یخ‌زده"
        61 -> "باران خفیف"
        63 -> "بارانی"
        65 -> "باران شدید"
        66, 67 -> "باران یخ‌زده"
        71 -> "برف خفیف"
        73 -> "برفی"
        75 -> "برف سنگین"
        77 -> "دانه‌های برف"
        80 -> "رگبار خفیف"
        81 -> "رگبار باران"
        82 -> "رگبار شدید"
        85 -> "رگبار برف"
        86 -> "کولاک و برف شدید"
        95 -> "رعد و برق"
        96, 99 -> "رعد و برق با تگرگ"
        else -> "—"
    }
}
