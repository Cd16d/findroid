package dev.jdtech.jellyfin.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.presentation.cast.DefaultCastManager
import dev.jdtech.jellyfin.player.core.domain.CastManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CastModule {
    @Binds
    @Singleton
    abstract fun bindCastManager(impl: DefaultCastManager): CastManager
}
