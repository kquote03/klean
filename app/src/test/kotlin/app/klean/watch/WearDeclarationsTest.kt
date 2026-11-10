package app.klean.watch

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.R
import app.klean.shared.wear.WearPaths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The pieces the Data Layer finds by declaration: the capability and the listener's filter. */
@RunWith(AndroidJUnit4::class)
class WearDeclarationsTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun thePhoneDeclaresTheCapabilityTheWatchLooksFor() {
        assertEquals(listOf(WearPaths.PHONE_CAPABILITY), ctx.resources.getStringArray(R.array.android_wear_capabilities).toList())
    }

    @Test fun watchMessagesUnderKleanReachWearSyncService() {
        val hits = WearPaths.run { listOf(CAPTURE, LOG_FOOD, START_WORKOUT, LOG_SET) }.map { path ->
            ctx.packageManager.queryIntentServices(
                Intent("com.google.android.gms.wearable.MESSAGE_RECEIVED").setData(Uri.parse("wear://node-1$path")).setPackage(ctx.packageName),
                PackageManager.GET_RESOLVED_FILTER,
            ).map { it.serviceInfo.name }
        }
        assertTrue(hits.toString(), hits.all { it == listOf(WearSyncService::class.java.name) })
        val other = ctx.packageManager.queryIntentServices(
            Intent("com.google.android.gms.wearable.MESSAGE_RECEIVED").setData(Uri.parse("wear://node-1/someone/else")).setPackage(ctx.packageName), 0,
        )
        assertTrue(other.isEmpty())
    }
}
