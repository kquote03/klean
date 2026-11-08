package app.klean.shared.id

/** Name-based (type 3) UUID of `"klean:" + name`. Two devices agree on it without coordination. */
expect fun nameUuid(name: String): String
