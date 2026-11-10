package app.klean.core.capture

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.shared.diet.CaptureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/** The intents shortcuts.xml and the Assistant send become the capture sheet's extras. */
@RunWith(AndroidJUnit4::class)
class CaptureDeepLinkTest {
    @Test fun aDeepLinkPrefillsTextAndTargetsTheModule() {
        val i = CaptureActivity.normalize(Intent(Intent.ACTION_VIEW, Uri.parse("klean://capture?text=Two%20eggs&module=food")))
        assertEquals("Two eggs", i.getStringExtra(CaptureActivity.EXTRA_TEXT))
        assertEquals("diet", i.getStringExtra(CaptureActivity.EXTRA_TARGET_MODULE))
        assertEquals(CaptureSource.ASSISTANT.name, i.getStringExtra(CaptureActivity.EXTRA_SOURCE))
    }

    @Test fun appActionsExtrasWork() {
        val i = CaptureActivity.normalize(Intent(Intent.ACTION_VIEW).putExtra("text", "call the bank").putExtra("module", "tasks"))
        assertEquals("call the bank", i.getStringExtra(CaptureActivity.EXTRA_TEXT))
        assertEquals("tasks", i.getStringExtra(CaptureActivity.EXTRA_TARGET_MODULE))
    }

    @Test fun ourOwnIntentsAreLeftAlone() {
        val own = Intent().putExtra(CaptureActivity.EXTRA_SOURCE, CaptureSource.QS_TILE.name)
        assertSame(own, CaptureActivity.normalize(own))
        assertNull(own.getStringExtra(CaptureActivity.EXTRA_TEXT))
    }
}
