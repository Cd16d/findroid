package dev.jdtech.jellyfin.models

data class VolumeKeyEvent(
    val isUp: Boolean,
    val isRepeat: Boolean = false,
)
