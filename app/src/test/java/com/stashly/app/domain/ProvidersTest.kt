package com.stashly.app.domain

import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import com.stashly.app.domain.model.AnalyzeOutcome
import com.stashly.app.domain.model.MediaKind
import com.stashly.app.domain.provider.DirectUrlProvider
import com.stashly.app.domain.provider.LinkHint
import com.stashly.app.domain.provider.ProviderRegistry
import com.stashly.app.domain.provider.RestrictedPlatforms
import com.stashly.app.domain.provider.archive.ArchiveApi
import com.stashly.app.domain.provider.archive.InternetArchiveProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ProvidersTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun archive(): InternetArchiveProvider {
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true; isLenient = true }.asConverterFactory("application/json".toMediaType()))
            .build().create(ArchiveApi::class.java)
        return InternetArchiveProvider(api, downloadBase = "https://archive.org")
    }

    private fun respond(block: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = block(request) }
    }
    private suspend fun err(block: suspend () -> Unit): ErrorCode? = try { block(); null } catch (e: AppError) { e.code }

    // ---------- direct ----------
    @Test fun `direct link reports its single real format`() = runBlocking {
        respond { MockResponse().setHeader("Content-Type", "video/mp4").setHeader("Content-Length", "12345") }
        val info = DirectUrlProvider(client).resolve(server.url("/clips/My%20Clip.mp4"))
        assertEquals("My Clip", info.title)
        val f = info.formats.single()
        assertEquals(MediaKind.VIDEO, f.kind); assertEquals("MP4", f.container); assertEquals("Original", f.qualityLabel); assertEquals(12345L, f.sizeBytes)
    }
    @Test fun `direct link uses Content-Disposition file name`() = runBlocking {
        respond { MockResponse().setHeader("Content-Type", "audio/mpeg").setHeader("Content-Disposition", "attachment; filename=\"Song One.mp3\"") }
        val info = DirectUrlProvider(client).resolve(server.url("/dl?id=7"))
        assertEquals("Song One", info.title); assertEquals("mp3", info.formats.single().extension)
    }
    @Test fun `direct link falls back to ranged GET when HEAD is refused`() = runBlocking {
        respond { r -> if (r.method == "HEAD") MockResponse().setResponseCode(405)
            else MockResponse().setResponseCode(206).setHeader("Content-Type", "audio/mpeg").setHeader("Content-Range", "bytes 0-0/999").setBody("x") }
        val info = DirectUrlProvider(client).resolve(server.url("/a.mp3"))
        assertEquals(999L, info.formats.single().sizeBytes)
    }
    @Test fun `web pages are not offered as downloads`() = runBlocking {
        respond { MockResponse().setHeader("Content-Type", "text/html") }
        assertEquals(ErrorCode.NO_MEDIA, err { DirectUrlProvider(client).resolve(server.url("/watch")) })
    }
    @Test fun `executables are blocked`() = runBlocking {
        respond { MockResponse().setHeader("Content-Type", "application/vnd.android.package-archive") }
        assertEquals(ErrorCode.UNSAFE_FILE, err { DirectUrlProvider(client).resolve(server.url("/app.apk")) })
    }
    @Test fun `errors are mapped`() = runBlocking {
        respond { MockResponse().setResponseCode(404) }
        assertEquals(ErrorCode.NOT_FOUND, err { DirectUrlProvider(client).resolve(server.url("/a.mp4")) })
        respond { MockResponse().setResponseCode(403) }
        assertEquals(ErrorCode.AUTH_REQUIRED, err { DirectUrlProvider(client).resolve(server.url("/a.mp4")) })
    }

    // ---------- Internet Archive ----------
    private val itemJson = """{"metadata":{"title":["Great Film"],"mediatype":"movies"},"files":[
      {"name":"film.mp4","format":"MPEG4","size":"9000000","length":"3661.5","height":"720","source":"derivative"},
      {"name":"film_512kb.mp4","format":"512Kb MPEG4","size":"3000000","height":"360","source":"derivative"},
      {"name":"sound/track one.mp3","format":"VBR MP3","size":"500000","bitrate":"128","length":"01:02"},
      {"name":"film.mp4_thumb.jpg","format":"Thumbnail","size":"100"},
      {"name":"secret.mp4","format":"MPEG4","size":"1","private":"true"},
      {"name":"film_meta.xml","format":"Metadata","size":"10"},
      {"name":"__ia_thumb.jpg","format":"JPEG Thumb","size":"5"}]}"""

    @Test fun `archive lists only real formats with real qualities`() = runBlocking {
        respond { MockResponse().setHeader("Content-Type", "application/json").setBody(itemJson) }
        val info = archive().resolve("https://archive.org/details/greatfilm".toHttpUrl())
        assertEquals("Great Film", info.title); assertEquals("Internet Archive", info.sourceName)
        val labels = info.formats.map { it.label }
        assertEquals(listOf("MP4 · 720p", "MP4 · 360p", "MP3 · 128 kbps"), labels)
        assertEquals(3661L, info.durationSeconds)
        assertTrue(info.formats.none { it.url.contains("secret") || it.url.contains("thumb") || it.extension == "xml" })
        assertEquals("https://archive.org/download/greatfilm/sound/track%20one.mp3", info.formats.last().url)
        assertEquals("https://archive.org/services/img/greatfilm", info.thumbnailUrl)
    }
    @Test fun `archive rejects restricted items`() = runBlocking {
        respond { MockResponse().setBody("""{"metadata":{"title":"X","access-restricted-item":"true"},"files":[{"name":"a.mp4","size":"1"}]}""") }
        assertEquals(ErrorCode.AUTH_REQUIRED, err { archive().resolve("https://archive.org/details/x".toHttpUrl()) })
    }
    @Test fun `archive item without media files`() = runBlocking {
        respond { MockResponse().setBody("""{"metadata":{"title":"X"},"files":[{"name":"a.xml","size":"1"},{"name":"b.torrent","size":"2"}]}""") }
        assertEquals(ErrorCode.NO_MEDIA, err { archive().resolve("https://archive.org/details/x".toHttpUrl()) })
    }
    @Test fun `archive missing item and server errors`() = runBlocking {
        respond { MockResponse().setBody("{}") }
        assertEquals(ErrorCode.NOT_FOUND, err { archive().resolve("https://archive.org/details/nope".toHttpUrl()) })
        respond { MockResponse().setResponseCode(503) }
        assertEquals(ErrorCode.SERVER_UNAVAILABLE, err { archive().resolve("https://archive.org/details/x".toHttpUrl()) })
        respond { MockResponse().setBody("<html>not json</html>") }
        assertEquals(ErrorCode.NO_MEDIA, err { archive().resolve("https://archive.org/details/x".toHttpUrl()) })
    }
    @Test fun `archive url recognition`() {
        val p = archive()
        assertTrue(p.supports("https://archive.org/details/abc".toHttpUrl()))
        assertTrue(p.supports("https://www.archive.org/download/abc/file.mp4".toHttpUrl()))
        assertFalse(p.supports("https://archive.org/".toHttpUrl()))
        assertFalse(p.supports("https://evilarchive.org/details/abc".toHttpUrl()))
        assertEquals(3723L, p.parseLength("1:02:03")); assertNull(p.parseLength("abc"))
    }

    // ---------- registry ----------
    private fun registry() = ProviderRegistry(listOf(archive(), DirectUrlProvider(client)), allowHttp = true)

    @Test fun `restricted platforms get a compliant alternative instead of fake support`() = runBlocking {
        for (u in listOf("https://www.youtube.com/watch?v=abc", "https://youtu.be/abc", "https://www.instagram.com/reel/x", "https://vm.tiktok.com/x", "https://fb.watch/x", "https://x.com/a/status/1")) {
            val out = registry().analyze(u)
            assertTrue(u, out is AnalyzeOutcome.Restricted)
        }
        assertEquals(0, server.requestCount) // nothing was ever fetched from those platforms
    }
    @Test fun `registry routes archive links to the archive provider`() = runBlocking {
        respond { MockResponse().setBody(itemJson) }
        val out = registry().analyze("https://archive.org/details/greatfilm") as AnalyzeOutcome.Success
        assertEquals("internet-archive", out.info.providerId)
    }
    @Test fun `live hints`() {
        val r = registry()
        assertEquals(LinkHint.Empty, r.hint(" "))
        assertTrue(r.hint("nonsense") is LinkHint.Invalid)
        assertEquals(LinkHint.Restricted("YouTube"), r.hint("https://youtube.com/watch?v=1"))
        assertEquals(LinkHint.Supported("Internet Archive"), r.hint("https://archive.org/details/x"))
        assertEquals(LinkHint.Supported("Direct link"), r.hint("https://example.com/a.mp4"))
    }
    @Test fun `restricted detection is not fooled by look-alike hosts`() {
        assertNull(RestrictedPlatforms.detect("https://notyoutube.com/x".toHttpUrl()))
        assertEquals("YouTube", RestrictedPlatforms.detect("https://m.youtube.com/x".toHttpUrl()))
    }
}
