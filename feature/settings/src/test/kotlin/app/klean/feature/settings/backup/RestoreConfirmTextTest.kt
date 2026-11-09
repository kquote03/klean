package app.klean.feature.settings.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Found on Waydroid: the restore dialog read "(4 entries, 1 photos)". Counts need plurals. */
@RunWith(AndroidJUnit4::class)
class RestoreConfirmTextTest {
    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    @Test fun singularCounts() {
        assertEquals(
            "Replace everything on this phone? This deletes the 1 entry, photos and settings currently in Klean " +
                "and replaces them with the backup from Nov 9, 2026 (1 entry, 1 photo). This can't be undone.",
            restoreConfirmText(res, currentEntries = 1, date = "Nov 9, 2026", backupEntries = 1, backupPhotos = 1),
        )
    }

    @Test fun pluralCountsKeepThePlanWording() {
        assertEquals(
            "Replace everything on this phone? This deletes the 3 entries, photos and settings currently in Klean " +
                "and replaces them with the backup from Nov 9, 2026 (4 entries, 0 photos). This can't be undone.",
            restoreConfirmText(res, currentEntries = 3, date = "Nov 9, 2026", backupEntries = 4, backupPhotos = 0),
        )
    }
}
