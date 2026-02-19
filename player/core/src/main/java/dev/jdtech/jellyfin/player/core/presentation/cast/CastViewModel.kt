package dev.jdtech.jellyfin.player.core.presentation.cast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.player.core.domain.CastManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CastViewModel @Inject constructor(private val castManager: CastManager) : ViewModel() {
    private val _state =
        MutableStateFlow(
            CastUiState(
                isCastingEnabled = castManager.castingEnabled.value,
                devices = emptyList(),
                connectedDevice = null,
                playbackState = CastPlaybackState.Idle,
            )
        )

    val state: StateFlow<CastUiState> = _state

    init {
        viewModelScope.launch {
            castManager.castingEnabled.collectLatest { enabled ->
                _state.update { it.copy(isCastingEnabled = enabled) }
            }
        }
    }

    fun startDiscovery() {
        if (_state.value.devices.isNotEmpty()) {
            return
        }
        _state.update {
            it.copy(
                devices =
                    listOf(
                        CastDevice(id = "living-room", name = "Living Room TV", location = "TV"),
                        CastDevice(id = "bedroom", name = "Bedroom Display", location = "Nest Hub"),
                        CastDevice(id = "office", name = "Office Speaker", location = "Audio"),
                    )
            )
        }
    }

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
