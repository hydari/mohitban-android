package ir.mohitban.app.gasht

import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import ir.mohitban.app.data.local.PatrolLogEntity

/** Ways of patrolling. */
object PatrolModes {
    val ALL = listOf("پیاده", "خودرو", "موتورسیکلت", "کمین / ایستگاهی")
}

/** What a patrol found or did; stored by key so titles can change later. */
enum class Finding(val title: String, val problem: Boolean) {
    HUNT("شکار غیرمجاز / آثار شکار", true),
    TRAP("تله، دام یا سم", true),
    GRAZE("چرای غیرمجاز دام", true),
    CUT("قطع درخت / برداشت گیاه", true),
    FIRE("آتش‌سوزی", true),
    BUILD("ساخت‌وساز / تخریب زیستگاه", true),
    MINE("برداشت شن، خاک یا معدن", true),
    WASTE("زباله و آلودگی", true),
    WILDLIFE("مشاهده حیات‌وحش", false),
    TALK("آموزش و گفت‌وگو با بهره‌برداران", false),
    NONE("بدون مورد خاص", false);

    companion object {
        fun parse(s: String): Set<Finding> = s.split(',').mapNotNull { k -> entries.firstOrNull { it.name == k.trim() } }.toSet()
        fun encode(set: Set<Finding>): String = set.sortedBy { it.ordinal }.joinToString(",") { it.name }
    }
}

/** A report period: one Jalali month, or a whole year when [month] is null. */
data class PatrolPeriod(val year: Int, val month: Int?) {
    val range: IntRange get() = if (month != null) {
        Jalali.toJdn(year, month, 1)..Jalali.toJdn(year, month, Jalali.monthLength(year, month))
    } else {
        Jalali.toJdn(year, 1, 1)..Jalali.toJdn(year, 12, Jalali.monthLength(year, 12))
    }
    val title: String get() = if (month != null) "${Jalali.MONTH_NAMES[month - 1]} $year" else "سال $year"
    val days: Int get() = range.last - range.first + 1

    fun previous() = if (month == null) copy(year = year - 1) else if (month == 1) PatrolPeriod(year - 1, 12) else copy(month = month - 1)
    fun next() = if (month == null) copy(year = year + 1) else if (month == 12) PatrolPeriod(year + 1, 1) else copy(month = month + 1)
}

/** Minutes of a patrol (end before start = it crossed midnight). */
fun PatrolLogEntity.minutes(): Int? {
    val s = startMin ?: return null
    val e = endMin ?: return null
    return if (e >= s) e - s else e + 1440 - s
}

/** Started at night (19:00–05:00). */
fun PatrolLogEntity.isNight(): Boolean = startMin?.let { it >= 19 * 60 || it < 5 * 60 } ?: false

class ZoneCoverage(val zone: String, val count: Int, val minutes: Int, val km: Double, val lastJdn: Int?, val problems: Int)

/** Everything the monthly / yearly report shows. */
class PatrolStats(
    val period: PatrolPeriod,
    val logs: List<PatrolLogEntity>,
    /** Protected areas with patrols, most patrolled first. */
    val zones: List<ZoneCoverage>,
    /** Known protected areas with no patrol in the period. */
    val unvisited: List<String>,
    val findings: List<Pair<Finding, Int>>,
    val modes: List<Pair<String, Int>>,
) {
    val count get() = logs.size
    val minutes get() = logs.sumOf { it.minutes() ?: 0 }
    val km get() = logs.sumOf { it.distanceKm ?: 0.0 }
    val violations get() = logs.sumOf { it.violations }
    val nights get() = logs.count { it.isNight() }
    val activeDays get() = logs.map { it.jdn }.distinct().size

    companion object {
        fun of(all: List<PatrolLogEntity>, period: PatrolPeriod, knownZones: List<String>): PatrolStats {
            val r = period.range
            val logs = all.filter { it.jdn in r }.sortedWith(compareBy({ it.jdn }, { it.startMin ?: 0 }))
            val zones = logs.groupBy { it.zone.trim().ifEmpty { "نامشخص" } }.map { (z, l) ->
                ZoneCoverage(
                    z, l.size, l.sumOf { it.minutes() ?: 0 }, l.sumOf { it.distanceKm ?: 0.0 }, l.maxOf { it.jdn },
                    l.sumOf { p -> Finding.parse(p.findings).count { it.problem } },
                )
            }.sortedWith(compareByDescending<ZoneCoverage> { it.count }.thenByDescending { it.minutes })
            val visited = zones.map { it.zone }.toSet()
            val unvisited = knownZones.filter { it !in visited }
            val findings = Finding.entries.map { f -> f to logs.count { f in Finding.parse(it.findings) } }.filter { it.second > 0 }.sortedByDescending { it.second }
            val modes = logs.groupingBy { it.mode }.eachCount().toList().sortedByDescending { it.second }
            return PatrolStats(period, logs, zones, unvisited, findings, modes)
        }
    }
}

/** Short duration text such as «۳ ساعت و ۲۰ دقیقه» (digits are made Persian by the caller). */
fun minutesText(m: Int): String = when {
    m <= 0 -> "0 دقیقه"
    m < 60 -> "$m دقیقه"
    m % 60 == 0 -> "${m / 60} ساعت"
    else -> "${m / 60} ساعت و ${m % 60} دقیقه"
}

fun clockText(m: Int?): String = m?.let { String.format(java.util.Locale.US, "%02d:%02d", (it / 60) % 24, it % 60) } ?: "—"

/** The protected area most of a track's points fall in (null when outside all of them). */
fun dominantZone(points: List<GeoPoint>, zones: List<Zone>): Zone? {
    if (points.isEmpty() || zones.isEmpty()) return null
    val step = (points.size / 60).coerceAtLeast(1)
    val votes = HashMap<Zone, Int>()
    var i = 0
    while (i < points.size) {
        ZoneStore.status(zones, points[i]).inside?.let { votes[it] = (votes[it] ?: 0) + 1 }
        i += step
    }
    return votes.maxByOrNull { it.value }?.key
}

/** A Jalali date from epoch milliseconds (local time). */
fun jalaliFromMillis(ms: Long): JalaliDate {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return Jalali.fromGregorian(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}

fun minuteOfDay(ms: Long): Int {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}
