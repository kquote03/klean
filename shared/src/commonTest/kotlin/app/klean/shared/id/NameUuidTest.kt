package app.klean.shared.id

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NameUuidTest {
    private val uuidRegex = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    @Test fun stableForSameKey() = assertEquals(nameUuid("core.quietHours"), nameUuid("core.quietHours"))
    @Test fun differsForOtherKey() = assertNotEquals(nameUuid("core.quietHours"), nameUuid("diet.cadenceHours"))
    @Test fun matchesUuidFormat() {
        val id = nameUuid("core.quietHours")
        assertTrue(uuidRegex.matches(id), id)
    }
}
