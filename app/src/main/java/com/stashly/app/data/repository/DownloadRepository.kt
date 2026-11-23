package com.stashly.app.data.repository

import android.net.Uri
import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import com.stashly.app.core.FileRules
import com.stashly.app.data.db.DownloadDao
import com.stashly.app.data.db.DownloadEntity
import com.stashly.app.domain.model.DownloadStatus
import com.stashly.app.domain.model.MediaFormat
import com.stashly.app.domain.model.MediaInfo
import com.stashly.app.download.DownloadNotifier
import com.stashly.app.download.DownloadScheduler
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val dao: DownloadDao,
    private val scheduler: DownloadScheduler,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val notifier: DownloadNotifier,
) {
    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()
    fun observe(id: Long): Flow<DownloadEntity?> = dao.observe(id)
    fun observeUnfinishedCount() = dao.observeUnfinishedCount()
    suspend fun get(id: Long) = dao.get(id)

    /** Validates, de-duplicates, persists and schedules a download. Returns the new record id. */
    suspend fun enqueue(info: MediaInfo, format: MediaFormat): Long {
        require(format.url.startsWith("https://")) { "Only https downloads are allowed" }
        val duplicate = dao.findDuplicate(info.sourceUrl, format.id)
        if (duplicate != null) {
            val stillThere = duplicate.status != DownloadStatus.COMPLETED ||
                duplicate.fileUri?.let { library.exists(Uri.parse(it)) } == true
            if (stillThere) throw AppError(ErrorCode.DUPLICATE,
                if (duplicate.status == DownloadStatus.COMPLETED) "You've already downloaded this. Find it in Files."
                else "This is already in your download queue.")
        }
        val s = settings.current()
        val suffix = if (format.qualityLabel.equals("Original", true)) "" else " (${format.qualityLabel})"
        val fileName = FileRules.sanitizeFileName(info.title.take(90) + suffix) + "." + format.extension
        FileRules.resolve(fileName, format.mimeType) // defence in depth
        val id = dao.insert(
            DownloadEntity(
                sourceUrl = info.sourceUrl, mediaUrl = format.url, providerId = info.providerId, sourceName = info.sourceName,
                title = info.title, fileName = fileName, mimeType = format.mimeType, formatId = format.id,
                format = format.container, quality = format.qualityLabel, kind = format.kind.name,
                thumbnailUrl = info.thumbnailUrl, folder = s.defaultFolder, wifiOnly = s.wifiOnly,
                totalBytes = format.sizeBytes ?: -1,
            )
        )
        scheduler.enqueue(id, s.wifiOnly, replace = true)
        return id
    }

    suspend fun pause(id: Long) {
        dao.setStatus(id, DownloadStatus.PAUSED) // persist first so the worker's cancellation handler sees it
        scheduler.cancel(id)
        notifier.dismiss(id)
    }

    suspend fun resume(id: Long) {
        dao.get(id) ?: return
        dao.markQueued(id)
        scheduler.enqueue(id, settings.current().wifiOnly, replace = true)
    }

    /** Retry after a failure/cancel; resumes from the partial file when it's still available. */
    suspend fun retry(id: Long) {
        val e = dao.get(id) ?: return
        if (e.status == DownloadStatus.COMPLETED) {
            // "file missing" case: download again from scratch
            dao.resetProgress(id)
        }
        dao.markQueued(id)
        scheduler.enqueue(id, settings.current().wifiOnly, replace = true)
    }

    suspend fun cancel(id: Long) {
        val e = dao.get(id) ?: return
        dao.markCanceled(id)
        scheduler.cancel(id)
        e.fileUri?.let { if (e.status != DownloadStatus.COMPLETED) library.deleteUri(Uri.parse(it)) }
        notifier.dismiss(id)
    }

    /** Removes a record from history. Downloaded files stay in Files; unfinished partials are discarded. */
    suspend fun remove(id: Long) {
        val e = dao.get(id) ?: return
        if (e.status != DownloadStatus.COMPLETED) {
            scheduler.cancel(id)
            e.fileUri?.let { library.deleteUri(Uri.parse(it)) }
        }
        notifier.dismiss(id)
        dao.delete(id)
    }

    suspend fun clearHistory() = dao.clearFinished()

    /** Deletes partial files for paused/failed/cancelled downloads to free space. Returns bytes freed. */
    suspend fun discardPartials(): Long {
        var freed = 0L
        dao.getByStatus(listOf(DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELED)).forEach { e ->
            e.fileUri?.let { freed += maxOf(library.sizeOf(Uri.parse(it)), 0); library.deleteUri(Uri.parse(it)) }
            dao.resetProgress(e.id)
            if (e.status == DownloadStatus.PAUSED) dao.setStatus(e.id, DownloadStatus.CANCELED)
        }
        return freed
    }

    /** Called at app start: re-arms downloads that were queued/running when the process died. */
    suspend fun reconcile() {
        val s = settings.current()
        dao.getByStatus(listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)).forEach {
            scheduler.enqueue(it.id, it.wifiOnly || s.wifiOnly, replace = false)
        }
        library.sync()
    }
}
