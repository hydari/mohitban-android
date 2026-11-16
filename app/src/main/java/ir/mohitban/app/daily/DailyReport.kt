package ir.mohitban.app.daily

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One species seen during the patrol and how many. */
data class WildlifeCount(val species: String, val count: Int)

/** How the ranger patrolled. */
enum class Transport(val title: String) { FOOT("پیاده"), VEHICLE("با وسیله‌ی نقلیه") }

/**
 * The ranger's daily patrol report (گزارش روزانه گشت و پایش). The ranger's own details and the vehicle are copied in
 * when the report is saved, so an old report still shows what was true that day even after the profile changes.
 */
data class DailyReport(
    val id: Long = 0,
    val jdn: Int,
    // the ranger, as on the day of the report
    val name: String,
    val grade: String,
    val rank: String,
    val nationalId: String,
    val station: String,
    val area: String,
    val city: String,
    val province: String,
    // how
    val transport: Transport,
    val vehicleId: Long? = null,
    val vehicleType: String = "",
    val vehicleTitle: String = "",
    /** The plate in [ir.mohitban.app.domain.model.Plate.encode] form; empty on foot. */
    val plate: String = "",
    // what
    val areas: String,
    val wildlife: List<WildlifeCount> = emptyList(),
    val destruction: Boolean = false,
    val poaching: Boolean = false,
    val violationText: String = "",
    val description: String = "",
    val events: String = "",
    val signaturePath: String?,
    val createdAt: Long,
    val editedAt: Long? = null,
) {
    val hasViolation: Boolean get() = destruction || poaching
    val wildlifeTotal: Int get() = wildlife.sumOf { it.count }
}

/**
 * The daily reports kept on the phone, in one JSON file next to the database (the signatures are pictures in the
 * media folder, so the backup carries them as well).
 */
object DailyReportStore {
    private const val FILE = "daily_reports.json"

    /** Bumped on every change, so screens reload. */
    val version = MutableStateFlow(0)

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    @Synchronized
    fun all(context: Context): List<DailyReport> = runCatching {
        val f = file(context)
        if (!f.exists()) return emptyList()
        fromJsonArray(JSONArray(f.readText(Charsets.UTF_8)))
    }.getOrDefault(emptyList()).sortedWith(compareByDescending<DailyReport> { it.jdn }.thenByDescending { it.createdAt })

    fun get(context: Context, id: Long): DailyReport? = all(context).firstOrNull { it.id == id }

    /** Saves a new report (id 0) or replaces the one with the same id; returns its id. */
    @Synchronized
    fun save(context: Context, report: DailyReport): Long {
        val list = all(context).toMutableList()
        val id = if (report.id != 0L) report.id else (list.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(System.currentTimeMillis()) + 1
        val r = report.copy(id = id)
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list[i] = r else list += r
        write(context, list)
        return id
    }

    @Synchronized
    fun delete(context: Context, id: Long): DailyReport? {
        val list = all(context)
        val gone = list.firstOrNull { it.id == id } ?: return null
        write(context, list.filter { it.id != id })
        return gone
    }

    /** Used by the backup restore. */
    @Synchronized
    fun replaceAll(context: Context, list: List<DailyReport>) = write(context, list)

    private fun write(context: Context, list: List<DailyReport>) {
        val f = file(context)
        val tmp = File(f.parentFile, "$FILE.tmp")
        tmp.writeText(toJsonArray(list).toString(), Charsets.UTF_8)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        version.value++
    }

    // ------------------------------------------------------------------ json

    fun toJsonArray(list: List<DailyReport>): JSONArray = JSONArray().apply { list.forEach { put(toJson(it)) } }

    fun fromJsonArray(arr: JSONArray): List<DailyReport> =
        (0 until arr.length()).mapNotNull { runCatching { fromJson(arr.getJSONObject(it)) }.getOrNull() }

    private fun toJson(r: DailyReport) = JSONObject().apply {
        put("id", r.id); put("jdn", r.jdn)
        put("name", r.name); put("grade", r.grade); put("rank", r.rank); put("nationalId", r.nationalId)
        put("station", r.station); put("area", r.area); put("city", r.city); put("province", r.province)
        put("transport", r.transport.name); put("vehicleId", r.vehicleId ?: JSONObject.NULL)
        put("vehicleType", r.vehicleType); put("vehicleTitle", r.vehicleTitle); put("plate", r.plate)
        put("areas", r.areas)
        put("wildlife", JSONArray().apply { r.wildlife.forEach { put(JSONObject().put("s", it.species).put("n", it.count)) } })
        put("destruction", r.destruction); put("poaching", r.poaching); put("violationText", r.violationText)
        put("description", r.description); put("events", r.events)
        put("signaturePath", r.signaturePath ?: JSONObject.NULL)
        put("createdAt", r.createdAt); put("editedAt", r.editedAt ?: JSONObject.NULL)
    }

    private fun JSONObject.strOrNull(k: String) = if (!has(k) || isNull(k)) null else optString(k)
    private fun JSONObject.longOrNull(k: String) = if (!has(k) || isNull(k)) null else optLong(k)

    private fun fromJson(o: JSONObject): DailyReport {
        val w = o.optJSONArray("wildlife")
        return DailyReport(
            id = o.getLong("id"), jdn = o.getInt("jdn"),
            name = o.optString("name"), grade = o.optString("grade"), rank = o.optString("rank"), nationalId = o.optString("nationalId"),
            station = o.optString("station"), area = o.optString("area"), city = o.optString("city"), province = o.optString("province"),
            transport = runCatching { Transport.valueOf(o.optString("transport")) }.getOrDefault(Transport.FOOT),
            vehicleId = o.longOrNull("vehicleId"), vehicleType = o.optString("vehicleType"), vehicleTitle = o.optString("vehicleTitle"),
            plate = o.optString("plate"),
            areas = o.optString("areas"),
            wildlife = if (w == null) emptyList() else (0 until w.length()).map { i ->
                val x = w.getJSONObject(i); WildlifeCount(x.optString("s"), x.optInt("n").coerceAtLeast(0))
            },
            destruction = o.optBoolean("destruction"), poaching = o.optBoolean("poaching"), violationText = o.optString("violationText"),
            description = o.optString("description"), events = o.optString("events"),
            signaturePath = o.strOrNull("signaturePath"),
            createdAt = o.optLong("createdAt"), editedAt = o.longOrNull("editedAt"),
        )
    }

    /** Species a ranger in Iran often records; the form also takes any other name. */
    val COMMON_SPECIES = listOf(
        "کل و بز", "قوچ و میش", "آهو", "جبیر", "مرال", "شوکا", "گراز", "پلنگ", "یوزپلنگ", "گرگ", "خرس قهوه‌ای",
        "کفتار", "روباه", "شغال", "گربه وحشی", "سیاه‌گوش", "خرگوش", "تشی", "کبک", "تیهو", "هوبره", "قرقاول",
        "عقاب طلایی", "هما", "دال", "شاهین", "جغد", "درنا", "فلامینگو", "پلیکان", "اردک", "غاز", "حواصیل",
        "لک‌لک", "باقرقره", "لاک‌پشت", "مار", "ماهی",
    )
}
