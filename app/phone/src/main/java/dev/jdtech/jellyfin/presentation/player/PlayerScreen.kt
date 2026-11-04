package dev.jdtech.jellyfin.presentation.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.View
import android.view.accessibility.CaptioningManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.core.presentation.components.AmbientPlayerBackground
import dev.jdtech.jellyfin.isControlsLocked
import dev.jdtech.jellyfin.models.VolumeKeyEvent
import dev.jdtech.jellyfin.player.core.domain.models.PlayerSidePanelType
import dev.jdtech.jellyfin.player.local.mpv.updateMpvResizeMode
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.components.DoubleTapSeekOverlay
import dev.jdtech.jellyfin.presentation.player.components.FloatingSkipButton
import dev.jdtech.jellyfin.presentation.player.components.PlayerBottomSheet
import dev.jdtech.jellyfin.presentation.player.components.PlayerCenterControls
import dev.jdtech.jellyfin.presentation.player.components.PlayerSidePanel
import dev.jdtech.jellyfin.presentation.player.components.PlayerTopBar
import dev.jdtech.jellyfin.presentation.player.components.QuickSpeedOverlay
import dev.jdtech.jellyfin.presentation.player.components.ScreenLockOverlay
import dev.jdtech.jellyfin.presentation.player.components.TrickplayScrubber
import dev.jdtech.jellyfin.presentation.player.components.VideoPlayerSeeker
import dev.jdtech.jellyfin.presentation.player.components.VolumeBrightnessOverlay
import dev.jdtech.jellyfin.presentation.player.components.ZoomFeedbackOverlay
import dev.jdtech.jellyfin.presentation.player.components.calculateTrickplayCardWidth
import dev.jdtech.jellyfin.presentation.player.components.rememberPlayerHaptics
import dev.jdtech.jellyfin.presentation.player.components.rememberVideoPlayerState
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerResizeMode
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

private fun VideoSize.toAspectRatio(): Float =
    if (height > 0 && width > 0) {
        val pixelRatio = if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f
        (width.toFloat() * pixelRatio) / height.toFloat()
    } else {
        16f / 9f
    }

