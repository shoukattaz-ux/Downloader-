package com.stashly.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stashly.app.core.AppError
import com.stashly.app.data.db.AppDatabase
import com.stashly.app.data.repository.LibraryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real MediaStore on a device/emulator (API 29+). Needs no storage permission. */
@RunWith(AndroidJUnit4::class)
class LibraryRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var repo: LibraryRepository
    private val created = mutableListOf<String>()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = LibraryRepository(context, db.libraryDao())
    }
    @After fun tearDown() = runBlocking {
        db.libraryDao().getAll().forEach { runCatching { repo.delete(it) } }
        db.close()
    }

    private suspend fun makeFile(name: String, mime: String, bytes: ByteArray, folder: String = ""): String {
        val uri = repo.createPending(name, mime, folder)
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
        return repo.publish(uri).uri
    }

    @Test fun publishedFileIsIndexedOnceAndCategorised() = runBlocking {
        val uri = makeFile("stashly-test.mp3", "audio/mpeg", ByteArray(2048) { 1 })
        val row = repo.get(uri)!!
        assertEquals("AUDIO", row.category); assertEquals(2048L, row.sizeBytes); assertEquals("", row.folder)
        assertEquals(1, repo.observe(null, "stashly-test", com.stashly.app.domain.model.SortOrder.DATE).first().size)
        assertEquals(0, repo.observe(com.stashly.app.domain.model.FileCategory.VIDEO, "stashly-test", com.stashly.app.domain.model.SortOrder.DATE).first().size)
    }
    @Test fun renameMoveCopyDelete() = runBlocking {
        val uri = makeFile("stashly-a.pdf", "application/pdf", "%PDF-1.4".toByteArray())
        var f = repo.get(uri)!!
        repo.rename(f, "stashly-b"); f = repo.get(uri)!!; assertEquals("stashly-b.pdf", f.displayName)
        repo.createFolder("", "StashlyTestFolder"); repo.move(f, "StashlyTestFolder"); f = repo.get(uri)!!; assertEquals("StashlyTestFolder", f.folder)
        repo.copy(f, ""); assertEquals(2, db.libraryDao().getAll().count { it.displayName.startsWith("stashly-b") })
        db.libraryDao().getAll().forEach { repo.delete(it) }
        assertEquals(0, db.libraryDao().getAll().size); assertFalse(repo.exists(android.net.Uri.parse(uri)))
    }
    @Test fun renameCannotTurnMediaIntoExecutable() = runBlocking {
        val uri = makeFile("stashly-safe.mp4", "video/mp4", ByteArray(64))
        try { repo.rename(repo.get(uri)!!, "stashly-safe.apk"); fail() } catch (e: AppError) { assertTrue(e.userMessage.isNotEmpty()) }
        assertEquals("stashly-safe.mp4", repo.get(uri)!!.displayName)
    }
    @Test fun syncDropsFilesDeletedBehindOurBack() = runBlocking {
        val uri = makeFile("stashly-gone.txt", "text/plain", "hi".toByteArray())
        context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
        repo.sync(); assertNull(repo.get(uri))
    }
    @Test fun invalidFolderNamesAreRejected() = runBlocking {
        for (bad in listOf("", "a/b", "..", ".hidden", "x*y")) { try { repo.createFolder("", bad); if (bad != "..") fail(bad) } catch (_: AppError) {} }
    }
    @Test fun pendingFilesAreInvisibleUntilPublished() = runBlocking {
        val uri = repo.createPending("stashly-pending.mp4", "video/mp4", "")
        repo.sync(); assertEquals(0, db.libraryDao().getAll().count { it.displayName == "stashly-pending.mp4" })
        repo.deleteUri(uri)
    }
}
