package ir.mohitban.app.domain

import ir.mohitban.app.core.jalali.Jalali
import ir.mohitban.app.domain.model.Grades
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradesTest {
    private fun jdn(y: Int, m: Int, d: Int) = Jalali.toJdn(y, m, d)

    @Test fun startsAtThirdRanger() {
        assertEquals("محیط‌بان سوم", Grades.suggest(jdn(1400, 5, 10), jdn(1400, 5, 10)))
    }

    @Test fun stillThirdRangerJustBeforeFourYears() {
        assertEquals("محیط‌بان سوم", Grades.suggest(jdn(1400, 5, 10), jdn(1404, 5, 9)))
    }

    @Test fun nextRankAfterFourYears() {
        assertEquals("محیط‌بان دوم", Grades.suggest(jdn(1400, 5, 10), jdn(1404, 5, 10)))
        assertEquals("محیط‌بان یکم", Grades.suggest(jdn(1400, 5, 10), jdn(1408, 5, 10)))
    }

    @Test fun neverGoesPastTheHighestRank() {
        assertEquals(Grades.ALL.last(), Grades.suggest(jdn(1300, 1, 1), jdn(1405, 1, 1)))
    }

    @Test fun hireDateInTheFutureGivesNothing() {
        assertNull(Grades.suggest(jdn(1405, 1, 1), jdn(1404, 1, 1)))
    }

    @Test fun fullYearsCountsWholeYearsOnly() {
        assertEquals(0, Grades.fullYears(jdn(1403, 6, 15), jdn(1403, 6, 20)))
        assertEquals(1, Grades.fullYears(jdn(1403, 6, 15), jdn(1404, 6, 15)))
        assertEquals(0, Grades.fullYears(jdn(1403, 6, 15), jdn(1404, 6, 14)))
        assertEquals(-1, Grades.fullYears(jdn(1404, 6, 15), jdn(1403, 6, 15)))
    }

    @Test fun rankListHasTwelveRanksWithThirdRangerSecond() {
        assertEquals(12, Grades.ALL.size)
        assertEquals("محیط‌بان سوم", Grades.ALL[Grades.START_INDEX])
    }
}
