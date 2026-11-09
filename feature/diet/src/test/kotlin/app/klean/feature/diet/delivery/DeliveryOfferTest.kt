package app.klean.feature.diet.delivery

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.feature.diet.FakeClock
import app.klean.feature.diet.FakeDietRepository
import app.klean.feature.diet.prompt.EngineRig
import app.klean.feature.diet.tempDevicePrefs
import app.klean.feature.diet.inMemoryDb
import app.klean.core.data.settings.SettingsRepository
import app.klean.core.data.settings.SyncedKeys
import app.klean.core.data.sync.SyncStamper
import org.junit.After
import app.klean.shared.diet.CaptureSource
import app.klean.shared.time.HOUR_MS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Task 12.2: the one-time, calm offer on the Diet screen. */
class DeliveryOfferPolicyTest {
    private val now = 1_000_000_000L
    private fun offer(state: String = DeliveryOffer.NOT_OFFERED, entries: Int = 3, hint: Boolean = true, granted: Boolean = false) =
        DeliveryOfferPolicy.shouldOffer(state, now, entries, hint, granted)

    @Test fun offeredOnceThereIsAReason() {
        assertTrue(offer())
        assertFalse(offer(entries = 2))
        assertFalse(offer(hint = false))
        assertFalse(offer(granted = true))
    }

    @Test fun answeredOffersStayAnswered() {
        assertFalse(offer(DeliveryOffer.ACCEPTED))
        assertFalse(offer(DeliveryOffer.DECLINED))
    }

    @Test fun notNowComesBackAfterTheSnooze() {
        assertFalse(offer(DeliveryOffer.snoozedUntil(now + 1)))
        assertTrue(offer(DeliveryOffer.snoozedUntil(now)))
        assertFalse(offer("SNOOZED_UNTIL:garbage"))
    }

    @Test fun deliveryishEntries() {
        listOf("Talabat burger", "shawarma from deliveroo", "takeaway pizza", "ordered sushi", "noon food salad").forEach {
            assertTrue(it, DeliveryOfferPolicy.looksLikeDelivery(it))
        }
        listOf("porridge", "tea with mint", "noodles", "afternoon snack").forEach {
            assertFalse(it, DeliveryOfferPolicy.looksLikeDelivery(it))
        }
    }
}

@RunWith(AndroidJUnit4::class)
class DefaultDeliveryOffersTest {
    private val clock = FakeClock(EngineRig.t(14))
    private val prefs = tempDevicePrefs(clock)
    private val repo = FakeDietRepository(clock)
    private val db = inMemoryDb()
    private val settings = SettingsRepository(db, SyncStamper(db, prefs, clock))
    private var installedPackages = emptySet<String>()
    private var installed: Boolean
        get() = "com.talabat" in installedPackages
        set(v) { installedPackages = if (v) setOf("com.talabat") else emptySet() }
    private var granted = false
    private val env = object : DeliveryEnvironment {
        override fun anyInstalled(packages: Collection<String>) = packages.any { it in installedPackages }
        override fun accessGranted() = granted
    }
    private val offers = DefaultDeliveryOffers(prefs, repo, clock, env, settings)

    @After fun tearDown() = db.close()

    @Test fun looksForTheUsersOwnAllowlist() = runBlocking {
        log("tea", "porridge", "wrap")
        installedPackages = setOf("com.example.food")
        assertFalse(offers.visible.first())
        settings.update(SyncedKeys.DietDeliveryApps) { it + "com.example.food" }
        assertTrue(offers.visible.first())
    }

    private suspend fun log(vararg texts: String) = texts.forEach { repo.logEntry(it, CaptureSource.MODULE_SCREEN) }

    @Test fun showsAfterThreeEntriesWhenADeliveryAppIsInstalled() = runBlocking {
        log("tea", "porridge")
        installed = true
        assertFalse(offers.visible.first())
        log("wrap")
        assertTrue(offers.visible.first())
    }

    @Test fun showsWhenSomethingLoggedSoundsLikeADelivery() = runBlocking {
        log("tea", "porridge", "talabat burger")
        assertTrue(offers.visible.first())
    }

    @Test fun answersAreRemembered() = runBlocking {
        log("tea", "porridge", "talabat burger")
        offers.snooze()
        assertEquals(DeliveryOffer.snoozedUntil(clock.nowMs + DeliveryOfferPolicy.SNOOZE_MS), prefs.deliveryOffer.first())
        assertFalse(offers.visible.first())
        clock.nowMs += DeliveryOfferPolicy.SNOOZE_MS + HOUR_MS
        assertTrue(offers.visible.first())
        offers.decline()
        assertFalse(offers.visible.first())
        offers.accept()
        assertEquals(DeliveryOffer.ACCEPTED, prefs.deliveryOffer.first())
        assertFalse(offers.visible.first())
    }

    @Test fun neverWhenAccessIsAlreadyOn() = runBlocking {
        granted = true
        log("tea", "porridge", "talabat burger")
        assertFalse(offers.visible.first())
    }
}
