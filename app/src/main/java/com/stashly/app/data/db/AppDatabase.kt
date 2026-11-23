package com.stashly.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.stashly.app.domain.model.DownloadStatus

class Converters {
    @TypeConverter fun toStatus(v: String): DownloadStatus = runCatching { DownloadStatus.valueOf(v) }.getOrDefault(DownloadStatus.FAILED)
    @TypeConverter fun fromStatus(s: DownloadStatus): String = s.name
}

@Database(
    entities = [DownloadEntity::class, LibraryFileEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun libraryDao(): LibraryDao
}
