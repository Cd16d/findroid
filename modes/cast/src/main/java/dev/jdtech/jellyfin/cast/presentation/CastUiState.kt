package dev.jdtech.jellyfin.cast.presentation

data class CastDevice(
    val id: String,
    val name: String,
    val location: String,
)

enum class CastPlaybackState {
    Idle,
    Playing,
    Paused,
}

data class CastUiState(
    val isCastingEnabled: Boolean,
    val devices: List<CastDevice>,
    val connectedDevice: CastDevice?,
    val playbackState: CastPlaybackState,
)
