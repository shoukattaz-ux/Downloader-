package com.stashly.app.domain.model

enum class MediaKind { VIDEO, AUDIO, IMAGE, DOCUMENT }

/** A concrete, genuinely available download option reported by an authorized source. */
data class MediaFormat(
    val id: String,
    val kind: MediaKind,
    val container: String,        // e.g. "MP4"
    val extension: String,        // e.g. "mp4"
    val mimeType: String,
    val qualityLabel: String,     // e.g. "720p", "128 kbps", "Original"
    val height: Int? = null,
    val bitrateKbps: Int? = null,
    val sizeBytes: Long? = null,
    val url: String,              // authorized download URL (https)
) {
    val label: String get() = "$container · $qualityLabel"
}

data class MediaInfo(
    val sourceUrl: String,
    val providerId: String,
    val sourceName: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val formats: List<MediaFormat>,
)

/** Outcome of analysing a pasted link. */
sealed interface AnalyzeOutcome {
    data class Success(val info: MediaInfo) : AnalyzeOutcome
    /** A known platform that does not permit third-party downloading. */
    data class Restricted(val platformName: String, val url: String) : AnalyzeOutcome
}

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELED;
    val isActive get() = this == QUEUED || this == DOWNLOADING
}

enum class FileCategory(val label: String) {
    VIDEO("Videos"), AUDIO("Audio"), IMAGE("Images"), DOCUMENT("Documents");
    companion object {
        fun fromMime(mime: String?): FileCategory = when {
            mime == null -> DOCUMENT
            mime.startsWith("video/") -> VIDEO
            mime.startsWith("audio/") -> AUDIO
            mime.startsWith("image/") -> IMAGE
            else -> DOCUMENT
        }
    }
}

enum class SortOrder(val label: String) { DATE("Newest"), NAME("Name"), SIZE("Size") }
