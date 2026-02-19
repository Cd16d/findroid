package dev.jdtech.jellyfin.player.core.domain

interface CastManager {
    val isCastingEnabled: Boolean

    fun setCastingEnabled(enabled: Boolean)
}
