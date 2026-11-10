package app.klean.feature.workout.suggest

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.workout.Dubai
import app.klean.feature.workout.FakeClock
import app.klean.feature.workout.inMemoryDb
import app.klean.feature.workout.tempDevicePrefs
import app.klean.shared.diet.prompt.PromptFormat
import app.klean.shared.diet.prompt.PromptOutcome
import app.klean.shared.diet.prompt.PromptTrigger
import app.klean.shared.time.QuietHours
import app.klean.shared.time.atMinute
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

class SuggestionPolicyTest {
    private val day = 86_400_000L
    private val now = 100 * day

    @Test fun onlyAfterNQuietDaysAndAtMostEveryNDays() {
        assertFalse(SuggestionPolicy.shouldSuggest(now, now - 2 * day, 0, 3))
        assertTrue(SuggestionPolicy.shouldSuggest(now, now - 3 * day, 0, 3))
        assertTrue(SuggestionPolicy.shouldSuggest(now, null, 0, 3))
        // Suggested yesterday: not again yet.
        assertFalse(SuggestionPolicy.shouldSuggest(now, now - 10 * day, now - day, 3))
        assertTrue(SuggestionPolicy.shouldSuggest(now, now - 10 * day, now - 3 * day, 3))
    }

    @Test fun nextIsSixPmOutOfQuietHours() {
        val d = LocalDate(2026, 11, 10)
        val q = QuietHours(17 * 60, 19 * 60)
        val morning = atMinute(d, 9 * 60, Dubai)
        assertEquals(atMinute(d, 18 * 60, Dubai), SuggestionPolicy.nextAt(morning, 18 * 60, Dubai) { it })
        assertEquals(atMinute(d, 19 * 60, Dubai), SuggestionPolicy.nextAt(morning, 18 * 60, Dubai) { q.nextAllowed(it, Dubai) })
        val evening = atMinute(d, 20 * 60, Dubai)
        assertEquals(atMinute(LocalDate(2026, 11, 11), 18 * 60, Dubai), SuggestionPolicy.nextAt(evening, 18 * 60, Dubai) { it })
    }
}

@RunWith(AndroidJUnit4::class)
class WorkoutPromptLogTest {
    private val clock = FakeClock(atMinute(LocalDate(2026, 11, 10), 18 * 60, Dubai))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val log = WorkoutPromptLog(db, stamper, clock)
    private val pool = listOf("a", "b", "c")

    @After fun close() = db.close()

    @Test fun anIgnoredSuggestionChangesFormatAndPhrasing() = runBlocking {
        val (f1, p1) = log.next(pool, Random(1))
        assertEquals(PromptFormat.STANDARD, f1)
        log.sent(f1, p1, clock.nowMs)
        clock.nowMs += 3 * 86_400_000L
        val (f2, p2) = log.next(pool, Random(1))
        assertEquals(PromptFormat.HEADS_UP, f2)
        assertNotEquals(p1, p2)
        val rows = db.promptLogDao().recentForTrigger(PromptTrigger.WORKOUT, 10)
        assertEquals(PromptOutcome.IGNORED, rows.single().outcome)
        assertEquals("workout", rows.single().moduleId)
    }

    @Test fun aLoggedWorkoutKeepsTheFormat() = runBlocking {
        val (f1, p1) = log.next(pool, Random(1))
        log.sent(f1, p1, clock.nowMs)
        log.resolve(PromptOutcome.LOGGED, since = clock.nowMs + 1000)
        assertEquals(PromptFormat.STANDARD, log.next(pool, Random(1)).first)
    }
}
