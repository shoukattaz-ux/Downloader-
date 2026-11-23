package com.stashly.app.domain.provider

import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import com.stashly.app.core.FileRules
import com.stashly.app.core.UrlRules
import com.stashly.app.domain.model.MediaFormat
import com.stashly.app.domain.model.MediaInfo
import com.stashly.app.domain.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Handles plain, publicly served media files (e.g. your own server, CDN or a file the owner shares).
 * No metadata is invented: the single offered format is the file exactly as served ("Original").
 */
class DirectUrlProvider(private val client: OkHttpClient) : MediaSourceProvider {
    override val id = "direct"
    override val displayName = "Direct link"

    override fun supports(url: HttpUrl) = true // lowest-priority fallback

    override suspend fun resolve(url: HttpUrl): MediaInfo = withContext(Dispatchers.IO) {
        val probe = probe(url)
        val disposition = filenameFromDisposition(probe.contentDisposition)
        val name = disposition ?: UrlRules.fileNameFromPath(probe.finalUrl) ?: UrlRules.fileNameFromPath(url)
        val resolved = FileRules.resolve(name, probe.contentType)
        val base = name?.let { FileRules.sanitizeFileName(it.substringBeforeLast('.', it)) } ?: "download"
        val format = MediaFormat(
            id = "original",
            kind = resolved.kind,
            container = resolved.extension.uppercase(),
            extension = resolved.extension,
            mimeType = resolved.mimeType,
            qualityLabel = "Original",
            sizeBytes = probe.length,
            url = probe.finalUrl.toString(),
        )
        MediaInfo(
            sourceUrl = url.toString(),
            providerId = id,
            sourceName = probe.finalUrl.host,
            title = base,
            thumbnailUrl = if (resolved.kind == MediaKind.IMAGE) probe.finalUrl.toString() else null,
            durationSeconds = null,
            formats = listOf(format),
        )
    }

    private data class Probe(val finalUrl: HttpUrl, val contentType: String?, val length: Long?, val contentDisposition: String?)

    private fun probe(url: HttpUrl): Probe {
        // HEAD first; some servers reject it, so fall back to a 1-byte ranged GET.
        val head = runCatching { execute(Request.Builder().url(url).head().build()) }.getOrNull()
        if (head != null) return head
        return execute(Request.Builder().url(url).header("Range", "bytes=0-0").get().build())
    }

    private fun execute(request: Request): Probe = try {
        client.newCall(request).execute().use { r ->
            when {
                r.code == 404 || r.code == 410 -> throw AppError(ErrorCode.NOT_FOUND, "This link is no longer available.")
                r.code == 401 || r.code == 403 -> throw AppError(ErrorCode.AUTH_REQUIRED,
                    "This file requires sign-in or permission. Stashly can't download protected content.")
                r.code >= 500 || r.code == 429 -> throw AppError(ErrorCode.SERVER_UNAVAILABLE, "The server is unavailable right now. Try again later.")
                r.code == 405 || r.code == 501 -> throw AppError(ErrorCode.SERVER_UNAVAILABLE, "HEAD not allowed")
                !r.isSuccessful -> throw AppError(ErrorCode.NO_MEDIA, "The server didn't return a downloadable file (HTTP ${r.code}).")
            }
            val length = if (r.code == 206)
                r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
            else r.header("Content-Length")?.toLongOrNull()
            Probe(r.request.url, r.header("Content-Type"), length?.takeIf { it >= 0 }, r.header("Content-Disposition"))
        }
    } catch (e: AppError) {
        throw e
    } catch (e: Exception) {
        throw AppError.from(e)
    }

    internal fun filenameFromDisposition(header: String?): String? {
        if (header == null) return null
        Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.let {
            return runCatching { java.net.URLDecoder.decode(it.groupValues[1].trim(), "UTF-8") }.getOrNull()
        }
        return Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)?.trim()
    }
}
