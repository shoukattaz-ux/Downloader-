package com.stashly.app.download

import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/** Where bytes are written. Android uses a pending MediaStore entry; tests use a plain file. */
interface DownloadSink {
    /** Bytes already stored (the resume offset). */
    fun length(): Long
    /** Opens the sink for writing; when [append] is false the existing content is truncated. */
    fun openOutput(append: Boolean): OutputStream
}

data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long, val bytesPerSecond: Long, val etaSeconds: Long?)
data class DownloadResult(val totalBytes: Long, val validator: String?, val contentType: String?)

/**
 * Resumable HTTP(S) downloader. Pure JVM (no Android dependencies) so it is fully unit tested.
 *
 * - Resumes with `Range` + `If-Range`, falling back to a clean restart if the server ignores/rejects it.
 * - Distinguishes network failures from disk failures (e.g. ENOSPC).
 * - Retries transient failures with exponential back-off, continuing from the bytes already stored.
 */
class HttpDownloader(
    private val client: OkHttpClient,
    private val nanoTime: () -> Long = System::nanoTime,
    private val progressIntervalMs: Long = 500,
) {
    suspend fun downloadWithRetries(
        url: String,
        sink: DownloadSink,
        validator: String?,
        maxAttempts: Int = 5,
        baseDelayMs: Long = 1_000,
        checkContentType: (String?) -> Unit = {},
        onStart: suspend (validator: String?, totalBytes: Long) -> Unit = { _, _ -> },
        onProgress: suspend (DownloadProgress) -> Unit,
    ): DownloadResult {
        var attempt = 0
        val currentValidator = validator
        while (true) {
            try {
                return download(url, sink, currentValidator, checkContentType, onStart, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppError) {
                attempt++
                if (!e.code.isRetryable || attempt >= maxAttempts) throw e
                delay(baseDelayMs * (1L shl (attempt - 1)).coerceAtMost(30))
            }
        }
    }

    suspend fun download(
        url: String,
        sink: DownloadSink,
        validator: String?,
        checkContentType: (String?) -> Unit = {},
        onStart: suspend (validator: String?, totalBytes: Long) -> Unit = { _, _ -> },
        onProgress: suspend (DownloadProgress) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        var offset = sink.length()
        var restarted = false
        while (true) {
            val request = Request.Builder().url(url).header("Accept-Encoding", "identity").apply {
                if (offset > 0) {
                    header("Range", "bytes=$offset-")
                    validator?.let { header("If-Range", it) }
                }
            }.build()
            val call = client.newCall(request)
            val handle = coroutineContext.job.invokeOnCompletion { call.cancel() }
            try {
                val response = try { call.execute() } catch (e: IOException) {
                    coroutineContext.ensureActive()
                    throw AppError.from(e)
                }
                response.use { r ->
                    when {
                        r.code == 416 -> {
                            val total = r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                            if (total != null && total == offset && offset > 0)
                                return@withContext DownloadResult(total, validator, r.header("Content-Type"))
                            if (restarted) throw AppError(ErrorCode.SERVER_UNAVAILABLE, "The server rejected the download range.")
                            restarted = true; offset = 0; sink.openOutput(false).close(); return@use
                        }
                        r.code == 404 || r.code == 410 -> throw AppError(ErrorCode.NOT_FOUND, "This file is no longer available.")
                        r.code == 401 || r.code == 403 -> throw AppError(ErrorCode.AUTH_REQUIRED, "This file requires sign-in or permission, which isn't supported.")
                        r.code == 429 || r.code >= 500 -> throw AppError(ErrorCode.SERVER_UNAVAILABLE, "The server is unavailable right now.")
                        !r.isSuccessful -> throw AppError(ErrorCode.SERVER_UNAVAILABLE, "The server returned an error (HTTP ${r.code}).")
                    }
                    checkContentType(r.header("Content-Type"))
                    var append = offset > 0
                    val total: Long
                    if (r.code == 206) {
                        val range = r.header("Content-Range") // bytes a-b/total
                        val start = range?.substringAfter("bytes ")?.substringBefore('-')?.toLongOrNull()
                        if (start != offset) throw AppError(ErrorCode.INTERRUPTED, "The server resumed from the wrong position.")
                        total = range.substringAfterLast('/').toLongOrNull() ?: -1
                    } else {
                        // 200: server sent the whole file (or ignored our Range) -> restart from zero.
                        if (offset > 0) { append = false; offset = 0 }
                        total = r.header("Content-Length")?.toLongOrNull() ?: -1
                    }
                    val newValidator = r.header("ETag")?.takeIf { !it.startsWith("W/") } ?: r.header("Last-Modified")
                    val body = r.body ?: throw AppError(ErrorCode.NO_MEDIA, "The server sent an empty response.")
                    onStart(newValidator, total)
                    stream(body.byteStream(), sink, append, offset, total, onProgress)
                    return@withContext DownloadResult(if (total >= 0) total else sink.length(), newValidator, r.header("Content-Type"))
                }
            } finally {
                handle.dispose()
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }

    private suspend fun stream(
        input: java.io.InputStream, sink: DownloadSink, append: Boolean, startOffset: Long, total: Long,
        onProgress: suspend (DownloadProgress) -> Unit,
    ) {
        val buffer = ByteArray(64 * 1024)
        var downloaded = startOffset
        var windowStart = nanoTime()
        var windowBytes = 0L
        var lastEmit = windowStart
        var smoothed = 0.0
        val out = try { sink.openOutput(append) } catch (e: IOException) { throw AppError.from(e) }
        try {
            input.use { src ->
                while (true) {
                    coroutineContext.ensureActive()
                    val n = try { src.read(buffer) } catch (e: IOException) {
                        coroutineContext.ensureActive()
                        throw AppError(ErrorCode.NETWORK, "The connection was interrupted.", e)
                    }
                    if (n < 0) break
                    try { out.write(buffer, 0, n) } catch (e: IOException) { throw AppError.from(e) }
                    downloaded += n; windowBytes += n
                    val now = nanoTime()
                    if ((now - lastEmit) / 1_000_000 >= progressIntervalMs) {
                        val seconds = (now - windowStart) / 1e9
                        val instant = if (seconds > 0) windowBytes / seconds else 0.0
                        smoothed = if (smoothed == 0.0) instant else smoothed * 0.6 + instant * 0.4
                        val eta = if (total > 0 && smoothed > 1) ((total - downloaded) / smoothed).toLong() else null
                        onProgress(DownloadProgress(downloaded, total, smoothed.toLong(), eta))
                        lastEmit = now; windowStart = now; windowBytes = 0
                    }
                }
            }
            try { out.flush() } catch (e: IOException) { throw AppError.from(e) }
        } finally {
            runCatching { out.close() }
        }
        if (total >= 0 && downloaded != total)
            throw AppError(ErrorCode.INTERRUPTED, "The download ended before the whole file was received.")
        onProgress(DownloadProgress(downloaded, if (total >= 0) total else downloaded, 0, 0))
    }
}
