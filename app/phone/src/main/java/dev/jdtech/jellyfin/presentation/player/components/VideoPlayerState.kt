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

/** Identifies the active sub-panel or bottom sheet displayed in the player. */
enum class PlayerSidePanelType {
    /** No panel or bottom sheet is open. */
    NONE,

    /** Audio track selection panel. */
    AUDIO,

    /** Subtitle track selection panel. */
    SUBTITLES,

    /** Main quick-settings navigation menu. */
    SETTINGS,

    /** Playback speed selection panel. */
    SPEED,

    /** Aspect ratio / video zoom mode selection panel. */
    ASPECT_RATIO,

    /** Chapters list panel. */
    CHAPTERS,
}

/**
 * State holder managing player UI visibility, panel navigation, timeouts, and touch locking.
 *
 * @param coroutineScope [CoroutineScope] used for managing auto-hide delays.
 */
@Stable
class VideoPlayerState(val coroutineScope: CoroutineScope) {
    /**
     * Whether the on-screen playback controls (top bar, center buttons, seeker) are currently
     * visible.
     */
    var controlsVisible by mutableStateOf(true)
        private set

    /** Whether player screen controls and touch interactions are locked. */
    var isControlsLocked by mutableStateOf(false)
        private set

    /** Currently open side panel or bottom sheet type. */
    var sidePanelType by mutableStateOf(PlayerSidePanelType.NONE)

    /** Returns true if any secondary side panel or bottom sheet is currently open. */
    val isPanelOpen: Boolean
        get() = sidePanelType != PlayerSidePanelType.NONE

    private var hideJob: Job? = null

    /**
     * Reveals the player controls.
     *
     * @param autoHide When true, initiates an auto-hide countdown.
     */
    fun showControls(autoHide: Boolean = true) {
        controlsVisible = true
        resetTimeout(autoHide)
    }

    /** Hides the player controls immediately and cancels any pending auto-hide timer. */
    fun hideControls() {
        controlsVisible = false
        hideJob?.cancel()
    }

    /**
     * Toggles visibility of the player controls.
     *
     * @param autoHide When true, initiates an auto-hide timer if controls become visible.
     */
    fun toggleControls(autoHide: Boolean = true) {
        if (controlsVisible) {
            hideControls()
        } else {
            showControls(autoHide)
        }
    }

    /**
     * Resets or restarts the 5-second controls auto-hide timer.
     *
     * @param autoHide Whether auto-hiding should be enabled.
     */
    fun resetTimeout(autoHide: Boolean = true) {
        hideJob?.cancel()
        if (autoHide && !isPanelOpen && !isControlsLocked) {
            hideJob = coroutineScope.launch {
                delay(5.seconds)
                controlsVisible = false
            }
        }
    }

    /**
     * Locks or unlocks on-screen player controls.
     *
     * @param locked When true, hides controls and disables panels; when false, restores controls.
     */
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

/**
 * Creates and remembers a [VideoPlayerState] instance across recompositions.
 *
 * @param coroutineScope [CoroutineScope] used by the state holder for auto-hide timeouts.
 * @return A remembered [VideoPlayerState] instance.
 */
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
