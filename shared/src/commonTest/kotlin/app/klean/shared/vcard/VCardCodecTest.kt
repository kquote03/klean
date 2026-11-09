package app.klean.shared.vcard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VCardCodecTest {
    private val full = VCard(
        uid = "urn:uuid:0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b",
        formattedName = "Amira Haddad",
        name = VCardName(family = "Haddad", given = "Amira", additional = "", prefixes = "Dr.", suffixes = ""),
        phones = listOf(VCardTyped(listOf("cell"), "+971 50 123 4567"), VCardTyped(listOf("work", "voice"), "+971 4 000 0000")),
        emails = listOf(VCardTyped(listOf("home"), "amira@example.com")),
        addresses = listOf(VCardAddress(types = listOf("home"), street = "12 Palm St, Apt 3", locality = "Dubai", region = "", postalCode = "00000", country = "UAE")),
        org = "Clinic; North",
        note = "Met at the park.\nLikes tea, not coffee.",
        birthday = VCardDate(1990, 4, 12),
        anniversary = VCardDate(null, 2, 29),
        extras = listOf(ContentLine(null, "X-CUSTOM", emptyList(), "keep me")),
    )

    @Test fun roundTripsVersion4() {
        val text = VCardCodec.write(full, VCardVersion.V4)
        assertTrue(text.startsWith("BEGIN:VCARD\r\nVERSION:4.0\r\n"), text)
        assertTrue("BDAY:19900412\r\n" in text, text)
        assertTrue("ANNIVERSARY:--0229\r\n" in text, text)
        assertEquals(listOf(full), VCardCodec.parse(text))
    }

    @Test fun roundTripsVersion3() {
        val text = VCardCodec.write(full, VCardVersion.V3)
        assertTrue("VERSION:3.0\r\n" in text)
        assertTrue("BDAY:1990-04-12\r\n" in text, text)
        assertTrue("TEL;TYPE=cell:" in text, text)
        assertEquals(listOf(full), VCardCodec.parse(text))
    }

    @Test fun escapesAndFoldsLongLines() {
        val long = full.copy(note = "x".repeat(200) + "é".repeat(50))
        val text = VCardCodec.write(long, VCardVersion.V4)
        text.split("\r\n").forEach { assertTrue(it.encodeToByteArray().size <= 75, "line too long: $it") }
        assertTrue("ORG:Clinic\\; North" in text, text)
        assertEquals(long, VCardCodec.parse(text).single())
    }

    @Test fun parsesAppleAndDavx5Flavours() {
        val text = """
            BEGIN:VCARD
            VERSION:3.0
            PRODID:-//Apple Inc.//iPhone OS 17.0//EN
            N:Okafor;Chidi;;;
            FN:Chidi Okafor
            item1.TEL;type=CELL;type=VOICE;type=pref:+1 555 0100
            item1.X-ABLABEL:mobile
            EMAIL;type=INTERNET;type=WORK:chidi@
             example.org
            BDAY;X-APPLE-OMIT-YEAR=1604:1604-07-03
            NOTE:Line one\nLine two\, with comma
            END:VCARD
            BEGIN:VCARD
            VERSION:4.0
            UID:abc
            FN:No Name Parts
            BDAY:--1225
            END:VCARD
        """.trimIndent().replace("\n", "\r\n")
        val cards = VCardCodec.parse(text)
        assertEquals(2, cards.size)
        val chidi = cards[0]
        assertNull(chidi.uid)
        assertEquals("Chidi Okafor", chidi.formattedName)
        assertEquals("Okafor", chidi.name?.family)
        assertEquals(VCardTyped(listOf("cell", "voice", "pref"), "+1 555 0100"), chidi.phones.single())
        assertEquals("chidi@example.org", chidi.emails.single().value)
        assertEquals(VCardDate(null, 7, 3), chidi.birthday)
        assertEquals("Line one\nLine two, with comma", chidi.note)
        assertTrue(chidi.extras.any { it.name == "X-ABLABEL" && it.group == "item1" })
        assertEquals(VCardDate(null, 12, 25), cards[1].birthday)
        assertEquals("abc", cards[1].uid)
    }

    @Test fun acceptsBareLfAndMissingFn() {
        val card = VCardCodec.parse("BEGIN:VCARD\nVERSION:4.0\nN:Doe;Jane;;;\nEND:VCARD\n").single()
        assertEquals("Jane Doe", card.formattedName)
    }

    @Test fun datesParseAllCommonForms() {
        assertEquals(VCardDate(1985, 4, 12), VCardDate.parse("19850412"))
        assertEquals(VCardDate(1985, 4, 12), VCardDate.parse("1985-04-12"))
        assertEquals(VCardDate(1985, 4, 12), VCardDate.parse("1985-04-12T00:00:00Z"))
        assertEquals(VCardDate(null, 4, 12), VCardDate.parse("--0412"))
        assertEquals(VCardDate(null, 4, 12), VCardDate.parse("--04-12"))
        assertNull(VCardDate.parse("garbage"))
    }
}
