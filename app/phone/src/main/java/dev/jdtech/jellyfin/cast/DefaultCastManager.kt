package dev.jdtech.jellyfin.cast

import dev.jdtech.jellyfin.player.core.domain.CastManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCastManager @Inject constructor(private val appPreferences: AppPreferences) :
    CastManager {
    override val isCastingEnabled: Boolean
        get() = appPreferences.getValue(appPreferences.playerCastEnabled)

    override fun setCastingEnabled(enabled: Boolean) {
        appPreferences.setValue(appPreferences.playerCastEnabled, enabled)
    }
}
