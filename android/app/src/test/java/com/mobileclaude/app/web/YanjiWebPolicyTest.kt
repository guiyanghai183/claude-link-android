package com.mobileclaude.app.web

import org.junit.Assert.*
import org.junit.Test

class YanjiWebPolicyTest {
    @Test fun defaultAddressOpensNotebookInsteadOfPortal() {
        assertEquals(YanjiWebPolicy.DEFAULT_URL, YanjiWebPolicy.normalizeUrl(" 8.133.186.127 "))
        assertEquals(YanjiWebPolicy.DEFAULT_URL, YanjiWebPolicy.normalizeUrl("https://8.133.186.127/"))
    }

    @Test fun customHttpsAddressAndPortRemainConfigurable() {
        assertEquals("https://notes.example.org:9443/notebook/", YanjiWebPolicy.normalizeUrl("notes.example.org:9443/notebook/"))
    }

    @Test fun unsafeOrCredentialBearingAddressesAreRejected() {
        listOf(
            "", "http://8.133.186.127/yanji/", "javascript:alert(1)", "file:///sdcard/test.html",
            "https://user:password@notes.example.org/", "https://notes.example.org:99999/",
            "https://notes.example.org/?token=secret", "https://notes.example.org/#secret",
        ).forEach { value ->
            assertTrue("Accepted unsafe address: " + value, runCatching { YanjiWebPolicy.normalizeUrl(value) }.isFailure)
        }
    }

    @Test fun sameOriginAllowsNotebookNavigationAndDefaultPort() {
        assertTrue(YanjiWebPolicy.isTrustedPage("https://8.133.186.127:443/yanji/?record=123", YanjiWebPolicy.DEFAULT_URL))
        assertTrue(YanjiWebPolicy.isTrustedPage("https://8.133.186.127/yanji/#record=123", YanjiWebPolicy.DEFAULT_URL))
    }

    @Test fun foreignOriginsPortsSchemesAndCredentialsNeverStayInWebView() {
        listOf(
            "https://8.133.186.127.evil.example/yanji/", "https://8.133.186.127:9443/yanji/",
            "http://8.133.186.127/yanji/", "https://user@8.133.186.127/yanji/",
            "content://com.example/provider/file", "javascript:alert(1)", "file:///sdcard/test.html",
        ).forEach { assertFalse(it, YanjiWebPolicy.isTrustedPage(it, YanjiWebPolicy.DEFAULT_URL)) }
    }

    @Test fun externalBrowserOnlyReceivesHttpsLinks() {
        assertTrue(YanjiWebPolicy.isExternalWebUrl("https://example.org/paper.pdf"))
        listOf("intent://evil", "tel:123", "javascript:alert(1)", "https://user:password@example.org/").forEach {
            assertFalse(it, YanjiWebPolicy.isExternalWebUrl(it))
        }
    }

    @Test fun readableZoomHonorsAccessibilityScaleAndCorruptPreferencesRecover() {
        assertEquals(110, YanjiWebPolicy.restoredTextZoom(-20))
        assertEquals(143, YanjiWebPolicy.effectiveTextZoom(110, 1.3f))
        assertEquals(240, YanjiWebPolicy.effectiveTextZoom(160, 2.0f))
    }
}
