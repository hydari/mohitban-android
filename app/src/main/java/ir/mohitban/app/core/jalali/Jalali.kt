package ir.mohitban.app.core.jalali

import java.util.Calendar
import java.util.Locale

/**
 * Iranian (Jalali / Solar Hijri) calendar.
 * Port of the well-tested jalaali-js algorithm (Borkowski), valid for years -61..3177.
 * Dates are stored in the database as Julian Day Numbers (JDN): a single Int that
 * sorts correctly and converts both ways without any time-zone issues.
 */
data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {

    val jdn: Int get() = Jalali.toJdn(year, month, day)

    /** 0 = شنبه … 6 = جمعه */
    val dayOfWeek: Int get() = Jalali.dayOfWeek(jdn)

    val season: Season get() = Season.ofMonth(month)

    fun plusDays(days: Int): JalaliDate = Jalali.fromJdn(jdn + days)

    fun plusMonths(months: Int): JalaliDate {
        val total = (year * 12 + (month - 1)) + months
        val y = fdiv(total, 12)
        val m = fmod(total, 12) + 1
        val d = minOf(day, Jalali.monthLength(y, m))
        return JalaliDate(y, m, d)
    }

    override fun compareTo(other: JalaliDate): Int = jdn.compareTo(other.jdn)

    /** 1405/08/03 */
    fun format(): String = String.format(Locale.US, "%04d/%02d/%02d", year, month, day)

    /** یکشنبه ۳ آبان ۱۴۰۵ (with Latin digits; pass through [ir.mohitban.app.core.text.fa] for display) */
    fun formatLong(withWeekday: Boolean = true): String {
        val base = "$day ${Jalali.MONTH_NAMES[month - 1]} $year"
        return if (withWeekday) "${Jalali.WEEKDAY_NAMES[dayOfWeek]} $base" else base
    }

    companion object {
        fun today(): JalaliDate = Jalali.today()
        fun fromJdn(jdn: Int): JalaliDate = Jalali.fromJdn(jdn)
    }
}

enum class Season(val index: Int, val title: String, val months: String, val firstMonth: Int) {
    SPRING(1, "بهار", "فروردین تا خرداد", 1),
    SUMMER(2, "تابستان", "تیر تا شهریور", 4),
    AUTUMN(3, "پاییز", "مهر تا آذر", 7),
    WINTER(4, "زمستان", "دی تا اسفند", 10);

    val lastMonth: Int get() = firstMonth + 2

    /** Inclusive JDN range of this season in the given Jalali year. */
    fun jdnRange(year: Int): IntRange {
        val start = Jalali.toJdn(year, firstMonth, 1)
        val end = Jalali.toJdn(year, lastMonth, Jalali.monthLength(year, lastMonth))
        return start..end
    }

    companion object {
        fun ofMonth(month: Int): Season = entries[(month - 1) / 3]
    }
}

object Jalali {

    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    /** Index 0 = شنبه */
    val WEEKDAY_NAMES = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
    val WEEKDAY_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )

    private data class Cal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int, withoutLeap: Boolean): Cal {
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        require(jy >= jp && jy < BREAKS[BREAKS.size - 1]) { "Invalid Jalali year $jy" }
        var jump = 0
        for (i in 1 until BREAKS.size) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (withoutLeap) return Cal(0, gy, march)
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return Cal(leap, gy, march)
    }

    fun isLeapYear(jy: Int): Boolean = jalCal(jy, false).leap == 0

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        isLeapYear(jy) -> 30
        else -> 29
    }

    fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Int {
        var d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4 +
            (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408
        d = d - (((gy + 100100 + (gm - 8) / 6) / 100) * 3) / 4 + 752
        return d
    }

    /** Returns (year, month, day) in the Gregorian calendar. */
    fun jdnToGregorian(jdn: Int): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139361631
        j += (((4 * jdn + 183187720) / 146097) * 3) / 4 * 4 - 3908
        val i = ((j % 1461) / 4) * 5 + 308
        val gd = (i % 153) / 5 + 1
        val gm = ((i / 153) % 12) + 1
        val gy = j / 1461 - 100100 + (8 - gm) / 6
        return Triple(gy, gm, gd)
    }

    fun toJdn(jy: Int, jm: Int, jd: Int): Int {
        val r = jalCal(jy, true)
        return gregorianToJdn(r.gy, 3, r.march) + (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1
    }

    fun fromJdn(jdn: Int): JalaliDate {
        val gy = jdnToGregorian(jdn).first
        var jy = gy - 621
        val r = jalCal(jy, false)
        val jdn1f = gregorianToJdn(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                return JalaliDate(jy, 1 + k / 31, (k % 31) + 1)
            } else {
                k -= 186
            }
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JalaliDate(jy, 7 + k / 30, (k % 30) + 1)
    }

    fun fromGregorian(gy: Int, gm: Int, gd: Int): JalaliDate = fromJdn(gregorianToJdn(gy, gm, gd))

    fun today(): JalaliDate {
        val c = Calendar.getInstance()
        return fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    /** 0 = شنبه … 6 = جمعه. (JDN mod 7: 0 = Monday) */
    fun dayOfWeek(jdn: Int): Int = fmod(jdn + 2, 7)

    fun isValid(jy: Int, jm: Int, jd: Int): Boolean =
        jy in -60..3176 && jm in 1..12 && jd >= 1 && jd <= monthLength(jy, jm)
}

/** floor division / modulo (Math.floorDiv / floorMod need Android 7+). */
internal fun fdiv(a: Int, b: Int): Int {
    val q = a / b
    return if ((a % b != 0) && ((a xor b) < 0)) q - 1 else q
}

internal fun fmod(a: Int, b: Int): Int = a - fdiv(a, b) * b
