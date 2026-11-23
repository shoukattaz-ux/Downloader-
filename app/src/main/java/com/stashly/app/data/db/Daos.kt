package com.stashly.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.stashly.app.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observe(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN (:statuses)")
    suspend fun getByStatus(statuses: List<DownloadStatus>): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE sourceUrl = :sourceUrl AND formatId = :formatId AND status IN ('QUEUED','DOWNLOADING','PAUSED','COMPLETED') LIMIT 1")
    suspend fun findDuplicate(sourceUrl: String, formatId: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, errorCode = NULL, errorMessage = NULL, speedBps = 0, etaSeconds = NULL WHERE id = :id AND status NOT IN ('COMPLETED')")
    suspend fun setStatus(id: Long, status: DownloadStatus)

    /** Only applies while still DOWNLOADING, so a concurrent pause/cancel is never overwritten. */
    @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, speedBps = :speed, etaSeconds = :eta WHERE id = :id AND status = 'DOWNLOADING'")
    suspend fun updateProgress(id: Long, downloaded: Long, total: Long, speed: Long, eta: Long?)

    @Query("UPDATE downloads SET fileUri = :uri, fileName = :fileName, validator = :validator WHERE id = :id")
    suspend fun setTarget(id: Long, uri: String?, fileName: String, validator: String?)

    @Query("UPDATE downloads SET validator = :validator, totalBytes = :total WHERE id = :id")
    suspend fun setValidator(id: Long, validator: String?, total: Long)

    @Query("UPDATE downloads SET status = 'FAILED', errorCode = :code, errorMessage = :message, speedBps = 0, etaSeconds = NULL WHERE id = :id AND status NOT IN ('PAUSED','CANCELED','COMPLETED')")
    suspend fun markFailed(id: Long, code: String, message: String)

    @Query("UPDATE downloads SET status = 'COMPLETED', downloadedBytes = :size, totalBytes = :size, speedBps = 0, etaSeconds = NULL, errorCode = NULL, errorMessage = NULL, completedAt = :at, fileUri = :uri, fileName = :fileName WHERE id = :id")
    suspend fun markCompleted(id: Long, size: Long, at: Long, uri: String, fileName: String)

    @Query("UPDATE downloads SET status = 'CANCELED', downloadedBytes = 0, speedBps = 0, etaSeconds = NULL, fileUri = NULL, validator = NULL WHERE id = :id")
    suspend fun markCanceled(id: Long)

    @Query("UPDATE downloads SET status = 'QUEUED', errorCode = NULL, errorMessage = NULL, speedBps = 0, etaSeconds = NULL WHERE id = :id")
    suspend fun markQueued(id: Long)

    @Query("UPDATE downloads SET downloadedBytes = 0, totalBytes = -1, fileUri = NULL, validator = NULL WHERE id = :id")
    suspend fun resetProgress(id: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED','FAILED','CANCELED')")
    suspend fun clearFinished()

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED','DOWNLOADING','PAUSED')")
    fun observeUnfinishedCount(): Flow<Int>
}

@Dao
interface LibraryDao {
    @Query(
        """SELECT * FROM library_files
           WHERE (:category = 'ALL' OR category = :category)
             AND displayName LIKE '%' || :query || '%' ESCAPE '\'
           ORDER BY
             CASE WHEN :sort = 'NAME' THEN displayName END COLLATE NOCASE ASC,
             CASE WHEN :sort = 'SIZE' THEN sizeBytes END DESC,
             CASE WHEN :sort = 'DATE' THEN dateAdded END DESC"""
    )
    fun observe(category: String, query: String, sort: String): Flow<List<LibraryFileEntity>>

    @Query(
        """SELECT * FROM library_files
           WHERE folder = :folder AND displayName LIKE '%' || :query || '%' ESCAPE '\'
           ORDER BY
             CASE WHEN :sort = 'NAME' THEN displayName END COLLATE NOCASE ASC,
             CASE WHEN :sort = 'SIZE' THEN sizeBytes END DESC,
             CASE WHEN :sort = 'DATE' THEN dateAdded END DESC"""
    )
    fun observeInFolder(folder: String, query: String, sort: String): Flow<List<LibraryFileEntity>>

    @Query("SELECT * FROM library_files WHERE uri = :uri")
    suspend fun get(uri: String): LibraryFileEntity?

    @Query("SELECT * FROM library_files WHERE mediaStoreId = :id")
    suspend fun getByMediaStoreId(id: Long): LibraryFileEntity?

    @Query("SELECT * FROM library_files")
    suspend fun getAll(): List<LibraryFileEntity>

    @Query("SELECT uri FROM library_files")
    fun observeUris(): Flow<List<String>>

    @Query("SELECT folder FROM library_files GROUP BY folder")
    fun observeFolderPaths(): Flow<List<String>>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM library_files")
    fun observeTotalBytes(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<LibraryFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LibraryFileEntity)

    @Query("DELETE FROM library_files WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM library_files WHERE uri NOT IN (:keep)")
    suspend fun deleteMissing(keep: List<String>)

    @Query("DELETE FROM library_files")
    suspend fun clear()

    @Query("SELECT * FROM library_folders ORDER BY path")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM library_folders")
    suspend fun getFolders(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: FolderEntity)

    @Query("DELETE FROM library_folders WHERE path = :path OR path LIKE :path || '/%'")
    suspend fun deleteFolderTree(path: String)
}
