package app.klean.core.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.CLUTTER_EXEMPT
import app.klean.core.design.theme.KleanTheme
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class ClutterBudgetTest {
    @get:Rule val compose = createComposeRule()

    private fun buttons(n: Int) = compose.setContent {
        KleanTheme { Column { repeat(n) { Button(onClick = {}) { Text("Button $it") } } } }
    }

    @Test fun eightButtonsAreOverBudget() {
        buttons(8)
        val message = try { compose.assertClutterBudget(); null } catch (e: AssertionError) { e.message }
        assertTrue("expected a failure, got $message", message?.startsWith("8 tappable elements") == true)
    }

    @Test fun sevenButtonsFit() {
        buttons(7)
        compose.assertClutterBudget()
    }

    @Test fun anExemptListCountsOnce() {
        compose.setContent {
            KleanTheme {
                Column {
                    Button(onClick = {}) { Text("One") }
                    Button(onClick = {}) { Text("Two") }
                    LazyColumn(Modifier.testTag(CLUTTER_EXEMPT)) {
                        items(20) { ListItem(headlineContent = { Text("Row $it") }, modifier = Modifier.clickable {}) }
                    }
                }
            }
        }
        compose.assertClutterBudget(maxInteractive = 3)
        val overTwo = try { compose.assertClutterBudget(maxInteractive = 2); false } catch (_: AssertionError) { true }
        assertTrue("two buttons and the list are three", overTwo)
    }

    @Test fun oneTextFieldIsOverTheDefaultBudget() {
        compose.setContent { KleanTheme { TextField(value = "", onValueChange = {}) } }
        val message = try { compose.assertClutterBudget(); null } catch (e: AssertionError) { e.message }
        assertTrue("expected an input failure, got $message", message?.contains("1 text inputs") == true)
        compose.assertClutterBudget(maxTextInputs = 1)
    }
}
