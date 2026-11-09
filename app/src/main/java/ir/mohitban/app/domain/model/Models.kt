package ir.mohitban.app.domain.model

import ir.mohitban.app.core.jalali.JalaliDate

enum class VehicleType(val title: String) {
    CAR("خودرو"),
    MOTORCYCLE("موتورسیکلت"),
}

enum class FuelType(val title: String, val unit: String, val unitShort: String, val short: String) {
    GASOLINE("بنزین", "لیتر", "لیتر", "بنزین"),
    CNG("گاز CNG", "کیلوگرم", "کیلو", "گاز"),
}

data class RangerProfile(
    val fullName: String,
    val fatherName: String,
    val nationalId: String,
    val mobile: String,
    val rank: String,
    val photoPath: String?,
    val province: String,
    val city: String,
    val protectedArea: String,
    val station: String,
    val grade: String = "",
    val hireJdn: Int? = null,
    val birthJdn: Int? = null,
)

data class Vehicle(
    val id: Long = 0,
    val type: VehicleType,
    val kind: String,
    val model: String,
    val year: String,
    val plate: Plate,
    val odometer: Long,
    val photoPath: String?,
) {
    /** "تویوتا هایلوکس" or, when the model is empty, the vehicle type. */
    val title: String get() = model.ifBlank { type.title }
}

/** Fuel prices in Rial. Editable in settings so a price change never needs a new APK. */
data class FuelRates(
    val gasoline: List<Long> = listOf(15_000L, 30_000L, 100_000L),
    val cng: Long = 6_568L,
)

/**
 * What the official form needs at the moment of the entry. Stored with each entry so that
 * later edits of the profile/vehicle do not change forms that were already printed.
 * The vehicle is shared between rangers, so the driver is whoever recorded the entry.
 */
data class EntrySnapshot(
    val driverName: String,
    val plate: Plate,
    val vehicleType: VehicleType,
    val vehicleModel: String,
    val province: String,
    val city: String,
    val protectedArea: String,
    val station: String,
)

data class FuelEntry(
    val id: Long = 0,
    val date: JalaliDate,
    val odometer: Long,
    val fuelType: FuelType,
    val amount: Double,
    val unitPrice: Long,
    val total: Long,
    val receiptPath: String?,
    val driverSignaturePath: String,
    val passengerSignaturePath: String?,
    val snapshot: EntrySnapshot,
    val createdAt: Long = System.currentTimeMillis(),
    val vehicleId: Long? = null,
    /** Set when the ranger edited the entry after saving it (shown only in the records list). */
    val editedAt: Long? = null,
)

data class QuarterSummary(
    val year: Int,
    val season: ir.mohitban.app.core.jalali.Season,
    val entries: List<FuelEntry>,
) {
    val count: Int get() = entries.size
    val gasolineLiters: Double get() = entries.filter { it.fuelType == FuelType.GASOLINE }.sumOf { it.amount }
    val cngKg: Double get() = entries.filter { it.fuelType == FuelType.CNG }.sumOf { it.amount }
    val gasolineTotal: Long get() = entries.filter { it.fuelType == FuelType.GASOLINE }.sumOf { it.total }
    val cngTotal: Long get() = entries.filter { it.fuelType == FuelType.CNG }.sumOf { it.total }
    val total: Long get() = entries.sumOf { it.total }
    val withReceipts: List<FuelEntry> get() = entries.filter { it.receiptPath != null }
    val firstOdometer: Long? get() = entries.minByOrNull { it.date.jdn * 10_000_000L + it.odometer }?.odometer
    val lastOdometer: Long? get() = entries.maxByOrNull { it.date.jdn * 10_000_000L + it.odometer }?.odometer
}

/** The official ranks (درجه‌ها) of the environment guard, lowest first, with their insignia. */
object Grades {
    val ALL = listOf(
        "کمک محیط‌بان", "محیط‌بان سوم", "محیط‌بان دوم", "محیط‌بان یکم", "سر محیط‌بان",
        "محیط‌دار سوم", "محیط‌دار دوم", "محیط‌دار یکم",
        "افسر محیط‌دار سوم", "افسر محیط‌دار دوم", "افسر محیط‌دار یکم", "ارشد محیط‌دار",
    )

    /** Years of service after which the rank goes up by one. */
    const val YEARS_PER_RANK = 4

    /**
     * The rank a ranger hired on [hireJdn] normally holds on [todayJdn]: the first rank at hiring and
     * one more every [YEARS_PER_RANK] full years, up to the highest. Null when the hire date is in the future.
     */
    fun suggest(hireJdn: Int, todayJdn: Int): String? {
        val years = fullYears(hireJdn, todayJdn)
        if (years < 0) return null
        return ALL[(years / YEARS_PER_RANK).coerceIn(0, ALL.lastIndex)]
    }

    /** Whole Jalali years between two day numbers, or -1 when [toJdn] is before [fromJdn]. */
    fun fullYears(fromJdn: Int, toJdn: Int): Int {
        val a = ir.mohitban.app.core.jalali.JalaliDate.fromJdn(fromJdn)
        val b = ir.mohitban.app.core.jalali.JalaliDate.fromJdn(toJdn)
        var years = b.year - a.year
        if (b.month < a.month || (b.month == a.month && b.day < a.day)) years--
        return if (toJdn < fromJdn) -1 else years
    }

    /** Index in [ALL] (0-based) or -1. */
    fun indexOf(grade: String): Int {
        // an early version spelled the first rank «کنک»; read it as «کمک»
        val g = grade.replace("کنک", "کمک").replace("\u200c", "").replace(" ", "")
        return ALL.indexOfFirst { it.replace("\u200c", "").replace(" ", "") == g }
    }
}

/** Whole years and months between two Jalali day numbers («۱۲ سال و ۴ ماه»). */
fun serviceText(fromJdn: Int, toJdn: Int): String {
    val a = ir.mohitban.app.core.jalali.JalaliDate.fromJdn(fromJdn)
    val b = ir.mohitban.app.core.jalali.JalaliDate.fromJdn(toJdn)
    var months = (b.year - a.year) * 12 + (b.month - a.month)
    if (b.day < a.day) months--
    if (months < 0) return "—"
    val y = months / 12; val m = months % 12
    return when {
        y == 0 && m == 0 -> "کمتر از یک ماه"
        y == 0 -> "$m ماه"
        m == 0 -> "$y سال"
        else -> "$y سال و $m ماه"
    }
}
