package com.stashly.app.download

import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private class FileSink(val file: File) : DownloadSink {
    override fun length() = if (file.exists()) file.length() else 0L
    override fun openOutput(append: Boolean): OutputStream = java.io.FileOutputStream(file, append)
}

class HttpDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    private lateinit var downloader: HttpDownloader
    private lateinit var file: File
    private lateinit var sink: FileSink

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        downloader = HttpDownloader(client, progressIntervalMs = 0)
        file = tmp.newFile("out.bin").also { it.delete() }
        sink = FileSink(file)
    }
    @After fun tearDown() { server.shutdown() }

    private fun data(n: Int, seed: Long = 1) = ByteArray(n).also { java.util.Random(seed).nextBytes(it) }
    private fun url() = server.url("/f.mp4").toString()

    /** Serves [bytes] honouring Range/If-Range when [ranges] is true. */
    private fun serve(bytes: ByteArray, ranges: Boolean = true, etag: String = "\"v1\"") {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                if (ranges && range != null) {
                    val start = range.removePrefix("bytes=").substringBefore('-').toLong()
                    if (start >= bytes.size) return MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */${bytes.size}")
                    val body = Buffer().write(bytes, start.toInt(), bytes.size - start.toInt())
                    return MockResponse().setResponseCode(206).setHeader("ETag", etag)
                        .setHeader("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}").setBody(body)
                }
                return MockResponse().setHeader("ETag", etag).setBody(Buffer().write(bytes))
            }
        }
    }

    @Test fun `downloads a whole file and reports progress`() = runBlocking {
        val bytes = data(300_000); serve(bytes)
        val progress = mutableListOf<DownloadProgress>()
        val r = downloader.download(url(), sink, null) { progress += it }
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(bytes.size.toLong(), r.totalBytes); assertEquals("\"v1\"", r.validator)
        assertTrue(progress.isNotEmpty()); assertEquals(bytes.size.toLong(), progress.last().downloadedBytes)
        assertTrue("monotonic", progress.zipWithNext().all { (a, b) -> b.downloadedBytes >= a.downloadedBytes })
    }

    @Test fun `resumes from partial data using Range and If-Range`() = runBlocking {
        val bytes = data(200_000); serve(bytes)
        file.writeBytes(bytes.copyOf(70_000))
        downloader.download(url(), sink, "\"v1\"") {}
        assertArrayEquals(bytes, file.readBytes())
        val req = server.takeRequest()
        assertEquals("bytes=70000-", req.getHeader("Range")); assertEquals("\"v1\"", req.getHeader("If-Range"))
    }

    @Test fun `restarts cleanly when server ignores Range`() = runBlocking {
        val bytes = data(150_000); serve(bytes, ranges = false)
        file.writeBytes(ByteArray(40_000) { 9 }) // stale partial
        downloader.download(url(), sink, null) {}
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun `already complete file is detected through 416`() = runBlocking {
        val bytes = data(50_000); serve(bytes); file.writeBytes(bytes)
        val r = downloader.download(url(), sink, null) {}
        assertEquals(50_000L, r.totalBytes); assertArrayEquals(bytes, file.readBytes())
    }

    @Test fun `recovers from a connection dropped mid-body by resuming`() = runBlocking {
        val bytes = data(600_000)
        val normal = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
                val body = Buffer().write(bytes, start, bytes.size - start)
                val resp = if (range != null) MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}")
                else MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY) // first attempt breaks
                return resp.setHeader("ETag", "\"v1\"").setBody(body)
            }
        }
        server.dispatcher = normal
        val r = downloader.downloadWithRetries(url(), sink, null, maxAttempts = 3, baseDelayMs = 1) {}
        assertArrayEquals(bytes, file.readBytes()); assertEquals(bytes.size.toLong(), r.totalBytes)
        assertEquals(2, server.requestCount)
    }

    @Test fun `gives up after max attempts on server errors`() = runBlocking {
        server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(503) }
        try { downloader.downloadWithRetries(url(), sink, null, maxAttempts = 3, baseDelayMs = 1) {}; fail() }
        catch (e: AppError) { assertEquals(ErrorCode.SERVER_UNAVAILABLE, e.code) }
        assertEquals(3, server.requestCount)
    }

    @Test fun `404 and 403 fail immediately without retry`() = runBlocking {
        for ((status, code) in listOf(404 to ErrorCode.NOT_FOUND, 403 to ErrorCode.AUTH_REQUIRED)) {
            server.dispatcher = object : Dispatcher() { override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(status) }
            val before = server.requestCount
            try { downloader.downloadWithRetries(url(), sink, null, maxAttempts = 4, baseDelayMs = 1) {}; fail() }
            catch (e: AppError) { assertEquals(code, e.code) }
            assertEquals(1, server.requestCount - before)
        }
    }

    @Test fun `content type check can veto the download before any bytes are written`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setHeader("Content-Type", "text/html").setBody("<html>login</html>")
        }
        try {
            downloader.download(url(), sink, null, checkContentType = { ct -> if (ct!!.startsWith("text/html")) throw AppError(ErrorCode.NO_MEDIA, "page") }) {}
            fail()
        } catch (e: AppError) { assertEquals(ErrorCode.NO_MEDIA, e.code) }
        assertEquals(0L, sink.length())
    }

    @Test fun `disk full is reported as insufficient storage`() = runBlocking {
        serve(data(100_000))
        val full = object : DownloadSink {
            override fun length() = 0L
            override fun openOutput(append: Boolean) = object : OutputStream() {
                override fun write(b: Int) = throw IOException("write failed: ENOSPC (No space left on device)")
                override fun write(b: ByteArray, off: Int, len: Int) = throw IOException("write failed: ENOSPC (No space left on device)")
            }
        }
        try { downloader.downloadWithRetries(url(), full, null, baseDelayMs = 1) {}; fail() }
        catch (e: AppError) { assertEquals(ErrorCode.INSUFFICIENT_STORAGE, e.code) }
        assertEquals("not retried", 1, server.requestCount)
    }

    @Test fun `short body is flagged as interrupted`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY).setBody(Buffer().write(data(100_000)))
        }
        try { downloader.download(url(), sink, null) {}; fail() } catch (e: AppError) { assertTrue(e.code.isRetryable) }
        assertTrue("partial data is kept for resume", file.length() < 100_000)
    }

    @Test fun `cancellation stops the transfer and keeps the partial file`() = runBlocking {
        val bytes = data(2_000_000)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody(Buffer().write(bytes)).throttleBody(300_000, 100, TimeUnit.MILLISECONDS)
        }
        val job = async(Dispatchers.IO) { downloader.download(url(), sink, null) {} }
        withTimeout(10_000) { while (file.length() == 0L) kotlinx.coroutines.delay(20) }
        job.cancel()
        try { job.await(); fail() } catch (_: CancellationException) {}
        assertTrue(file.length() in 1 until bytes.size.toLong())
    }

    @Test fun `large file downloads intact and resumes after a pause`() = runBlocking {
        val bytes = data(48 * 1024 * 1024, seed = 7); serve(bytes)
        // first part, as if paused at ~40%
        file.writeBytes(bytes.copyOf(19_000_000))
        downloader.download(url(), sink, "\"v1\"") {}
        assertEquals(bytes.size.toLong(), file.length())
        val md = { b: ByteArray -> MessageDigest.getInstance("SHA-256").digest(b).toList() }
        assertEquals(md(bytes), md(file.readBytes()))
    }
}
