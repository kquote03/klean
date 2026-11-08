package app.klean.feature.diet.ui

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemovedEntrySnackbarTest {
    @get:Rule val compose = createComposeRule()

    /** Found on Waydroid: consuming the id re-keyed the effect and cancelled its own snackbar. */
    @Test fun showsEntryRemovedWithUndoAndConsumesTheId() {
        val removed = mutableStateOf<String?>("e1")
        val undone = mutableListOf<String>()
        compose.setContent {
            val host = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { SnackbarHost(host) }) { _ -> Text("list") }
            RemovedEntrySnackbar(removed.value, onConsumed = { removed.value = null }, snackbar = host, onUndo = { undone += it })
        }
        compose.onNodeWithText("Entry removed").assertExists()
        compose.onNodeWithText("Undo").performClick()
        compose.waitForIdle()
        assertEquals(listOf("e1"), undone)
        assertNull(removed.value)
    }
}
