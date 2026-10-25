package ir.mohitban.app.core.text

import kotlin.math.abs
import kotlin.math.roundToLong

private const val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
private const val AR_DIGITS = "٠١٢٣٤٥٦٧٨٩"

/** Converts every Latin digit in the text to a Persian digit. */
fun String.fa(): String {
    val sb = StringBuilder(length)
    for (ch in this) sb.append(if (ch in '0'..'9') FA_DIGITS[ch - '0'] else ch)
    return sb.toString()
}

fun Int.fa(): String = toString().fa()
fun Long.fa(): String = toString().fa()

/** Converts Persian/Arabic digits to Latin digits (used for every user input before storing). */
fun String.toLatinDigits(): String {
    val sb = StringBuilder(length)
    for (ch in this) {
        val p = FA_DIGITS.indexOf(ch)
        val a = AR_DIGITS.indexOf(ch)
        sb.append(
            when {
                p >= 0 -> '0' + p
                a >= 0 -> '0' + a
                ch == '٫' -> '.'
                else -> ch
            },
        )
    }
    return sb.toString()
}

/** Keeps only Latin digits (after normalising Persian/Arabic ones). */
fun String.digitsOnly(): String = toLatinDigits().filter { it in '0'..'9' }

/** 1234567 -> "1,234,567" (Latin digits). */
fun groupThousands(value: Long): String {
    val negative = value < 0
    val s = abs(value).toString()
    val sb = StringBuilder()
    for (i in s.indices) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(s[i])
    }
    return if (negative) "-$sb" else sb.toString()
}

/** 1234567 -> "۱,۲۳۴,۵۶۷" */
fun Long.rial(): String = groupThousands(this).fa()
fun Int.rial(): String = toLong().rial()

/** Formats an amount (litres / kilograms) without useless decimals: 30.0 -> "۳۰", 12.5 -> "۱۲.۵" */
fun Double.amountText(): String {
    val rounded = (this * 100).roundToLong() / 100.0
    val txt = if (rounded == rounded.toLong().toDouble()) {
        groupThousands(rounded.toLong())
    } else {
        val whole = groupThousands(rounded.toLong())
        val frac = ((abs(rounded) - abs(rounded.toLong().toDouble())) * 100).roundToLong()
            .toString().padStart(2, '0').trimEnd('0')
        "$whole.$frac"
    }
    return txt.fa().replace('.', '٫')
}

/** Persian words for an integer amount, e.g. for "جمع کل به حروف". */
object PersianWords {
    private val ones = listOf("", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه")
    private val teens = listOf("ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده")
    private val tens = listOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private val hundreds = listOf("", "یکصد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
    private val scales = listOf("", "هزار", "میلیون", "میلیارد", "هزار میلیارد")

    private fun threeDigits(n: Int): String {
        val parts = mutableListOf<String>()
        val h = n / 100
        val rest = n % 100
        if (h > 0) parts += hundreds[h]
        when {
            rest in 10..19 -> parts += teens[rest - 10]
            else -> {
                val t = rest / 10
                val o = rest % 10
                if (t > 0) parts += tens[t]
                if (o > 0) parts += ones[o]
            }
        }
        return parts.joinToString(" و ")
    }

    fun of(value: Long): String {
        if (value == 0L) return "صفر"
        if (value < 0) return "منفی " + of(-value)
        val groups = mutableListOf<Int>()
        var v = value
        while (v > 0) {
            groups += (v % 1000).toInt()
            v /= 1000
        }
        val words = mutableListOf<String>()
        for (i in groups.indices.reversed()) {
            val g = groups[i]
            if (g == 0) continue
            val w = threeDigits(g)
            words += if (i == 0) w else "$w ${scales.getOrElse(i) { "" }}".trim()
        }
        return words.joinToString(" و ")
    }
}
