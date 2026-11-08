package app.klean.shared.id

actual fun nameUuid(name: String): String =
    java.util.UUID.nameUUIDFromBytes(("klean:" + name).encodeToByteArray()).toString()
