/*
 * The content-line syntax shared by vCard (RFC 6350 / 2426) and iCalendar (RFC 5545):
 * `[group.]NAME[;PARAM=v1,v2]*:VALUE`, folded at 75 octets with CRLF + one space.
 */
package app.klean.shared.vcard

data class ContentLine(
    val group: String?,
    /** Upper-cased. */
    val name: String,
    /** Parameter name (upper-cased) to values, in order. */
    val params: List<Pair<String, List<String>>>,
    /** Raw value, still escaped. */
    val value: String,
) {
    fun param(name: String): String? = params.firstOrNull { it.first == name }?.second?.firstOrNull()
    fun paramValues(name: String): List<String> = params.filter { it.first == name }.flatMap { it.second }
}

object ContentLines {
    /** Splits text into logical lines: accepts CRLF or bare LF, joins continuation lines (leading space/tab). */
    fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.split('\n')) {
            val line = raw.removeSuffix("\r")
            if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + line.substring(1)
            } else if (line.isNotEmpty()) {
                out += line
            }
        }
        return out
    }

    fun parse(line: String): ContentLine? {
        // Find the first ':' outside a quoted parameter value.
        var inQuote = false
        var colon = -1
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuote = !inQuote
            else if (c == ':' && !inQuote) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = splitOutsideQuotes(head, ';')
        val nameAndGroup = parts.first()
        val dot = nameAndGroup.lastIndexOf('.')
        val group = if (dot > 0) nameAndGroup.substring(0, dot) else null
        val name = (if (dot > 0) nameAndGroup.substring(dot + 1) else nameAndGroup).uppercase()
        if (name.isEmpty()) return null
        val params = parts.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq < 0) {
                // vCard 2.1-style bare type, e.g. "TEL;CELL:…".
                "TYPE" to listOf(p.lowercase())
            } else {
                val pName = p.substring(0, eq).uppercase()
                val values = splitOutsideQuotes(p.substring(eq + 1), ',').map { it.removeSurrounding("\"") }
                pName to values
            }
        }
        return ContentLine(group, name, params, value)
    }

    /** One logical line, unfolded. */
    fun format(line: ContentLine): String = buildString {
        line.group?.let { append(it).append('.') }
        append(line.name)
        for ((name, values) in line.params) {
            append(';').append(name).append('=')
            append(values.joinToString(",") { v -> if (v.any { it == ':' || it == ';' || it == ',' }) "\"$v\"" else v })
        }
        append(':').append(line.value)
    }

    /** Folds one logical line to ≤75 octets per physical line, never splitting a UTF-8 sequence. Adds CRLF. */
    fun fold(line: String): String {
        val sb = StringBuilder()
        var octets = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            val cp = line[i]
            val len = if (cp.isHighSurrogate() && i + 1 < line.length) 2 else 1
            val chunk = line.substring(i, i + len)
            val size = chunk.encodeToByteArray().size
            if (octets + size > limit) {
                sb.append("\r\n ")
                octets = 0
                limit = 74 // the leading space counts
            }
            sb.append(chunk)
            octets += size
            i += len
        }
        return sb.append("\r\n").toString()
    }

    fun escapeText(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            ';' -> append("\\;")
            ',' -> append("\\,")
            '\n' -> append("\\n")
            '\r' -> {}
            else -> append(c)
        }
    }

    fun unescapeText(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> append('\n')
                    else -> append(n)
                }
                i += 2
            } else {
                append(c); i++
            }
        }
    }

    /** Splits a raw (escaped) value on unescaped [sep]; the parts stay escaped. */
    fun splitEscaped(value: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                cur.append(c).append(value[i + 1]); i += 2; continue
            }
            if (c == sep) { out += cur.toString(); cur.clear() } else cur.append(c)
            i++
        }
        out += cur.toString()
        return out
    }

    private fun splitOutsideQuotes(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuote = false
        for (c in s) {
            if (c == '"') inQuote = !inQuote
            if (c == sep && !inQuote) { out += cur.toString(); cur.clear() } else cur.append(c)
        }
        out += cur.toString()
        return out
    }
}
