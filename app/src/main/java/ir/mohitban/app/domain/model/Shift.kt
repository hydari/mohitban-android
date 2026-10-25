package ir.mohitban.app.domain.model

import ir.mohitban.app.core.jalali.fmod

/**
 * A ranger's rotating work pattern: [work] days on duty followed by [rest] days off, repeating
 * from [startJdn] (the first duty day of a cycle). [overrides] holds days the ranger changed by
 * hand (true = shift, false = rest).
 */
data class ShiftPattern(
    val work: Int,
    val rest: Int,
    val startJdn: Int,
    val overrides: Map<Int, Boolean> = emptyMap(),
) {
    val cycle: Int get() = (work + rest).coerceAtLeast(1)

    /** Shift by the pattern alone, ignoring manual changes. */
    fun patternShift(jdn: Int): Boolean = fmod(jdn - startJdn, cycle) < work

    fun isShift(jdn: Int): Boolean = overrides[jdn] ?: patternShift(jdn)

    fun isOverridden(jdn: Int): Boolean = overrides.containsKey(jdn)

    /** Today's position: on duty (day x of a run of n) or resting (n days to the next duty day). */
    fun status(today: Int): ShiftStatus {
        if (isShift(today)) {
            var first = today
            while (today - first < 400 && isShift(first - 1)) first--
            var last = today
            while (last - today < 400 && isShift(last + 1)) last++
            return ShiftStatus.OnDuty(dayNumber = today - first + 1, length = last - first + 1, daysLeft = last - today)
        }
        var d = 1
        while (d <= 400 && !isShift(today + d)) d++
        return ShiftStatus.Resting(daysToNext = if (d > 400) null else d)
    }

    /** Toggles one day against the pattern; going back to the pattern's value removes the override. */
    fun toggle(jdn: Int): ShiftPattern {
        val target = !isShift(jdn)
        val next = overrides.toMutableMap()
        if (target == patternShift(jdn)) next.remove(jdn) else next[jdn] = target
        return copy(overrides = next)
    }

    fun encodeOverrides(): String = overrides.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${if (it.value) 1 else 0}" }

    companion object {
        val PRESETS = listOf(2 to 2, 3 to 3, 5 to 5, 2 to 4, 3 to 6)

        fun decodeOverrides(text: String?): Map<Int, Boolean> =
            text.orEmpty().split(",").mapNotNull { part ->
                val (k, v) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
                val jdn = k.trim().toIntOrNull() ?: return@mapNotNull null
                jdn to (v.trim() == "1")
            }.toMap()
    }
}

sealed interface ShiftStatus {
    data class OnDuty(val dayNumber: Int, val length: Int, val daysLeft: Int) : ShiftStatus
    data class Resting(val daysToNext: Int?) : ShiftStatus
}
