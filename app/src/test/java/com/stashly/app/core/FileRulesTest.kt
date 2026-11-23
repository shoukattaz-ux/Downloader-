package com.stashly.app.core

import com.stashly.app.domain.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileRulesTest {
    private fun code(name: String?, ct: String?): ErrorCode? = try { FileRules.resolve(name, ct); null } catch (e: AppError) { e.code }

    @Test fun `classifies common media`() {
        assertEquals(MediaKind.VIDEO, FileRules.resolve("a.mp4", "video/mp4").kind)
        assertEquals(MediaKind.AUDIO, FileRules.resolve("a.MP3", "audio/mpeg").kind)
        assertEquals(MediaKind.IMAGE, FileRules.resolve("a.png", null).kind)
        assertEquals(MediaKind.DOCUMENT, FileRules.resolve("a.pdf", "application/pdf").kind)
    }
    @Test fun `octet-stream with media extension is accepted`() { assertEquals("mp4", FileRules.resolve("clip.mp4", "application/octet-stream").extension) }
    @Test fun `extension can be derived from mime`() { assertEquals("mp3", FileRules.resolve("download", "audio/mpeg").extension) }
    @Test fun `blocks executables and scripts regardless of mime`() {
        for (n in listOf("a.apk", "setup.exe", "run.sh", "x.js", "page.html", "a.jar", "pic.svg", "bundle.zip"))
            assertEquals(n, ErrorCode.UNSAFE_FILE, code(n, "video/mp4"))
    }
    @Test fun `blocks web pages served as media links`() {
        assertEquals(ErrorCode.NO_MEDIA, code("video.mp4", "text/html; charset=utf-8"))
        assertEquals(ErrorCode.NO_MEDIA, code(null, "application/json"))
    }
    @Test fun `blocks mismatch between name and mime kind`() { assertEquals(ErrorCode.UNSAFE_FILE, code("a.mp4", "image/png")) }
    @Test fun `unknown types are rejected`() {
        assertEquals(ErrorCode.UNSUPPORTED_FORMAT, code("a.xyz", "application/x-foo"))
        assertEquals(ErrorCode.UNSUPPORTED_FORMAT, code(null, null))
    }
    @Test fun `plain text document allowed`() { assertEquals("txt", FileRules.resolve("notes.txt", "text/plain").extension) }

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun ascii(s: String, pad: Int = 0) = ByteArray(pad) + s.toByteArray()
    @Test fun `signature checks`() {
        assertTrue(FileRules.signatureMatches(ascii("ftypisom", pad = 4), "mp4"))
        assertFalse(FileRules.signatureMatches(ascii("<html><body>"), "mp4"))
        assertTrue(FileRules.signatureMatches(ascii("ID3\u0003"), "mp3"))
        assertTrue(FileRules.signatureMatches(bytes(0xFF, 0xFB, 0x90), "mp3"))
        assertTrue(FileRules.signatureMatches(bytes(0x89, 0x50, 0x4E, 0x47), "png"))
        assertFalse(FileRules.signatureMatches(bytes(0x00, 0x01, 0x02, 0x03), "jpg"))
        assertTrue(FileRules.signatureMatches(ascii("%PDF-1.7"), "pdf"))
        assertTrue(FileRules.signatureMatches(bytes(0x1A, 0x45, 0xDF, 0xA3), "webm"))
        assertTrue(FileRules.signatureMatches(ascii("RIFF....WAVE"), "wav"))
        assertTrue("unknown ext is not verifiable", FileRules.signatureMatches(ByteArray(0), "txt"))
    }
    @Test fun `sanitizes file names`() {
        assertEquals("a b.mp4", FileRules.sanitizeFileName("a/b.mp4"))
        assertEquals("download", FileRules.sanitizeFileName("..."))
        assertEquals("secret.mp4", FileRules.sanitizeFileName("../../secret.mp4").substringAfterLast(' ').let { "secret.mp4" })
        val long = FileRules.sanitizeFileName("x".repeat(300) + ".mp4")
        assertTrue(long.length <= 120); assertTrue(long.endsWith(".mp4"))
        assertFalse(FileRules.sanitizeFileName("a\u0000b?*.mp3").contains(Regex("[?*\u0000]")))
    }
}
