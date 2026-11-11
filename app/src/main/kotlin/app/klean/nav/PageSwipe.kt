package app.klean.nav

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** How far a finger travels sideways before the move counts as a page swipe. */
private val SWIPE_MIN = 72.dp

/**
 * A sideways swipe across a top-level page ([isPage]) calls [onSwipe] with the horizontal travel in pixels, once the
 * finger lifts. It listens on the final pass, after every child, and gives up as soon as a child consumed a change, so
 * horizontally scrolling children (chip rows, the calendar's week swipe, maps) keep their own swipes and a vertical
 * scroll stays a scroll. Once a move is clearly sideways it consumes the rest of the gesture, so the row under the
 * finger doesn't also take it as a tap.
 */
@Composable
fun Modifier.pageSwipe(isPage: Boolean, onSwipe: (dx: Float) -> Unit): Modifier {
    val swipe by rememberUpdatedState(onSwipe)
    val page by rememberUpdatedState(isPage)
    val minPx = with(LocalDensity.current) { SWIPE_MIN.toPx() }
    return pointerInput(minPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            if (!page) return@awaitEachGesture
            var claimed = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!claimed && event.changes.any { it.isConsumed }) return@awaitEachGesture
                val d = change.position - down.position
                if (!change.pressed) {
                    if (isPageSwipe(d.x, d.y, minPx)) swipe(d.x)
                    return@awaitEachGesture
                }
                if (!claimed && abs(d.x) > viewConfiguration.touchSlop && abs(d.x) > 2 * abs(d.y)) claimed = true
                if (claimed) event.changes.forEach { it.consume() }
            }
        }
    }
}
