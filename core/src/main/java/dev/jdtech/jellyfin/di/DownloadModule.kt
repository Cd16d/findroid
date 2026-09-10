package dev.jdtech.jellyfin.di

import android.app.Application
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.DownloaderImpl
import dev.jdtech.jellyfin.utils.download.MediaDownloadEngine
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * Hilt qualifier for the OkHttpClient used exclusively by MediaDownloadEngine. Has no overall call
 * timeout (downloads are long-running) and retry on connection failure.
 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DownloadHttpClient

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    @Singleton
    @Provides
    @DownloadHttpClient
    fun provideDownloadOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    @Singleton
    @Provides
    fun provideDownloader(
        application: Application,
        serverDatabase: ServerDatabaseDao,
        jellyfinRepository: JellyfinRepository,
        appPreferences: AppPreferences,
        workManager: WorkManager,
        engine: MediaDownloadEngine,
    ): Downloader =
        DownloaderImpl(
            application,
            serverDatabase,
            jellyfinRepository,
            appPreferences,
            workManager,
            engine,
        )
}
