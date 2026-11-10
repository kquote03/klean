package app.klean.feature.calendar.ui

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import app.klean.feature.calendar.FakeCalendarRepository
import app.klean.feature.calendar.FakeDeviceAccess
import app.klean.feature.calendar.FakeIcsFiles
import app.klean.feature.calendar.FakePrefs
import app.klean.feature.calendar.ui.settings.CalendarSettingsViewModel
import app.klean.feature.calendar.ui.settings.SettingsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CalendarSettingsViewModelTest {
    private val repo = FakeCalendarRepository()
    private val device = FakeDeviceAccess(granted = true)
    private val prefs = FakePrefs()
    private val files = FakeIcsFiles()
    private val store = ViewModelStore()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun vm() = CalendarSettingsViewModel(repo, device, prefs, files).also { store.put("vm", it) }

    @Test fun listsKleanAndThisPhonesCalendarsAndTogglesThem() = runTest {
        val vm = vm()
        vm.state.test {
            var s = awaitItem()
            while (s.calendars.size < 2) s = awaitItem()
            assertEquals(listOf("Klean", "Work"), s.calendars.map { it.name })
            vm.setSelected(s.calendars[1].id, false)
            while (s.calendars[1].selected) s = awaitItem()
            assertFalse(repo.sources.value.first { it.name == "Work" }.selected)
        }
    }

    @Test fun showSettingsAndDefaultReminderAreWritten() = runTest {
        val vm = vm()
        vm.setShowTasks(false)
        vm.setShowReminders(false)
        vm.setDefaultReminder(60)
        assertFalse(prefs.showTasks.value)
        assertFalse(prefs.showReminders.value)
        assertEquals(60, prefs.defaultReminder.value)
    }

    @Test fun importAndExportReportCalmly() = runTest {
        val vm = vm()
        files.readText = "BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"
        vm.import("content://file.ics")
        assertEquals(files.readText, repo.imported)
        assertEquals(SettingsMessage.Imported(added = 2, updated = 1), vm.state.value.message)
        vm.export("content://out.ics")
        assertEquals(repo.exportText, files.written)
        assertEquals(SettingsMessage.Exported(0), vm.state.value.message)
        vm.messageShown()
        assertEquals(null, vm.state.value.message)
    }

    @Test fun anUnreadableFileSaysSo() = runTest {
        val vm = vm()
        val broken = object : app.klean.feature.calendar.ui.settings.IcsFiles {
            override suspend fun read(uri: String): String = throw java.io.IOException("gone")
            override suspend fun write(uri: String, text: String) = throw java.io.IOException("gone")
        }
        val vm2 = CalendarSettingsViewModel(repo, device, prefs, broken).also { store.put("vm2", it) }
        vm2.import("content://x")
        assertEquals(SettingsMessage.ImportFailed, vm2.state.value.message)
        vm2.export("content://x")
        assertEquals(SettingsMessage.ExportFailed, vm2.state.value.message)
        vm.messageShown()
    }
}
