package app.klean.core.data.account

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KleanAccountTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun accountTypeIsThePackageName() {
        assertEquals(ctx.packageName, KleanAccount.accountType(ctx))
    }

    @Test fun ensureAddsOneSyncableAccountOnce() = runBlocking {
        val first = KleanAccount.ensure(ctx)
        val second = KleanAccount.ensure(ctx)
        assertEquals(first, second)
        assertEquals("Klean", first.name)
        assertEquals(ctx.packageName, first.type)
        assertEquals(1, AccountManager.get(ctx).getAccountsByType(ctx.packageName).size)
        assertEquals(1, ContentResolver.getIsSyncable(first, KleanAccount.CALENDAR_AUTHORITY))
        assertEquals(1, ContentResolver.getIsSyncable(first, KleanAccount.CONTACTS_AUTHORITY))
    }
}
