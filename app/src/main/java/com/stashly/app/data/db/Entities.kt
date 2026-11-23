package com.stashly.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.stashly.app.domain.model.DownloadStatus

@Entity(
    tableName = "downloads",
    indices = [Index("status"), Index("createdAt"), Index(value = ["sourceUrl", "formatId"])],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val mediaUrl: String,
    val providerId: String,
    val sourceName: String,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val formatId: String,
    val format: String,            // e.g. MP4
    val quality: String,           // e.g. 720p
    val kind: String,              // MediaKind name
    val thumbnailUrl: String? = null,
    val folder: String = "",       // target sub-folder inside the library root
    val wifiOnly: Boolean = false,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val speedBps: Long = 0,
    val etaSeconds: Long? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val validator: String? = null, // ETag / Last-Modified for If-Range
    val fileUri: String? = null,   // MediaStore URI (pending while downloading, final when complete)
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
) {
    val progressPercent: Int get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

@Entity(
    tableName = "library_files",
    indices = [Index("category"), Index("folder"), Index("dateAdded"), Index("displayName")],
)
data class LibraryFileEntity(
    @PrimaryKey val uri: String,
    val mediaStoreId: Long,
    val displayName: String,
    val mimeType: String,
    val category: String,          // FileCategory name
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val dateAdded: Long,           // epoch millis
    val folder: String,            // path relative to the library root ("" for root)
)

@Entity(tableName = "library_folders")
data class FolderEntity(
    @PrimaryKey val path: String,  // relative to library root, e.g. "Trips/2025"
    val createdAt: Long = System.currentTimeMillis(),
)
