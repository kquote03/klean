package app.klean.feature.diet.unlock

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.klean.core.notifications.Channels
import app.klean.core.notifications.NotificationIds
import app.klean.feature.diet.R
import app.klean.feature.diet.data.DietRepository
import app.klean.shared.time.MINUTE_MS
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Provider
import app.klean.core.notifications.R as NotifR

/** Whether the system lets Klean launch full-screen intents (Android 14+ can refuse; always true before). */
fun interface FullScreenIntentCheck {
    fun allowed(): Boolean
}

/**
 * Posts the unlock sheet on channel UNLOCK_SHEET (HIGH). With full-screen permission the sheet
 * activity opens by itself once the user unlocks; without it, the same notification is a heads-up
 * whose tap opens the sheet. Background activity starts and overlays are never used.
 */
class UnlockSheetNotifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    // A Provider, like DietStatusNotifier: the repository starts collecting settings when built, and the
    // screen monitor builds this at app start.
    private val repo: Provider<DietRepository>,
    private val fullScreen: FullScreenIntentCheck,
) : UnlockSheetPoster {
    override suspend fun post(logId: String) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!canPost(nm)) return
        val count = repo.get().todaySummary().count
        val open = PendingIntent.getActivity(
            ctx, RC_OPEN, UnlockSheetActivity.intent(ctx, logId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (count == 0) {
            ctx.getString(R.string.unlock_notification_empty)
        } else {
            ctx.resources.getQuantityString(R.plurals.unlock_notification_text, count, count)
        }
        val builder = NotificationCompat.Builder(ctx, Channels.UNLOCK_SHEET)
            .setSmallIcon(NotifR.drawable.ic_stat_klean)
            .setContentTitle(ctx.getString(R.string.unlock_title))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Only a count, never what was eaten, so it can show on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .setContentIntent(open)
        val fsi = fullScreen.allowed()
        if (fsi) builder.setFullScreenIntent(open, true)
        Log.i("Klean", "unlock sheet posted (fullScreen=$fsi)")
        post(nm, builder)
    }

    private fun canPost(nm: NotificationManagerCompat): Boolean =
        nm.areNotificationsEnabled() && (
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            )

    @SuppressLint("MissingPermission") // checked in canPost()
    private fun post(nm: NotificationManagerCompat, b: NotificationCompat.Builder) = nm.notify(NotificationIds.UNLOCK, b.build())

    companion object {
        const val TIMEOUT_MS = 10 * MINUTE_MS
        private const val RC_OPEN = 30
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UnlockSheetModule {
    @Binds abstract fun poster(impl: UnlockSheetNotifier): UnlockSheetPoster

    companion object {
        @Provides
        fun fullScreen(@ApplicationContext ctx: Context): FullScreenIntentCheck =
            FullScreenIntentCheck { NotificationManagerCompat.from(ctx).canUseFullScreenIntent() }
    }
}
