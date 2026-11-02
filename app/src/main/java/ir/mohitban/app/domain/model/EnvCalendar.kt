package ir.mohitban.app.domain.model

import ir.mohitban.app.core.jalali.Jalali

/** One environmental day of the year. */
data class Occasion(val title: String, val national: Boolean)

/**
 * Environmental calendar of Iran and the world. National days are fixed Jalali dates; world
 * days are fixed Gregorian dates, so they fall on the right Jalali day every year.
 */
object EnvCalendar {
    // Jalali month to (day to title)
    private val NATIONAL: Map<Pair<Int, Int>, String> = mapOf(
        (1 to 13) to "روز طبیعت",
        (2 to 10) to "روز ملی خلیج فارس",
        (8 to 8) to "روز ملی محیط‌بان",
        (10 to 29) to "روز ملی هوای پاک",
        (12 to 15) to "روز درختکاری و آغاز هفته‌ی منابع طبیعی",
        (12 to 22) to "پایان هفته‌ی منابع طبیعی",
    )

    // Gregorian month to (day to title)
    private val WORLD: Map<Pair<Int, Int>, String> = mapOf(
        (2 to 2) to "روز جهانی تالاب‌ها",
        (3 to 3) to "روز جهانی حیات‌وحش",
        (3 to 20) to "روز جهانی گنجشک",
        (3 to 21) to "روز جهانی جنگل",
        (3 to 22) to "روز جهانی آب",
        (4 to 22) to "روز جهانی زمین پاک",
        (5 to 20) to "روز جهانی زنبور عسل",
        (5 to 22) to "روز جهانی تنوع زیستی",
        (6 to 5) to "روز جهانی محیط زیست",
        (6 to 8) to "روز جهانی اقیانوس‌ها",
        (6 to 17) to "روز جهانی مقابله با بیابان‌زایی",
        (7 to 29) to "روز جهانی ببر",
        (7 to 31) to "روز جهانی محیط‌بان",
        (8 to 31) to "روز ملی حمایت از یوزپلنگ ایرانی",
        (9 to 7) to "روز جهانی هوای پاک برای آسمان آبی",
        (9 to 16) to "روز جهانی حفاظت از لایه‌ی اوزون",
        (10 to 4) to "روز جهانی حیوانات",
        (10 to 23) to "روز جهانی پلنگ برفی",
        (12 to 4) to "روز جهانی یوزپلنگ",
        (12 to 5) to "روز جهانی خاک",
        (12 to 11) to "روز جهانی کوهستان",
    )

    /** The occasions of one day (Jalali day number). */
    fun on(jdn: Int): List<Occasion> {
        val j = Jalali.fromJdn(jdn)
        val (_, gm, gd) = Jalali.jdnToGregorian(jdn)
        return listOfNotNull(
            NATIONAL[j.month to j.day]?.let { Occasion(it, true) },
            WORLD[gm to gd]?.let { Occasion(it, it.contains("یوزپلنگ ایرانی")) },
        )
    }
}
