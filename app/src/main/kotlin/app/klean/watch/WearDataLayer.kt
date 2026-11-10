package app.klean.watch

import android.content.Context
import android.util.Log
import app.klean.shared.wear.WearPaths
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * The phone's side of the Wear OS Data Layer, behind an interface. The Data Layer needs Google Play
 * services; a phone without them (Waydroid, a de-Googled ROM) gets [NoWearDataLayer] and everything
 * watch-related quietly does nothing. Nothing here may crash the phone app.
 */
interface WearDataLayer {
    /** False when there is no Data Layer on this phone: callers skip the work that feeds it. */
    val available: Boolean get() = true

    /** Puts the [WearPaths.STATE] DataItem (urgent). True once the Data Layer accepted it. */
    suspend fun putState(bytes: ByteArray): Boolean

    /** Sends a message to every connected watch; returns how many accepted it. */
    suspend fun sendToWatches(path: String, bytes: ByteArray): Int
}

/** No Play services: the phone works as before and the watch path is off. */
object NoWearDataLayer : WearDataLayer {
    override val available: Boolean = false
    override suspend fun putState(bytes: ByteArray): Boolean = false
    override suspend fun sendToWatches(path: String, bytes: ByteArray): Int = 0
}

/**
 * [WearDataLayer] over Play services' `DataClient` / `NodeClient` / `MessageClient`, every call timed out.
 * Play services can be there without the Wearable API (no Wear OS app on the phone, e.g. Waydroid's GApps):
 * the first API_UNAVAILABLE (status 17) switches this layer off for the rest of the process.
 */
class PlayWearDataLayer(context: Context) : WearDataLayer {
    private val ctx = context.applicationContext
    @Volatile private var apiMissing = false

    override val available: Boolean get() = !apiMissing

    override suspend fun putState(bytes: ByteArray): Boolean = guard(false) {
        val request = PutDataRequest.create(WearPaths.STATE).setData(bytes).setUrgent()
        Wearable.getDataClient(ctx).putDataItem(request).await()
        true
    }

    override suspend fun sendToWatches(path: String, bytes: ByteArray): Int = guard(0) {
        val nodes = Wearable.getNodeClient(ctx).connectedNodes.await()
        val messages = Wearable.getMessageClient(ctx)
        nodes.count { node ->
            runCatching { messages.sendMessage(node.id, path, bytes).await() }
                .onFailure { Log.w("Klean", "watch message to ${node.displayName} failed", it) }
                .isSuccess
        }
    }

    private suspend fun <T> guard(none: T, block: suspend () -> T): T {
        if (apiMissing) return none
        return try {
            withTimeout(TIMEOUT_MS) { block() }
        } catch (e: ApiException) {
            if (e.statusCode != CommonStatusCodes.API_NOT_CONNECTED) throw e
            apiMissing = true
            Log.i("Klean", "no Wearable API on this phone; the watch path is off")
            none
        }
    }

    companion object {
        const val TIMEOUT_MS = 10_000L

        /** True when Play services is installed and usable; never throws. */
        fun isAvailable(ctx: Context): Boolean = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }
}

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}

@Module
@InstallIn(SingletonComponent::class)
object WearDataLayerModule {
    /** Decided once per process: Play services don't appear or vanish while Klean runs. */
    @Provides
    @Singleton
    fun dataLayer(@ApplicationContext ctx: Context): WearDataLayer =
        if (PlayWearDataLayer.isAvailable(ctx)) PlayWearDataLayer(ctx) else NoWearDataLayer
}
