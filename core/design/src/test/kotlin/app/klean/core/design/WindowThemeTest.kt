package app.klean.core.design

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The window theme (what shows before Compose draws, and behind translucent sheets) follows the
 * system light/dark setting, so a light phone never flashes a dark window at launch.
 */
@RunWith(AndroidJUnit4::class)
class WindowThemeTest {
    private fun isLight(): Boolean {
        val themed = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Klean)
        val a = themed.theme.obtainStyledAttributes(intArrayOf(android.R.attr.isLightTheme))
        return a.getBoolean(0, false).also { a.recycle() }
    }

    @Config(qualifiers = "notnight")
    @Test fun lightSystemGivesALightWindow() = assertEquals(true, isLight())

    @Config(qualifiers = "night")
    @Test fun darkSystemGivesADarkWindow() = assertEquals(false, isLight())
}
