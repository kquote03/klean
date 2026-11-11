package app.klean.core.google

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The OpenID `userinfo` response, the photo URL size and the image check. */
class GoogleProfileTest {
    @Test fun parsesTheFieldsKleanKeepsAndIgnoresTheRest() {
        val info = parseUserInfo(
            """{"sub":"1077","email":"Name@Gmail.com","email_verified":true,"name":"Kenan Q","given_name":"Kenan",
               "family_name":"Q","picture":"https://lh3.googleusercontent.com/a/ACg8oc=s96-c","locale":"en"}""",
        )!!
        assertEquals("name@gmail.com", info.email)
        assertEquals("Kenan Q", info.name)
        assertEquals("Kenan", info.givenName)
        assertEquals("https://lh3.googleusercontent.com/a/ACg8oc=s96-c", info.picture)
    }

    @Test fun noEmailMeansNoProfile() {
        assertNull(parseUserInfo("""{"sub":"1","name":"Kenan"}"""))
        assertNull(parseUserInfo("""{"email":"not-an-address"}"""))
        assertNull(parseUserInfo("<html>"))
    }

    @Test fun blankNamesAndNonHttpsPicturesAreDropped() {
        val info = parseUserInfo("""{"email":"a@b.c","name":"  ","given_name":"","picture":"http://x/y"}""")!!
        assertNull(info.name)
        assertNull(info.givenName)
        assertNull(info.picture)
    }

    @Test fun pictureIsAskedForAt192SquareCropped() {
        assertEquals("https://lh3.googleusercontent.com/a/X=s192-c", pictureAtSize("https://lh3.googleusercontent.com/a/X=s96-c"))
        assertEquals("https://lh3.googleusercontent.com/a/X=s192-c", pictureAtSize("https://lh3.googleusercontent.com/a/X"))
        assertEquals("https://lh3.googleusercontent.com/a-/Y=s192-c", pictureAtSize("https://lh3.googleusercontent.com/a-/Y=s64"))
    }

    @Test fun onlyImageBytesCount() {
        assertTrue(looksLikeImage(FAKE_PNG))
        assertTrue(looksLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertFalse(looksLikeImage("<html>".toByteArray()))
        assertFalse(looksLikeImage(ByteArray(0)))
    }

    @Test fun clientSendsTheTokenAndMapsNoScopeTo401() = runBlocking {
        val server = FakeProfileServer()
        server.profile("tok", "a@b.c", "A B", "A")
        val client = server.client()
        assertEquals("a@b.c", client.userInfo("tok").email)
        val e = runCatching { client.userInfo("other") }.exceptionOrNull()
        assertEquals(401, (e as GoogleHttpException).status)
        assertTrue(server.requested.first().startsWith("https://openidconnect.googleapis.com/v1/userinfo"))
    }

    @Test fun initialFallsBackFromNameToEmail() {
        assertEquals("K", accountInitial("kenan", "x@y.z"))
        assertEquals("Q", accountInitial(null, "quote@gmail.com"))
        assertEquals("Q", accountInitial("  ", "  quote@gmail.com"))
        assertEquals("", accountInitial(null, null))
        assertEquals("K", GoogleAccount("kq@gmail.com", name = "Kenan Q").initial)
        assertEquals("L", GoogleAccount("kq@gmail.com", name = "Kenan Q", givenName = "Lina").initial)
        assertEquals("K", GoogleAccount("kq@gmail.com").initial)
    }

    @Test fun firstNameForTheGreeting() {
        assertEquals("Lina", GoogleAccount("kq@gmail.com", name = "Kenan Q", givenName = "Lina").firstName)
        assertEquals("Kenan", GoogleAccount("kq@gmail.com", name = "Kenan Q").firstName)
        assertEquals("kq", GoogleAccount("kq@gmail.com").firstName)
    }
}
