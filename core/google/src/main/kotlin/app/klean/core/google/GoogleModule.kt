package app.klean.core.google

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.klean.core.data.db.KleanDatabase
import app.klean.core.data.db.entity.TaskEntity
import app.klean.core.data.sync.SyncStamper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.flow.first

/**
 * The Tasks module tells this about every local edit of a Google task (Tasks never depends on the Google
 * details). Multibound, so builds and tests without Google simply have none.
 */
interface GoogleTaskEdits {
    /** [task] (origin GOOGLE) changed in Klean: queue its title, notes, due date and done state for Google. */
    suspend fun edited(task: TaskEntity)
    /** The Tasks screen opened: refresh from Google soon, when connected. */
    suspend fun refreshSoon()
}

@Singleton
class OutboxGoogleTaskEdits @Inject constructor(
    private val state: GoogleLocalState,
    private val scheduler: GoogleTasksScheduler,
    private val clock: Clock,
) : GoogleTaskEdits {
    override suspend fun edited(task: TaskEntity) {
        val op = task.toOutboxOp(clock.now().toEpochMilliseconds()) ?: return
        state.enqueue(op)
        if (state.connected.first()) scheduler.syncNow()
    }

    override suspend fun refreshSoon() {
        if (state.connected.first()) scheduler.syncNow()
    }
}

/** Every hour while connected, plus a run after each edit or Tasks visit (needs a network). */
@Singleton
class GoogleTasksScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val wm get() = WorkManager.getInstance(ctx)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        val req = PeriodicWorkRequestBuilder<GoogleTasksWorker>(1, TimeUnit.HOURS)
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun syncNow() {
        val req = OneTimeWorkRequestBuilder<GoogleTasksWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
            .build()
        wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun cancel() {
        wm.cancelUniqueWork(PERIODIC)
        wm.cancelUniqueWork(NOW)
    }

    companion object {
        const val PERIODIC = "klean.google.periodic"
        const val NOW = "klean.google.now"
        const val BACKOFF_S = 30L
    }
}

@HiltWorker
class GoogleTasksWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val sync: GoogleTasksSync,
    private val accounts: GoogleAccounts,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val r = sync.run()
        // The account's name and photo, at most daily; a failure here never fails the sync.
        if (r is GoogleSyncResult.Done) runCatching { accounts.refreshIfStale() }
        return when (r) {
            is GoogleSyncResult.Failed -> if (r.retry && runAttemptCount < 5) Result.retry() else Result.failure()
            else -> Result.success()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object GoogleProvides {
    @Provides @Singleton @GoogleStore
    fun store(@ApplicationContext ctx: Context): DataStore<Preferences> = ctx.googleDataStore

    @Provides @Singleton
    fun state(@GoogleStore store: DataStore<Preferences>): GoogleLocalState = GoogleLocalState(store)

    @Provides @Singleton
    fun api(auth: GoogleAuth): GoogleTasksApi = GoogleTasksClient(HttpClient(OkHttp), auth)

    @Provides @Singleton
    fun profile(): GoogleProfileApi = GoogleProfileClient(HttpClient(OkHttp))

    @Provides @Singleton
    fun accounts(
        @ApplicationContext ctx: Context,
        auth: GoogleAuth,
        profile: GoogleProfileApi,
        state: GoogleLocalState,
        sync: GoogleTasksSync,
        clock: Clock,
    ): GoogleAccounts = GoogleAccounts(auth, profile, state, sync, java.io.File(ctx.filesDir, GoogleAccounts.PHOTO_DIR), clock)

    @Provides @Singleton
    fun sync(db: KleanDatabase, stamper: SyncStamper, auth: GoogleAuth, api: GoogleTasksApi, state: GoogleLocalState, clock: Clock): GoogleTasksSync =
        GoogleTasksSync(db, stamper, auth, api, state, clock)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GoogleBinds {
    @Binds abstract fun auth(impl: PlayGoogleAuth): GoogleAuth
    @Binds @IntoSet abstract fun edits(impl: OutboxGoogleTaskEdits): GoogleTaskEdits
    @Binds abstract fun accountActions(impl: GoogleAccountActions): AccountActions
}
