package com.stashly.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stashly.app.data.db.AppDatabase
import com.stashly.app.data.db.DownloadDao
import com.stashly.app.data.db.DownloadEntity
import com.stashly.app.data.db.FolderEntity
import com.stashly.app.data.db.LibraryDao
import com.stashly.app.data.db.LibraryFileEntity
import com.stashly.app.domain.model.DownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var downloads: DownloadDao
    private lateinit var library: LibraryDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        downloads = db.downloadDao(); library = db.libraryDao()
    }
    @After fun tearDown() { db.close() }

    private fun dl(url: String = "https://h/p", fmt: String = "f", status: DownloadStatus = DownloadStatus.QUEUED) = DownloadEntity(
        sourceUrl = url, mediaUrl = "$url/m", providerId = "p", sourceName = "s", title = "t", fileName = "t.mp4", mimeType = "video/mp4",
        formatId = fmt, format = "MP4", quality = "720p", kind = "VIDEO", status = status)

    private fun file(n: String, size: Long = 1, date: Long = 1, folder: String = "", mime: String = "video/mp4", cat: String = "VIDEO") =
        LibraryFileEntity("content://media/$n", n.hashCode().toLong(), n, mime, cat, size, null, date, folder)

    @Test fun insertAndReadBack() = runBlocking {
        val id = downloads.insert(dl()); val e = downloads.get(id)!!
        assertEquals("https://h/p", e.sourceUrl); assertEquals(DownloadStatus.QUEUED, e.status); assertEquals(-1, e.totalBytes)
    }
    @Test fun duplicateDetectionIgnoresFailedAndCanceled() = runBlocking {
        val failed = downloads.insert(dl(status = DownloadStatus.FAILED)); assertNull(downloads.findDuplicate("https://h/p", "f"))
        downloads.insert(dl(status = DownloadStatus.PAUSED)); assertNotNull(downloads.findDuplicate("https://h/p", "f"))
        assertNull(downloads.findDuplicate("https://h/p", "other-format"))
    }
    @Test fun progressNeverOverwritesPausedOrCanceled() = runBlocking {
        val id = downloads.insert(dl(status = DownloadStatus.DOWNLOADING))
        downloads.updateProgress(id, 10, 100, 5, 18); assertEquals(10L, downloads.get(id)!!.downloadedBytes)
        downloads.setStatus(id, DownloadStatus.PAUSED)
        downloads.updateProgress(id, 99, 100, 5, 1); assertEquals(10L, downloads.get(id)!!.downloadedBytes)
        downloads.markFailed(id, "NETWORK", "x"); assertEquals(DownloadStatus.PAUSED, downloads.get(id)!!.status)
    }
    @Test fun completionAndClearFinished() = runBlocking {
        val done = downloads.insert(dl(url = "a")); val active = downloads.insert(dl(url = "b", status = DownloadStatus.DOWNLOADING))
        downloads.markCompleted(done, 100, 5L, "content://x", "t.mp4")
        assertEquals(DownloadStatus.COMPLETED, downloads.get(done)!!.status); assertEquals(100, downloads.get(done)!!.progressPercent)
        downloads.clearFinished()
        assertNull(downloads.get(done)); assertNotNull(downloads.get(active))
    }
    @Test fun cancelResetsProgressAndTarget() = runBlocking {
        val id = downloads.insert(dl()); downloads.setTarget(id, "content://p", "n.mp4", "v"); downloads.updateProgress(id, 5, 10, 0, null)
        downloads.setStatus(id, DownloadStatus.DOWNLOADING); downloads.markCanceled(id)
        val e = downloads.get(id)!!; assertEquals(0L, e.downloadedBytes); assertNull(e.fileUri)
    }
    @Test fun observeReflectsChanges() = runBlocking {
        val id = downloads.insert(dl()); downloads.setStatus(id, DownloadStatus.PAUSED)
        assertEquals(DownloadStatus.PAUSED, downloads.observe(id).first()!!.status)
        assertEquals(1, downloads.observeUnfinishedCount().first())
    }

    @Test fun libraryFiltersSortsAndSearches() = runBlocking {
        library.upsert(listOf(file("b.mp4", size = 5, date = 1), file("A.mp4", size = 50, date = 3), file("song.mp3", 9, 2, mime = "audio/mpeg", cat = "AUDIO")))
        assertEquals(listOf("A.mp4", "b.mp4"), library.observe("VIDEO", "", "NAME").first().map { it.displayName })
        assertEquals(listOf("A.mp4", "b.mp4"), library.observe("VIDEO", "", "SIZE").first().map { it.displayName })
        assertEquals(listOf("A.mp4", "song.mp3", "b.mp4"), library.observe("ALL", "", "DATE").first().map { it.displayName })
        assertEquals(listOf("song.mp3"), library.observe("ALL", "SONG", "DATE").first().map { it.displayName })
    }
    @Test fun searchTreatsWildcardsLiterally() = runBlocking {
        library.upsert(listOf(file("100%.mp4"), file("a_b.mp4"), file("axb.mp4")))
        assertEquals(listOf("100%.mp4"), library.observe("ALL", "100\\%", "NAME").first().map { it.displayName })
        assertEquals(listOf("a_b.mp4"), library.observe("ALL", "a\\_b", "NAME").first().map { it.displayName })
    }
    @Test fun foldersAreQueriedByPathAndDeletedAsATree() = runBlocking {
        library.upsert(listOf(file("r.mp4"), file("in.mp4", folder = "Trips"), file("deep.mp4", folder = "Trips/2025")))
        assertEquals(listOf("in.mp4"), library.observeInFolder("Trips", "", "NAME").first().map { it.displayName })
        library.insertFolder(FolderEntity("Trips")); library.insertFolder(FolderEntity("Trips/2025")); library.insertFolder(FolderEntity("Other"))
        library.deleteFolderTree("Trips")
        assertEquals(listOf("Other"), library.getFolders().map { it.path })
    }
    @Test fun upsertIsIdempotentAndDeleteWorks() = runBlocking {
        val f = file("x.mp4"); library.upsert(f); library.upsert(f.copy(sizeBytes = 99))
        assertEquals(1, library.getAll().size); assertEquals(99L, library.get(f.uri)!!.sizeBytes)
        assertEquals(99L, library.observeTotalBytes().first()); library.delete(f.uri); assertNull(library.get(f.uri))
    }
}
