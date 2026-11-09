package app.klean.shared.capture

import app.klean.shared.module.ModuleId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuleCaptureClassifierTest {
    private val now = LocalDateTime(2026, 11, 9, 10, 0)
    private val rules = RuleCaptureClassifier { now }

    private fun opt(m: ModuleId, type: String) = CaptureOption(m, ActionSpec(type, type))
    private val all = listOf(
        opt(ModuleId.DIET, "log_food"),
        opt(ModuleId.TASKS, "add_task"),
        opt(ModuleId.REMINDERS, "add_reminder"),
        opt(ModuleId.CALENDAR, "add_event"),
        opt(ModuleId.CONTACTS, "add_birthday"),
        opt(ModuleId.BIRTHDAYS, "add_birthday"),
        opt(ModuleId.WORKOUT, "log_workout"),
        opt(ModuleId.SLEEP, "log_sleep"),
    )
    private val dietOnly = listOf(opt(ModuleId.DIET, "log_food"))

    private suspend fun first(text: String, options: List<CaptureOption> = all) = rules.classify(text, options)?.first()

    private suspend fun expect(text: String, module: ModuleId, action: String, confidence: Float? = null) {
        val c = first(text)
        assertEquals(module to action, c?.moduleId to c?.actionType, "for \"$text\"")
        if (confidence != null) assertEquals(confidence, c!!.confidence, "confidence for \"$text\"")
    }

    @Test fun briefExamples() = runTest {
        assertNull(rules.classify("ate 2 manakish", all))
        expect("gym tomorrow 6pm", ModuleId.CALENDAR, "add_event", 0.8f)
        expect("Sara's birthday is March 3", ModuleId.CONTACTS, "add_birthday", 0.9f)
        expect("remind me at 5 to call Ahmed", ModuleId.REMINDERS, "add_reminder", 0.9f)
    }

    @Test fun gymWithFutureTimeAlsoOffersWorkout() = runTest {
        val r = rules.classify("gym tomorrow 6pm", all)!!
        assertEquals(listOf(ModuleId.CALENDAR to 0.8f, ModuleId.WORKOUT to 0.5f), r.map { it.moduleId to it.confidence })
    }

    @Test fun eachRule() = runTest {
        expect("bday party for Omar", ModuleId.CONTACTS, "add_birthday")
        expect("wake me at 6", ModuleId.REMINDERS, "add_reminder")
        expect("set an alarm for 7am", ModuleId.REMINDERS, "add_reminder")
        expect("slept at 11 woke at 7", ModuleId.SLEEP, "log_sleep")
        expect("going to bed", ModuleId.SLEEP, "log_sleep")
        expect("did 30 pushups", ModuleId.WORKOUT, "log_workout", 0.9f)
        expect("ran 5 km", ModuleId.WORKOUT, "log_workout")
        expect("ran 5k in 30 min", ModuleId.WORKOUT, "log_workout", 0.9f)
        expect("dentist friday 9:30", ModuleId.CALENDAR, "add_event", 0.7f)
        expect("pay rent friday", ModuleId.TASKS, "add_task", 0.7f)
        expect("pay rent", ModuleId.TASKS, "add_task", 0.8f)
        expect("todo fix the bike", ModuleId.TASKS, "add_task", 0.8f)
        expect("need to renew passport", ModuleId.TASKS, "add_task")
    }

    @Test fun foodWithATimeIsLeftToDiet() = runTest {
        assertNull(rules.classify("lunch at 1pm", all))
        assertNull(rules.classify("coffee 8:30", all))
    }

    @Test fun onlyDietRegisteredGivesNull() = runTest {
        for (t in listOf("gym tomorrow 6pm", "Sara's birthday is March 3", "remind me at 5 to call Ahmed", "pay rent",
            "did 30 pushups", "going to bed", "dentist friday 9:30")) {
            assertNull(rules.classify(t, dietOnly), "for \"$t\"")
        }
    }

    @Test fun onlyReturnsModulesInOptions() = runTest {
        // Birthdays without Contacts: the BIRTHDAYS module takes it.
        val b = rules.classify("Sara's birthday is March 3", dietOnly + opt(ModuleId.BIRTHDAYS, "add_birthday"))!!
        assertEquals(listOf(ModuleId.BIRTHDAYS), b.map { it.moduleId })
        // Gym with a time but no Calendar: only the workout candidate.
        val g = rules.classify("gym tomorrow 6pm", dietOnly + opt(ModuleId.WORKOUT, "log_workout"))!!
        assertEquals(listOf(ModuleId.WORKOUT), g.map { it.moduleId })
        // A rule whose module is absent falls through to later rules.
        expect2("remind me to pay rent", dietOnly + opt(ModuleId.TASKS, "add_task"), ModuleId.TASKS)
        for (opts in listOf(all, dietOnly + opt(ModuleId.TASKS, "add_task"))) {
            rules.classify("call mum at 9", opts)?.forEach { c -> assertTrue(opts.any { it.moduleId == c.moduleId }) }
        }
    }

    private suspend fun expect2(text: String, options: List<CaptureOption>, module: ModuleId) =
        assertEquals(module, first(text, options)?.moduleId, "for \"$text\"")
}

class CompositeCaptureClassifierTest {
    private val now = LocalDateTime(2026, 11, 9, 10, 0)
    private val rules = RuleCaptureClassifier { now }
    private class CountingAi(val answer: List<CandidateAction>?) : CaptureClassifier {
        var calls = 0
        override suspend fun classify(text: String, options: List<CaptureOption>): List<CandidateAction>? { calls++; return answer }
    }
    private val options = listOf(
        CaptureOption(ModuleId.DIET, ActionSpec("log_food", "")),
        CaptureOption(ModuleId.TASKS, ActionSpec("add_task", "")),
    )
    private val aiAnswer = listOf(CandidateAction(ModuleId.TASKS, "add_task", 0.6f))

    @Test fun rulesWinWithoutCallingAi() = runTest {
        val ai = CountingAi(aiAnswer)
        val r = CompositeCaptureClassifier(rules, ai).classify("pay rent", options)
        assertEquals(ModuleId.TASKS to 0.8f, r!!.first().let { it.moduleId to it.confidence })
        assertEquals(0, ai.calls)
    }

    @Test fun fallsBackToAiWhenRulesHaveNothing() = runTest {
        val ai = CountingAi(aiAnswer)
        assertEquals(aiAnswer, CompositeCaptureClassifier(rules, ai).classify("grey notebook", options))
        assertEquals(1, ai.calls)
    }

    @Test fun noAiGivesNull() = runTest {
        assertNull(CompositeCaptureClassifier(rules, null).classify("grey notebook", options))
    }

    @Test fun birthdaysNeverReachAi() = runTest {
        val ai = CountingAi(aiAnswer)
        val c = CompositeCaptureClassifier(rules, ai)
        // No contacts/birthdays module registered: the rules find nothing, and still the AI is not asked.
        assertNull(c.classify("it's Omar's bday", options))
        // A later rule may still claim it (here: a dated task), but never the AI.
        assertEquals(ModuleId.TASKS, c.classify("Sara's birthday is March 3", options)?.first()?.moduleId)
        assertEquals(ModuleId.TASKS, c.classify("BDAY of Omar 4/5", options)?.first()?.moduleId)
        assertEquals(0, ai.calls)
    }

    @Test fun routerWithOnlyDietKeepsTodaysBehaviour() = runTest {
        val r = rules.classify("gym tomorrow 6pm", options.take(1))
        assertNull(r)
    }
}
