package app.klean.feature.contacts.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.sync.SyncStamper
import app.klean.feature.contacts.DubaiZone
import app.klean.feature.contacts.FakeClock
import app.klean.feature.contacts.data.Birthday
import app.klean.feature.contacts.data.ContactDraft
import app.klean.feature.contacts.data.DefaultContactRepository
import app.klean.feature.contacts.data.birthday
import app.klean.feature.contacts.dubai
import app.klean.feature.contacts.inMemoryDb
import app.klean.feature.contacts.tempDevicePrefs
import app.klean.shared.capture.ActionSpec
import app.klean.shared.capture.CaptureOption
import app.klean.shared.capture.RuleCaptureClassifier
import app.klean.shared.diet.CaptureSource
import app.klean.shared.capture.CaptureInput
import app.klean.shared.module.ModuleId
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

class BirthdayTextTest {
    private val now = LocalDateTime(2026, 11, 10, 12, 0)
    private fun p(s: String) = BirthdayText.parse(s, now)

    @Test fun commonPhrases() {
        assertEquals(BirthdayPhrase("Sara", Birthday(3, 3)), p("Sara's birthday is March 3"))
        assertEquals(BirthdayPhrase("Mum", Birthday(4, 12)), p("Mum's bday 12/4"))
        assertEquals(BirthdayPhrase("Omar Khalil", Birthday(3, 3, 1990)), p("birthday omar khalil 3 March 1990"))
        assertEquals(BirthdayPhrase("Lina", Birthday(11, 11)), p("Lina's birthday is tomorrow"))
        assertEquals(BirthdayPhrase("Sara", Birthday(2, 29)), p("Sara’s birthday is on the 29th of February"))
        assertEquals(BirthdayPhrase("Ahmed", Birthday(7, 1, 1985)), p("Ahmed birthday 1985-07-01"))
        assertEquals(BirthdayPhrase("Me", Birthday(1, 5)), p("my birthday is jan 5"))
    }

    @Test fun noDateKeepsTheName() {
        assertEquals(BirthdayPhrase("Sara", null), p("Sara's birthday"))
    }
}

@RunWith(AndroidJUnit4::class)
class ContactsCaptureHandlerTest {
    private val clock = FakeClock(dubai(2026, 11, 10, 12))
    private val db = inMemoryDb()
    private val stamper = SyncStamper(db, tempDevicePrefs(clock), clock)
    private val repo = DefaultContactRepository(db, stamper, clock, SettingsRepository(db, stamper), emptySet())
    private val handler = ContactsCaptureHandler(repo, DubaiZone)
    private fun input(t: String) = CaptureInput(t, CaptureSource.DASHBOARD, clock.nowMs)

    @After fun close() = db.close()

    @Test fun createsAContactWithTheBirthdayAndUndoRemovesIt() = runBlocking {
        val id = handler.execute(input("Sara's birthday is March 3"), handler.defaultAction(input("")))
        val c = repo.get(id)!!
        assertEquals("Sara", c.displayName)
        assertEquals(Birthday(3, 3), c.birthday)
        handler.undo(id)
        assertTrue(repo.live().isEmpty())
    }

    @Test fun setsItOnAnExistingContactAndUndoPutsTheOldOneBack() = runBlocking {
        val sara = repo.add(ContactDraft("Sara", birthday = Birthday(3, 4)))
        val id = handler.execute(input("sara's birthday is March 3"), handler.defaultAction(input("")))
        assertEquals(sara, id)
        assertEquals(Birthday(3, 3), repo.get(sara)!!.birthday)
        assertEquals(1, repo.live().size)
        handler.undo(id)
        assertEquals(Birthday(3, 4), repo.get(sara)!!.birthday)
    }

    @Test fun hasNoAiDependency() {
        val params = ContactsCaptureHandler::class.java.constructors.single().parameterTypes.map { it.name }
        assertTrue(params.toString(), params.none { it.contains(".ai.", ignoreCase = true) || it.contains("Ai") })
        assertEquals(listOf(RuleCaptureClassifier.ADD_BIRTHDAY), handler.actionSpecs.map { it.type })
        assertEquals(ModuleId.CONTACTS, handler.moduleId)
    }

    @Test fun withContactsOffABirthdayPhraseBecomesADatedTask() = runBlocking {
        val rules = RuleCaptureClassifier { LocalDateTime(2026, 11, 10, 12, 0) }
        val options = listOf(CaptureOption(ModuleId.TASKS, ActionSpec(RuleCaptureClassifier.ADD_TASK, "")), CaptureOption(ModuleId.DIET, ActionSpec("log_food", "")))
        val r = rules.classify("Sara's birthday is March 3", options)!!
        assertEquals(ModuleId.TASKS, r.single().moduleId)
        assertEquals(RuleCaptureClassifier.ADD_TASK, r.single().actionType)
        // With Contacts on, it is ours.
        val on = rules.classify("Sara's birthday is March 3", options + CaptureOption(ModuleId.CONTACTS, handler.actionSpecs.single()))!!
        assertEquals(ModuleId.CONTACTS, on.first().moduleId)
        assertNull(rules.classify("Sara's birthday", options.drop(1)))
    }
}
