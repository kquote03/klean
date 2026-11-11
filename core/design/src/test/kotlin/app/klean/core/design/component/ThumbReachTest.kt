package app.klean.core.design.component

import org.junit.Assert.assertEquals
import org.junit.Test

/** Today's buttons sit where the thumb is: the first in reach order takes the bottom corner on the holding side. */
class ThumbReachTest {
    private val eight = listOf("Speak", "Type", "Task", "Food", "Sleep", "Workout", "Event", "Reminder")

    @Test fun rightHandPutsSpeakBottomRightAndTypeBesideIt() {
        val rows = thumbRows(eight, leftHanded = false)
        assertEquals(listOf("Reminder", "Event", "Workout", "Sleep"), rows[0])
        assertEquals(listOf("Food", "Task", "Type", "Speak"), rows[1])
    }

    @Test fun leftHandMirrors() {
        val rows = thumbRows(eight, leftHanded = true)
        assertEquals(listOf("Sleep", "Workout", "Event", "Reminder"), rows[0])
        assertEquals(listOf("Speak", "Type", "Task", "Food"), rows[1])
    }

    @Test fun aShortTopRowHugsTheThumbSide() {
        assertEquals(listOf(null, null, "Workout", "Sleep"), thumbRows(eight.take(6), leftHanded = false)[0])
        assertEquals(listOf("Sleep", "Workout", null, null), thumbRows(eight.take(6), leftHanded = true)[0])
    }

    @Test fun oneRowWhenFourOrFewer() {
        assertEquals(listOf(listOf(null, "Task", "Type", "Speak")), thumbRows(eight.take(3), leftHanded = false))
    }
}
