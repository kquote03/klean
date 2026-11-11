package app.klean.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Shared axis X between screens; a short cross-fade without movement when animations are off. */
class KleanMotionTest {
    @Test fun reducedMotionIsTheAnimatorScaleAtZero() {
        assertTrue(KleanMotion.isReduced(0f))
        assertEquals(false, KleanMotion.isReduced(1f))
        assertEquals(false, KleanMotion.isReduced(0.5f))
    }

    @Test fun reducedMotionOnlyFades() {
        val m = KleanMotion(reduced = true)
        assertEquals(fadeIn(tween(KleanMotion.FADE_MS)), m.enter(forward = true))
        assertEquals(fadeOut(tween(KleanMotion.FADE_MS)), m.exit(forward = false))
    }

    @Test fun sharedAxisMovesAndDiffersByDirection() {
        val m = KleanMotion(reduced = false)
        assertNotEquals(fadeIn(tween(KleanMotion.FADE_MS)), m.enter(forward = true))
        assertNotEquals(m.enter(forward = true), m.enter(forward = false))
    }
}
