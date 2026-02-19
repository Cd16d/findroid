package dev.jdtech.jellyfin.presentation.cast

import dev.jdtech.jellyfin.player.core.domain.CastManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class DefaultCastManager @Inject constructor(private val appPreferences: AppPreferences) :
    CastManager {
    private val _castingEnabled =
        MutableStateFlow(appPreferences.getValue(appPreferences.playerCastEnabled))

    override val castingEnabled: StateFlow<Boolean> = _castingEnabled

    override fun setCastingEnabled(enabled: Boolean) {
        appPreferences.setValue(appPreferences.playerCastEnabled, enabled)
        _castingEnabled.value = enabled
    }
}
