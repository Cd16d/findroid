package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.utils.NetworkConnectivity
import dev.jdtech.jellyfin.utils.NetworkConnectivityImpl
import dev.jdtech.jellyfin.utils.NetworkPriorityManager
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Singleton
    @Provides
    fun provideOkHttpClient(priorityManager: NetworkPriorityManager): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                priorityManager.onUiRequestStarted()
                try {
                    chain.proceed(chain.request())
                } finally {
                    priorityManager.onUiRequestFinished()
                }
            }
            .build()
    }

    @Singleton
    @Provides
    fun provideNetworkConnectivity(impl: NetworkConnectivityImpl): NetworkConnectivity = impl
}
