package app.klean.core.capture

import app.klean.core.common.ZoneProvider
import app.klean.shared.capture.CaptureInput
import app.klean.shared.capture.DefaultCaptureRouter
import app.klean.shared.diet.CaptureSource
import app.klean.shared.module.ModuleId
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M28: every action has a handler and a no-AI rule; switching a module off removes it from routing; "Move to…"
 * works for every pair; text nothing recognised goes to Diet with every other module one tap away (H7).
 */
class CaptureEverywhereTest {
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-09T06:00:00Z") }
    private val zone = object : ZoneProvider { override fun current() = TimeZone.of("Asia/Dubai") }
    private val modules = listOf(
        ModuleId.DIET, ModuleId.TASKS, ModuleId.REMINDERS, ModuleId.CALENDAR,
        ModuleId.CONTACTS, ModuleId.WORKOUT, ModuleId.SLEEP, ModuleId.DELEGATION,
    )
    private val phrase = mapOf(
        ModuleId.DIET to "two eggs and toast",
        ModuleId.TASKS to "buy batteries tomorrow",
        ModuleId.REMINDERS to "remind me to water the plants at 18:00",
        ModuleId.CALENDAR to "dentist friday 9:30",
        ModuleId.CONTACTS to "Sara's birthday is March 3",
        ModuleId.WORKOUT to "30 pushups",
        ModuleId.SLEEP to "slept 23:30 to 7:00",
        ModuleId.DELEGATION to "ask Hermes to book a table",
    )
    private val handlers = modules.associateWith { FakeHandler(it) }
    private val gate = FakeModuleGate()
    // No AI: the offline rules alone.
    private val service = UniversalCaptureService(handlers.values.toSet(), gate, captureRouter(null, clock, zone))
    private fun input(text: String, target: ModuleId? = null) = CaptureInput(text, CaptureSource.DASHBOARD, 0L, targetModule = target)

    @Test fun everyPhraseReachesItsModuleWithoutAi() = runTest {
        modules.forEach { m ->
            val r = service.submit(input(phrase.getValue(m))) as CaptureResult.Saved
            assertEquals(phrase[m], m, r.moduleId)
        }
    }

    @Test fun switchingAModuleOffRemovesItFromRouting() = runTest {
        modules.forEach { m ->
            gate.state.value = setOf(m)
            val r = service.submit(input(phrase.getValue(m))) as CaptureResult.Saved
            assertNotEquals("$m off: \"${phrase[m]}\"", m, r.moduleId)
            assertTrue(r.others.none { it.moduleId == m } && r.alternatives.none { it.moduleId == m })
            // Even aimed at it directly (a stale shortcut), a module that is off never gets the capture.
            val aimed = service.submit(input(phrase.getValue(m), target = m)) as CaptureResult.Saved
            assertNotEquals(m, aimed.moduleId)
        }
        gate.state.value = emptySet()
    }

    @Test fun moveToWorksForEveryPairAndOffersTheWayBack() = runTest {
        for (from in modules) for (to in modules) {
            if (from == to) continue
            val saved = service.submit(input(phrase.getValue(from), target = from)) as CaptureResult.Saved
            val moved = service.correct(saved, handlers.getValue(to).defaultAction(saved.input))
            assertEquals("$from → $to", to, moved.moduleId)
            assertEquals("$from → $to undoes the original", saved.recordId, handlers.getValue(from).undone.last())
            assertEquals(saved.input, handlers.getValue(to).executed.last())
            assertTrue("$from → $to offers $from back", moved.alternatives.any { it.moduleId == from })
        }
    }

    @Test fun unrecognisedTextGoesToDietWithEveryOtherModuleBehindMoveTo() = runTest {
        val r = service.submit(input("oats")) as CaptureResult.Saved
        assertEquals(ModuleId.DIET, r.moduleId)
        assertEquals(
            listOf(ModuleId.TASKS, ModuleId.REMINDERS, ModuleId.CALENDAR, ModuleId.WORKOUT, ModuleId.SLEEP, ModuleId.CONTACTS, ModuleId.DELEGATION),
            r.others.map { it.moduleId },
        )
        // A rule hit or a capture aimed at a module is not a guess: no "Move to…" list.
        assertTrue((service.submit(input("remind me at 5 to stretch")) as CaptureResult.Saved).others.isEmpty())
        assertTrue((service.submit(input("oats", target = ModuleId.DIET)) as CaptureResult.Saved).others.isEmpty())
    }

    @Test fun dietAloneSavesQuietly() = runTest {
        val diet = UniversalCaptureService(setOf(FakeHandler(ModuleId.DIET)), gate, DefaultCaptureRouter(null))
        val r = diet.submit(input("oats")) as CaptureResult.Saved
        assertTrue(r.others.isEmpty() && r.alternatives.isEmpty())
    }
}
