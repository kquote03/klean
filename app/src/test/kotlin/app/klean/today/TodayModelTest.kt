package app.klean.today

import java.time.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayModelTest {
    @Test fun greetingByHour() {
        assertEquals(Greeting.NIGHT, greetingFor(0))
        assertEquals(Greeting.NIGHT, greetingFor(4))
        assertEquals(Greeting.MORNING, greetingFor(5))
        assertEquals(Greeting.MORNING, greetingFor(11))
        assertEquals(Greeting.AFTERNOON, greetingFor(12))
        assertEquals(Greeting.AFTERNOON, greetingFor(16))
        assertEquals(Greeting.EVENING, greetingFor(17))
        assertEquals(Greeting.EVENING, greetingFor(21))
        assertEquals(Greeting.NIGHT, greetingFor(22))
        assertEquals(Greeting.NIGHT, greetingFor(23))
    }

    @Test fun weekStartsOnTheGivenDay() {
        val mon = weekOf(LocalDate(2026, 11, 11), DayOfWeek.MONDAY) // a Wednesday
        assertEquals(LocalDate(2026, 11, 9), mon.first())
        assertEquals(7, mon.size)
        val sat = weekOf(LocalDate(2026, 11, 11), DayOfWeek.SATURDAY)
        assertEquals(LocalDate(2026, 11, 7), sat.first())
        assertEquals(LocalDate(2026, 11, 13), sat.last())
        val sun = weekOf(LocalDate(2026, 11, 8), DayOfWeek.SUNDAY) // a Sunday
        assertEquals(LocalDate(2026, 11, 8), sun.first())
    }

    @Test fun weekShiftNeverPassesTheCurrentWeek() {
        val today = LocalDate(2026, 11, 11)
        assertEquals(0, clampWeekOffset(1))
        assertEquals(-3, clampWeekOffset(-3))
        assertEquals(LocalDate(2026, 11, 2), weekOf(today, DayOfWeek.MONDAY, offset = -1).first())
    }
}
