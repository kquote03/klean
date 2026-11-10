package app.klean.assistant

import app.klean.core.capture.CaptureLinks
import app.klean.shared.module.ModuleId
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * App shortcuts and App Actions capabilities (M21, docs/assistant.md): every entry opens the capture sheet,
 * maps to a real module, and the debug twin (app.klean.debug) matches the release file.
 */
class ShortcutsXmlTest {
    private val appDir = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "src/main/res").isDirectory }
    private val main = File(appDir, "src/main/res/xml/shortcuts.xml")
    private val debug = File(appDir, "src/debug/res/xml/shortcuts.xml")
    private val android = "http://schemas.android.com/apk/res/android"

    private fun intents(): List<Element> {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = f.newDocumentBuilder().parse(main)
        val nodes = doc.getElementsByTagName("intent")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    @Test fun debugTwinDiffersOnlyInThePackage() {
        assertEquals(main.readText().replace("\"app.klean\"", "\"app.klean.debug\""), debug.readText())
    }

    @Test fun everyEntryOpensTheCaptureSheet() {
        val all = intents()
        assertEquals(7, all.size)
        all.forEach {
            assertEquals("app.klean.core.capture.CaptureActivity", it.getAttributeNS(android, "targetClass"))
            assertEquals("app.klean", it.getAttributeNS(android, "targetPackage"))
        }
    }

    @Test fun shortcutsAndCapabilitiesMapToModules() {
        val modules = intents().map { i ->
            val data = i.getAttributeNS(android, "data").ifEmpty { null }
            val extras = i.getElementsByTagName("extra")
            val extraModule = (0 until extras.length).map { extras.item(it) as Element }
                .firstOrNull { it.getAttributeNS(android, "key") == "module" }?.getAttributeNS(android, "value")
            CaptureLinks.parse(data, extraModule = extraModule).module
        }
        assertEquals(
            listOf(null, ModuleId.TASKS, ModuleId.DIET, null, ModuleId.DIET, ModuleId.TASKS, ModuleId.REMINDERS),
            modules,
        )
        val caps = main.readText()
        listOf("actions.intent.CREATE_THING", "actions.intent.UPDATE_ITEM_LIST", "actions.intent.RECORD_FOOD_OBSERVATION")
            .forEach { assertTrue(it, caps.contains(it)) }
    }
}
