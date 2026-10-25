package ir.mohitban.app.domain.model

/**
 * Iranian red (government / administrative) licence plates.
 *
 * Car:        [12] [الف] [345] | ایران [67]
 * Motorcycle: top row 2–5 digits, bottom row 4–6 digits.
 * Digits are always stored as Latin digits; the UI shows Persian digits.
 */
sealed interface Plate {

    data class Car(
        val first: String,
        val letter: String,
        val middle: String,
        val iranCode: String,
    ) : Plate

    data class Motorcycle(
        val top: String,
        val bottom: String,
    ) : Plate

    /** Single-line text for tables and reports, e.g. "12 الف 345 - ایران 67". */
    fun displayText(): String = when (this) {
        is Car -> "$first $letter $middle - ایران $iranCode"
        is Motorcycle -> "$top - $bottom"
    }

    fun encode(): String = when (this) {
        is Car -> listOf("C", first, letter, middle, iranCode).joinToString(SEP)
        is Motorcycle -> listOf("M", top, bottom).joinToString(SEP)
    }

    companion object {
        private const val SEP = "|"

        val LETTERS = listOf(
            "الف", "ب", "پ", "ت", "ث", "ج", "د", "ز", "س", "ش", "ص", "ط",
            "ع", "ف", "ق", "ک", "گ", "ل", "م", "ن", "و", "هـ", "ی", "D", "S",
        )

        fun decode(raw: String?): Plate? {
            if (raw.isNullOrBlank()) return null
            val p = raw.split(SEP)
            return when (p.firstOrNull()) {
                "C" -> if (p.size == 5) Car(p[1], p[2], p[3], p[4]) else null
                "M" -> if (p.size == 3) Motorcycle(p[1], p[2]) else null
                else -> null
            }
        }
    }
}

object PlateRules {
    const val MOTO_TOP_MIN = 2
    const val MOTO_TOP_MAX = 5
    const val MOTO_BOTTOM_MIN = 4
    const val MOTO_BOTTOM_MAX = 6

    private fun digits(s: String) = s.isNotEmpty() && s.all { it in '0'..'9' }

    /** Returns null when valid, otherwise a Persian error message. */
    fun validate(plate: Plate): String? = when (plate) {
        is Plate.Car -> when {
            !digits(plate.first) || plate.first.length != 2 -> "دو رقم سمت چپ پلاک را کامل وارد کنید"
            plate.first.startsWith("0") -> "دو رقم سمت چپ نمی‌تواند با صفر شروع شود"
            plate.letter !in Plate.LETTERS -> "حرف پلاک را انتخاب کنید"
            !digits(plate.middle) || plate.middle.length != 3 -> "سه رقم وسط پلاک را کامل وارد کنید"
            plate.middle.startsWith("0") -> "سه رقم وسط نمی‌تواند با صفر شروع شود"
            !digits(plate.iranCode) || plate.iranCode.length != 2 -> "کد دو رقمی ایران را وارد کنید"
            else -> null
        }
        is Plate.Motorcycle -> when {
            !digits(plate.top) -> "ردیف بالای پلاک را وارد کنید"
            plate.top.length < MOTO_TOP_MIN -> "ردیف بالا دست‌کم ۲ رقم است"
            plate.top.length > MOTO_TOP_MAX -> "ردیف بالا حداکثر ۵ رقم است"
            !digits(plate.bottom) -> "ردیف پایین پلاک را وارد کنید"
            plate.bottom.length < MOTO_BOTTOM_MIN -> "ردیف پایین دست‌کم ۴ رقم است"
            plate.bottom.length > MOTO_BOTTOM_MAX -> "ردیف پایین حداکثر ۶ رقم است"
            else -> null
        }
    }
}
