package app.klean.shared.ai

import kotlinx.serialization.Serializable

@Serializable data class ParsedItemDto(val name: String, val portion: String? = null, val calories: Int? = null)

@Serializable data class ParsedEntryDto(val items: List<ParsedItemDto>, val mealType: String? = null) {
    fun sanitized(): ParsedEntryDto? {
        val clean = items.filter { it.name.isNotBlank() }.map {
            it.copy(name = it.name.trim().take(80), portion = it.portion?.trim()?.take(60), calories = it.calories?.takeIf { c -> c in 0..5000 })
        }
        if (clean.isEmpty()) return null
        return ParsedEntryDto(clean, mealType?.lowercase()?.takeIf { it in setOf("breakfast", "lunch", "dinner", "snack", "drink") })
    }
}
