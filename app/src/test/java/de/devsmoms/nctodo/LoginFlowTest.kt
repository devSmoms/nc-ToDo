package de.devsmoms.nctodo

import de.devsmoms.nctodo.data.LoginFlowUrls
import de.devsmoms.nctodo.dav.DavException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class LoginFlowTest {

    @Test
    fun baseUrlFromUserInput() {
        assertEquals("https://cloud.example.de", LoginFlowUrls.base("cloud.example.de"))
        assertEquals("https://cloud.example.de", LoginFlowUrls.base(" https://Cloud.Example.de/ "))
        assertEquals("https://cloud.example.de", LoginFlowUrls.base("https://cloud.example.de/remote.php/dav"))
        assertEquals("https://cloud.example.de", LoginFlowUrls.base("https://cloud.example.de/index.php/apps/tasks/"))
        // Installation in a sub folder and on a custom port
        assertEquals("https://example.de/nextcloud", LoginFlowUrls.base("example.de/nextcloud/remote.php/dav/"))
        assertEquals("https://example.de:8443", LoginFlowUrls.base("https://example.de:8443/"))
        assertEquals("https://example.de", LoginFlowUrls.base("https://example.de:443"))
    }

    @Test
    fun rejectsHttpAndGarbage() {
        assertFailsWith<DavException> { LoginFlowUrls.base("http://cloud.example.de") }
        assertFailsWith<DavException> { LoginFlowUrls.base("") }
        assertFailsWith<DavException> { LoginFlowUrls.base("https://") }
    }

    @Test
    fun credentialsOnlyForTheSameServer() {
        val base = "https://cloud.example.de"
        assertTrue(LoginFlowUrls.sameServer("https://cloud.example.de/index.php/login/v2/poll", base))
        assertTrue(LoginFlowUrls.sameServer("https://CLOUD.example.de", base))
        assertFalse(LoginFlowUrls.sameServer("http://cloud.example.de/index.php/login/v2/poll", base))
        assertFalse(LoginFlowUrls.sameServer("https://evil.example/index.php/login/v2/poll", base))
        assertFalse(LoginFlowUrls.sameServer("https://cloud.example.de.evil.example/", base))
        assertFalse(LoginFlowUrls.sameServer("https://cloud.example.de:8443/", base))
        assertFalse(LoginFlowUrls.sameServer("not a url", base))
    }
}
