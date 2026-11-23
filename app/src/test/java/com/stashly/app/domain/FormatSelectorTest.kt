package com.stashly.app.domain

import com.stashly.app.domain.model.MediaFormat
import com.stashly.app.domain.model.MediaKind
import com.stashly.app.domain.provider.FormatSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatSelectorTest {
    private fun v(h: Int) = MediaFormat("v$h", MediaKind.VIDEO, "MP4", "mp4", "video/mp4", "${h}p", height = h, url = "https://x/$h")
    private fun a(k: Int) = MediaFormat("a$k", MediaKind.AUDIO, "MP3", "mp3", "audio/mpeg", "$k kbps", bitrateKbps = k, url = "https://x/$k")
    private val all = listOf(v(360), v(720), v(1080), a(64), a(128))

    @Test fun best() { assertEquals("v1080", FormatSelector.pick(all, MediaKind.VIDEO, "BEST")?.id); assertEquals("a128", FormatSelector.pick(all, MediaKind.AUDIO, "BEST")?.id) }
    @Test fun lowest() { assertEquals("v360", FormatSelector.pick(all, MediaKind.VIDEO, "LOWEST")?.id) }
    @Test fun `target picks highest at or below`() { assertEquals("v720", FormatSelector.pick(all, MediaKind.VIDEO, "720")?.id); assertEquals("v720", FormatSelector.pick(all, MediaKind.VIDEO, "900")?.id) }
    @Test fun `target below everything falls back to the smallest real option`() { assertEquals("v360", FormatSelector.pick(all, MediaKind.VIDEO, "240")?.id) }
    @Test fun `falls back to other kind rather than inventing one`() { assertEquals("a128", FormatSelector.pick(listOf(a(64), a(128)), MediaKind.VIDEO, "BEST")?.id) }
    @Test fun `empty gives null`() { assertNull(FormatSelector.pick(emptyList(), MediaKind.VIDEO, "BEST")) }
}
