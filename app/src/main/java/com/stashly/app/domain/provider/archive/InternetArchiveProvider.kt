package com.stashly.app.domain.provider.archive

import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import com.stashly.app.core.FileRules
import com.stashly.app.core.UrlRules
import com.stashly.app.domain.model.MediaFormat
import com.stashly.app.domain.model.MediaInfo
import com.stashly.app.domain.model.MediaKind
import com.stashly.app.domain.provider.MediaSourceProvider
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import retrofit2.HttpException
import java.io.IOException

/**
 * Internet Archive (archive.org) integration using its official public metadata API and its
 * documented `/download/` endpoint. Items flagged as access-restricted (e.g. lending library) or
 * private files are never offered. Rights remain with the item's owner/licence; the UI reminds the
 * user to respect the licence shown on the item page.
 */
class InternetArchiveProvider(
    private val api: ArchiveApi,
    private val downloadBase: String = "https://archive.org",
) : MediaSourceProvider {
    override val id = "internet-archive"
    override val displayName = "Internet Archive"

    override fun supports(url: HttpUrl) = UrlRules.hostMatches(url.host, "archive.org") && itemId(url) != null

    internal fun itemId(url: HttpUrl): String? {
        val seg = url.pathSegments
        if (seg.size >= 2 && (seg[0] == "details" || seg[0] == "download" || seg[0] == "embed")) return seg[1].takeIf { it.isNotBlank() }
        return null
    }

    override suspend fun resolve(url: HttpUrl): MediaInfo {
        val itemId = itemId(url) ?: throw AppError(ErrorCode.UNSUPPORTED_SOURCE, "That Internet Archive link isn't an item page.")
        val md = try {
            api.metadata(itemId)
        } catch (e: HttpException) {
            throw if (e.code() == 404) AppError(ErrorCode.NOT_FOUND, "This Internet Archive item doesn't exist.")
            else AppError(ErrorCode.SERVER_UNAVAILABLE, "Internet Archive is unavailable right now. Try again later.", e)
        } catch (e: IOException) {
            throw AppError.from(e)
        } catch (e: kotlinx.serialization.SerializationException) {
            throw AppError(ErrorCode.NO_MEDIA, "Internet Archive returned no usable data for this item.", e)
        }
        if (md.metadata == null && md.files.isEmpty())
            throw AppError(ErrorCode.NOT_FOUND, "This Internet Archive item doesn't exist.")
        if (md.is_dark == true || md.metadata?.accessRestricted.isTruthy())
            throw AppError(ErrorCode.AUTH_REQUIRED, "This item is access-restricted by its owner and can't be downloaded.")

        val formats = md.files.asSequence()
            .filter { it.isPrivate != "true" && !it.name.startsWith("__") && !it.name.contains("_thumb", true) }
            .mapNotNull { toFormat(itemId, it) }
            .distinctBy { it.url }
            .sortedWith(compareBy<MediaFormat> { it.kind.ordinal }.thenByDescending { it.height ?: it.bitrateKbps ?: 0 }.thenByDescending { it.sizeBytes ?: 0 })
            .take(24).toList()
        if (formats.isEmpty()) throw AppError(ErrorCode.NO_MEDIA, "This item has no downloadable video, audio, image or document files.")

        val duration = md.files.asSequence().filter { f -> formats.any { it.url.endsWith(encodePath(f.name)) && it.kind != MediaKind.IMAGE } }
            .mapNotNull { parseLength(it.length) }.firstOrNull()
        return MediaInfo(
            sourceUrl = url.toString(),
            providerId = id,
            sourceName = displayName,
            title = md.metadata?.title.firstString() ?: itemId,
            thumbnailUrl = "$downloadBase/services/img/$itemId",
            durationSeconds = duration,
            formats = formats,
        )
    }

    private fun toFormat(itemId: String, f: ArchiveFile): MediaFormat? {
        val ext = FileRules.extensionOf(f.name) ?: return null
        val resolved = FileRules.classify(ext) ?: return null
        val height = f.height?.toIntOrNull()?.takeIf { it > 0 }
        val bitrate = f.bitrate?.toDoubleOrNull()?.toInt()?.takeIf { it > 0 }
        val quality = when {
            resolved.kind == MediaKind.VIDEO && height != null -> "${height}p"
            resolved.kind == MediaKind.AUDIO && bitrate != null -> "$bitrate kbps"
            else -> f.format?.takeIf { it.isNotBlank() } ?: "Original"
        }
        return MediaFormat(
            id = f.name, kind = resolved.kind, container = ext.uppercase(), extension = ext,
            mimeType = resolved.mimeType, qualityLabel = quality, height = height, bitrateKbps = bitrate,
            sizeBytes = f.size?.toLongOrNull(), url = "$downloadBase/download/$itemId/${encodePath(f.name)}",
        )
    }

    private fun encodePath(name: String) = name.split('/').joinToString("/") {
        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
    }

    private fun JsonElement?.firstString(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonArray -> firstOrNull().firstString()
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun JsonElement?.isTruthy(): Boolean = firstString()?.lowercase() in setOf("true", "1")

    internal fun parseLength(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        s.toDoubleOrNull()?.let { return it.toLong() }
        val parts = s.split(':').mapNotNull { it.toLongOrNull() }
        if (parts.size != s.split(':').size) return null
        return parts.fold(0L) { acc, p -> acc * 60 + p }
    }
}
