package com.stashly.app.di

import android.content.Context
import androidx.room.Room
import com.stashly.app.data.db.AppDatabase
import com.stashly.app.data.db.DownloadDao
import com.stashly.app.data.db.LibraryDao
import com.stashly.app.domain.provider.DirectUrlProvider
import com.stashly.app.domain.provider.MediaSourceProvider
import com.stashly.app.domain.provider.ProviderRegistry
import com.stashly.app.domain.provider.archive.ArchiveApi
import com.stashly.app.domain.provider.archive.InternetArchiveProvider
import com.stashly.app.download.HttpDownloader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "stashly.db").build()

    @Provides fun downloadDao(db: AppDatabase): DownloadDao = db.downloadDao()
    @Provides fun libraryDao(db: AppDatabase): LibraryDao = db.libraryDao()

    /** One shared client. HTTPS->HTTP redirects are refused so a link can never downgrade to cleartext. */
    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides @Singleton
    fun archiveApi(client: OkHttpClient): ArchiveApi = Retrofit.Builder()
        .baseUrl("https://archive.org/")
        .client(client)
        .addConverterFactory(Json { ignoreUnknownKeys = true; isLenient = true }.asConverterFactory("application/json".toMediaType()))
        .build().create(ArchiveApi::class.java)

    /** Order matters: specific providers first, the generic direct-link provider last. */
    @Provides @Singleton
    fun providers(archive: ArchiveApi, client: OkHttpClient): List<@JvmSuppressWildcards MediaSourceProvider> =
        listOf(InternetArchiveProvider(archive), DirectUrlProvider(client))

    @Provides @Singleton
    fun registry(providers: List<@JvmSuppressWildcards MediaSourceProvider>) = ProviderRegistry(providers)

    @Provides @Singleton
    fun downloader(client: OkHttpClient) = HttpDownloader(client)
}
