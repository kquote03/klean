package app.klean.core.sync

import java.net.URLDecoder

/** A pairing link from the web's QR code: `klean://pair?url=<urlencoded backend URL>&code=ABCD-EFGH`. */
data class PairLink(val url: String, val code: String) {
    companion object {
        private const val PREFIX = "klean://pair?"

        /** Null for anything that isn't a complete pairing link with an http(s) URL. */
        fun parse(text: String): PairLink? {
            val t = text.trim()
            if (!t.startsWith(PREFIX, ignoreCase = true)) return null
            val params = t.substring(PREFIX.length).split('&').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i) to runCatching { URLDecoder.decode(part.substring(i + 1), "UTF-8") }.getOrNull()
            }.toMap()
            val url = params["url"]?.trim()?.trimEnd('/') ?: return null
            val code = params["code"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (!(url.startsWith("http://") || url.startsWith("https://")) || url.length <= "https://".length) return null
            if (url.any { it.isWhitespace() }) return null
            return PairLink(url, code)
        }
    }
}
