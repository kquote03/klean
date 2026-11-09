package app.klean.feature.settings.about

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutInfoTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun readsTheCantarellLicenceFromAssets() {
        val info = AboutInfo.load(ctx)
        assertTrue(info.fontLicence.contains("SIL Open Font License, Version 1.1"))
        assertTrue(info.fontLicence.contains("Cantarell"))
    }

    @Test fun versionFallsBackWhenThePackageHasNone() {
        assertEquals("0.1.0-120-abc1234", AboutInfo.versionLabel("0.1.0-120-abc1234"))
        assertEquals("dev", AboutInfo.versionLabel(null))
    }
}
