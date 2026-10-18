package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class PlayerSidePanelType {
    NONE,
    AUDIO,
    SUBTITLES,
    SETTINGS,
    SPEED,
    ASPECT_RATIO,
    CHAPTERS,
}

@Stable
class VideoPlayerState(val coroutineScope: CoroutineScope) {
    var controlsVisible by mutableStateOf(true)
        private set

    var isControlsLocked by mutableStateOf(false)
        private set

    var sidePanelType by mutableStateOf(PlayerSidePanelType.NONE)

    val isPanelOpen: Boolean
        get() = sidePanelType != PlayerSidePanelType.NONE

    private var hideJob: Job? = null

    fun showControls(autoHide: Boolean = true) {
        controlsVisible = true
        resetTimeout(autoHide)
    }

    fun hideControls() {
        controlsVisible = false
        hideJob?.cancel()
    }

    fun toggleControls(autoHide: Boolean = true) {
        if (controlsVisible) {
            hideControls()
        } else {
            showControls(autoHide)
        }
    }

    fun resetTimeout(autoHide: Boolean = true) {
        hideJob?.cancel()
        if (autoHide && !isPanelOpen && !isControlsLocked) {
            hideJob = coroutineScope.launch {
                delay(5.seconds)
                controlsVisible = false
            }
        }
    }

    fun setLocked(locked: Boolean) {
        isControlsLocked = locked
        if (locked) {
            controlsVisible = false
            sidePanelType = PlayerSidePanelType.NONE
            hideJob?.cancel()
        } else {
            showControls(autoHide = true)
        }
    }
}

@Composable
fun rememberVideoPlayerState(
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): VideoPlayerState {
    val state = remember { VideoPlayerState(coroutineScope) }
    LaunchedEffect(state.isPanelOpen, state.isControlsLocked) {
        if (state.isPanelOpen || state.isControlsLocked) {
            state.resetTimeout(autoHide = false)
        } else if (state.controlsVisible) {
            state.resetTimeout(autoHide = true)
        }
    }
    return state
}
