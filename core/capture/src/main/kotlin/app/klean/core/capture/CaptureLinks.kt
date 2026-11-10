package app.klean.core.capture

import app.klean.shared.module.ModuleId
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** What a capture deep link, app shortcut or Assistant intent asks for: prefilled text and a target module. */
data class CaptureLink(val text: String?, val module: ModuleId?)

/**
 * `klean://capture?text=…&module=…` (M21). Used by `shortcuts.xml` (static shortcuts and App Actions
 * capabilities), the web and adb. The text is only prefilled; nothing is saved until the person taps Save.
 */
object CaptureLinks {
    const val SCHEME = "klean"
    const val HOST = "capture"
    /** Intent extra keys App Actions capabilities map their parameters to (see res/xml/shortcuts.xml). */
    const val EXTRA_TEXT = "text"
    const val EXTRA_MODULE = "module"
    private const val MAX_TEXT = 500

    private val aliases: Map<String, ModuleId> = mapOf(
        "diet" to ModuleId.DIET, "food" to ModuleId.DIET, "meal" to ModuleId.DIET,
        "tasks" to ModuleId.TASKS, "task" to ModuleId.TASKS, "todo" to ModuleId.TASKS,
        "reminders" to ModuleId.REMINDERS, "reminder" to ModuleId.REMINDERS, "remind" to ModuleId.REMINDERS,
        "calendar" to ModuleId.CALENDAR, "event" to ModuleId.CALENDAR,
        "contacts" to ModuleId.CONTACTS, "contact" to ModuleId.CONTACTS,
        "birthdays" to ModuleId.BIRTHDAYS, "birthday" to ModuleId.BIRTHDAYS,
        "workout" to ModuleId.WORKOUT, "sleep" to ModuleId.SLEEP,
        "delegation" to ModuleId.DELEGATION, "delegate" to ModuleId.DELEGATION,
    )

    fun isCaptureLink(uri: String?): Boolean =
        uri != null && runCatching { URI(uri).let { it.scheme == SCHEME && it.host == HOST } }.getOrDefault(false)

    /** Extras win over the URI's query; unknown modules mean universal capture. */
    fun parse(uri: String?, extraText: String? = null, extraModule: String? = null): CaptureLink {
        val query = uri?.takeIf(::isCaptureLink)?.let { runCatching { URI(it).rawQuery }.getOrNull() }.orEmpty()
        val params = query.split('&').filter { '=' in it }.associate { p ->
            val (k, v) = p.split('=', limit = 2)
            k to runCatching { URLDecoder.decode(v, "UTF-8") }.getOrDefault(v)
        }
        val text = (extraText ?: params[EXTRA_TEXT])?.trim()?.take(MAX_TEXT)?.ifEmpty { null }
        val module = (extraModule ?: params[EXTRA_MODULE])?.trim()?.lowercase()?.let(aliases::get)
        return CaptureLink(text, module)
    }

    fun uri(text: String? = null, module: ModuleId? = null): String {
        val q = listOfNotNull(
            text?.let { "$EXTRA_TEXT=" + URLEncoder.encode(it, "UTF-8").replace("+", "%20") },
            module?.let { "$EXTRA_MODULE=${it.value}" },
        )
        return "$SCHEME://$HOST" + if (q.isEmpty()) "" else "?" + q.joinToString("&")
    }
}
