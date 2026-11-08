package app.klean.shared.id

import kotlin.random.Random
import kotlin.time.Clock

object UuidV7 {
    fun generate(nowMs: Long = Clock.System.now().toEpochMilliseconds(), random: Random = Random.Default): String {
        val b = ByteArray(16)
        random.nextBytes(b)
        for (i in 0 until 6) b[i] = (nowMs ushr (8 * (5 - i))).toByte()
        b[6] = ((b[6].toInt() and 0x0F) or 0x70).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
    fun timestampOf(id: String): Long = id.replace("-", "").substring(0, 12).toLong(16)
}
