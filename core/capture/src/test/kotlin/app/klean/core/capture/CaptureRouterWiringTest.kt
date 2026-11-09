package app.klean.core.capture

import app.klean.core.common.ZoneProvider
import app.klean.shared.capture.CandidateAction
import app.klean.shared.capture.CaptureClassifier
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.CaptureOption
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

class CaptureRouterWiringTest {
    /** 2026-11-09 10:00 in Dubai (a Monday). */
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-09T06:00:00Z") }
    private val zone = object : ZoneProvider { override fun current() = TimeZone.of("Asia/Dubai") }
    private class CountingAi(val answer: List<CandidateAction>?) : CaptureClassifier {
        var calls = 0
        override suspend fun classify(text: String, options: List<CaptureOption>): List<CandidateAction>? { calls++; return answer }
    }
    private val diet = FakeHandler(ModuleId.DIET)
    private val tasks = FakeHandler(ModuleId.TASKS)
    private val calendar = FakeHandler(ModuleId.CALENDAR)
    private val contacts = FakeHandler(ModuleId.CONTACTS)
    private fun input(text: String) = CaptureInput(text, CaptureSource.DASHBOARD, 0L)

    @Test fun rulesRouteWithoutAi() = runTest {
        val ai = CountingAi(null)
        val r = captureRouter(ai, clock, zone)
        val handlers = listOf(diet, tasks, calendar, contacts)
        assertEquals(ModuleId.TASKS, r.route(input("pay rent"), handlers).first().moduleId)
        assertEquals(ModuleId.CALENDAR, r.route(input("dentist friday 9:30"), handlers).first().moduleId)
        assertEquals(ModuleId.CONTACTS, r.route(input("Sara's birthday is March 3"), handlers).first().moduleId)
        assertEquals(0, ai.calls)
    }

    @Test fun noRuleFallsBackToAiThenDiet() = runTest {
        val ai = CountingAi(null)
        val r = captureRouter(ai, clock, zone)
        assertEquals(listOf(diet.defaultAction(input("toast"))), r.route(input("toast"), listOf(diet, tasks)))
        assertEquals(1, ai.calls)
    }

    @Test fun dietOnlyIsUnchanged() = runTest {
        val ai = CountingAi(listOf(CandidateAction(ModuleId.TASKS, "add_task", 1f)))
        val r = captureRouter(ai, clock, zone)
        assertEquals(listOf(diet.defaultAction(input("pay rent"))), r.route(input("pay rent"), listOf(diet)))
        assertEquals(0, ai.calls)
    }

    @Test fun birthdayWithoutContactsNeverReachesAi() = runTest {
        val ai = CountingAi(listOf(CandidateAction(ModuleId.TASKS, "add_task", 1f)))
        val r = captureRouter(ai, clock, zone)
        assertEquals(ModuleId.DIET, r.route(input("it's Omar's bday"), listOf(diet, tasks)).first().moduleId)
        assertEquals(0, ai.calls)
    }
}