/**
 * Fullscreen video player screen providing gesture-based scrubbing, volume/brightness swipe HUDs,
 * double-tap seeking, chapter navigation, track selection, and PiP on phone and tablet devices.
 *
 * @param itemId Unique [UUID] identifier of the media item being played.
 * @param itemKind The media type (e.g. "Movie", "Episode").
 * @param startFromBeginning Whether playback should start from 0 rather than resuming.
 * @param onBack Callback when pressing back or navigating away.
 * @param onEnterPip Callback to request Picture-in-Picture mode.
 * @param isPipSupported Whether the current device and OS version support PiP.
 * @param isInPictureInPictureMode Whether the host activity is currently in PiP mode.
 * @param resizeMode Active [AspectRatioFrameLayout] scaling mode (Fit, Zoom, Fill).
 * @param onResizeModeChange Callback invoked when the user selects a new resize mode.
 * @param onToggleOrientation Callback to lock or toggle display orientation.
 * @param isOrientationLocked Whether orientation lock is currently active.
 * @param volumeKeyEvents Stream of hardware volume key events (true for up, false for down).
 * @param appPreferences Injected or remembered [AppPreferences] instance.
 * @param viewModel Injected [PlayerViewModel] managing media playback state.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    itemId: UUID,
    itemKind: String,
    startFromBeginning: Boolean,
    onBack: () -> Unit,
    onEnterPip: () -> Unit,
    isPipSupported: Boolean = true,
    isInPictureInPictureMode: Boolean = false,
    resizeMode: PlayerResizeMode? = null,
    onResizeModeChange: (PlayerResizeMode) -> Unit = {},
    onToggleOrientation: () -> Unit = {},
    isOrientationLocked: Boolean = false,
    volumeKeyEvents: Flow<VolumeKeyEvent> = emptyFlow(),
    onPlayerViewAvailable: (PlayerView) -> Unit = {},
    appPreferences: AppPreferences? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = appPreferences ?: remember(context) { AppPreferences(context) }
    val defaultResizeModePref by
        remember(prefs) { prefs.observe(prefs.playerDefaultResizeMode) }
            .collectAsState(initial = prefs.getValue(prefs.playerDefaultResizeMode))
    val defaultResizeMode =
        remember(defaultResizeModePref) { PlayerResizeMode.fromValue(defaultResizeModePref) }
    val currentResizeMode = resizeMode ?: defaultResizeMode
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val controlsTimeoutSeconds by
        remember(prefs) { prefs.observe(prefs.playerControlsTimeout) }
            .collectAsState(initial = prefs.getValue(prefs.playerControlsTimeout))
    val videoPlayerState = rememberVideoPlayerState(controlsTimeoutSeconds = controlsTimeoutSeconds)

    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = windowAdaptiveInfo.windowSizeClass
    val isExpandedWidth =
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val isCompactWidth =
        !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val isInPip = isInPictureInPictureMode || viewModel.isInPictureInPictureMode
    val hapticsEnabled by
        remember(prefs) { prefs.observe(prefs.playerHaptics) }
            .collectAsState(initial = prefs.getValue(prefs.playerHaptics))
    val haptics = rememberPlayerHaptics(enabled = hapticsEnabled)

    val isAmbientModeEnabled by
        remember(prefs) { prefs.observe(prefs.playerAmbientMode) }
            .collectAsState(initial = prefs.getValue(prefs.playerAmbientMode))
    var capturedPlayerView by remember { mutableStateOf<PlayerView?>(null) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(viewModel.player.isPlaying) }
    var isBuffering by remember {
        mutableStateOf(viewModel.player.playbackState == Player.STATE_BUFFERING)
    }
    var videoAspectRatio by remember {
        mutableFloatStateOf(viewModel.player.videoSize.toAspectRatio())
    }
    var lastAppliedResizeMode by remember { mutableStateOf<PlayerResizeMode?>(null) }

    var hasPreviousItem by remember { mutableStateOf(viewModel.player.hasPreviousMediaItem()) }
    var hasNextItem by remember { mutableStateOf(viewModel.player.hasNextMediaItem()) }
    var mediaItemCount by remember { mutableIntStateOf(viewModel.player.mediaItemCount) }

    // Dual Trickplay Scrubbing state
    // Mode A: Horizontal swipe on video screen (TV-style centered)
    var isGestureScrubbing by remember { mutableStateOf(false) }
    var gestureScrubPositionMs by remember { mutableLongStateOf(0L) }
    var gestureScrubDeltaMs by remember { mutableLongStateOf(0L) }

    // Mode B: Scrubbing on SeekBar (floating card follows touch)
    var isSeekBarScrubbing by remember { mutableStateOf(false) }
    var seekBarScrubPositionMs by remember { mutableLongStateOf(0L) }
    var seekBarTouchX by remember { mutableFloatStateOf(0f) }

    val isAnyScrubbing = isGestureScrubbing || isSeekBarScrubbing

    // Volume & Brightness HUD
    val initialVolume = remember {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (maxVol > 0) curVol.toFloat() / maxVol.toFloat() else 0.5f
    }
    var volumeLevel by remember { mutableFloatStateOf(initialVolume) }
    var isVolumeHudVisible by remember { mutableStateOf(false) }
    var volumeJob by remember { mutableStateOf<Job?>(null) }
    var isVerticalGestureActive by remember { mutableStateOf(false) }

    val adjustVolume: (Boolean, Boolean) -> Unit = { isUp, isRepeat ->
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) {
            val prevSysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val direction = if (isUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)

            val currentSysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val newLevel = currentSysVol.toFloat() / max.toFloat()
            volumeLevel = newLevel

            val volumeChanged = currentSysVol != prevSysVol

            // Single press: NO vibration.
            // Keeping pressed (isRepeat == true): Light tick vibration on intermediate steps,
            // reject haptic ONCE when hitting 0% or 100%, and STOP vibrating when held at limit.
            if (isRepeat && volumeChanged) {
                if (currentSysVol == 0 || currentSysVol == max) {
                    haptics.reject()
                } else {
                    haptics.tick()
                }
            }

            isVolumeHudVisible = true
            volumeJob?.cancel()
            volumeJob = coroutineScope.launch {
                delay(1200.milliseconds)
                isVolumeHudVisible = false
            }
        }
    }

    LaunchedEffect(isVolumeHudVisible) {
        if (!isVolumeHudVisible) {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (maxVol > 0) {
                val currentSysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                volumeLevel = currentSysVol.toFloat() / maxVol.toFloat()
            }
        }
    }

    LaunchedEffect(volumeKeyEvents) {
        volumeKeyEvents.collect { event -> adjustVolume(event.isUp, event.isRepeat) }
    }

    val isGesturesMasterEnabled by
        remember(prefs) { prefs.observe(prefs.playerGestures) }
            .collectAsState(initial = prefs.getValue(prefs.playerGestures))

    val isGesturesVBEnabled by
        remember(prefs) { prefs.observe(prefs.playerGesturesVB) }
            .collectAsState(initial = prefs.getValue(prefs.playerGesturesVB))

    val isGesturesSeekEnabled by
        remember(prefs) { prefs.observe(prefs.playerGesturesSeek) }
            .collectAsState(initial = prefs.getValue(prefs.playerGesturesSeek))

    val isGesturesZoomEnabled by
        remember(prefs) { prefs.observe(prefs.playerGesturesZoom) }
            .collectAsState(initial = prefs.getValue(prefs.playerGesturesZoom))

    val isGesturesQuickSpeedEnabled by
        remember(prefs) { prefs.observe(prefs.playerGesturesQuickSpeed) }
            .collectAsState(initial = prefs.getValue(prefs.playerGesturesQuickSpeed))

    val isQuickSpeedAllowed = isGesturesMasterEnabled && isGesturesQuickSpeedEnabled
    var isQuickSpeedActive by remember { mutableStateOf(false) }

    val isGesturesSeekTrickplayEnabled by
        remember(prefs) { prefs.observe(prefs.playerGesturesSeekTrickplay) }
            .collectAsState(initial = prefs.getValue(prefs.playerGesturesSeekTrickplay))

    val isTrickplayEnabled by
        remember(prefs) { prefs.observe(prefs.playerTrickplay) }
            .collectAsState(initial = prefs.getValue(prefs.playerTrickplay))

    val isBrightnessRememberEnabled by
        remember(prefs) { prefs.observe(prefs.playerGesturesBrightnessRemember) }
            .collectAsState(initial = prefs.getValue(prefs.playerGesturesBrightnessRemember))

    val savedBrightnessPref by
        remember(prefs) { prefs.observe(prefs.playerBrightness) }
            .collectAsState(initial = prefs.getValue(prefs.playerBrightness))

    val isVBAllowed = isGesturesMasterEnabled && isGesturesVBEnabled
    val isSeekAllowed = isGesturesMasterEnabled && isGesturesSeekEnabled
    val isZoomAllowed = isGesturesMasterEnabled && isGesturesZoomEnabled
    val isSeekTrickplayAllowed = isTrickplayEnabled && isGesturesSeekTrickplayEnabled

    val initialBrightness =
        remember(isBrightnessRememberEnabled, savedBrightnessPref) {
            if (isBrightnessRememberEnabled && savedBrightnessPref >= 0f) {
                savedBrightnessPref
            } else if (activity != null) {
                val lp = activity.window.attributes
                if (lp.screenBrightness >= 0f) {
                    lp.screenBrightness
                } else {
                    try {
                        Settings.System.getFloat(
                            activity.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS,
                            128f,
                        ) / 255f
                    } catch (_: Exception) {
                        0.5f
                    }
                }
            } else {
                0.5f
            }
        }
    var brightnessLevel by remember { mutableFloatStateOf(initialBrightness) }
    var isBrightnessHudVisible by remember { mutableStateOf(false) }
    var brightnessJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(isBrightnessRememberEnabled, savedBrightnessPref, activity) {
        if (isBrightnessRememberEnabled && savedBrightnessPref >= 0f && activity != null) {
            val lp = activity.window.attributes
            lp.screenBrightness = savedBrightnessPref.coerceIn(0.01f, 1f)
            activity.window.attributes = lp
        }
    }

    // Double tap seek ripple & cumulative seek seconds
    var showDoubleTapSeekLeft by remember { mutableStateOf(false) }
    var showDoubleTapSeekRight by remember { mutableStateOf(false) }
    var accumulatedSeekSeconds by remember { mutableIntStateOf(0) }
    var doubleTapJob by remember { mutableStateOf<Job?>(null) }

    // Screen locked temporary unlock button display
    var showUnlockButton by remember { mutableStateOf(false) }
    var unlockJob by remember { mutableStateOf<Job?>(null) }

    // Pinch / Aspect ratio zoom feedback
    var showZoomFeedback by remember { mutableStateOf(false) }
    var zoomFeedbackJob by remember { mutableStateOf<Job?>(null) }

    val isPanelOpen = videoPlayerState.isPanelOpen
    var openedPanelDirectly by remember { mutableStateOf(false) }

    // Handle back button / gesture
    BackHandler {
        when {
            videoPlayerState.isControlsLocked -> {
                showUnlockButton = true
                unlockJob?.cancel()
                unlockJob = coroutineScope.launch {
                    delay(3000.milliseconds)
                    showUnlockButton = false
                }
            }
            videoPlayerState.sidePanelType != PlayerSidePanelType.NONE -> {
                if (
                    videoPlayerState.sidePanelType != PlayerSidePanelType.SETTINGS &&
                        !openedPanelDirectly
                ) {
                    videoPlayerState.sidePanelType = PlayerSidePanelType.SETTINGS
                } else {
                    videoPlayerState.sidePanelType = PlayerSidePanelType.NONE
                    videoPlayerState.hideControls()
                    openedPanelDirectly = false
                }
            }
            videoPlayerState.controlsVisible -> {
                videoPlayerState.hideControls()
            }
            else -> onBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventsChannelFlow.collect { event ->
            if (event is PlayerEvents.NavigateBack) onBack()
        }
    }

    DisposableEffect(viewModel.player) {
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    currentPosition = viewModel.player.currentPosition
                    bufferedPosition = viewModel.player.bufferedPosition
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    bufferedPosition = viewModel.player.bufferedPosition
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.height > 0 && videoSize.width > 0) {
                        videoAspectRatio = videoSize.toAspectRatio()
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    currentPosition = newPosition.positionMs
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    hasPreviousItem = viewModel.player.hasPreviousMediaItem()
                    hasNextItem = viewModel.player.hasNextMediaItem()
                    mediaItemCount = viewModel.player.mediaItemCount
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    hasPreviousItem = viewModel.player.hasPreviousMediaItem()
                    hasNextItem = viewModel.player.hasNextMediaItem()
                    mediaItemCount = viewModel.player.mediaItemCount
                }
            }
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            currentPosition = viewModel.player.currentPosition
            bufferedPosition = viewModel.player.bufferedPosition
            hasPreviousItem = viewModel.player.hasPreviousMediaItem()
            hasNextItem = viewModel.player.hasNextMediaItem()
            mediaItemCount = viewModel.player.mediaItemCount
            if (isPlaying) {
                if (tick % 3 == 0) viewModel.updateCurrentSegment()
                if (tick % 16 == 0 && tick > 0) viewModel.updatePlaybackProgress()
            }
            tick++
            delay(300.milliseconds)
        }
    }

    val shouldShrinkVideo = isExpandedWidth && isPanelOpen && !isInPip
    val panelProgressState =
        animateFloatAsState(
            targetValue = if (shouldShrinkVideo) 1f else 0f,
            animationSpec =
                if (isInPip) snap() else tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "panelProgress",
        )
    val panelSlideAnimSpec =
        tween<IntOffset>(
            durationMillis = 320,
            easing = FastOutSlowInEasing,
        )
    val panelFadeAnimSpec =
        tween<Float>(
            durationMillis = 320,
            easing = FastOutSlowInEasing,
        )

    val videoPaddingEnd = 432.dp * panelProgressState.value
    val videoCornerRadius = 24.dp * panelProgressState.value
    val videoPaddingInset = 16.dp * panelProgressState.value

    LaunchedEffect(isInPip) {
        if (isInPip && videoPlayerState.isPanelOpen) {
            videoPlayerState.sidePanelType = PlayerSidePanelType.NONE
            openedPanelDirectly = false
        }
    }

    // Fade center controls in landscape to alpha 0f while scrubbing
    val centerControlsAlpha by
        animateFloatAsState(
            targetValue = if (isSeekBarScrubbing || isGestureScrubbing) 0f else 1f,
            animationSpec = tween(180),
            label = "centerControlsAlpha",
        )

    val skipButtonDurationPref by
        remember(prefs) { prefs.observe(prefs.playerMediaSegmentsSkipButtonDuration) }
            .collectAsState(initial = prefs.getValue(prefs.playerMediaSegmentsSkipButtonDuration))

    val segment = uiState.currentSegment
    var isSkipButtonTimeoutExpired by remember(segment) { mutableStateOf(false) }

    LaunchedEffect(segment, skipButtonDurationPref) {
        if (segment != null) {
            isSkipButtonTimeoutExpired = false
            val durationSeconds = skipButtonDurationPref
            if (durationSeconds > 0) {
                delay((durationSeconds * 1000L).milliseconds)
                isSkipButtonTimeoutExpired = true
            }
        } else {
            isSkipButtonTimeoutExpired = true
        }
    }

    val showFloatingSkipButton =
        segment != null &&
            !isPanelOpen &&
            !videoPlayerState.isControlsLocked &&
            !isAnyScrubbing &&
            (videoPlayerState.controlsVisible || !isSkipButtonTimeoutExpired)

    val isShrunkOrAnimating =
        isExpandedWidth && (isPanelOpen || panelProgressState.value > 0f) && !isInPip
    val effectiveResizeMode =
        if (isInPip || isShrunkOrAnimating) AspectRatioFrameLayout.RESIZE_MODE_FIT
        else currentResizeMode.aspectRatioMode

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val insets = WindowInsets.safeDrawing.union(WindowInsets.displayCutout).asPaddingValues()
        val density = LocalDensity.current
        val leftInset = insets.calculateLeftPadding(LayoutDirection.Ltr)
        val rightInset = insets.calculateRightPadding(LayoutDirection.Ltr)
        val topInset = insets.calculateTopPadding()
        val bottomInset = insets.calculateBottomPadding()

        val minTitleTopPadding = topInset + 24.dp
        val titleContentHeight = if (!uiState.currentItemSubtitle.isNullOrBlank()) 52.dp else 32.dp
        val reservedTopHeight = minTitleTopPadding + titleContentHeight + 16.dp

        val bottomControllerPadding = bottomInset + 12.dp
        val controllerContentHeight = 112.dp
        val reservedBottomHeight = bottomControllerPadding + controllerContentHeight + 48.dp

        val maxHorizontalInset = maxOf(leftInset, rightInset)

        val (shrunkVideoWidth, shrunkVideoHeight) =
            remember(
                screenWidth,
                screenHeight,
                videoAspectRatio,
                reservedTopHeight,
                reservedBottomHeight,
            ) {
                val maxW = (screenWidth - 432.dp - 32.dp).coerceAtLeast(0.dp)
                val maxH =
                    (screenHeight - reservedTopHeight - reservedBottomHeight).coerceAtLeast(0.dp)
                if (videoAspectRatio > 0f && maxW.value > 0f && maxH.value > 0f) {
                    val containerRatio = maxW.value / maxH.value
                    if (containerRatio > videoAspectRatio) {
                        (maxH * videoAspectRatio) to maxH
                    } else {
                        maxW to (maxW / videoAspectRatio)
                    }
                } else {
                    maxW to maxH
                }
            }

        val (fullVideoWidth, fullVideoHeight) =
            remember(screenWidth, screenHeight, videoAspectRatio, currentResizeMode) {
                when (currentResizeMode) {
                    PlayerResizeMode.FILL -> screenWidth to screenHeight
                    PlayerResizeMode.ZOOM -> {
                        if (
                            videoAspectRatio > 0f &&
                                screenWidth.value > 0f &&
                                screenHeight.value > 0f
                        ) {
                            val containerRatio = screenWidth.value / screenHeight.value
                            if (containerRatio > videoAspectRatio) {
                                screenWidth to (screenWidth / videoAspectRatio)
                            } else {
                                (screenHeight * videoAspectRatio) to screenHeight
                            }
                        } else {
                            screenWidth to screenHeight
                        }
                    }
                    PlayerResizeMode.FIT -> {
                        if (
                            videoAspectRatio > 0f &&
                                screenWidth.value > 0f &&
                                screenHeight.value > 0f
                        ) {
                            val containerRatio = screenWidth.value / screenHeight.value
                            if (containerRatio > videoAspectRatio) {
                                (screenHeight * videoAspectRatio) to screenHeight
                            } else {
                                screenWidth to (screenWidth / videoAspectRatio)
                            }
                        } else {
                            screenWidth to screenHeight
                        }
                    }
                }
            }

        val targetWidth = if (isShrunkOrAnimating) shrunkVideoWidth else screenWidth
        val targetHeight = if (isShrunkOrAnimating) shrunkVideoHeight else screenHeight

        val shrunkVideoCenterY =
            reservedTopHeight +
                ((screenHeight - reservedTopHeight - reservedBottomHeight).coerceAtLeast(0.dp) / 2)
        val shrunkCenterX = (screenWidth - 432.dp) / 2
        val shrunkCenterY = shrunkVideoCenterY

        val fullCenterX = screenWidth / 2
        val fullCenterY = screenHeight / 2

        val targetCenterX = if (isShrunkOrAnimating) shrunkCenterX else fullCenterX
        val targetCenterY = if (isShrunkOrAnimating) shrunkCenterY else fullCenterY

        val videoTop = targetCenterY - (targetHeight / 2)

        val titleTopPadding = maxOf(minTitleTopPadding, (videoTop - titleContentHeight) / 2)

        val ambientWidth = targetWidth + 600.dp
        val ambientHeight = targetHeight + 600.dp

        val cardWidth = calculateTrickplayCardWidth(uiState.currentTrickplay, videoAspectRatio)
        val cardHalfWidth = cardWidth / 2
        val seekBarStartOffset = leftInset + 20.dp
        val touchXAbsoluteDp = seekBarStartOffset + with(density) { seekBarTouchX.toDp() }
        val minAvailableLeft = leftInset + 16.dp
        val maxAvailableRight = screenWidth - videoPaddingEnd - rightInset - 20.dp
        val clampedOffsetX =
            (touchXAbsoluteDp - cardHalfWidth).coerceIn(
                minAvailableLeft,
                (maxAvailableRight - cardWidth + 4.dp).coerceAtLeast(minAvailableLeft),
            )

        val trickplayBottomPadding by
            animateDpAsState(
                targetValue =
                    if (shouldShrinkVideo) {
                        bottomControllerPadding + controllerContentHeight + 8.dp
                    } else {
                        bottomInset + (if (isPortrait) 142.dp else 72.dp) + videoPaddingInset
                    },
                animationSpec = tween(220),
                label = "trickplayBottomPadding",
            )

        val isAmbientActive =
            isAmbientModeEnabled &&
                !isInPip &&
                (isPanelOpen || currentResizeMode == PlayerResizeMode.FIT)

        val startScaleX =
            if (shrunkVideoWidth.value > 0f) fullVideoWidth.value / shrunkVideoWidth.value else 1f
        val startScaleY =
            if (shrunkVideoHeight.value > 0f) fullVideoHeight.value / shrunkVideoHeight.value
            else 1f
        val startTransX = with(density) { (fullCenterX - shrunkCenterX).toPx() }
        val startTransY = with(density) { (fullCenterY - shrunkCenterY).toPx() }

        if (!isInPip) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier.requiredSize(ambientWidth, ambientHeight)
                            .offset(
                                x = targetCenterX - fullCenterX,
                                y = targetCenterY - fullCenterY,
                            )
                            .graphicsLayer {
                                val p = panelProgressState.value
                                if (isShrunkOrAnimating) {
                                    scaleX = startScaleX + (1f - startScaleX) * p
                                    scaleY = startScaleY + (1f - startScaleY) * p
                                    translationX = startTransX * (1f - p)
                                    translationY = startTransY * (1f - p)
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    AmbientPlayerBackground(
                        playerView = capturedPlayerView,
                        isPlaying = isPlaying,
                        enabled = isAmbientActive,
                        currentPositionMs = currentPosition,
                        isPanelOpen = isPanelOpen,
                        applyBlackBorder = shouldShrinkVideo,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Video Player Container (dynamically adapts when side panel opens on tablet)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val currentRadius = 24.dp * panelProgressState.value
            Box(
                modifier =
                    Modifier.size(targetWidth, targetHeight)
                        .offset(
                            x = targetCenterX - fullCenterX,
                            y = targetCenterY - fullCenterY,
                        )
                        .graphicsLayer {
                            val p = panelProgressState.value
                            if (isShrunkOrAnimating) {
                                scaleX = startScaleX + (1f - startScaleX) * p
                                scaleY = startScaleY + (1f - startScaleY) * p
                                translationX = startTransX * (1f - p)
                                translationY = startTransY * (1f - p)
                            }
                        }
                        .then(
                            if (currentRadius > 0.dp)
                                Modifier.clip(RoundedCornerShape(currentRadius))
                            else Modifier
                        )
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).also { playerView ->
                            onPlayerViewAvailable(playerView)
                            playerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                onPlayerViewAvailable(playerView)
                            }
                            playerView.useController = false
                            playerView.controllerAutoShow = false
                            playerView.player = viewModel.player
                            playerView.resizeMode = effectiveResizeMode
                            playerView.player?.updateMpvResizeMode(currentResizeMode)
                            lastAppliedResizeMode = currentResizeMode
                            val captioningManager =
                                ctx.getSystemService(Context.CAPTIONING_SERVICE)
                                    as? CaptioningManager
                            playerView.subtitleView?.apply {
                                if (captioningManager?.isEnabled == true) {
                                    setUserDefaultStyle()
                                    setUserDefaultTextSize()
                                } else {
                                    setStyle(
                                        CaptionStyleCompat(
                                            android.graphics.Color.WHITE,
                                            android.graphics.Color.TRANSPARENT,
                                            android.graphics.Color.TRANSPARENT,
                                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                            android.graphics.Color.BLACK,
                                            null,
                                        )
                                    )
                                    setFractionalTextSize(
                                        SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.25f
                                    )
                                }
                                setBottomPaddingFraction(0.08f)
                            }
                            viewModel.initializePlayer(
                                itemId = itemId,
                                itemKind = itemKind,
                                startFromBeginning = startFromBeginning,
                            )
                            playerView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            playerView.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            playerView
                                .findViewById<View>(androidx.media3.ui.R.id.exo_shutter)
                                ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            playerView
                                .findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
                                ?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            capturedPlayerView = playerView
                        }
                    },
                    update = { playerView ->
                        onPlayerViewAvailable(playerView)
                        capturedPlayerView = playerView
                        if (playerView.resizeMode != effectiveResizeMode) {
                            playerView.resizeMode = effectiveResizeMode
                        }
                        if (lastAppliedResizeMode != currentResizeMode) {
                            lastAppliedResizeMode = currentResizeMode
                            playerView.player?.updateMpvResizeMode(currentResizeMode)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (shouldShrinkVideo) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize().clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                videoPlayerState.sidePanelType = PlayerSidePanelType.NONE
                                videoPlayerState.hideControls()
                                openedPanelDirectly = false
                            }
                    )
                }
            }
        }

        val shrunkStartPadding = maxHorizontalInset + 16.dp
        val shrunkEndPadding =
            if (isExpandedWidth) 432.dp + maxHorizontalInset + 16.dp else maxHorizontalInset + 16.dp

        // Top: Centered Title & Subtitle when side panel is open on tablet (no back button)
        AnimatedVisibility(
            visible = shouldShrinkVideo,
            enter = fadeIn(panelFadeAnimSpec),
            exit = fadeOut(tween(200, easing = LinearEasing)),
            modifier =
                Modifier.align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(
                        start = shrunkStartPadding,
                        end = shrunkEndPadding,
                        top = titleTopPadding,
                    )
                    .zIndex(5f),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiState.currentItemTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (!uiState.currentItemSubtitle.isNullOrBlank()) {
                    Text(
                        text = uiState.currentItemSubtitle.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Bottom: Seeker ABOVE buttons when side panel is open on tablet
        AnimatedVisibility(
            visible = shouldShrinkVideo,
            enter = fadeIn(panelFadeAnimSpec),
            exit = fadeOut(tween(200, easing = LinearEasing)),
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(
                        start = shrunkStartPadding,
                        end = shrunkEndPadding,
                        bottom = bottomControllerPadding,
                    )
                    .zIndex(5f),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                VideoPlayerSeeker(
                    state = videoPlayerState,
                    onSeek = { fraction ->
                        val duration = viewModel.player.duration.coerceAtLeast(0L)
                        val targetMs = (fraction * duration).toLong()
                        currentPosition = targetMs
                        viewModel.player.seekTo(targetMs)
                    },
                    contentProgress = currentPosition.milliseconds,
                    contentDuration = viewModel.player.duration.coerceAtLeast(0L).milliseconds,
                    bufferedProgress = bufferedPosition.milliseconds,
                    chapters =
                        if (uiState.showChapterMarkers) uiState.currentChapters else emptyList(),
                    segments = if (uiState.showChapterMarkers) uiState.segments else emptyList(),
                    isScrubbing = isSeekBarScrubbing,
                    scrubPositionMs = seekBarScrubPositionMs,
                    onScrubbingChanged = { scrubbing, posMs, touchX ->
                        isSeekBarScrubbing = scrubbing
                        seekBarScrubPositionMs = posMs
                        seekBarTouchX = touchX
                    },
                    onOpenChapters = {
                        openedPanelDirectly = true
                        videoPlayerState.sidePanelType = PlayerSidePanelType.CHAPTERS
                    },
                    isPortrait = false,
                    isCompactWidth = false,
                    modifier = Modifier.fillMaxWidth(),
                )

                val isEpisode = itemKind.equals("Episode", ignoreCase = true)
                PlayerCenterControls(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    onPlayPause = {
                        videoPlayerState.resetTimeout()
                        if (isPlaying) viewModel.player.pause() else viewModel.player.play()
                    },
                    onSeekRelative = { deltaMs ->
                        videoPlayerState.resetTimeout()
                        val duration = viewModel.player.duration.coerceAtLeast(0L)
                        val newPos =
                            (viewModel.player.currentPosition + deltaMs).coerceIn(0L, duration)
                        currentPosition = newPos
                        viewModel.player.seekTo(newPos)
                    },
                    hasPreviousItem = hasPreviousItem,
                    hasNextItem = hasNextItem,
                    onPreviousItem =
                        if (isEpisode) {
                            {
                                videoPlayerState.resetTimeout()
                                viewModel.player.seekToPreviousMediaItem()
                            }
                        } else null,
                    onNextItem =
                        if (isEpisode) {
                            {
                                videoPlayerState.resetTimeout()
                                viewModel.player.seekToNextMediaItem()
                            }
                        } else null,
                    isPortrait = false,
                )
            }
        }

        AnimatedVisibility(
            visible =
                (isBuffering || !uiState.fileLoaded) &&
                    !isAnyScrubbing &&
                    !(isExpandedWidth && isPanelOpen),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center).zIndex(6f),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 6.dp,
                modifier = Modifier.size(56.dp),
            )
        }

        if (!isInPip) {
            fun performSeekLeft() {
                showDoubleTapSeekRight = false
                haptics.tick()
                val seekBackIncMs = viewModel.player.seekBackIncrement.takeIf { it > 0 } ?: 5_000L
                val seekBackSec = (seekBackIncMs / 1000).toInt().coerceAtLeast(1)
                if (showDoubleTapSeekLeft) {
                    accumulatedSeekSeconds += seekBackSec
                } else {
                    accumulatedSeekSeconds = seekBackSec
                    showDoubleTapSeekLeft = true
                }
                val newPos = (viewModel.player.currentPosition - seekBackIncMs).coerceAtLeast(0L)
                viewModel.player.seekTo(newPos)
                doubleTapJob?.cancel()
                doubleTapJob = coroutineScope.launch {
                    delay(800.milliseconds)
                    showDoubleTapSeekLeft = false
                }
            }

            fun performSeekRight() {
                haptics.tick()
                showDoubleTapSeekLeft = false
                val seekForwardIncMs =
                    viewModel.player.seekForwardIncrement.takeIf { it > 0 } ?: 15_000L
                val seekForwardSec = (seekForwardIncMs / 1000).toInt().coerceAtLeast(1)
                if (showDoubleTapSeekRight) {
                    accumulatedSeekSeconds += seekForwardSec
                } else {
                    accumulatedSeekSeconds = seekForwardSec
                    showDoubleTapSeekRight = true
                }
                val duration = viewModel.player.duration.coerceAtLeast(0L)
                val newPos =
                    (viewModel.player.currentPosition + seekForwardIncMs).coerceAtMost(duration)
                viewModel.player.seekTo(newPos)
                doubleTapJob?.cancel()
                doubleTapJob = coroutineScope.launch {
                    delay(800.milliseconds)
                    showDoubleTapSeekRight = false
                }
            }

            val currentShowDoubleTapLeft by rememberUpdatedState(showDoubleTapSeekLeft)
            val currentShowDoubleTapRight by rememberUpdatedState(showDoubleTapSeekRight)
            val currentPerformSeekLeft by rememberUpdatedState { performSeekLeft() }
            val currentPerformSeekRight by rememberUpdatedState { performSeekRight() }

            fun triggerPinchZoomFeedback() {
                showZoomFeedback = true
                haptics.confirm()
                zoomFeedbackJob?.cancel()
                zoomFeedbackJob = coroutineScope.launch {
                    delay(1200.milliseconds)
                    showZoomFeedback = false
                }
            }

            // Gesture Detection Overlay on the video area (only when not shrunk on tablet)
            if (!shouldShrinkVideo) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(
                                start = videoPaddingInset,
                                end = videoPaddingEnd,
                                top = videoPaddingInset,
                                bottom = videoPaddingInset,
                            )
                            .pointerInput(
                                videoPlayerState.isControlsLocked,
                                isPanelOpen,
                            ) {
                                if (videoPlayerState.isControlsLocked) {
                                    detectTapGestures(
                                        onTap = {
                                            showUnlockButton = true
                                            unlockJob?.cancel()
                                            unlockJob = coroutineScope.launch {
                                                delay(3000.milliseconds)
                                                showUnlockButton = false
                                            }
                                        }
                                    )
                                } else {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            if (
                                                currentShowDoubleTapLeft &&
                                                    offset.x < size.width * 0.4f
                                            ) {
                                                currentPerformSeekLeft()
                                            } else if (
                                                currentShowDoubleTapRight &&
                                                    offset.x > size.width * 0.6f
                                            ) {
                                                currentPerformSeekRight()
                                            } else if (isPanelOpen) {
                                                videoPlayerState.sidePanelType =
                                                    PlayerSidePanelType.NONE
                                                videoPlayerState.hideControls()
                                                openedPanelDirectly = false
                                            } else {
                                                videoPlayerState.toggleControls(
                                                    autoHide = isPlaying
                                                )
                                            }
                                        },
                                        onDoubleTap = { offset ->
                                            if (isPanelOpen) {
                                                videoPlayerState.sidePanelType =
                                                    PlayerSidePanelType.NONE
                                                videoPlayerState.hideControls()
                                                openedPanelDirectly = false
                                            } else {
                                                val videoScreenWidth = size.width
                                                if (offset.x < videoScreenWidth * 0.4f) {
                                                    currentPerformSeekLeft()
                                                } else if (offset.x > videoScreenWidth * 0.6f) {
                                                    currentPerformSeekRight()
                                                } else {
                                                    // Center double tap: play / pause
                                                    if (!isBuffering) {
                                                        haptics.confirm()
                                                    }
                                                    if (viewModel.player.isPlaying)
                                                        viewModel.player.pause()
                                                    else viewModel.player.play()
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            .pointerInput(
                                videoPlayerState.isControlsLocked,
                                currentResizeMode,
                                isPanelOpen,
                                isGesturesMasterEnabled,
                                isVBAllowed,
                                isSeekAllowed,
                                isZoomAllowed,
                                isQuickSpeedAllowed,
                            ) {
                                if (
                                    !videoPlayerState.isControlsLocked &&
                                        !isPanelOpen &&
                                        isGesturesMasterEnabled
                                ) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val videoScreenWidth = size.width
                                        val videoScreenHeight = size.height
                                        val horizontalEdgeExclusionPx = 28.dp.toPx()
                                        val verticalEdgeExclusionPx = 48.dp.toPx()
                                        val isHorizontalEdgeTouch =
                                            down.position.x < horizontalEdgeExclusionPx ||
                                                down.position.x >
                                                    videoScreenWidth - horizontalEdgeExclusionPx
                                        val isVerticalEdgeTouch =
                                            down.position.y < verticalEdgeExclusionPx ||
                                                down.position.y >
                                                    videoScreenHeight - verticalEdgeExclusionPx
                                        val isRightSide = down.position.x > videoScreenWidth / 2f
                                        var totalDragY = 0f
                                        var totalDragX = 0f
                                        var isVerticalDrag = false
                                        var isHorizontalDrag = false
                                        var isPinchGesture = false
                                        var totalZoom = 1f
                                        var startPosition = viewModel.player.currentPosition
                                        var continuousVolume = 0f
                                        var continuousBrightness = 0f
                                        var lastSetVolumeInt = -1

                                        var originalSpeed =
                                            viewModel.player.playbackParameters.speed
                                        var quickSpeedTriggered = false
                                        val quickSpeedJob =
                                            if (
                                                isQuickSpeedAllowed &&
                                                    !isHorizontalEdgeTouch &&
                                                    !isVerticalEdgeTouch
                                            ) {
                                                coroutineScope.launch {
                                                    delay(400.milliseconds)
                                                    quickSpeedTriggered = true
                                                    isQuickSpeedActive = true
                                                    haptics.tick()
                                                    originalSpeed =
                                                        viewModel.player.playbackParameters.speed
                                                    viewModel.player.playbackParameters =
                                                        PlaybackParameters(2.0f)
                                                }
                                            } else null

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val pressedChanges = event.changes.filter { it.pressed }
                                            if (pressedChanges.isEmpty()) {
                                                quickSpeedJob?.cancel()
                                                if (quickSpeedTriggered) {
                                                    isQuickSpeedActive = false
                                                    viewModel.player.playbackParameters =
                                                        PlaybackParameters(originalSpeed)
                                                }
                                                if (isHorizontalDrag) {
                                                    haptics.gestureEnd()
                                                    viewModel.player.seekTo(gestureScrubPositionMs)
                                                    isGestureScrubbing = false
                                                    gestureScrubDeltaMs = 0L
                                                }
                                                if (isVerticalDrag) {
                                                    isVerticalGestureActive = false
                                                }
                                                break
                                            }

                                            if (pressedChanges.size >= 2) {
                                                quickSpeedJob?.cancel()
                                                if (quickSpeedTriggered) {
                                                    isQuickSpeedActive = false
                                                    viewModel.player.playbackParameters =
                                                        PlaybackParameters(originalSpeed)
                                                }
                                                isPinchGesture = true
                                                if (isHorizontalDrag) {
                                                    isGestureScrubbing = false
                                                    gestureScrubDeltaMs = 0L
                                                    isHorizontalDrag = false
                                                }
                                                if (isVerticalDrag) {
                                                    isVerticalGestureActive = false
                                                }
                                                isVerticalDrag = false

                                                if (isZoomAllowed) {
                                                    val zoomChange = event.calculateZoom()
                                                    totalZoom *= zoomChange
                                                    event.changes.forEach { it.consume() }

                                                    if (totalZoom > 1.15f) {
                                                        if (
                                                            currentResizeMode !=
                                                                PlayerResizeMode.ZOOM
                                                        ) {
                                                            onResizeModeChange(
                                                                PlayerResizeMode.ZOOM
                                                            )
                                                            totalZoom = 1f
                                                            triggerPinchZoomFeedback()
                                                        }
                                                    } else if (totalZoom < 0.85f) {
                                                        if (
                                                            currentResizeMode !=
                                                                PlayerResizeMode.FIT
                                                        ) {
                                                            onResizeModeChange(PlayerResizeMode.FIT)
                                                            totalZoom = 1f
                                                            triggerPinchZoomFeedback()
                                                        }
                                                    }
                                                }
                                            } else if (!isPinchGesture) {
                                                val dragChange =
                                                    event.changes.firstOrNull() ?: break
                                                if (dragChange.isConsumed) break

                                                val pan = event.calculatePan()
                                                totalDragY += pan.y
                                                totalDragX += pan.x

                                                if (!isVerticalDrag && !isHorizontalDrag) {
                                                    if (
                                                        isVBAllowed &&
                                                            !isHorizontalEdgeTouch &&
                                                            !isVerticalEdgeTouch &&
                                                            abs(totalDragY) > 20f &&
                                                            abs(totalDragY) > abs(totalDragX) * 1.5f
                                                    ) {
                                                        quickSpeedJob?.cancel()
                                                        if (quickSpeedTriggered) {
                                                            isQuickSpeedActive = false
                                                            viewModel.player.playbackParameters =
                                                                PlaybackParameters(originalSpeed)
                                                        }
                                                        isVerticalDrag = true
                                                        isVerticalGestureActive = true
                                                        if (isRightSide) {
                                                            val maxVol =
                                                                audioManager.getStreamMaxVolume(
                                                                    AudioManager.STREAM_MUSIC
                                                                )
                                                            val currentVol =
                                                                audioManager.getStreamVolume(
                                                                    AudioManager.STREAM_MUSIC
                                                                )
                                                            continuousVolume =
                                                                if (isVolumeHudVisible) {
                                                                    volumeLevel
                                                                } else if (maxVol > 0) {
                                                                    currentVol.toFloat() /
                                                                        maxVol.toFloat()
                                                                } else {
                                                                    0f
                                                                }
                                                            lastSetVolumeInt = currentVol
                                                            volumeLevel = continuousVolume
                                                        } else {
                                                            if (activity != null) {
                                                                val lp = activity.window.attributes
                                                                continuousBrightness =
                                                                    if (lp.screenBrightness < 0f) {
                                                                        try {
                                                                            Settings.System
                                                                                .getFloat(
                                                                                    activity
                                                                                        .contentResolver,
                                                                                    Settings.System
                                                                                        .SCREEN_BRIGHTNESS,
                                                                                    128f,
                                                                                ) / 255f
                                                                        } catch (_: Exception) {
                                                                            0.5f
                                                                        }
                                                                    } else {
                                                                        brightnessLevel
                                                                    }
                                                                brightnessLevel =
                                                                    continuousBrightness
                                                            }
                                                        }
                                                    } else if (
                                                        isSeekAllowed &&
                                                            !isHorizontalEdgeTouch &&
                                                            !isVerticalEdgeTouch &&
                                                            abs(totalDragX) > 25f &&
                                                            abs(totalDragX) > abs(totalDragY) * 1.5f
                                                    ) {
                                                        quickSpeedJob?.cancel()
                                                        if (quickSpeedTriggered) {
                                                            isQuickSpeedActive = false
                                                            viewModel.player.playbackParameters =
                                                                PlaybackParameters(originalSpeed)
                                                        }
                                                        isHorizontalDrag = true
                                                        haptics.gestureStart()
                                                        startPosition =
                                                            viewModel.player.currentPosition
                                                        isGestureScrubbing = true
                                                    }
                                                }

                                                if (isVerticalDrag) {
                                                    dragChange.consume()
                                                    val deltaPercent =
                                                        -pan.y / (size.height * 0.75f)
                                                    if (isRightSide) {
                                                        val maxVol =
                                                            audioManager.getStreamMaxVolume(
                                                                AudioManager.STREAM_MUSIC
                                                            )
                                                        val newContinuousVolume =
                                                            (continuousVolume + deltaPercent)
                                                                .coerceIn(
                                                                    0f,
                                                                    1f,
                                                                )
                                                        if (
                                                            (continuousVolume < 1f &&
                                                                newContinuousVolume == 1f) ||
                                                                (continuousVolume > 0f &&
                                                                    newContinuousVolume == 0f)
                                                        ) {
                                                            haptics.boundary()
                                                        }
                                                        continuousVolume = newContinuousVolume
                                                        volumeLevel = continuousVolume
                                                        val targetVol =
                                                            kotlin.math
                                                                .round(continuousVolume * maxVol)
                                                                .toInt()
                                                        if (targetVol != lastSetVolumeInt) {
                                                            audioManager.setStreamVolume(
                                                                AudioManager.STREAM_MUSIC,
                                                                targetVol,
                                                                0,
                                                            )
                                                            lastSetVolumeInt = targetVol
                                                        }
                                                        isVolumeHudVisible = true
                                                        volumeJob?.cancel()
                                                        volumeJob = coroutineScope.launch {
                                                            delay(1200.milliseconds)
                                                            isVolumeHudVisible = false
                                                        }
                                                    } else {
                                                        if (activity != null) {
                                                            val newContinuousBrightness =
                                                                (continuousBrightness +
                                                                        deltaPercent)
                                                                    .coerceIn(
                                                                        0f,
                                                                        1f,
                                                                    )
                                                            if (
                                                                (continuousBrightness < 1f &&
                                                                    newContinuousBrightness ==
                                                                        1f) ||
                                                                    (continuousBrightness > 0f &&
                                                                        newContinuousBrightness ==
                                                                            0f)
                                                            ) {
                                                                haptics.boundary()
                                                            }
                                                            continuousBrightness =
                                                                newContinuousBrightness
                                                            val lp = activity.window.attributes
                                                            lp.screenBrightness =
                                                                continuousBrightness.coerceIn(
                                                                    0.01f,
                                                                    1f,
                                                                )
                                                            activity.window.attributes = lp
                                                            brightnessLevel = continuousBrightness
                                                            if (isBrightnessRememberEnabled) {
                                                                prefs.setValue(
                                                                    prefs.playerBrightness,
                                                                    continuousBrightness,
                                                                )
                                                            }
                                                            isBrightnessHudVisible = true
                                                            brightnessJob?.cancel()
                                                            brightnessJob = coroutineScope.launch {
                                                                delay(1200.milliseconds)
                                                                isBrightnessHudVisible = false
                                                            }
                                                        }
                                                    }
                                                } else if (isHorizontalDrag) {
                                                    dragChange.consume()
                                                    val duration =
                                                        viewModel.player.duration.coerceAtLeast(0L)
                                                    val deltaMs =
                                                        (totalDragX / size.width * 120_000L)
                                                            .toLong()
                                                    val targetPos =
                                                        (startPosition + deltaMs).coerceIn(
                                                            0L,
                                                            duration,
                                                        )
                                                    gestureScrubPositionMs = targetPos
                                                    gestureScrubDeltaMs = deltaMs
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                )
            }

            // Volume / Brightness HUD
            VolumeBrightnessOverlay(
                value = brightnessLevel,
                isVolume = false,
                visible = isBrightnessHudVisible,
                animate = !isVerticalGestureActive,
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .padding(start = 32.dp + videoPaddingInset)
                        .zIndex(9f),
            )

            VolumeBrightnessOverlay(
                value = volumeLevel,
                isVolume = true,
                visible = isVolumeHudVisible,
                animate = !isVerticalGestureActive,
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = 32.dp + videoPaddingEnd)
                        .zIndex(9f),
            )

            // Double Tap Seek Ripple Overlays
            DoubleTapSeekOverlay(
                isForward = false,
                visible = showDoubleTapSeekLeft,
                seekSeconds = accumulatedSeekSeconds,
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .padding(start = 64.dp + videoPaddingInset)
                        .zIndex(9f),
            )

            DoubleTapSeekOverlay(
                isForward = true,
                visible = showDoubleTapSeekRight,
                seekSeconds = accumulatedSeekSeconds,
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = 64.dp + videoPaddingEnd)
                        .zIndex(9f),
            )

            // Zoom / Aspect Ratio Feedback Overlay (YouTube-style edge flash & badge)
            ZoomFeedbackOverlay(
                visible = showZoomFeedback,
                resizeMode = currentResizeMode.aspectRatioMode,
                cornerRadius = videoCornerRadius,
                modifier =
                    Modifier.padding(
                            start = videoPaddingInset,
                            end = videoPaddingEnd,
                            top = videoPaddingInset,
                            bottom = videoPaddingInset,
                        )
                        .zIndex(10f),
            )

            // Hold for 2x speed quick speed overlay
            Box(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .padding(top = insets.calculateTopPadding() + 20.dp)
                        .zIndex(10f)
            ) {
                QuickSpeedOverlay(
                    visible = isQuickSpeedActive,
                    speed = "2\u00D7",
                )
            }

            // A. Centered Trickplay Scrubber Card when swiping on video screen (TV-style)
            AnimatedVisibility(
                visible = isGestureScrubbing,
                enter =
                    fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(180)),
                exit =
                    fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
                modifier =
                    Modifier.align(Alignment.Center)
                        .padding(start = videoPaddingInset, end = videoPaddingEnd)
                        .zIndex(7f),
            ) {
                val gestureTrickplay =
                    if (isSeekTrickplayAllowed) uiState.currentTrickplay else null
                TrickplayScrubber(
                    trickplay = gestureTrickplay,
                    currentPositionMs = gestureScrubPositionMs,
                    durationMs = viewModel.player.duration.coerceAtLeast(0L),
                    segments = uiState.segments,
                    chapters = uiState.currentChapters,
                    seekDeltaMs = gestureScrubDeltaMs,
                    videoAspectRatio = videoAspectRatio,
                    cardHeight = 150.dp,
                )
            }

            // B. Floating Trickplay Preview Card directly above the seekbar when scrubbing on
            // seekbar
            AnimatedVisibility(
                visible = isSeekBarScrubbing,
                enter =
                    fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(180)),
                exit =
                    fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .padding(bottom = trickplayBottomPadding)
                        .offset(x = clampedOffsetX)
                        .zIndex(7f),
            ) {
                TrickplayScrubber(
                    trickplay = uiState.currentTrickplay,
                    currentPositionMs = seekBarScrubPositionMs,
                    durationMs = viewModel.player.duration.coerceAtLeast(0L),
                    segments = uiState.segments,
                    chapters = uiState.currentChapters,
                    seekDeltaMs = 0L,
                    videoAspectRatio = videoAspectRatio,
                )
            }

            // Floating Skip Intro/Credits Button
            val floatingSkipButtonBottomPadding by
                animateDpAsState(
                    targetValue =
                        if (videoPlayerState.controlsVisible) {
                            insets.calculateBottomPadding() +
                                (if (isPortrait) 168.dp else 96.dp) +
                                videoPaddingInset
                        } else {
                            insets.calculateBottomPadding() + 32.dp + videoPaddingInset
                        },
                    animationSpec = tween(250),
                    label = "floatingSkipButtonBottomPadding",
                )

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(
                            end =
                                insets.calculateRightPadding(LayoutDirection.Ltr) +
                                    24.dp +
                                    videoPaddingEnd,
                            bottom = floatingSkipButtonBottomPadding,
                        )
                        .zIndex(6f),
                contentAlignment = Alignment.BottomEnd,
            ) {
                FloatingSkipButton(
                    stringRes = uiState.currentSkipButtonStringRes,
                    onClick = {
                        haptics.confirm()
                        if (segment != null) viewModel.skipSegment(segment)
                    },
                    visible = showFloatingSkipButton,
                )
            }

            // Screen Lock Overlay
            ScreenLockOverlay(
                isLocked = videoPlayerState.isControlsLocked,
                showUnlockButton = showUnlockButton,
                onUnlock = {
                    haptics.confirm()
                    videoPlayerState.setLocked(false)
                    if (isOrientationLocked) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                    } else {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                    }
                    isControlsLocked = false
                    (activity as? PlayerActivity)?.updatePipParams()
                    showUnlockButton = false
                    unlockJob?.cancel()
                },
                onKeepAlive = {
                    unlockJob?.cancel()
                    unlockJob = coroutineScope.launch {
                        delay(3000.milliseconds)
                        showUnlockButton = false
                    }
                },
            )

            // Main Controls Overlay (Top Bar, Center Playback Buttons, Bottom Seeker Bar)
            AnimatedVisibility(
                visible =
                    videoPlayerState.controlsVisible &&
                        !videoPlayerState.isControlsLocked &&
                        !isPanelOpen,
                enter = fadeIn(panelFadeAnimSpec),
                exit = fadeOut(panelFadeAnimSpec),
                modifier = Modifier.fillMaxSize().zIndex(4f),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Black.copy(alpha = 0.65f),
                                    (if (isCompactWidth) 0.25f else 0.15f) to Color.Transparent,
                                    (if (isPortrait) 0.60f else 0.75f) to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.80f),
                                )
                            )
                            .padding(
                                start = maxHorizontalInset + 16.dp,
                                end = maxHorizontalInset + 16.dp,
                                top = insets.calculateTopPadding() + 12.dp,
                                bottom =
                                    insets.calculateBottomPadding() +
                                        (if (isPortrait) 16.dp else 24.dp),
                            )
                ) {
                    // Top Bar: Back, Title, Top-Right Actions (Subtitles, Audio, Lock, PiP,
                    // Settings)
                    PlayerTopBar(
                        title = uiState.currentItemTitle,
                        subtitle = uiState.currentItemSubtitle,
                        onBack = onBack,
                        onOpenSubtitles = {
                            openedPanelDirectly = true
                            videoPlayerState.sidePanelType = PlayerSidePanelType.SUBTITLES
                        },
                        onOpenAudio = {
                            openedPanelDirectly = true
                            videoPlayerState.sidePanelType = PlayerSidePanelType.AUDIO
                        },
                        onEnterPip = onEnterPip,
                        onOpenSettings = {
                            openedPanelDirectly = false
                            videoPlayerState.sidePanelType = PlayerSidePanelType.SETTINGS
                        },
                        onOpenSleepTimer = {
                            openedPanelDirectly = true
                            videoPlayerState.sidePanelType = PlayerSidePanelType.SLEEP_TIMER
                        },
                        onToggleOrientation = onToggleOrientation,
                        isOrientationLocked = isOrientationLocked,
                        isPipSupported = isPipSupported,
                        isCompactWidth = isCompactWidth,
                        sleepTimerStatus = uiState.sleepTimerStatus,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    val isEpisode = itemKind.equals("Episode", ignoreCase = true)
                    if (!isPortrait) {
                        // Landscape Center Controls: Rewind 10s, Play/Pause, Fast-Forward 10s
                        Column(
                            modifier =
                                Modifier.align(Alignment.Center).graphicsLayer {
                                    alpha = centerControlsAlpha
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PlayerCenterControls(
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                onPlayPause = {
                                    videoPlayerState.resetTimeout()
                                    if (isPlaying) viewModel.player.pause()
                                    else viewModel.player.play()
                                },
                                onSeekRelative = { deltaMs ->
                                    videoPlayerState.resetTimeout()
                                    val duration = viewModel.player.duration.coerceAtLeast(0L)
                                    val newPos =
                                        (viewModel.player.currentPosition + deltaMs).coerceIn(
                                            0L,
                                            duration,
                                        )
                                    currentPosition = newPos
                                    viewModel.player.seekTo(newPos)
                                },
                                onPreviousItem =
                                    if (isEpisode) {
                                        {
                                            videoPlayerState.resetTimeout()
                                            viewModel.player.seekToPreviousMediaItem()
                                        }
                                    } else null,
                                onNextItem =
                                    if (isEpisode) {
                                        {
                                            videoPlayerState.resetTimeout()
                                            viewModel.player.seekToNextMediaItem()
                                        }
                                    } else null,
                                hasPreviousItem = hasPreviousItem,
                                hasNextItem = hasNextItem,
                                isPortrait = false,
                            )
                        }

                        // Landscape Bottom Controls: VideoPlayerSeeker
                        VideoPlayerSeeker(
                            state = videoPlayerState,
                            onSeek = { fraction ->
                                val duration = viewModel.player.duration.coerceAtLeast(0L)
                                val targetMs = (fraction * duration).toLong()
                                currentPosition = targetMs
                                viewModel.player.seekTo(targetMs)
                            },
                            contentProgress = currentPosition.milliseconds,
                            contentDuration =
                                viewModel.player.duration.coerceAtLeast(0L).milliseconds,
                            bufferedProgress = bufferedPosition.milliseconds,
                            chapters =
                                if (uiState.showChapterMarkers) uiState.currentChapters
                                else emptyList(),
                            segments =
                                if (uiState.showChapterMarkers) uiState.segments else emptyList(),
                            isScrubbing = isSeekBarScrubbing,
                            scrubPositionMs = seekBarScrubPositionMs,
                            onScrubbingChanged = { scrubbing, posMs, touchX ->
                                isSeekBarScrubbing = scrubbing
                                seekBarScrubPositionMs = posMs
                                seekBarTouchX = touchX
                            },
                            onOpenChapters = {
                                openedPanelDirectly = true
                                videoPlayerState.sidePanelType = PlayerSidePanelType.CHAPTERS
                            },
                            isPortrait = false,
                            isCompactWidth = isCompactWidth,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    } else {
                        // Portrait Bottom Deck: Seeker directly above Center Controls
                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            VideoPlayerSeeker(
                                state = videoPlayerState,
                                onSeek = { fraction ->
                                    val duration = viewModel.player.duration.coerceAtLeast(0L)
                                    val targetMs = (fraction * duration).toLong()
                                    currentPosition = targetMs
                                    viewModel.player.seekTo(targetMs)
                                },
                                contentProgress = currentPosition.milliseconds,
                                contentDuration =
                                    viewModel.player.duration.coerceAtLeast(0L).milliseconds,
                                bufferedProgress = bufferedPosition.milliseconds,
                                chapters =
                                    if (uiState.showChapterMarkers) uiState.currentChapters
                                    else emptyList(),
                                segments =
                                    if (uiState.showChapterMarkers) uiState.segments
                                    else emptyList(),
                                isScrubbing = isSeekBarScrubbing,
                                scrubPositionMs = seekBarScrubPositionMs,
                                onScrubbingChanged = { scrubbing, posMs, touchX ->
                                    isSeekBarScrubbing = scrubbing
                                    seekBarScrubPositionMs = posMs
                                    seekBarTouchX = touchX
                                },
                                onOpenChapters = {
                                    openedPanelDirectly = true
                                    videoPlayerState.sidePanelType = PlayerSidePanelType.CHAPTERS
                                },
                                isPortrait = true,
                                isCompactWidth = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            PlayerCenterControls(
                                isPlaying = isPlaying,
                                isBuffering = isBuffering,
                                onPlayPause = {
                                    videoPlayerState.resetTimeout()
                                    if (isPlaying) viewModel.player.pause()
                                    else viewModel.player.play()
                                },
                                onSeekRelative = { deltaMs ->
                                    videoPlayerState.resetTimeout()
                                    val duration = viewModel.player.duration.coerceAtLeast(0L)
                                    val newPos =
                                        (viewModel.player.currentPosition + deltaMs).coerceIn(
                                            0L,
                                            duration,
                                        )
                                    currentPosition = newPos
                                    viewModel.player.seekTo(newPos)
                                },
                                onPreviousItem =
                                    if (isEpisode) {
                                        {
                                            videoPlayerState.resetTimeout()
                                            viewModel.player.seekToPreviousMediaItem()
                                        }
                                    } else null,
                                onNextItem =
                                    if (isEpisode) {
                                        {
                                            videoPlayerState.resetTimeout()
                                            viewModel.player.seekToNextMediaItem()
                                        }
                                    } else null,
                                hasPreviousItem = hasPreviousItem,
                                hasNextItem = hasNextItem,
                                isPortrait = true,
                                modifier = Modifier.graphicsLayer { alpha = centerControlsAlpha },
                            )
                        }
                    }
                }
            }

            // Tablet Side Panel (Slides in from right, shrinks video on expanded width)
            if (isExpandedWidth) {
                AnimatedVisibility(
                    visible = isPanelOpen,
                    enter =
                        slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = panelSlideAnimSpec,
                        ),
                    exit =
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = panelSlideAnimSpec,
                        ),
                    modifier = Modifier.align(Alignment.CenterEnd).zIndex(8f),
                ) {
                    PlayerSidePanel(
                        panelType = videoPlayerState.sidePanelType,
                        onPanelTypeChange = { videoPlayerState.sidePanelType = it },
                        viewModel = viewModel,
                        currentResizeMode = currentResizeMode,
                        onResizeModeChange = onResizeModeChange,
                        openedDirectly = openedPanelDirectly,
                        isAmbientModeEnabled = isAmbientModeEnabled,
                        onAmbientModeToggle = { enabled ->
                            prefs.setValue(prefs.playerAmbientMode, enabled)
                        },
                        onClose = {
                            videoPlayerState.sidePanelType = PlayerSidePanelType.NONE
                            videoPlayerState.hideControls()
                            openedPanelDirectly = false
                        },
                        onLock = {
                            if (!isPlaying) return@PlayerSidePanel
                            haptics.confirm()
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                            isControlsLocked = true
                            (activity as? PlayerActivity)?.updatePipParams()
                            videoPlayerState.setLocked(true)
                        },
                    )
                }
            }

            // Bottom Sheet (overlays video on compact/medium width, e.g. phone or portrait tablet)
            if (!isExpandedWidth && isPanelOpen) {
                PlayerBottomSheet(
                    panelType = videoPlayerState.sidePanelType,
                    onPanelTypeChange = { videoPlayerState.sidePanelType = it },
                    viewModel = viewModel,
                    currentResizeMode = currentResizeMode,
                    onResizeModeChange = onResizeModeChange,
                    openedDirectly = openedPanelDirectly,
                    isAmbientModeEnabled = isAmbientModeEnabled,
                    onAmbientModeToggle = { enabled ->
                        prefs.setValue(prefs.playerAmbientMode, enabled)
                    },
                    onDismiss = {
                        videoPlayerState.sidePanelType = PlayerSidePanelType.NONE
                        videoPlayerState.hideControls()
                        openedPanelDirectly = false
                    },
                    onLock = {
                        if (!isPlaying) return@PlayerBottomSheet
                        haptics.confirm()
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        isControlsLocked = true
                        (activity as? PlayerActivity)?.updatePipParams()
                        videoPlayerState.setLocked(true)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 840, heightDp = 400)
@Composable
private fun PlayerScreenLandscapePreview() {
    FindroidTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            PlayerTopBar(
                title = "S01E01 - Pilot",
                subtitle = "Breaking Bad",
                onBack = {},
                onOpenSubtitles = {},
                onOpenAudio = {},
                onEnterPip = {},
                onOpenSettings = {},
                onToggleOrientation = {},
            )
            PlayerCenterControls(
                isPlaying = true,
                isBuffering = false,
                onPlayPause = {},
                onSeekRelative = {},
                hasPreviousItem = true,
                hasNextItem = true,
                onPreviousItem = {},
                onNextItem = {},
                modifier = Modifier.align(Alignment.Center),
            )
            VideoPlayerSeeker(
                state = rememberVideoPlayerState(),
                onSeek = {},
                contentProgress = 12.minutes,
                contentDuration = 45.minutes,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 800)
@Composable
private fun PlayerScreenPortraitPreview() {
    FindroidTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            PlayerTopBar(
                title = "S01E01 - Pilot",
                subtitle = "Breaking Bad",
                onBack = {},
                onOpenSubtitles = {},
                onOpenAudio = {},
                onEnterPip = {},
                onOpenSettings = {},
                onToggleOrientation = {},
                isCompactWidth = true,
            )
            PlayerCenterControls(
                isPlaying = false,
                isBuffering = false,
                onPlayPause = {},
                onSeekRelative = {},
                hasPreviousItem = true,
                hasNextItem = true,
                onPreviousItem = {},
                onNextItem = {},
                modifier = Modifier.align(Alignment.Center),
            )
            VideoPlayerSeeker(
                state = rememberVideoPlayerState(),
                onSeek = {},
                contentProgress = 5.minutes,
                contentDuration = 42.minutes,
                isCompactWidth = true,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}
