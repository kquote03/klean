package app.klean.feature.contacts.birthday

import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BirthdaysTest {
    @Test fun nextIsTodayOnTheDayAndRollsOverAfterwards() {
        val b = Birthday(11, 10)
        assertEquals(date(2026, 11, 10), Birthdays.next(b, date(2026, 11, 10)))
        assertEquals(date(2027, 11, 10), Birthdays.next(b, date(2026, 11, 11)))
        assertEquals(0, Birthdays.daysUntil(b, date(2026, 11, 10)))
        assertEquals(3, Birthdays.daysUntil(b, date(2026, 11, 7)))
    }

    @Test fun feb29FallsOnFeb28InOtherYears() {
        val b = Birthday(2, 29)
        assertEquals(date(2027, 2, 28), Birthdays.next(b, date(2026, 11, 10)))
        assertEquals(date(2028, 2, 29), Birthdays.next(b, date(2027, 3, 1)))
        assertEquals(date(2028, 2, 29), Birthdays.inYear(b, 2028))
        assertEquals(date(2100, 2, 28), Birthdays.inYear(b, 2100))
    }

    @Test fun betweenListsEveryYearInRangeAndNoneBeforeBirth() {
        val b = Birthday(1, 5, 2026)
        assertEquals(listOf(date(2027, 1, 5)), Birthdays.between(b, date(2025, 12, 1), date(2027, 1, 31)).filter { it.year == 2027 })
        assertEquals(listOf(date(2026, 1, 5), date(2027, 1, 5)), Birthdays.between(b, date(2025, 1, 1), date(2027, 12, 31)))
        assertEquals(listOf(date(2025, 1, 5)), Birthdays.between(Birthday(1, 5), date(2025, 1, 1), date(2025, 1, 5)))
    }

    @Test fun ageOnlyWithAYear() {
        assertEquals(41, Birthdays.ageOn(Birthday(4, 12, 1985), date(2026, 4, 12)))
        assertNull(Birthdays.ageOn(Birthday(4, 12), date(2026, 4, 12)))
    }

    @Test fun reminderDateCountsBack() {
        assertEquals(date(2027, 2, 25), Birthdays.reminderDate(date(2027, 2, 28), 3))
        assertEquals(date(2026, 12, 29), Birthdays.reminderDate(date(2027, 1, 1), 3))
    }
}
