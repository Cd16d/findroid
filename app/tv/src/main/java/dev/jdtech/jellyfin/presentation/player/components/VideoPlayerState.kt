package dev.jdtech.jellyfin.presentation.player.components

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow

class VideoPlayerState
internal constructor(@param:IntRange(from = 0) private val hideSeconds: Int) {
    private var _controlsVisible by mutableStateOf(true)
    val controlsVisible
        get() = _controlsVisible

    fun showControls(seconds: Int = hideSeconds) {
        _controlsVisible = true
        channel.trySend(seconds)
    }

    fun hideControls() {
        _controlsVisible = false
    }

    private val channel = Channel<Int>(CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.receiveAsFlow().debounce { it.toLong() * 1000 }.collect { _controlsVisible = false }
    }
}

@Composable
fun rememberVideoPlayerState(@IntRange(from = 0) hideSeconds: Int = 4): VideoPlayerState {
    val state = remember(hideSeconds) { VideoPlayerState(hideSeconds = hideSeconds) }
    LaunchedEffect(state) { state.observe() }
    return state
}
