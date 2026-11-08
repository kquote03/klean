package app.klean.feature.diet.prompt

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.prefs.DevicePrefs
import app.klean.core.notifications.RescheduleReceiver
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** Stand-in for `adb reboot`: BOOT_COMPLETED through the real Hilt graph re-arms the check-in alarm. */
@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class RebootRescheduleTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var prefs: DevicePrefs
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val am = ctx.getSystemService(AlarmManager::class.java)

    @Before fun setUp() = hilt.inject()
    @After fun tearDown() = ShadowAlarmManager.reset()

    private fun promptAlarm() = shadowOf(am).scheduledAlarms.firstOrNull {
        shadowOf(it.operation).savedIntent.component?.className == PromptAlarmReceiver::class.java.name
    }

    @Test fun bootCompletedArmsTheCheckInAlarm() = runBlocking {
        RescheduleReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))
        var tries = 0
        while ((promptAlarm() == null || prefs.nextPromptAt.first() == null) && tries++ < 150) delay(20)
        val alarm = promptAlarm()
        assertNotNull("no check-in alarm after boot", alarm)
        val next = prefs.nextPromptAt.first()
        assertNotNull(next)
        assertTrue(alarm!!.triggerAtMs == next)
    }
}
