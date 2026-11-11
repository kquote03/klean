package app.klean.core.design.component

/**
 * The line limit for [text] in a narrow place: a single word gets one line and ends in "…" instead of breaking
 * mid-word (seen at font scale 1.5 in rows and cards); text with spaces keeps [max] lines.
 */
fun lineLimit(text: String, max: Int): Int = if (text.trim().any { it.isWhitespace() }) max else 1
