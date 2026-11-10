package app.klean.shared.delegation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A structured message between Klean and an agent, carried as one JSON object in plain text (`docs/hermes.md`). */
sealed interface KleanBlock {
    data class Question(val text: String) : KleanBlock
    data class Approval(val action: String, val draft: String, val recipients: List<String>) : KleanBlock
    data class Draft(val text: String) : KleanBlock
    data class Answer(val text: String) : KleanBlock
    data class Decision(val decision: String, val draft: String, val recipients: List<String>) : KleanBlock
}

/** Finds, strips and writes Klean blocks: `{"klean":"question"|"approval"|"draft"|"answer"|"decision", …}`. */
object KleanBlocks {
    private val json = Json { ignoreUnknownKeys = true }

    /** What Hermes is told at the start of every delegation. */
    const val INSTRUCTIONS: String =
        "You are working on a task delegated from Klean, the user's personal organiser. Do the task with your own tools. " +
            "When you need something from the user, stop and output exactly one JSON object on its own line, then end your turn: " +
            "{\"klean\":\"question\",\"text\":\"...\"} to ask a question, or " +
            "{\"klean\":\"approval\",\"action\":\"send_email\",\"draft\":\"...\",\"recipients\":[\"...\"]} before sending, posting, " +
            "booking or paying for anything. Never act on those before the user's decision arrives as " +
            "{\"klean\":\"decision\",\"decision\":\"APPROVED|EDITED|REJECTED\",\"draft\":\"...\",\"recipients\":[...]}; " +
            "with EDITED, use exactly the draft and recipients given. Answers arrive as {\"klean\":\"answer\",\"text\":\"...\"}. " +
            "When you are done, reply with a short summary of what you did."

    fun parse(text: String): List<KleanBlock> = spans(text).mapNotNull { (_, obj) -> toBlock(obj) }

    /** [text] without its Klean blocks and code fences around them, trimmed. */
    fun strip(text: String): String {
        var out = text
        spans(text).sortedByDescending { it.first.first }.forEach { (range, _) -> out = out.removeRange(range) }
        return out.replace(Regex("""```(?:json)?\s*```"""), "").trim()
    }

    fun answer(text: String): String = buildJsonObject { put("klean", "answer"); put("text", text) }.toString()

    fun decision(decision: String, draft: String, recipients: List<String>): String = buildJsonObject {
        put("klean", "decision"); put("decision", decision); put("draft", draft)
        putJsonArray("recipients") { recipients.forEach { add(it) } }
    }.toString()

    private val ASK = Regex("""^(?:ask\s+hermes\s+to|hermes\s*[,:]|delegate\s*[:,]|delegate\s+to\s+hermes\s*[:,]?)\s*""", RegexOption.IGNORE_CASE)

    /** The task title in a capture like \"ask Hermes to book a table\" → \"Book a table\". */
    fun delegateTitle(capture: String): String =
        capture.trim().replace(ASK, "").trim().replaceFirstChar { it.uppercaseChar() }.ifEmpty { capture.trim() }

    private fun toBlock(o: JsonObject): KleanBlock? {
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        fun list(k: String) = (o[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return when (s("klean")) {
            "question" -> s("text")?.let { KleanBlock.Question(it) }
            "approval" -> KleanBlock.Approval(s("action") ?: "action", s("draft").orEmpty(), list("recipients"))
            "draft" -> s("text")?.let { KleanBlock.Draft(it) }
            "answer" -> s("text")?.let { KleanBlock.Answer(it) }
            "decision" -> s("decision")?.let { KleanBlock.Decision(it, s("draft").orEmpty(), list("recipients")) }
            else -> null
        }
    }

    /** Every balanced `{…}` containing `"klean"` that parses as a JSON object, with its range in [text]. */
    private fun spans(text: String): List<Pair<IntRange, JsonObject>> {
        val out = ArrayList<Pair<IntRange, JsonObject>>()
        var i = 0
        while (i < text.length) {
            if (text[i] != '{') { i++; continue }
            val end = matchingBrace(text, i)
            if (end < 0) { i++; continue }
            val candidate = text.substring(i, end + 1)
            val obj = if ("\"klean\"" in candidate) runCatching { json.parseToJsonElement(candidate) as? JsonObject }.getOrNull() else null
            if (obj != null && obj["klean"] != null) {
                out += (i..end) to obj
                i = end + 1
            } else {
                i++
            }
        }
        return out
    }

    /** Index of the brace closing the one at [start], skipping braces inside JSON strings; -1 if none. */
    private fun matchingBrace(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (j in start until text.length) {
            val c = text[j]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return j }
            }
        }
        return -1
    }
}

/** Schema and prompt of a partial delegation: a draft, subtasks and notes (M23). */
val PARTIAL_DELEGATION_SCHEMA: JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject {
        put("draft", buildJsonObject { put("type", "string") })
        put("subtasks", buildJsonObject { put("type", "array"); put("items", buildJsonObject { put("type", "string") }) })
        put("notes", buildJsonObject { put("type", "string") })
    })
    putJsonArray("required") { add("draft"); add("subtasks"); add("notes") }
}

const val PARTIAL_DELEGATION_SYSTEM: String =
    "Help the user get a task done. Return JSON with: draft = a ready-to-use draft of the work (a message, a plan, a " +
        "list) the user can copy; subtasks = at most 8 short, concrete next steps; notes = anything they should check. " +
        "Be brief and practical. Never claim to have done anything yourself."
