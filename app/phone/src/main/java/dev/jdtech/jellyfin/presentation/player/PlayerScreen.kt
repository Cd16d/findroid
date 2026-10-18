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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.isControlsLocked
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.components.DoubleTapSeekOverlay
import dev.jdtech.jellyfin.presentation.player.components.FloatingSkipButton
import dev.jdtech.jellyfin.presentation.player.components.PlayerBottomSheet
import dev.jdtech.jellyfin.presentation.player.components.PlayerCenterControls
import dev.jdtech.jellyfin.presentation.player.components.PlayerSidePanel
import dev.jdtech.jellyfin.presentation.player.components.PlayerSidePanelType
import dev.jdtech.jellyfin.presentation.player.components.PlayerTopBar
import dev.jdtech.jellyfin.presentation.player.components.ScreenLockOverlay
import dev.jdtech.jellyfin.presentation.player.components.TrickplayScrubber
import dev.jdtech.jellyfin.presentation.player.components.VideoPlayerSeeker
import dev.jdtech.jellyfin.presentation.player.components.VolumeBrightnessOverlay
import dev.jdtech.jellyfin.presentation.player.components.calculateTrickplayCardWidth
import dev.jdtech.jellyfin.presentation.player.components.rememberVideoPlayerState
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onResizeModeChange: (Int) -> Unit = {},
    onToggleOrientation: () -> Unit = {},
    isOrientationLocked: Boolean = false,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val coroutineScope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val videoPlayerState = rememberVideoPlayerState()

    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = windowAdaptiveInfo.windowSizeClass
    val isTablet =
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
            windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    val isInPip = isInPictureInPictureMode || viewModel.isInPictureInPictureMode

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
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var isVolumeHudVisible by remember { mutableStateOf(false) }
    var volumeJob by remember { mutableStateOf<Job?>(null) }

    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var isBrightnessHudVisible by remember { mutableStateOf(false) }
    var brightnessJob by remember { mutableStateOf<Job?>(null) }

    // Double tap seek ripple
    var showDoubleTapSeekLeft by remember { mutableStateOf(false) }
    var showDoubleTapSeekRight by remember { mutableStateOf(false) }
    var doubleTapJob by remember { mutableStateOf<Job?>(null) }

    // Screen locked temporary unlock button display
    var showUnlockButton by remember { mutableStateOf(false) }
    var unlockJob by remember { mutableStateOf<Job?>(null) }

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
        tween<androidx.compose.ui.unit.IntOffset>(
            durationMillis = 350,
            easing = FastOutSlowInEasing,
        )
    val shouldShrinkVideo = isTablet && isPanelOpen
    val videoPaddingEnd by
        animateDpAsState(
            targetValue = if (shouldShrinkVideo) 432.dp else 0.dp,
            animationSpec = panelDpAnimSpec,
            label = "videoPaddingEnd",
        )
    val videoPaddingInset by
        animateDpAsState(
            targetValue = if (shouldShrinkVideo) 32.dp else 0.dp,
            animationSpec = panelDpAnimSpec,
            label = "videoPaddingInset",
        )
    val videoCornerRadius by
        animateDpAsState(
            targetValue = if (shouldShrinkVideo) 24.dp else 0.dp,
            animationSpec = panelDpAnimSpec,
            label = "videoCornerRadius",
        )

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

    val segment = uiState.currentSegment
    val showFloatingSkipButton =
        segment != null &&
            !videoPlayerState.controlsVisible &&
            !isPanelOpen &&
            !videoPlayerState.isControlsLocked &&
            !isAnyScrubbing

    val effectiveResizeMode =
        if (isInPip || shouldShrinkVideo) AspectRatioFrameLayout.RESIZE_MODE_FIT else resizeMode

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidth = maxWidth
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val density = LocalDensity.current

        val cardWidth =
            remember(uiState.currentTrickplay, videoAspectRatio) {
                calculateTrickplayCardWidth(uiState.currentTrickplay, videoAspectRatio)
            }
        val cardHalfWidth = cardWidth / 2
        val seekBarStartOffset =
            insets.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + 20.dp
        val touchXAbsoluteDp = seekBarStartOffset + with(density) { seekBarTouchX.toDp() }
        val clampedOffsetX =
            (touchXAbsoluteDp - cardHalfWidth).coerceIn(
                16.dp,
                (screenWidth - cardWidth - 16.dp).coerceAtLeast(16.dp),
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
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).also { playerView ->
                            playerView.player = viewModel.player
                            playerView.useController = false
                            playerView.resizeMode = effectiveResizeMode
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
                    update = { playerView -> playerView.resizeMode = effectiveResizeMode },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (!isInPip) {
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
                                    onTap = {
                                        if (isPanelOpen) {
                                            videoPlayerState.sidePanelType =
                                                PlayerSidePanelType.NONE
                                        } else {
                                            videoPlayerState.toggleControls(autoHide = isPlaying)
                                        }
                                    },
                                    onDoubleTap = { offset ->
                                        val videoScreenWidth = size.width
                                        if (offset.x < videoScreenWidth * 0.4f) {
                                            // Left double tap: rewind 10s
                                            val newPos =
                                                (viewModel.player.currentPosition - 10_000L)
                                                    .coerceAtLeast(0L)
                                            viewModel.player.seekTo(newPos)
                                            showDoubleTapSeekLeft = true
                                            doubleTapJob?.cancel()
                                            doubleTapJob = coroutineScope.launch {
                                                delay(700.milliseconds)
                                                showDoubleTapSeekLeft = false
                                            }
                                        } else if (offset.x > videoScreenWidth * 0.6f) {
                                            // Right double tap: forward 10s
                                            val duration =
                                                viewModel.player.duration.coerceAtLeast(0L)
                                            val newPos =
                                                (viewModel.player.currentPosition + 10_000L)
                                                    .coerceAtMost(duration)
                                            viewModel.player.seekTo(newPos)
                                            showDoubleTapSeekRight = true
                                            doubleTapJob?.cancel()
                                            doubleTapJob = coroutineScope.launch {
                                                delay(700.milliseconds)
                                                showDoubleTapSeekRight = false
                                            }
                                        } else {
                                            // Center double tap: play / pause
                                            if (viewModel.player.isPlaying) viewModel.player.pause()
                                            else viewModel.player.play()
                                        }
                                    },
                                )
                            }
                        }
                        .pointerInput(videoPlayerState.isControlsLocked, resizeMode) {
                            if (!videoPlayerState.isControlsLocked) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val videoScreenWidth = size.width
                                    val edgeExclusionPx = 24.dp.toPx()
                                    val isEdgeTouch =
                                        down.position.x < edgeExclusionPx ||
                                            down.position.x > videoScreenWidth - edgeExclusionPx
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

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressedChanges = event.changes.filter { it.pressed }
                                        if (pressedChanges.isEmpty()) {
                                            if (isHorizontalDrag) {
                                                viewModel.player.seekTo(gestureScrubPositionMs)
                                                isGestureScrubbing = false
                                                gestureScrubDeltaMs = 0L
                                            }
                                            break
                                        }

                                        if (pressedChanges.size >= 2) {
                                            isPinchGesture = true
                                            if (isHorizontalDrag) {
                                                isGestureScrubbing = false
                                                gestureScrubDeltaMs = 0L
                                                isHorizontalDrag = false
                                            }
                                            isVerticalDrag = false

                                            val zoomChange = event.calculateZoom()
                                            totalZoom *= zoomChange
                                            event.changes.forEach { it.consume() }

                                            if (totalZoom > 1.15f) {
                                                if (
                                                    resizeMode !=
                                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                ) {
                                                    onResizeModeChange(
                                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                    )
                                                }
                                            } else if (totalZoom < 0.85f) {
                                                if (
                                                    resizeMode !=
                                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                ) {
                                                    onResizeModeChange(
                                                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                    )
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
                                                    !isEdgeTouch &&
                                                        abs(totalDragY) > 20f &&
                                                        abs(totalDragY) > abs(totalDragX) * 1.5f
                                                ) {
                                                    isVerticalDrag = true
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
                                                            if (maxVol > 0) {
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
                                                                    Settings.System.getFloat(
                                                                        activity.contentResolver,
                                                                        Settings.System
                                                                            .SCREEN_BRIGHTNESS,
                                                                        128f,
                                                                    ) / 255f
                                                                } else {
                                                                    lp.screenBrightness
                                                                }
                                                            brightnessLevel = continuousBrightness
                                                        }
                                                    }
                                                } else if (
                                                    !isEdgeTouch &&
                                                        abs(totalDragX) > 25f &&
                                                        abs(totalDragX) > abs(totalDragY) * 1.5f
                                                ) {
                                                    isHorizontalDrag = true
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
                                                    continuousVolume =
                                                        (continuousVolume + deltaPercent).coerceIn(
                                                            0f,
                                                            1f,
                                                        )
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
                                                        continuousBrightness =
                                                            (continuousBrightness + deltaPercent)
                                                                .coerceIn(
                                                                    0f,
                                                                    1f,
                                                                )
                                                        val lp = activity.window.attributes
                                                        lp.screenBrightness = continuousBrightness
                                                        activity.window.attributes = lp
                                                        brightnessLevel = continuousBrightness
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
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .padding(start = 32.dp + videoPaddingInset)
                        .zIndex(9f),
            )

            VolumeBrightnessOverlay(
                value = volumeLevel,
                isVolume = true,
                visible = isVolumeHudVisible,
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = 32.dp + videoPaddingEnd)
                        .zIndex(9f),
            )

            // Double Tap Seek Ripple Overlays
            DoubleTapSeekOverlay(
                isForward = false,
                visible = showDoubleTapSeekLeft,
                modifier =
                    Modifier.align(Alignment.CenterStart)
                        .padding(start = 64.dp + videoPaddingInset)
                        .zIndex(9f),
            )

            DoubleTapSeekOverlay(
                isForward = true,
                visible = showDoubleTapSeekRight,
                modifier =
                    Modifier.align(Alignment.CenterEnd)
                        .padding(end = 64.dp + videoPaddingEnd)
                        .zIndex(9f),
            )

            // A. Centered Trickplay Scrubber Card when swiping on video screen (TV-style)
            AnimatedVisibility(
                visible = isGestureScrubbing,
                enter =
                    fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(180)),
                exit =
                    fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
                modifier = Modifier.align(Alignment.Center).zIndex(7f),
            ) {
                TrickplayScrubber(
                    trickplay = uiState.currentTrickplay,
                    currentPositionMs = gestureScrubPositionMs,
                    durationMs = viewModel.player.duration.coerceAtLeast(0L),
                    segments = uiState.segments,
                    chapters = uiState.currentChapters,
                    seekDeltaMs = gestureScrubDeltaMs,
                    videoAspectRatio = videoAspectRatio,
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
                        .padding(bottom = insets.calculateBottomPadding() + 12.dp + 48.dp)
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
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(
                            end =
                                insets.calculateRightPadding(
                                    androidx.compose.ui.unit.LayoutDirection.Ltr
                                ) + 24.dp + videoPaddingEnd,
                            bottom = insets.calculateBottomPadding() + 32.dp + videoPaddingInset,
                        )
                        .zIndex(6f),
                contentAlignment = Alignment.BottomEnd,
            ) {
                FloatingSkipButton(
                    stringRes = uiState.currentSkipButtonStringRes,
                    onClick = { if (segment != null) viewModel.skipSegment(segment) },
                    visible = showFloatingSkipButton,
                )
            }

            // Screen Lock Overlay
            ScreenLockOverlay(
                isLocked = videoPlayerState.isControlsLocked,
                showUnlockButton = showUnlockButton,
                onUnlock = {
                    videoPlayerState.setLocked(false)
                    activity?.requestedOrientation =
                        if (isPortrait) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    isControlsLocked = false
                },
            )

            // Main Controls Overlay (Top Bar, Center Playback Buttons, Bottom Seeker Bar)
            AnimatedVisibility(
                visible = videoPlayerState.controlsVisible && !videoPlayerState.isControlsLocked,
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
                                    0.15f to Color.Transparent,
                                    0.75f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.75f * controlsScrimAlpha),
                                )
                            )
                            .padding(
                                start =
                                    insets.calculateLeftPadding(
                                        androidx.compose.ui.unit.LayoutDirection.Ltr
                                    ) + 16.dp,
                                end =
                                    insets.calculateRightPadding(
                                        androidx.compose.ui.unit.LayoutDirection.Ltr
                                    ) + 16.dp,
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
                        onToggleOrientation = onToggleOrientation,
                        isOrientationLocked = isOrientationLocked,
                        isPipSupported = isPipSupported,
                        isPortrait = isPortrait,
                        modifier =
                            Modifier.align(Alignment.TopCenter).graphicsLayer {
                                alpha = topBarAlpha
                            },
                    )

                    // Center Controls: Rewind 10s, Play/Pause, Fast-Forward 10s
                    val showMediaItemControls = mediaItemCount > 1 || hasPreviousItem || hasNextItem
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
                        onPreviousItem =
                            if (showMediaItemControls) {
                                {
                                    videoPlayerState.resetTimeout()
                                    viewModel.player.seekToPreviousMediaItem()
                                }
                            } else null,
                        onNextItem =
                            if (showMediaItemControls) {
                                {
                                    videoPlayerState.resetTimeout()
                                    viewModel.player.seekToNextMediaItem()
                                }
                            } else null,
                        hasPreviousItem = hasPreviousItem,
                        hasNextItem = hasNextItem,
                        isPortrait = isPortrait,
                        modifier =
                            Modifier.align(Alignment.Center).graphicsLayer {
                                alpha = centerControlsAlpha
                            },
                    )

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
                        chapters = uiState.currentChapters,
                        segments = uiState.segments,
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
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            // Tablet Side Panel (Slides in from right, shrinks video)
            if (isTablet) {
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
                        currentResizeMode = resizeMode,
                        onResizeModeChange = onResizeModeChange,
                        openedDirectly = openedPanelDirectly,
                        onClose = {
                            videoPlayerState.sidePanelType = PlayerSidePanelType.NONE
                            openedPanelDirectly = false
                        },
                        onLock = {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                            isControlsLocked = true
                            videoPlayerState.setLocked(true)
                        },
                    )
                }
            }

            // Phone Bottom Sheet (Chromecast style, overlays video)
            if (!isTablet && isPanelOpen) {
                PlayerBottomSheet(
                    panelType = videoPlayerState.sidePanelType,
                    onPanelTypeChange = { videoPlayerState.sidePanelType = it },
                    viewModel = viewModel,
                    currentResizeMode = resizeMode,
                    onResizeModeChange = onResizeModeChange,
                    openedDirectly = openedPanelDirectly,
                    onDismiss = {
                        videoPlayerState.sidePanelType = PlayerSidePanelType.NONE
                        openedPanelDirectly = false
                    },
                    onLock = {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        isControlsLocked = true
                        videoPlayerState.setLocked(true)
                    },
                )
            }
        }
    }
}

private fun androidx.compose.ui.input.pointer.PointerInputChange.changedToUpIgnoreConsumed():
    Boolean = !pressed && previousPressed
