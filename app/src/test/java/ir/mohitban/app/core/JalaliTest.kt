package ir.mohitban.app.core

import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.core.jalali.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JalaliTest {
    @Test fun nowruzDates() {
        assertEquals(JalaliDate(1403, 1, 1), Jalali.fromGregorian(2024, 3, 20))
        assertEquals(JalaliDate(1404, 1, 1), Jalali.fromGregorian(2025, 3, 21))
    }

    @Test fun leapYears() {
        assertTrue(Jalali.isLeapYear(1403))
        assertFalse(Jalali.isLeapYear(1404))
        assertEquals(30, Jalali.monthLength(1403, 12))
        assertEquals(29, Jalali.monthLength(1404, 12))
    }

    @Test fun monthLengths() {
        for (m in 1..6) assertEquals(31, Jalali.monthLength(1404, m))
        for (m in 7..11) assertEquals(30, Jalali.monthLength(1404, m))
    }

    @Test fun jdnRoundTripForEveryDay() {
        for (y in 1380..1420) for (m in 1..12) for (d in 1..Jalali.monthLength(y, m)) {
            val jdn = Jalali.toJdn(y, m, d)
            assertEquals(JalaliDate(y, m, d), Jalali.fromJdn(jdn))
        }
    }

    @Test fun consecutiveDaysAreOneJdnApart() {
        assertEquals(Jalali.toJdn(1403, 12, 30) + 1, Jalali.toJdn(1404, 1, 1))
        assertEquals(Jalali.toJdn(1404, 12, 29) + 1, Jalali.toJdn(1405, 1, 1))
    }

    @Test fun plusDays() {
        assertEquals(JalaliDate(1404, 1, 1), JalaliDate(1403, 12, 30).plusDays(1))
    }

    @Test fun damagedDatesDoNotCrash() {
        // a date from a damaged or tampered backup is pulled to the nearest valid one
        assertEquals(JalaliDate(3000, 12, 29), Jalali.fromJdn(Int.MAX_VALUE))
        assertEquals(JalaliDate(1, 1, 1), Jalali.fromJdn(Int.MIN_VALUE))
        assertEquals(Jalali.toJdn(3000, 1, 1), Jalali.toJdn(99_999, 1, 1))
        assertEquals(Jalali.toJdn(1, 1, 1), Jalali.toJdn(-500, 1, 1))
        Jalali.isLeapYear(99_999)
        Jalali.monthLength(-500, 12)
    }

    @Test fun edgeYearsRoundTrip() {
        for (y in listOf(1, 2, 39, 2980, 3000)) for (m in 1..12) for (d in 1..29) {
            assertEquals(JalaliDate(y, m, d), Jalali.fromJdn(Jalali.toJdn(y, m, d)))
        }
    }
}
