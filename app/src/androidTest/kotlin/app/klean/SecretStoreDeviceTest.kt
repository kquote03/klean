package app.klean

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.data.secret.SecretStore
import app.klean.core.data.secret.SecretsDataStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Plan Task 10.1: the real Android Keystore cipher, which Robolectric can't provide. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SecretStoreDeviceTest {
    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var secrets: SecretStore
    @Inject @SecretsDataStore lateinit var raw: DataStore<Preferences>

    private val name = "test.roundTrip"

    @Before fun inject() = hilt.inject()

    @After fun cleanUp() = runBlocking { secrets.put(name, null) }

    @Test fun keystoreRoundTripStoresCiphertextOnly() = runBlocking {
        val plain = "AIzaSy-device-test-1234567890"
        secrets.put(name, plain)
        assertEquals(plain, secrets.get(name))
        val stored = raw.data.first()[stringPreferencesKey(name)]
        assertNotNull(stored)
        assertFalse("ciphertext must not contain the plaintext", stored!!.contains(plain))
        // A second write uses a fresh IV, so the same value never produces the same ciphertext.
        secrets.put(name, plain)
        val again = raw.data.first()[stringPreferencesKey(name)]
        assertFalse(stored == again)
        assertEquals(plain, secrets.get(name))
        secrets.put(name, null)
        assertNull(secrets.get(name))
    }
}
