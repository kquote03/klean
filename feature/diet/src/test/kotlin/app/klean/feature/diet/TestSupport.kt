package app.klean.feature.diet

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.klean.core.common.ZoneProvider
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.prefs.DevicePrefs
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone

class FakeClock(var nowMs: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
}

val Dubai: TimeZone = TimeZone.of("Asia/Dubai")

object DubaiZone : ZoneProvider {
    override fun current(): TimeZone = Dubai
}

fun inMemoryDb(): KleanDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), KleanDatabase::class.java)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

fun tempDevicePrefs(clock: Clock, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)): DevicePrefs {
    val file = File(createTempDirectory("prefs").toFile(), "device_prefs.preferences_pb")
    return DevicePrefs(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }), clock)
}

/** The holistic status stack with diet as its only line: diet's provider wired to the real notifier. */
class StatusStack(
    ctx: Context,
    repo: app.klean.feature.diet.data.DietRepository,
    gate: app.klean.core.data.module.ModuleGate,
    settings: app.klean.core.data.settings.SettingsRepository,
    highlight: app.klean.feature.diet.notification.PromptHighlight = app.klean.feature.diet.notification.PromptHighlight { null },
    others: Set<app.klean.core.ui.module.StatusLineProvider> = emptySet(),
) {
    lateinit var notifier: app.klean.core.capture.status.KleanStatusNotifier
        private set
    val line: app.klean.feature.diet.notification.DietStatusLine = app.klean.feature.diet.notification.DietStatusLine(
        ctx, { repo }, gate, { others + line }, app.klean.feature.diet.ui.AndroidDietFormatter(ctx, DubaiZone), highlight,
        { app.klean.core.ui.module.StatusRefresh { setOf(notifier) } },
    )

    init {
        notifier = app.klean.core.capture.status.KleanStatusNotifier(
            ctx, app.klean.core.ui.module.DashboardContributions(emptySet(), emptySet(), gate, others + line), settings,
        )
    }
}
