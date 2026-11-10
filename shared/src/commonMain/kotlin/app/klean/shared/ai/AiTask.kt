package app.klean.shared.ai

enum class AiTask { ENTRY_PARSE, CALORIE_ESTIMATE, CAPTURE_ROUTING, DELEGATION }

enum class ProviderId(val wire: String) { GEMINI("gemini"), OPENAI_COMPAT("openai_compat"), ON_DEVICE("on_device") }
