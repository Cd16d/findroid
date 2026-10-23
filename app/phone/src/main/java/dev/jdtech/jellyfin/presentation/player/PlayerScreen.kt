package dev.jdtech.jellyfin.presentation.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.provider.Settings
import android.view.accessibility.CaptioningManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
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
import dev.jdtech.jellyfin.isControlsLocked
import dev.jdtech.jellyfin.models.VolumeKeyEvent
import dev.jdtech.jellyfin.player.local.mpv.updateMpvResizeMode
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.components.DoubleTapSeekOverlay
import dev.jdtech.jellyfin.presentation.player.components.FloatingSkipButton
import dev.jdtech.jellyfin.presentation.player.components.PlayerBottomSheet
import dev.jdtech.jellyfin.presentation.player.components.PlayerCenterControls
import dev.jdtech.jellyfin.presentation.player.components.PlayerSidePanel
import dev.jdtech.jellyfin.presentation.player.components.PlayerSidePanelType
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
import kotlin.math.roundToInt
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
    onVideoBoundsChanged: (android.graphics.Rect) -> Unit = {},
    volumeKeyEvents: Flow<VolumeKeyEvent> = emptyFlow(),
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
    val currentResizeMode =
        if (viewModel.isMpv && (resizeMode ?: defaultResizeMode) == PlayerResizeMode.FILL) {
            PlayerResizeMode.FIT
        } else {
            resizeMode ?: defaultResizeMode
        }
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

    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(viewModel.player.isPlaying) }
    var isBuffering by remember {
        mutableStateOf(viewModel.player.playbackState == Player.STATE_BUFFERING)
    }
    var videoAspectRatio by remember {
        mutableFloatStateOf(viewModel.player.videoSize.toAspectRatio())
    }

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
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
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

    LaunchedEffect(isPlaying) {
        var tick = 0
        while (isPlaying) {
            currentPosition = viewModel.player.currentPosition
            hasPreviousItem = viewModel.player.hasPreviousMediaItem()
            hasNextItem = viewModel.player.hasNextMediaItem()
            mediaItemCount = viewModel.player.mediaItemCount
            if (tick % 3 == 0) viewModel.updateCurrentSegment()
            if (tick % 16 == 0 && tick > 0) viewModel.updatePlaybackProgress()
            tick++
            delay(300.milliseconds)
        }
    }

    val panelDpAnimSpec = tween<Dp>(durationMillis = 350, easing = FastOutSlowInEasing)
    val panelSlideAnimSpec =
        tween<IntOffset>(
            durationMillis = 350,
            easing = FastOutSlowInEasing,
        )
    val shouldShrinkVideo = isExpandedWidth && isPanelOpen && !isInPip
    val animSpec = if (isInPip) snap() else panelDpAnimSpec
    val videoPaddingEnd by
        animateDpAsState(
            targetValue = if (shouldShrinkVideo) 432.dp else 0.dp,
            animationSpec = animSpec,
            label = "videoPaddingEnd",
        )
    val videoPaddingInset by
        animateDpAsState(
            targetValue = if (shouldShrinkVideo) 32.dp else 0.dp,
            animationSpec = animSpec,
            label = "videoPaddingInset",
        )
    val videoCornerRadius by
        animateDpAsState(
            targetValue = if (shouldShrinkVideo) 24.dp else 0.dp,
            animationSpec = animSpec,
            label = "videoCornerRadius",
        )

    LaunchedEffect(isInPip) {
        if (isInPip && videoPlayerState.isPanelOpen) {
            videoPlayerState.sidePanelType = PlayerSidePanelType.NONE
            openedPanelDirectly = false
        }
    }

    // Fade controls to alpha 0f while scrubbing on the seekbar
    val topBarAlpha by
        animateFloatAsState(
            targetValue = if (isSeekBarScrubbing) 0f else 1f,
            animationSpec = tween(180),
            label = "topBarAlpha",
        )
    val centerControlsAlpha by
        animateFloatAsState(
            targetValue = if (isSeekBarScrubbing || isGestureScrubbing) 0f else 1f,
            animationSpec = tween(180),
            label = "centerControlsAlpha",
        )
    val controlsScrimAlpha by
        animateFloatAsState(
            targetValue = if (isSeekBarScrubbing) 0f else 1f,
            animationSpec = tween(180),
            label = "controlsScrimAlpha",
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

    val effectiveResizeMode =
        if (isInPip || shouldShrinkVideo) AspectRatioFrameLayout.RESIZE_MODE_FIT
        else currentResizeMode.aspectRatioMode

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidth = maxWidth
        val insets = WindowInsets.safeDrawing.union(WindowInsets.displayCutout).asPaddingValues()
        val density = LocalDensity.current

        val cardWidth = calculateTrickplayCardWidth(uiState.currentTrickplay, videoAspectRatio)
        val cardHalfWidth = cardWidth / 2
        val seekBarStartOffset =
            videoPaddingInset + insets.calculateLeftPadding(LayoutDirection.Ltr) + 20.dp
        val touchXAbsoluteDp = seekBarStartOffset + with(density) { seekBarTouchX.toDp() }
        val minAvailableLeft =
            videoPaddingInset + insets.calculateLeftPadding(LayoutDirection.Ltr) + 16.dp
        val maxAvailableRight =
            screenWidth -
                videoPaddingEnd -
                insets.calculateRightPadding(LayoutDirection.Ltr) -
                20.dp
        val clampedOffsetX =
            (touchXAbsoluteDp - cardHalfWidth).coerceIn(
                minAvailableLeft,
                (maxAvailableRight - cardWidth + 4.dp).coerceAtLeast(minAvailableLeft),
            )

        // Video Player Container (with animated padding and corner radius when panel opens on
        // tablet)
        BoxWithConstraints(
            modifier =
                Modifier.fillMaxSize()
                    .padding(
                        start = videoPaddingInset,
                        end = videoPaddingEnd,
                        top = videoPaddingInset,
                        bottom = videoPaddingInset,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val shouldApplyAspectRatio = shouldShrinkVideo || videoCornerRadius > 0.dp
            val (targetWidth, targetHeight) =
                if (shouldApplyAspectRatio && videoAspectRatio > 0f && maxHeight.value > 0f) {
                    val containerRatio = maxWidth.value / maxHeight.value
                    if (containerRatio > videoAspectRatio) {
                        (maxHeight * videoAspectRatio) to maxHeight
                    } else {
                        maxWidth to (maxWidth / videoAspectRatio)
                    }
                } else {
                    maxWidth to maxHeight
                }

            Box(
                modifier =
                    Modifier.size(targetWidth, targetHeight)
                        .clip(RoundedCornerShape(videoCornerRadius))
                        .background(Color.Black)
                        .onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            onVideoBoundsChanged(
                                android.graphics.Rect(
                                    bounds.left.roundToInt(),
                                    bounds.top.roundToInt(),
                                    bounds.right.roundToInt(),
                                    bounds.bottom.roundToInt(),
                                )
                            )
                        }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).also { playerView ->
                            playerView.useController = false
                            playerView.controllerAutoShow = false
                            playerView.player = viewModel.player
                            playerView.resizeMode = effectiveResizeMode
                            playerView.player?.updateMpvResizeMode(currentResizeMode)
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
                            playerView.setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { playerView ->
                        playerView.resizeMode = effectiveResizeMode
                        playerView.player?.updateMpvResizeMode(currentResizeMode)
                    },
                    modifier =
                        Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            onVideoBoundsChanged(
                                android.graphics.Rect(
                                    bounds.left.roundToInt(),
                                    bounds.top.roundToInt(),
                                    bounds.right.roundToInt(),
                                    bounds.bottom.roundToInt(),
                                )
                            )
                        },
                )
            }
        }

        AnimatedVisibility(
            visible =
                (isBuffering || !uiState.fileLoaded) &&
                    !isAnyScrubbing &&
                    videoPlayerState.sidePanelType == PlayerSidePanelType.NONE,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier =
                Modifier.align(Alignment.Center)
                    .padding(
                        start =
                            videoPaddingInset + insets.calculateLeftPadding(LayoutDirection.Ltr),
                        end = videoPaddingEnd + insets.calculateRightPadding(LayoutDirection.Ltr),
                        top = videoPaddingInset + insets.calculateTopPadding(),
                        bottom = videoPaddingInset + insets.calculateBottomPadding(),
                    )
                    .zIndex(6f),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
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

            // Gesture Detection Overlay on the video area
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(
                            start = videoPaddingInset,
                            end = videoPaddingEnd,
                            top = videoPaddingInset,
                            bottom = videoPaddingInset,
                        )
                        .pointerInput(videoPlayerState.isControlsLocked, isPanelOpen) {
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
                                            currentShowDoubleTapLeft && offset.x < size.width * 0.4f
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
                                            videoPlayerState.toggleControls(autoHide = isPlaying)
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

                                    var originalSpeed = viewModel.player.playbackParameters.speed
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
                                                        currentResizeMode != PlayerResizeMode.ZOOM
                                                    ) {
                                                        onResizeModeChange(PlayerResizeMode.ZOOM)
                                                        totalZoom = 1f
                                                        triggerPinchZoomFeedback()
                                                    }
                                                } else if (totalZoom < 0.85f) {
                                                    if (currentResizeMode != PlayerResizeMode.FIT) {
                                                        onResizeModeChange(PlayerResizeMode.FIT)
                                                        totalZoom = 1f
                                                        triggerPinchZoomFeedback()
                                                    }
                                                }
                                            }
                                        } else if (!isPinchGesture) {
                                            val dragChange = event.changes.firstOrNull() ?: break
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
                                                                        Settings.System.getFloat(
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
                                                            brightnessLevel = continuousBrightness
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
                                                    startPosition = viewModel.player.currentPosition
                                                    isGestureScrubbing = true
                                                }
                                            }

                                            if (isVerticalDrag) {
                                                dragChange.consume()
                                                val deltaPercent = -pan.y / (size.height * 0.75f)
                                                if (isRightSide) {
                                                    val maxVol =
                                                        audioManager.getStreamMaxVolume(
                                                            AudioManager.STREAM_MUSIC
                                                        )
                                                    val newContinuousVolume =
                                                        (continuousVolume + deltaPercent).coerceIn(
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
                                                            (continuousBrightness + deltaPercent)
                                                                .coerceIn(
                                                                    0f,
                                                                    1f,
                                                                )
                                                        if (
                                                            (continuousBrightness < 1f &&
                                                                newContinuousBrightness == 1f) ||
                                                                (continuousBrightness > 0f &&
                                                                    newContinuousBrightness == 0f)
                                                        ) {
                                                            haptics.boundary()
                                                        }
                                                        continuousBrightness =
                                                            newContinuousBrightness
                                                        val lp = activity.window.attributes
                                                        lp.screenBrightness =
                                                            continuousBrightness.coerceIn(0.01f, 1f)
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
                                                    (totalDragX / size.width * 120_000L).toLong()
                                                val targetPos =
                                                    (startPosition + deltaMs).coerceIn(0L, duration)
                                                gestureScrubPositionMs = targetPos
                                                gestureScrubDeltaMs = deltaMs
                                            }
                                        }
                                    }
                                }
                            }
                        }
            )

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
            Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp).zIndex(10f)) {
                QuickSpeedOverlay(
                    visible = isQuickSpeedActive,
                    speed = "2x",
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
                        .padding(
                            bottom =
                                insets.calculateBottomPadding() + 12.dp + 48.dp + videoPaddingInset
                        )
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
                            insets.calculateBottomPadding() + 84.dp + videoPaddingInset
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
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize().zIndex(4f),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(
                                start = videoPaddingInset,
                                end = videoPaddingEnd,
                                top = videoPaddingInset,
                                bottom = videoPaddingInset,
                            )
                            .clip(RoundedCornerShape(videoCornerRadius))
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Black.copy(alpha = 0.65f * controlsScrimAlpha),
                                    (if (isCompactWidth) 0.25f else 0.15f) to Color.Transparent,
                                    0.75f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.75f * controlsScrimAlpha),
                                )
                            )
                            .padding(
                                start = insets.calculateLeftPadding(LayoutDirection.Ltr) + 16.dp,
                                end = insets.calculateRightPadding(LayoutDirection.Ltr) + 16.dp,
                                top = insets.calculateTopPadding() + 12.dp,
                                bottom = insets.calculateBottomPadding() + 12.dp,
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
                        modifier =
                            Modifier.align(Alignment.TopCenter).graphicsLayer {
                                alpha = topBarAlpha
                            },
                    )

                    // Center Controls: Rewind 10s, Play/Pause, Fast-Forward 10s (+ Title/Subtitle
                    // in portrait)
                    Column(
                        modifier =
                            Modifier.align(Alignment.Center).graphicsLayer {
                                alpha = centerControlsAlpha
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
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
                            isPortrait = isPortrait,
                        )
                    }

                    // Bottom Controls: VideoPlayerSeeker
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
                        isPortrait = isPortrait,
                        isCompactWidth = isCompactWidth,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
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
