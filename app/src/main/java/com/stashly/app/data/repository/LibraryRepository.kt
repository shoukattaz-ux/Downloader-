package com.stashly.app.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.stashly.app.core.AppError
import com.stashly.app.core.ErrorCode
import com.stashly.app.core.FileRules
import com.stashly.app.data.db.FolderEntity
import com.stashly.app.data.db.LibraryDao
import com.stashly.app.data.db.LibraryFileEntity
import com.stashly.app.domain.model.FileCategory
import com.stashly.app.domain.model.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class StorageInfo(val totalBytes: Long, val freeBytes: Long, val appBytes: Long) {
    val usedBytes get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val otherUsedBytes get() = (usedBytes - appBytes).coerceAtLeast(0)
}

/**
 * The File Manager's source of truth is MediaStore (the `Download/Stashly` folder tree), which Android keeps
 * indexed and which needs **no storage permission** for files this app created. Room mirrors it as a
 * fast, searchable index. A file is stored once; categories (Videos/Audio/...) are *queries*, so
 * nothing is ever duplicated.
 */
@Singleton
class LibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LibraryDao,
) {
    companion object {
        const val ROOT = "Download/Stashly"
        private val COLLECTION: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        private val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.RELATIVE_PATH,
        )
        fun relativePath(folder: String) = if (folder.isEmpty()) "$ROOT/" else "$ROOT/$folder/"
        fun folderFromRelative(rel: String?): String? {
            val r = rel?.trimEnd('/') ?: return null
            return when {
                r == ROOT -> ""
                r.startsWith("$ROOT/") -> r.removePrefix("$ROOT/")
                else -> null
            }
        }
        fun validFolderName(name: String): Boolean =
            name.isNotBlank() && name.length <= 60 && !name.contains(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")) && !name.startsWith(".")
    }

    private val resolver get() = context.contentResolver

    // ---------- observation ----------
    fun observe(category: FileCategory?, query: String, sort: SortOrder): Flow<List<LibraryFileEntity>> =
        dao.observe(category?.name ?: "ALL", escape(query), sort.name)

    fun observeFolder(folder: String, query: String, sort: SortOrder) =
        dao.observeInFolder(folder, escape(query), sort.name)

    /** Immediate sub-folders of [parent] (virtual + implied by files). */
    fun observeSubfolders(parent: String): Flow<List<String>> =
        combine(dao.observeFolders(), dao.observeFolderPaths()) { virtual, fromFiles ->
            val all = HashSet<String>()
            (virtual.map { it.path } + fromFiles).filter { it.isNotEmpty() }.forEach { p ->
                var cur = ""
                p.split('/').forEach { seg -> cur = if (cur.isEmpty()) seg else "$cur/$seg"; all += cur }
            }
            all.filter { it.substringBeforeLast('/', "") == parent }.sortedBy { it.lowercase() }
        }

    fun observeAllFolders(): Flow<List<String>> =
        combine(dao.observeFolders(), dao.observeFolderPaths()) { virtual, fromFiles ->
            val all = sortedSetOf<String>(String.CASE_INSENSITIVE_ORDER)
            (virtual.map { it.path } + fromFiles).filter { it.isNotEmpty() }.forEach { p ->
                var cur = ""
                p.split('/').forEach { seg -> cur = if (cur.isEmpty()) seg else "$cur/$seg"; all += cur }
            }
            all.toList()
        }

    fun observeUris(): Flow<Set<String>> = dao.observeUris().map { it.toSet() }
    fun observeAppBytes() = dao.observeTotalBytes()
    suspend fun get(uri: String) = dao.get(uri)
    suspend fun getByMediaStoreId(id: Long) = dao.getByMediaStoreId(id)

    private fun escape(q: String) = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    // ---------- storage ----------
    fun storage(appBytes: Long): StorageInfo {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        return StorageInfo(stat.totalBytes, stat.availableBytes, appBytes)
    }

    fun freeBytes(): Long = StatFs(Environment.getExternalStorageDirectory().path).availableBytes

    // ---------- download pipeline ----------
    /** Creates a *pending* (invisible to other apps) entry that downloads stream into. */
    fun createPending(displayName: String, mime: String, folder: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(folder))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return try {
            resolver.insert(COLLECTION, values)
        } catch (e: SecurityException) {
            throw AppError(ErrorCode.PERMISSION_DENIED, "Couldn't create the file. Storage access was denied.", e)
        } ?: throw AppError(ErrorCode.UNKNOWN, "Couldn't create the file in storage.")
    }

    fun displayName(uri: Uri): String? = queryString(uri, MediaStore.MediaColumns.DISPLAY_NAME)

    fun exists(uri: Uri): Boolean = try {
        resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (_: Exception) { false }

    fun sizeOf(uri: Uri): Long = try {
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1
    } catch (_: Exception) { -1 }

    fun readHead(uri: Uri, n: Int = 4096): ByteArray = try {
        resolver.openInputStream(uri)?.use { s -> val b = ByteArray(n); val r = s.read(b); if (r <= 0) ByteArray(0) else b.copyOf(r) } ?: ByteArray(0)
    } catch (_: Exception) { ByteArray(0) }

    /** Throws if Android can't parse the media container (corrupted/truncated). */
    fun probeDurationMs(uri: Uri): Long? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } finally { runCatching { r.release() } }
    }

    fun deleteUri(uri: Uri) { runCatching { resolver.delete(uri, null, null) } }

    /** Publishes a finished pending entry and indexes it. */
    suspend fun publish(uri: Uri): LibraryFileEntity = withContext(Dispatchers.IO) {
        val v = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        try { resolver.update(uri, v, null, null) } catch (e: SecurityException) {
            throw AppError(ErrorCode.PERMISSION_DENIED, "Couldn't finish saving the file.", e)
        }
        index(uri) ?: throw AppError(ErrorCode.UNKNOWN, "The file was saved but couldn't be indexed.")
    }

    // ---------- indexing ----------
    suspend fun index(uri: Uri): LibraryFileEntity? = withContext(Dispatchers.IO) {
        val existing = dao.get(uri.toString())
        queryEntity(uri, existing)?.also { dao.upsert(it) }
    }

    /** Reconciles Room with MediaStore: adds new files, removes rows whose files are gone. */
    suspend fun sync() = withContext(Dispatchers.IO) {
        val existing = dao.getAll().associateBy { it.uri }
        val found = ArrayList<LibraryFileEntity>()
        try {
            resolver.query(COLLECTION, PROJECTION, "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?", arrayOf("$ROOT/%"), null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val uri = ContentUris.withAppendedId(COLLECTION, id)
                    toEntity(c, uri, existing[uri.toString()])?.let(found::add)
                }
            }
        } catch (e: SecurityException) { return@withContext }
        // fill missing durations for a bounded number of files per sync (keeps scrolling/startup fast)
        var budget = 25
        val filled = found.map { f ->
            if (f.durationMs == null && budget > 0 && (f.category == "VIDEO" || f.category == "AUDIO")) {
                budget--
                f.copy(durationMs = runCatching { probeDurationMs(Uri.parse(f.uri)) }.getOrNull() ?: 0L)
            } else f
        }
        dao.upsert(filled)
        val keep = filled.map { it.uri }.toHashSet()
        existing.keys.filter { it !in keep }.chunked(500).forEach { chunk -> chunk.forEach { dao.delete(it) } }
    }

    private suspend fun queryEntity(uri: Uri, existing: LibraryFileEntity?): LibraryFileEntity? =
        resolver.query(uri, PROJECTION, null, null, null)?.use { c ->
            if (c.moveToFirst()) toEntity(c, uri, existing) else null
        }?.let { e ->
            if (e.durationMs == null && (e.category == "VIDEO" || e.category == "AUDIO"))
                e.copy(durationMs = runCatching { probeDurationMs(uri) }.getOrNull() ?: 0L) else e
        }

    private fun toEntity(c: android.database.Cursor, uri: Uri, existing: LibraryFileEntity?): LibraryFileEntity? {
        val rel = c.getString(5)
        val folder = folderFromRelative(rel) ?: return null
        val name = c.getString(1) ?: return null
        val mime = c.getString(2) ?: FileRules.classify(FileRules.extensionOf(name))?.mimeType ?: "application/octet-stream"
        return LibraryFileEntity(
            uri = uri.toString(), mediaStoreId = c.getLong(0), displayName = name, mimeType = mime,
            category = FileCategory.fromMime(mime).name, sizeBytes = c.getLong(3),
            durationMs = existing?.durationMs, dateAdded = c.getLong(4) * 1000, folder = folder,
        )
    }

    private fun queryString(uri: Uri, column: String): String? =
        try { resolver.query(uri, arrayOf(column), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }
        catch (_: Exception) { null }

    // ---------- file operations ----------
    suspend fun rename(file: LibraryFileEntity, newNameRaw: String): Unit = withContext(Dispatchers.IO) {
        val oldExt = FileRules.extensionOf(file.displayName)
        var name = FileRules.sanitizeFileName(newNameRaw, "")
        if (name.isEmpty()) throw AppError(ErrorCode.UNKNOWN, "Please enter a file name.")
        if (FileRules.extensionOf(name) == null && oldExt != null) name = "$name.$oldExt"
        val newExt = FileRules.extensionOf(name)
        if (FileRules.isBlocked(newExt) || (oldExt != null && newExt != oldExt && FileRules.classify(newExt)?.kind != FileRules.classify(oldExt)?.kind))
            throw AppError(ErrorCode.UNSAFE_FILE, "That file extension isn't allowed.")
        val uri = Uri.parse(file.uri)
        val v = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, name) }
        mutate { resolver.update(uri, v, null, null) }
        index(uri)
    }

    suspend fun move(file: LibraryFileEntity, folder: String): Unit = withContext(Dispatchers.IO) {
        if (file.folder == folder) return@withContext
        val uri = Uri.parse(file.uri)
        val v = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(folder)) }
        mutate { resolver.update(uri, v, null, null) }
        index(uri)
    }

    suspend fun copy(file: LibraryFileEntity, folder: String): Unit = withContext(Dispatchers.IO) {
        val src = Uri.parse(file.uri)
        val dst = createPending(file.displayName, file.mimeType, folder)
        try {
            val input = resolver.openInputStream(src) ?: throw AppError(ErrorCode.NOT_FOUND, "The file no longer exists.")
            val output = resolver.openOutputStream(dst, "wt") ?: throw AppError(ErrorCode.UNKNOWN, "Couldn't write the copy.")
            input.use { i -> output.use { o -> i.copyTo(o, 64 * 1024) } }
            publish(dst)
        } catch (t: Throwable) {
            deleteUri(dst)
            throw if (t is IOException) AppError.from(t) else t
        }
    }

    suspend fun delete(file: LibraryFileEntity): Unit = withContext(Dispatchers.IO) {
        mutate { resolver.delete(Uri.parse(file.uri), null, null) }
        dao.delete(file.uri)
    }

    // ---------- folders ----------
    suspend fun createFolder(parent: String, name: String) {
        val clean = name.trim()
        if (!validFolderName(clean)) throw AppError(ErrorCode.UNKNOWN, "Folder names can't be empty or contain / \\ : * ? \" < > |")
        val path = if (parent.isEmpty()) clean else "$parent/$clean"
        dao.insertFolder(FolderEntity(path))
    }

    suspend fun renameFolder(path: String, newName: String): Unit = withContext(Dispatchers.IO) {
        val clean = newName.trim()
        if (!validFolderName(clean)) throw AppError(ErrorCode.UNKNOWN, "Folder names can't be empty or contain / \\ : * ? \" < > |")
        val parent = path.substringBeforeLast('/', "")
        val newPath = if (parent.isEmpty()) clean else "$parent/$clean"
        if (newPath == path) return@withContext
        val virtual = dao.getFolders().map { it.path }.filter { it == path || it.startsWith("$path/") }
        dao.getAll().filter { it.folder == path || it.folder.startsWith("$path/") }.forEach { f ->
            val target = newPath + f.folder.removePrefix(path)
            val v = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(target)) }
            mutate { resolver.update(Uri.parse(f.uri), v, null, null) }
            index(Uri.parse(f.uri))
        }
        dao.deleteFolderTree(path)
        (virtual + newPath).map { if (it == newPath) it else newPath + it.removePrefix(path) }.distinct().forEach { dao.insertFolder(FolderEntity(it)) }
    }

    suspend fun deleteFolder(path: String): Unit = withContext(Dispatchers.IO) {
        dao.getAll().filter { it.folder == path || it.folder.startsWith("$path/") }.forEach { delete(it) }
        dao.deleteFolderTree(path)
    }

    suspend fun clearIndex() = dao.clear()

    private inline fun mutate(block: () -> Int) {
        val n = try { block() } catch (e: SecurityException) {
            throw AppError(ErrorCode.PERMISSION_DENIED, "Android doesn't allow this app to change that file.", e)
        } catch (e: IllegalStateException) {
            throw AppError(ErrorCode.UNKNOWN, "A file with that name already exists here.", e)
        } catch (e: IllegalArgumentException) {
            throw AppError(ErrorCode.UNKNOWN, "That name isn't allowed.", e)
        }
        if (n <= 0) throw AppError(ErrorCode.NOT_FOUND, "The file no longer exists.")
    }
}
