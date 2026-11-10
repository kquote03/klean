package app.klean.core.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue

/**
 * The clutter budget (docs/ui/design-system.md, a hard rule): in the first viewport, at most [maxInteractive]
 * tappable elements and at most [maxTextInputs] text fields, outside subtrees tagged [CLUTTER_EXEMPT]
 * (the navigation bar, and lists whose rows open detail, which count once: tag the list, and the helper adds one).
 */
/** Tag for subtrees the budget skips: the navigation bar, and a list whose rows open detail (counted once). */
const val CLUTTER_EXEMPT_TAG = app.klean.core.design.component.CLUTTER_EXEMPT

fun ComposeContentTestRule.assertClutterBudget(maxInteractive: Int = 7, maxTextInputs: Int = 0) {
    val report = clutterCount()
    assertTrue("${report.tappable} tappable elements in the first viewport (budget $maxInteractive): ${report.labels}", report.tappable <= maxInteractive)
    assertTrue("${report.inputs} text inputs in the first viewport (budget $maxTextInputs)", report.inputs <= maxTextInputs)
}

data class ClutterCount(val tappable: Int, val inputs: Int, val labels: List<String>)

/** What [assertClutterBudget] counts; exempt lists in view count as one each. */
fun ComposeContentTestRule.clutterCount(): ClutterCount {
    waitForIdle()
    val viewport = onRoot().getUnclippedBoundsInRoot()
    val bottomPx = with(density) { viewport.bottom.toPx() }
    fun inViewport(n: SemanticsNode) = n.boundsInRoot.top < bottomPx && n.boundsInRoot.bottom > 0f &&
        n.boundsInRoot.width > 0f && n.boundsInRoot.height > 0f
    fun tagged(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.TestTag) == CLUTTER_EXEMPT_TAG
    fun exempt(n: SemanticsNode): Boolean {
        var p: SemanticsNode? = n
        while (p != null) {
            if (tagged(p)) return true
            p = p.parent
        }
        return false
    }
    val all = onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = false).fetchSemanticsNodes()
        .filter { inViewport(it) }
    val counted = all.filter { !exempt(it) }
    val tappables = counted.filter { SemanticsActions.OnClick in it.config }
    val lists = all.count { tagged(it) && it.parent?.let(::exempt) != true }
    val inputs = counted.count { SemanticsActions.SetText in it.config }
    fun label(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
        ?: n.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: "#${n.id}"
    return ClutterCount(tappables.size + lists, inputs, tappables.map(::label) + List(lists) { "list" })
}
