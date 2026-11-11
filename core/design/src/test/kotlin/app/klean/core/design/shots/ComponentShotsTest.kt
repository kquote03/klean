package app.klean.core.design.shots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.klean.core.design.component.AddMenuItem
import app.klean.core.design.component.AddMenuSpec
import app.klean.core.design.component.CardTone
import app.klean.core.design.component.EmptyState
import app.klean.core.design.component.FactCard
import app.klean.core.design.component.GroupChrome
import app.klean.core.design.component.GroupChromeResolver
import app.klean.core.design.component.KleanCard
import app.klean.core.design.component.KleanScreen
import app.klean.core.design.component.ListGroup
import app.klean.core.design.component.ListRow
import app.klean.core.design.component.LocalAddMenu
import app.klean.core.design.component.LocalGroupChrome
import app.klean.core.design.component.MenuAction
import app.klean.core.design.component.ModuleCard
import app.klean.core.design.component.NewSavedCard
import app.klean.core.design.component.SavedCard
import app.klean.core.design.component.SectionHeader
import app.klean.core.design.component.SegmentedTabs
import app.klean.core.design.component.SummaryTile
import app.klean.core.design.component.WeekBars
import app.klean.core.design.component.listGroup
import app.klean.core.design.theme.Space
import app.klean.core.testing.PHONE
import app.klean.core.testing.assertClutterBudget
import app.klean.core.testing.shot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every shared component in light and dark at phone width (reference images in src/test/snapshots). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ComponentShotsTest {
    @get:Rule val compose = createComposeRule()

    private val addSpec = AddMenuSpec(
        items = listOf(
            AddMenuItem("sleep", "Log sleep", Icons.Rounded.Today) {},
            AddMenuItem("event", "Event", Icons.Rounded.Event) {},
            AddMenuItem("task", "Task", Icons.Rounded.CheckCircle) {},
        ),
        onCapture = {},
    )

    @Test fun summaryTile() { compose.shot("summary-tile") {
        SummaryTile("Last night", "7h 10m", Icons.Rounded.Today, onClick = {})
    }}

    @Test fun kleanCard() { compose.shot("klean-card") {
        KleanCard(title = "Workout", onOpen = {}) { Text("Push day is saved") }
    }}

    @Test fun moduleCard() {
        var opened = 0
        compose.shot("module-card") {
            Column(verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                ModuleCard(label = "Next", fact = "Team sync", supporting = "15:00 to 15:30", onClick = { opened++ }) {
                    ListRow("Dinner with Lina", meta = "19:30", dense = true, leading = { Icon(Icons.Rounded.Event, null) })
                    ListRow("Buy groceries", meta = "Today", dense = true, leading = { Icon(Icons.Rounded.RadioButtonUnchecked, null) })
                }
                ModuleCard(label = "Last night", fact = "7h 10m", supporting = "23:40 to 06:50", tone = CardTone.SECONDARY)
                ModuleCard(label = "Coming up", fact = "Sara's birthday", supporting = "Friday, in 3 days", tone = CardTone.TERTIARY)
            }
        }
        compose.onNodeWithText("Team sync", substring = true).performClick()
        assertEquals("the whole card is one target", 1, opened)
    }

    @Test fun sectionsAndListGroup() { compose.shot("list-group") {
        Column {
            SectionHeader("Today", first = true)
            ListGroup(listOf("Buy groceries" to "Oat milk, bananas, spinach")) { (t, s) ->
                ListRow(t, supporting = s, leading = { Icon(Icons.Rounded.RadioButtonUnchecked, null) }, onClick = {})
            }
            SectionHeader("Still want these?", action = MenuAction("See all") {})
            ListGroup(listOf("Book a dentist check-up" to "Thursday", "Renew car registration" to "From Saturday")) { (t, m) ->
                ListRow(t, meta = m, leading = { Icon(Icons.Rounded.RadioButtonUnchecked, null) }, onClick = {})
            }
        }
    }}

    @Test fun emptyState() { compose.shot("empty-state") {
        EmptyState("Your workouts will show up here.", action = MenuAction("Start a workout") {})
    }}

    @Test fun segmentedTabs() { compose.shot("segmented-tabs") {
        SegmentedTabs(listOf("Workout", "Sleep", "Diet"), selected = 1, onSelect = {})
    }}

    @Test fun weekBarsAndSavedCards() { compose.shot("week-bars") {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            WeekBars(
                values = listOf(0.8f, null, 0.6f, 1f, null, 0.4f, 0.7f),
                labels = listOf("M", "T", "W", "T", "F", "S", "S"),
                today = 6,
                description = "Sleep this week",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                SavedCard("Push day", "Bench, dips, push-ups", "Start", onClick = {})
                NewSavedCard("New", onClick = {})
            }
        }
    }}

    @Test fun heroWithBarsAndFactCards() { compose.shot("fact-cards") {
        Column(verticalArrangement = Arrangement.spacedBy(Space.cardGap)) {
            ModuleCard(
                label = "This week", fact = "7h 05m of sleep", supporting = "on average, over 6 nights", tone = CardTone.SECONDARY,
                hero = {
                    WeekBars(listOf(0.7f, 0.8f, null, 0.9f, 0.75f, 0.7f, 0.85f), listOf("W", "T", "F", "S", "S", "M", "T"), 6, "Sleep this week")
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.cardGap)) {
                FactCard("Workouts", "2 sessions", Modifier.weight(1f), supporting = "75 min, last on Thursday")
                FactCard("Food today", "1 entry", Modifier.weight(1f), supporting = "Oatmeal, banana at 8:10")
            }
        }
    }}

    @Test fun savedCardLongPressEdits() {
        var edited = 0
        compose.shot("saved-card") {
            SavedCard("Push day", "Bench, dips, push-ups", "Start", onClick = {}, onLongClick = { edited++ }, onLongClickLabel = "Edit")
        }
        compose.onNodeWithText("Push day").performTouchInput { longClick() }
        assertEquals(1, edited)
    }

    @Test fun screenInAGroupWithTheAddMenu() {
        compose.shot("klean-screen", screen = true) {
            CompositionLocalProvider(
                LocalGroupChrome provides GroupChromeResolver { GroupChrome("Plan", listOf("Tasks", "Calendar"), 0, {}, listOf(MenuAction("Settings") {})) },
                LocalAddMenu provides addSpec,
            ) {
                KleanScreen(title = "Tasks", menu = listOf(MenuAction("Lists") {})) { pad ->
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
                        item { SectionHeader("Today", first = true) }
                        listGroup("t", listOf("Buy groceries", "Call the bank"), { it }) { ListRow(it, onClick = {}) }
                    }
                }
            }
        }
        compose.onNodeWithText("Plan").assertExists()
        compose.onNodeWithText("Calendar").assertExists()
        compose.assertClutterBudget()
    }

    @Test fun addMenuOpen() {
        val shot = compose.shot("add-menu", screen = true) {
            CompositionLocalProvider(LocalAddMenu provides addSpec) {
                KleanScreen(title = "Today") { pad -> Text("Content", Modifier.padding(pad)) }
            }
        }
        compose.onNodeWithContentDescription("Add").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Type or speak…").assertExists()
        compose.onNodeWithText("Task").assertExists()
        shot.capture("add-menu-open")
    }
}
