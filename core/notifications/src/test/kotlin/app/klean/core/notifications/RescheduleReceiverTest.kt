package app.klean.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Records each reschedule; a second one always fails, to prove one failure doesn't stop the rest. */
object Recorder {
    val calls = CopyOnWriteArrayList<String>()
}

@Module
@InstallIn(SingletonComponent::class)
object FakeReschedulables {
    @Provides @IntoSet fun ok(): Reschedulable = Reschedulable { Recorder.calls += "ok" }
    @Provides @IntoSet fun broken(): Reschedulable = Reschedulable { Recorder.calls += "broken"; error("boom") }
}

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class RescheduleReceiverTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setUp() {
        hilt.inject()
        Recorder.calls.clear()
    }

    private suspend fun awaitCalls(n: Int) {
        repeat(100) { if (Recorder.calls.size >= n) return; delay(20) }
    }

    @Test fun bootCompletedReschedulesEverythingAndRegistersChannels() = runBlocking {
        RescheduleReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))
        awaitCalls(2)
        assertEquals(setOf("ok", "broken"), Recorder.calls.toSet())
        val nm = ctx.getSystemService(NotificationManager::class.java)
        assertTrue(nm.getNotificationChannel(Channels.DIET_PROMPT) != null)
    }

    @Test fun manifestRoutesSystemBroadcastsToTheReceiver() = runBlocking {
        for (action in RescheduleReceiver.SYSTEM_ACTIONS) {
            Recorder.calls.clear()
            ctx.sendBroadcast(Intent(action).setPackage(ctx.packageName))
            shadowOf(Looper.getMainLooper()).idle()
            awaitCalls(2)
            assertEquals(action, setOf("ok", "broken"), Recorder.calls.toSet())
        }
    }

    @Test fun unrelatedActionsAreIgnored() = runBlocking {
        RescheduleReceiver().onReceive(ctx, Intent("something.else"))
        delay(200)
        assertEquals(emptyList<String>(), Recorder.calls.toList())
    }
}
