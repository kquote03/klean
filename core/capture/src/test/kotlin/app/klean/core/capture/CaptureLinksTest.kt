package app.klean.core.capture

import app.klean.shared.module.ModuleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLinksTest {
    @Test fun readsTextAndModuleFromTheDeepLink() {
        val l = CaptureLinks.parse("klean://capture?text=Buy%20milk%20tomorrow&module=task")
        assertEquals(CaptureLink("Buy milk tomorrow", ModuleId.TASKS), l)
        assertEquals(ModuleId.DIET, CaptureLinks.parse("klean://capture?module=food").module)
        assertEquals("a+b & c", CaptureLinks.parse("klean://capture?text=a%2Bb%20%26%20c").text)
    }

    @Test fun assistantExtrasWinAndUnknownModulesMeanUniversalCapture() {
        val l = CaptureLinks.parse("klean://capture?text=x&module=diet", extraText = " call mum ", extraModule = "reminder")
        assertEquals(CaptureLink("call mum", ModuleId.REMINDERS), l)
        assertNull(CaptureLinks.parse(null, extraText = "x", extraModule = "spaceship").module)
        assertEquals(CaptureLink(null, null), CaptureLinks.parse("klean://capture"))
    }

    @Test fun onlyCaptureLinksCountAndTextIsCapped() {
        assertTrue(CaptureLinks.isCaptureLink("klean://capture?text=x"))
        assertFalse(CaptureLinks.isCaptureLink("klean://today"))
        assertFalse(CaptureLinks.isCaptureLink("https://capture"))
        assertNull(CaptureLinks.parse("klean://today?text=x").text)
        assertEquals(500, CaptureLinks.parse(null, extraText = "a".repeat(900)).text!!.length)
    }

    @Test fun buildsLinksThatParseBack() {
        val uri = CaptureLinks.uri("Eat 2 eggs", ModuleId.DIET)
        assertEquals("klean://capture?text=Eat%202%20eggs&module=diet", uri)
        assertEquals(CaptureLink("Eat 2 eggs", ModuleId.DIET), CaptureLinks.parse(uri))
    }
}
