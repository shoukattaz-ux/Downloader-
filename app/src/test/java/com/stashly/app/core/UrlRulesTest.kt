package com.stashly.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UrlRulesTest {
    private fun code(raw: String): ErrorCode? = try { UrlRules.parse(raw); null } catch (e: AppError) { e.code }

    @Test fun `accepts https links and trims whitespace`() {
        assertEquals("example.com", UrlRules.parse("  https://example.com/a.mp4 ").host)
    }
    @Test fun `adds https when scheme missing`() {
        assertEquals("https", UrlRules.parse("example.com/video.mp4").scheme)
    }
    @Test fun `rejects blank and garbage`() {
        assertEquals(ErrorCode.INVALID_URL, code(""))
        assertEquals(ErrorCode.INVALID_URL, code("not a url"))
        assertEquals(ErrorCode.INVALID_URL, code("hello"))
    }
    @Test fun `rejects cleartext http by default`() { assertEquals(ErrorCode.INVALID_URL, code("http://example.com/a.mp4")) }
    @Test fun `rejects non web schemes`() {
        assertEquals(ErrorCode.INVALID_URL, code("file:///sdcard/a.mp4"))
        assertEquals(ErrorCode.INVALID_URL, code("javascript:alert(1)"))
        assertEquals(ErrorCode.INVALID_URL, code("ftp://example.com/a.mp4"))
    }
    @Test fun `rejects embedded credentials`() { assertEquals(ErrorCode.INVALID_URL, code("https://user:pw@example.com/a.mp4")) }
    @Test fun `rejects local and private hosts`() {
        for (h in listOf("localhost", "127.0.0.1", "10.0.0.5", "192.168.1.20", "172.16.0.1", "169.254.1.1", "nas.local", "[::1]"))
            assertEquals(h, ErrorCode.INVALID_URL, code("https://$h/a.mp4"))
    }
    @Test fun `public ip is allowed`() { assertEquals(null, code("https://8.8.8.8/a.mp4")); assertFalse(UrlRules.isLocalHost("172.32.0.1")) }
    @Test fun `host matching handles subdomains but not look-alikes`() {
        assertTrue(UrlRules.hostMatches("www.youtube.com", "youtube.com"))
        assertFalse(UrlRules.hostMatches("notyoutube.com", "youtube.com"))
    }
    @Test fun `decodes file name from path`() {
        assertEquals("my video.mp4", UrlRules.fileNameFromPath(UrlRules.parse("https://a.com/x/my%20video.mp4")))
    }
}
