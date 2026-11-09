package app.klean.feature.diet.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Found on Waydroid (F5): at 2×1 the compact widget read "3 today · last: b…", so the latest entry
 * was cut off behind the count. The entry gets its own lines; a short count sits under it.
 */
@RunWith(AndroidJUnit4::class)
class DietWidgetCompactTextTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private fun entry(text: String) = WidgetEntry(id = text, time = "4:48 PM", text = text)

    @Test fun latestEntryLeadsAndTheCountFollows() {
        val state = DietWidgetState(count = 3, entries = listOf(entry("backup probe"), entry("toast")))
        assertEquals(CompactText("backup probe", "3 today"), compactText(ctx, state))
    }

    @Test fun singleEntry() {
        val state = DietWidgetState(count = 1, entries = listOf(entry("tea")))
        assertEquals(CompactText("tea", "1 today"), compactText(ctx, state))
    }

    @Test fun emptyDayHasNoCountLine() {
        assertEquals(CompactText("Nothing yet today", null), compactText(ctx, DietWidgetState()))
    }
}
