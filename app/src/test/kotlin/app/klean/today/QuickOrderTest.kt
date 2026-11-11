package app.klean.today

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickOrderTest {
    private val available = listOf("speak", "type", "tasks", "calendar", "sleep", "workout", "diet", "reminders")

    @Test fun foodSitsRightAboveSpeakByDefault() {
        val order = quickButtonOrder(available, saved = emptyList())
        assertEquals(listOf("speak", "type", "tasks", "calendar", "diet", "sleep", "workout", "reminders"), order)
        // Right hand: reach index 4 is the top row's right-most tile, straight above Speak.
        val rows = app.klean.core.design.component.thumbRows(order, leftHanded = false)
        assertEquals("diet", rows[0].last())
        assertEquals("speak", rows[1].last())
    }

    @Test fun savedOrderWins() {
        val saved = listOf("diet", "speak", "type", "tasks", "calendar", "sleep", "workout", "reminders")
        assertEquals(saved, quickButtonOrder(available, saved))
    }

    @Test fun aModuleTurnedOffDropsOutAndANewOneTakesItsDefaultPlace() {
        val saved = listOf("speak", "diet", "type", "tasks", "sleep", "workout", "reminders", "gone")
        val order = quickButtonOrder(available, saved)
        assertEquals(available.toSet(), order.toSet())
        assertEquals(listOf("speak", "diet", "type"), order.take(3))
    }

    @Test fun fewModulesStillPutFoodLast() {
        assertEquals(listOf("speak", "type", "diet"), quickButtonOrder(listOf("speak", "type", "diet"), emptyList()))
    }

    @Test fun swapExchangesTwoButtons() {
        assertEquals(listOf("diet", "type", "speak"), swapKeys(listOf("speak", "type", "diet"), "speak", "diet"))
    }
}
