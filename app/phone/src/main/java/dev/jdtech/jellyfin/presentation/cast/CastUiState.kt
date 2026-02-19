package dev.jdtech.jellyfin.presentation.cast

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
