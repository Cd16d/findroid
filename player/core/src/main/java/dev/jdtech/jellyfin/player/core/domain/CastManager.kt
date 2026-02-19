package dev.jdtech.jellyfin.player.core.domain

import kotlinx.coroutines.flow.StateFlow

interface CastManager {
    val castingEnabled: StateFlow<Boolean>

    fun setCastingEnabled(enabled: Boolean)
}
