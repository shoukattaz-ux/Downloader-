package com.stashly.app.download

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import com.stashly.app.core.FileRules
import com.stashly.app.data.db.DownloadDao
import com.stashly.app.data.db.DownloadEntity
import com.stashly.app.data.repository.LibraryRepository
import com.stashly.app.domain.model.DownloadStatus
import com.stashly.app.domain.model.MediaKind
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Executes one download as persistent, constraint-aware background work. State lives in Room, so
 * progress and results survive screen changes, process death and app restarts.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val library: LibraryRepository,
    private val downloader: HttpDownloader,
    private val notifier: DownloadNotifier,
) : CoroutineWorker(context, params) {

    companion object { const val KEY_ID = "download_id" }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val e = dao.get(inputData.getLong(KEY_ID, -1))
        return notifier.foregroundInfo(e ?: placeholder())
    }

    private fun placeholder() = DownloadEntity(id = 0, sourceUrl = "", mediaUrl = "", providerId = "", sourceName = "",
        title = "Downloading", fileName = "", mimeType = "", formatId = "", format = "", quality = "", kind = "VIDEO")

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val entity = dao.get(id) ?: return Result.success()
        if (entity.status == DownloadStatus.COMPLETED || entity.status == DownloadStatus.PAUSED || entity.status == DownloadStatus.CANCELED)
            return Result.success()
        dao.setStatus(id, DownloadStatus.DOWNLOADING)
        runCatching { setForeground(notifier.foregroundInfo(entity)) }
        return try {
            execute(id)
            Result.success()
        } catch (c: CancellationException) {
            // Pause/cancel already persisted their state. Anything else (constraints, system stop) -> wait.
            withContext(NonCancellable) { if (dao.get(id)?.status == DownloadStatus.DOWNLOADING) dao.setStatus(id, DownloadStatus.QUEUED) }
            throw c
        } catch (t: Throwable) {
            withContext(NonCancellable) { fail(id, AppError.from(t)) }
            Result.failure()
        }
    }

    private suspend fun execute(id: Long) {
        var e = dao.get(id) ?: return
        val resolver = applicationContext.contentResolver

        // Reuse the partial file when it still exists; otherwise start clean.
        var uri: Uri? = e.fileUri?.let(Uri::parse)?.takeIf { library.exists(it) }
        if (uri == null) {
            dao.resetProgress(id)
            uri = library.createPending(e.fileName, e.mimeType, e.folder)
            dao.setTarget(id, uri.toString(), library.displayName(uri) ?: e.fileName, null)
            e = dao.get(id) ?: return
        }
        val sink = MediaStoreSink(resolver, uri)

        val needed = if (e.totalBytes > 0) e.totalBytes - sink.length() else 0
        if (needed > 0 && library.freeBytes() < needed + 16L * 1024 * 1024)
            throw AppError(ErrorCode.INSUFFICIENT_STORAGE, "Not enough free storage for this download.")

        val expectedKind = MediaKind.valueOf(e.kind)
        var lastNotify = 0L
        val result = downloader.downloadWithRetries(
            url = e.mediaUrl, sink = sink, validator = e.validator,
            checkContentType = { ct ->
                val r = FileRules.resolve(e.fileName, ct)
                if (r.kind != expectedKind) throw AppError(ErrorCode.UNSAFE_FILE, "The server sent a different type of file than expected.")
            },
            onStart = { validator, total ->
                if (total > 0 && total - sink.length() > 0 && library.freeBytes() < total - sink.length() + 16L * 1024 * 1024)
                    throw AppError(ErrorCode.INSUFFICIENT_STORAGE, "Not enough free storage for this download.")
                dao.setValidator(id, validator, total)
            },
            onProgress = { p ->
                dao.updateProgress(id, p.downloadedBytes, p.totalBytes, p.bytesPerSecond, p.etaSeconds)
                val now = System.currentTimeMillis()
                if (now - lastNotify > 1000) {
                    lastNotify = now
                    dao.get(id)?.let { cur -> runCatching { setForeground(notifier.foregroundInfo(cur)) } }
                }
            },
        )

        // ---- verification before the file becomes visible ----
        val size = library.sizeOf(uri)
        if (size <= 0 || (result.totalBytes > 0 && size != result.totalBytes))
            throw AppError(ErrorCode.CORRUPTED, "The downloaded file is incomplete.")
        val ext = FileRules.extensionOf(e.fileName) ?: ""
        if (!FileRules.signatureMatches(library.readHead(uri), ext))
            throw AppError(ErrorCode.CORRUPTED, "The downloaded file isn't a valid .$ext file.")
        if (expectedKind == MediaKind.VIDEO || expectedKind == MediaKind.AUDIO) {
            try { library.probeDurationMs(uri) } catch (t: Exception) {
                throw AppError(ErrorCode.CORRUPTED, "The downloaded media file is corrupted and can't be played.", t)
            }
        }
        val entry = library.publish(uri)
        dao.markCompleted(id, entry.sizeBytes, System.currentTimeMillis(), entry.uri, entry.displayName)
        dao.get(id)?.let { notifier.completed(it) }
    }

    private suspend fun fail(id: Long, error: AppError) {
        val e = dao.get(id) ?: return
        if (e.status == DownloadStatus.PAUSED || e.status == DownloadStatus.CANCELED) return
        // Unrecoverable problems: discard the bad partial file so it never appears in the library.
        if (error.code == ErrorCode.CORRUPTED || error.code == ErrorCode.UNSAFE_FILE || error.code == ErrorCode.NO_MEDIA) {
            e.fileUri?.let { library.deleteUri(Uri.parse(it)) }
            dao.resetProgress(id)
        }
        dao.markFailed(id, error.code.name, error.userMessage)
        notifier.failed(e, error.userMessage)
    }
}
