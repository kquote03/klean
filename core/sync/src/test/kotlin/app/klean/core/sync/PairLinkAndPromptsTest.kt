package app.klean.core.sync

import app.klean.core.notifications.NotificationIds
import app.klean.shared.sync.SyncRecord
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairLinkAndPromptsTest {
    @Test fun parsesTheWebsPairingLink() {
        val link = PairLink.parse("  klean://pair?url=http%3A%2F%2F192.168.240.1%3A8080&code=ABCD-EFGH\n")
        assertEquals(PairLink("http://192.168.240.1:8080", "ABCD-EFGH"), link)
        assertEquals("https://klean.example.ts.net", PairLink.parse("klean://pair?code=x&url=https%3A%2F%2Fklean.example.ts.net%2F")!!.url)
    }

    @Test fun rejectsAnythingElse() {
        listOf(
            "", "hello", "https://klean.test", "klean://pair?url=http%3A%2F%2Fa", "klean://pair?code=ABCD",
            "klean://pair?url=ftp%3A%2F%2Fa&code=X", "klean://pair?url=http%3A%2F%2F&code=X", "klean://pair?url=http%3A%2F%2Fa%20b&code=X",
        ).forEach { assertNull(it, PairLink.parse(it)) }
    }

    private fun prompt(trigger: String, detail: String?, outcome: String, device: String = "other") = SyncRecord(
        "prompt_log", "p-$trigger", 10, device, false,
        buildJsonObject {
            put("trigger", trigger)
            put("trigger_detail", detail?.let(::JsonPrimitive) ?: JsonNull)
            put("outcome", outcome)
        },
    )

    @Test fun promptsAnsweredOnAnotherDeviceMapToTheirNotifications() {
        val got = PromptNotifications.answeredElsewhere(
            listOf(
                prompt("CADENCE", null, "LOGGED"),
                prompt("REMINDER", "rem-1", "DISMISSED"),
                prompt("WORKOUT", null, "LOGGED"),
                prompt("DELIVERY", "com.talabat", "PENDING"), // still open: keep it
                prompt("GEOFENCE", "place", "IGNORED"),
                prompt("UNLOCK_SHEET", null, "LOGGED", device = "me"), // answered here
            ),
            me = "me",
        )
        assertEquals(
            listOf(
                PromptNotification(null, NotificationIds.DIET_PROMPT),
                PromptNotification("rem-1", PromptNotifications.REMINDER_ID),
                PromptNotification(null, PromptNotifications.WORKOUT_ID),
            ),
            got,
        )
    }

    @Test fun blobNamesNeverEscapeTheirDirectory() {
        val files = BlobFiles(tempDir("blobs"))
        assertNull(files.file(BlobDir.PHOTOS, "../secrets"))
        assertNull(files.file(BlobDir.PHOTOS, "a/b.jpg"))
        assertEquals("p.jpg", files.file(BlobDir.PHOTOS, "p.jpg")!!.name)
        val target = files.file(BlobDir.ROUTES, "r.gpx")!!
        assertFalse(files.write(target, byteArrayOf(1, 2), "00"))
        assertTrue(files.write(target, byteArrayOf(1, 2), BlobFiles.sha256(byteArrayOf(1, 2))))
        assertEquals("image/jpeg", BlobFiles.contentType("x.JPG"))
    }
}
