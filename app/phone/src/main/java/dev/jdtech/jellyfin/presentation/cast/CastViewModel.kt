package dev.jdtech.jellyfin.presentation.cast

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.player.core.domain.CastManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class CastViewModel @Inject constructor(private val castManager: CastManager) : ViewModel() {
    private val _state =
        MutableStateFlow(
            CastUiState(
                isCastingEnabled = castManager.isCastingEnabled,
                devices =
                    listOf(
                        CastDevice(id = "living-room", name = "Living Room TV", location = "TV"),
                        CastDevice(id = "bedroom", name = "Bedroom Display", location = "Nest Hub"),
                        CastDevice(id = "office", name = "Office Speaker", location = "Audio"),
                    ),
                connectedDevice = null,
                playbackState = CastPlaybackState.Idle,
            )
        )

    val state: StateFlow<CastUiState> = _state

    fun connect(device: CastDevice) {
        _state.update {
            it.copy(connectedDevice = device, playbackState = CastPlaybackState.Playing)
        }
    }

    fun disconnect() {
        _state.update { it.copy(connectedDevice = null, playbackState = CastPlaybackState.Idle) }
    }

    fun togglePlayback() {
        _state.update { state ->
            when (state.playbackState) {
                CastPlaybackState.Playing ->
                    state.copy(playbackState = CastPlaybackState.Paused)
                CastPlaybackState.Paused ->
                    state.copy(playbackState = CastPlaybackState.Playing)
                CastPlaybackState.Idle -> state
            }
        }
    }
}
