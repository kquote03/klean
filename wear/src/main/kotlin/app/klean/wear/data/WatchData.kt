package app.klean.wear.data

import app.klean.shared.wear.WatchState
import app.klean.shared.wear.WatchMessage
import app.klean.shared.wear.WearCodec
import app.klean.shared.wear.WearPaths
import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** The last [WatchState] the phone sent, kept on the watch (SharedPreferences; the watch keeps no other data). */
class WatchStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("klean_watch", Context.MODE_PRIVATE)

    fun load(): WatchState = prefs.getString(KEY, null)?.let { WearCodec.decodeState(it.encodeToByteArray()) } ?: WatchState()

    fun save(bytes: ByteArray): Boolean {
        val s = WearCodec.decodeState(bytes) ?: return false
        if (s.generatedAt < load().generatedAt) return false // an older snapshot arriving late
        prefs.edit().putString(KEY, bytes.decodeToString()).apply()
        return true
    }

    private companion object {
        const val KEY = "state"
    }
}

/** Sends [WatchMessage]s to the phone. */
fun interface PhoneLink {
    /** True once a reachable phone with Klean accepted it. */
    suspend fun send(m: WatchMessage): Boolean
}

/** [PhoneLink] over the Data Layer: the nearest reachable node with the [WearPaths.PHONE_CAPABILITY]. */
class DataLayerPhoneLink(context: Context) : PhoneLink {
    private val ctx = context.applicationContext

    override suspend fun send(m: WatchMessage): Boolean = runCatching {
        withTimeoutOrNull(TIMEOUT_MS) {
            val info = Wearable.getCapabilityClient(ctx).getCapability(WearPaths.PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
            val node = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull() ?: return@withTimeoutOrNull false
            val (path, bytes) = WearCodec.encode(m)
            Wearable.getMessageClient(ctx).sendMessage(node.id, path, bytes).await()
            true
        } ?: false
    }.onFailure { Log.w("KleanWatch", "send failed", it) }.getOrDefault(false)

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}

/** Tiny service locator: the watch app has no DI. Tests swap [link]. */
object WatchGraph {
    @Volatile var link: ((Context) -> PhoneLink) = { DataLayerPhoneLink(it) }
    fun store(context: Context) = WatchStateStore(context)
}

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
