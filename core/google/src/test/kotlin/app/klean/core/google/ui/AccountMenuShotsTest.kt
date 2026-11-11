package app.klean.core.google.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.google.AccountsState
import app.klean.core.google.GoogleAccount
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The account menu in light and dark (src/test/snapshots/account-menu-*.png): one account, two, none, and at font 1.5. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccountMenuShotsTest {
    @get:Rule val compose = createComposeRule()

    private val ada = GoogleAccount("ada@gmail.com", name = "Ada Lovelace", givenName = "Ada", pictureFile = "x.img")
    private val bo = GoogleAccount("bo.chen@gmail.com", name = "Bo Chen", givenName = "Bo")

    private fun state(vararg all: GoogleAccount, current: GoogleAccount? = all.firstOrNull()) =
        AccountMenuUi(AccountsState(available = true, connected = all.isNotEmpty(), current = current, accounts = all.toList()))

    private fun render(name: String, ui: AccountMenuUi, expanded: Boolean = false, actions: AccountMenuActions = AccountMenuActions(), fontScale: Float = 1f) {
        val photos = mapOf(ada.email to photo())
        compose.shot(name, screen = true) {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                AccountMenuContent(ui = ui, photos = photos, actions = actions, startExpanded = expanded)
            }
        }
    }

    @Test fun oneAccount() {
        val managed = mutableListOf<String>()
        var closed = 0
        render("account-menu-one", state(ada), actions = AccountMenuActions(onManageGoogle = { managed += it }, onClose = { closed++ }))
        compose.onNodeWithText("ada@gmail.com").assertExists()
        compose.onNodeWithText("Hi, Ada").assertExists()
        compose.onNodeWithText("Switch account").assertExists()
        compose.onNodeWithText("More from Klean").assertExists()
        compose.onNodeWithText("Privacy policy").assertDoesNotExist()
        compose.assertClutterBudget()
        compose.onNodeWithText("Manage your Google Account").performClick()
        assertEquals(listOf("ada@gmail.com"), managed)
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(1, closed)
    }

    @Test fun twoAccountsExpanded() {
        val picked = mutableListOf<String>()
        var added = 0
        render("account-menu-two", state(ada, bo), expanded = true, actions = AccountMenuActions(onSwitch = { picked += it }, onAdd = { added++ }))
        compose.onNodeWithText("Bo Chen").assertExists()
        compose.onNodeWithContentDescription("Current account").assertExists()
        compose.onNodeWithText("Manage accounts on this device").assertExists()
        compose.assertClutterBudget()
        compose.onNodeWithText("Bo Chen").performClick()
        compose.onNodeWithText("Add another account").performClick()
        assertEquals(listOf("bo.chen@gmail.com"), picked)
        assertEquals(1, added)
    }

    @Test fun switchAccountExpandsOnTap() {
        render("account-menu-one", state(ada))
        compose.onNodeWithText("Add another account").assertDoesNotExist()
        compose.onNodeWithText("Switch account").performClick()
        compose.onNodeWithText("Add another account").assertExists()
    }

    @Test fun noGoogle() {
        var connect = 0
        var settings = 0
        render("account-menu-none", state(), actions = AccountMenuActions(onConnect = { connect++ }, onSettings = { settings++ }))
        compose.onNodeWithText("Hi, Ada").assertDoesNotExist()
        compose.onNodeWithText("Switch account").assertDoesNotExist()
        compose.assertClutterBudget()
        compose.onNodeWithText("Connect Google Tasks").performClick()
        compose.onNodeWithText("Settings").performClick()
        assertEquals(1, connect)
        assertEquals(1, settings)
    }

    @Test fun connectedBeforeTheProfileAsksQuietly() {
        var asked = 0
        val ui = AccountMenuUi(AccountsState(available = true, connected = true, current = null, accounts = emptyList()))
        render("account-menu-no-profile", ui, actions = AccountMenuActions(onShowPhoto = { asked++ }))
        compose.onNodeWithText("Google Tasks is connected").assertExists()
        compose.assertClutterBudget()
        compose.onNodeWithText("Show your Google photo").performClick()
        assertEquals(1, asked)
    }

    @Test fun largeFont() {
        render("account-menu-two-font150", state(ada, bo), expanded = true, fontScale = 1.5f)
        compose.onNodeWithText("Hi, Ada").assertExists()
        compose.onNodeWithText("Manage your Google Account").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun legalLinksShowOnlyWithUrls() {
        compose.setContent {
            AccountMenuContent(ui = state(ada), photos = emptyMap(), actions = AccountMenuActions(), privacyUrl = "https://example.org/p", termsUrl = null)
        }
        compose.onNodeWithText("Privacy policy").assertExists()
        compose.onNodeWithText("Terms").assertDoesNotExist()
    }

    private fun photo(): ImageBitmap {
        val img = ImageBitmap(192, 192)
        val c = Canvas(img)
        c.drawRect(0f, 0f, 192f, 192f, Paint().apply { color = Color(0xFF9DB8A0) })
        c.drawCircle(Offset(96f, 76f), 40f, Paint().apply { color = Color(0xFFF2D7C9) })
        c.drawCircle(Offset(96f, 200f), 80f, Paint().apply { color = Color(0xFF6E7FA8) })
        return img
    }
}
