package app.klean.feature.contacts.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DavBackendMatchTest {
    private val backend = "http://192.168.240.1:8080"

    @Test fun aCollectionUnderTheBackendsDavRootMatches() {
        assertTrue(DavBackendMatch.matches(backend, listOf("HTTP://192.168.240.1:8080/dav/contacts/klean/"), emptyList(), emptySet()))
        assertTrue(DavBackendMatch.matches("$backend/", listOf("http://192.168.240.1:8080/dav/contacts/klean/"), emptyList(), emptySet()))
        assertFalse(DavBackendMatch.matches(backend, listOf("https://nextcloud.example/remote.php/dav/addressbooks/me/"), emptyList(), setOf("c1")))
        assertFalse(DavBackendMatch.matches(backend, listOf("http://192.168.240.1:8080/other/"), emptyList(), emptySet()))
    }

    @Test fun membersNamedAfterKleanContactsMatch() {
        val ids = setOf("c1", "c2", "c3", "c4")
        assertTrue(DavBackendMatch.matches(backend, emptyList(), listOf("c1.vcf", "c2.vcf", "c3.vcf", "x.vcf"), ids))
        assertFalse(DavBackendMatch.matches(backend, emptyList(), listOf("c1.vcf", "x.vcf"), ids))
        // A small address book: all of them.
        assertTrue(DavBackendMatch.matches(backend, emptyList(), listOf("c1.vcf"), setOf("c1")))
        assertFalse(DavBackendMatch.matches(backend, emptyList(), listOf("c1.vcf"), emptySet()))
    }
}
