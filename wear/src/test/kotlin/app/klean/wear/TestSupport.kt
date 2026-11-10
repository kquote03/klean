package app.klean.wear

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Text
import app.klean.wear.data.WatchGraph
import app.klean.shared.wear.WatchMessage
import app.klean.wear.data.PhoneLink
import app.klean.shared.wear.WatchState
import app.klean.shared.wear.WearCodec

val ctx: Context get() = ApplicationProvider.getApplicationContext()

/** Every text in a layout tree, in order. */
fun texts(e: LayoutElement?): List<String> = when (e) {
    is Text -> listOfNotNull(e.text?.value)
    is Column -> e.contents.flatMap(::texts)
    is Box -> e.contents.flatMap(::texts)
    is Row -> e.contents.flatMap(::texts)
    else -> emptyList()
}

/** Every clickable in a layout tree: id → the launched activity's extras (key → string value). */
fun clickables(e: LayoutElement?): Map<String, Pair<String, Map<String, String>>> {
    val here = when (e) {
        is Box -> e.modifiers?.clickable
        is Column -> e.modifiers?.clickable
        is Row -> e.modifiers?.clickable
        is Text -> e.modifiers?.clickable
        else -> null
    }?.let { c ->
        val launch = c.onClick as? ActionBuilders.LaunchAction
        val activity = launch?.androidActivity
        val extras = activity?.keyToExtraMapping.orEmpty().mapValues { (_, v) -> (v as? ActionBuilders.AndroidStringExtra)?.value.orEmpty() }
        mapOf(c.id to (activity?.className.orEmpty() to extras))
    }.orEmpty()
    val kids = when (e) {
        is Column -> e.contents
        is Box -> e.contents
        is Row -> e.contents
        else -> emptyList()
    }
    return here + kids.flatMap { clickables(it).entries }.associate { it.key to it.value }
}

fun storeState(s: WatchState) {
    WatchGraph.store(ctx).save(WearCodec.encodeState(s))
}

class RecordingLink : PhoneLink {
    val sent = mutableListOf<WatchMessage>()
    var reachable = true
    override suspend fun send(m: WatchMessage): Boolean {
        sent += m
        return reachable
    }
}
