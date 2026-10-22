package dev.jdtech.jellyfin.presentation.player

import android.content.Context
import android.view.accessibility.CaptioningManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.PlayerOverlayBackground
import dev.jdtech.jellyfin.core.presentation.theme.PlayerTvSurface
import dev.jdtech.jellyfin.core.presentation.theme.PlayerTvSurfaceFocused
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.components.AppScreensaver
import dev.jdtech.jellyfin.presentation.components.KeepScreenOn
import dev.jdtech.jellyfin.presentation.components.ProgressBadge
import dev.jdtech.jellyfin.presentation.components.SpoilerMask
import dev.jdtech.jellyfin.presentation.components.toScreensaverSlide
import dev.jdtech.jellyfin.presentation.player.components.PlayerSidePanel
import dev.jdtech.jellyfin.presentation.player.components.PlayerSidePanelType
import dev.jdtech.jellyfin.presentation.player.components.TrickplayScrubber
import dev.jdtech.jellyfin.presentation.player.components.VideoPlayerControlsLayout
import dev.jdtech.jellyfin.presentation.player.components.VideoPlayerMediaButton
import dev.jdtech.jellyfin.presentation.player.components.VideoPlayerMediaTitle
import dev.jdtech.jellyfin.presentation.player.components.VideoPlayerOverlay
import dev.jdtech.jellyfin.presentation.player.components.VideoPlayerSeeker
import dev.jdtech.jellyfin.presentation.player.components.VideoPlayerState
import dev.jdtech.jellyfin.presentation.player.components.rememberVideoPlayerState
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.NoOpBringIntoViewSpec
import dev.jdtech.jellyfin.presentation.utils.TvPivotItem
import dev.jdtech.jellyfin.presentation.utils.rememberTvPivotRowController
import dev.jdtech.jellyfin.presentation.utils.rememberTvTrailingSpace
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.toBlurHashPainter
import dev.jdtech.jellyfin.utils.toOptimizedImageUri
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

// Base horizontal padding for the Season Episodes row aligned with player controls (24.dp)
private val EpisodeShelfStartPadding = 24.dp

private fun VideoSize.toAspectRatio(): Float =
    if (height > 0 && width > 0) {
        val pixelRatio = if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f
        (width.toFloat() * pixelRatio) / height.toFloat()
    } else {
        16f / 9f
    }

/**
 * Main player screen for Android TV, supporting ExoPlayer playback, UI overlays, side panels,
 * season/episode navigation shelf, and screensaver integration.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    itemId: UUID,
    itemKind: String,
    startFromBeginning: Boolean,
    onNavigateToShow: ((UUID) -> Unit)? = null,
    onBack: () -> Unit = {},
) {
    val viewModel = hiltViewModel<PlayerViewModel>()
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.eventsChannelFlow.collect { event ->
            if (event is PlayerEvents.NavigateBack) onBack()
        }
    }

    DisposableEffect(Unit) { onDispose { viewModel.player.pause() } }

    DisposableEffect(lifecycleOwner) {
        var session: MediaSession? =
            try {
                MediaSession.Builder(context, viewModel.player)
                    .setId("findroid_tv_${UUID.randomUUID()}")
                    .build()
            } catch (e: Exception) {
                Timber.e(e, "Error creating MediaSession")
                null
            }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                session?.release()
                session = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            session?.release()
            session = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val prefs = remember(context) { AppPreferences(context) }
    val hideSeconds = remember { prefs.getValue(prefs.playerControlsTimeout).toInt() }
    val videoPlayerState = rememberVideoPlayerState(hideSeconds = hideSeconds)

    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(viewModel.player.isPlaying) }
    var isBuffering by remember {
        mutableStateOf(viewModel.player.playbackState == Player.STATE_BUFFERING)
    }
    var videoAspectRatio by remember {
        mutableFloatStateOf(viewModel.player.videoSize.toAspectRatio())
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
            }
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        var tick = 0
        while (isPlaying) {
            currentPosition = viewModel.player.currentPosition
            if (tick % 3 == 0) viewModel.updateCurrentSegment()
            if (tick % 16 == 0 && tick > 0) viewModel.updatePlaybackProgress()
            tick++
            delay(300.milliseconds)
        }
    }

    var sidePanel by remember { mutableStateOf(PlayerSidePanelType.NONE) }
    var panelOpenedFromSettings by remember { mutableStateOf(false) }
    val isPanelOpen = sidePanel != PlayerSidePanelType.NONE

    fun closeOrStepBackSidePanel() {
        if (panelOpenedFromSettings && sidePanel != PlayerSidePanelType.SETTINGS) {
            sidePanel = PlayerSidePanelType.SETTINGS
        } else {
            panelOpenedFromSettings = false
            sidePanel = PlayerSidePanelType.NONE
        }
    }

    fun toggleSidePanel(target: PlayerSidePanelType) {
        if (sidePanel == target) {
            sidePanel = PlayerSidePanelType.NONE
        } else {
            panelOpenedFromSettings = false
            sidePanel = target
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var scrubStartingPositionMs by remember { mutableLongStateOf(0L) }
    var scrubJob by remember { mutableStateOf<Job?>(null) }

    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    val isMovie =
        remember(itemKind) {
            itemKind.equals(BaseItemKind.MOVIE.serialName, ignoreCase = true) ||
                itemKind.equals("Movie", ignoreCase = true)
        }
    val hasEpisodesShelf = uiState.seasonEpisodes.isNotEmpty() && !isMovie
    val trickplayBottomPadding by
        animateDpAsState(
            targetValue = if (hasEpisodesShelf) 196.dp else 126.dp,
            label = "trickplayBottomPadding",
        )

    var isScreensaverVisible by remember { mutableStateOf(false) }
    var isDismissingScreensaver by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val isScreensaverEnabled = uiState.isScreensaverEnabled

    if (isPlaying || (isScreensaverEnabled && isScreensaverVisible)) {
        KeepScreenOn()
    }

    LaunchedEffect(isPlaying, isScreensaverVisible, lastInteractionTime, isScreensaverEnabled) {
        if (!isScreensaverEnabled || isPlaying) {
            isScreensaverVisible = false
        } else if (!isScreensaverVisible) {
            delay(60_000L.milliseconds)
            isScreensaverVisible = true
        }
    }

    BackHandler(enabled = isScreensaverVisible) {
        isScreensaverVisible = false
        videoPlayerState.showControls()
    }

    BackHandler(enabled = isPanelOpen) { closeOrStepBackSidePanel() }

    BackHandler(enabled = isScrubbing && !isPanelOpen && !isScreensaverVisible) {
        scrubJob?.cancel()
        isScrubbing = false
    }

    BackHandler(enabled = !isPanelOpen && !isScreensaverVisible && !isScrubbing) { onBack() }

    LaunchedEffect(isScrubbing) {
        if (isScrubbing) {
            videoPlayerState.showControls(seconds = Int.MAX_VALUE)
        } else {
            videoPlayerState.showControls()
        }
    }

    val panelAnimSpec = tween<Dp>(durationMillis = 350, easing = FastOutSlowInEasing)
    val videoPaddingEnd by
        animateDpAsState(
            targetValue = if (isPanelOpen) 400.dp else 0.dp,
            animationSpec = panelAnimSpec,
            label = "videoPaddingEnd",
        )
    val videoPaddingInset by
        animateDpAsState(
            targetValue = if (isPanelOpen) 32.dp else 0.dp,
            animationSpec = panelAnimSpec,
            label = "videoPaddingInset",
        )
    val videoCornerRadius by
        animateDpAsState(
            targetValue = if (isPanelOpen) 24.dp else 0.dp,
            animationSpec = panelAnimSpec,
            label = "videoCornerRadius",
        )

    val segment = uiState.currentSegment
    val showFloatingSkipButton =
        segment != null && !videoPlayerState.controlsVisible && !isPanelOpen
    val skipButtonFocusRequester = remember { FocusRequester() }
    val playerFocusRequester = remember { FocusRequester() }

    AnimatedVisibility(
        visible = showFloatingSkipButton,
        enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.9f, animationSpec = tween(250)),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200)),
        modifier = Modifier.zIndex(10f),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacings.large),
            contentAlignment = Alignment.BottomEnd,
        ) {
            PlayerSkipButton(
                stringRes = uiState.currentSkipButtonStringRes,
                onClick = {
                    try {
                        playerFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                    if (segment != null) viewModel.skipSegment(segment)
                },
                modifier =
                    Modifier.focusRequester(skipButtonFocusRequester).onPreviewKeyEvent { keyEvent
                        ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (keyEvent.key) {
                            Key.DirectionCenter,
                            Key.Enter,
                            Key.NumPadEnter -> {
                                try {
                                    playerFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                                if (segment != null) viewModel.skipSegment(segment)
                                true
                            }
                            Key.Back -> {
                                false
                            }
                            Key.DirectionUp,
                            Key.DirectionDown,
                            Key.DirectionLeft,
                            Key.DirectionRight -> {
                                videoPlayerState.showControls()
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
    }

    LaunchedEffect(showFloatingSkipButton, videoPlayerState.controlsVisible, isPanelOpen, segment) {
        if (showFloatingSkipButton) {
            skipButtonFocusRequester.requestFocus()
        } else if (!videoPlayerState.controlsVisible && !isPanelOpen && segment == null) {
            try {
                playerFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    fun seekRelativeWithTrickplay(deltaMs: Long) {
        val currentPos = viewModel.player.currentPosition
        val safeDuration = viewModel.player.duration.coerceAtLeast(0L)
        if (!isScrubbing) {
            scrubStartingPositionMs = currentPos
        }
        val newPos = (currentPos + deltaMs).coerceIn(0L, safeDuration)
        viewModel.player.seekTo(newPos)
        currentPosition = newPos
        scrubPositionMs = newPos
        isScrubbing = true
        scrubJob?.cancel()
        scrubJob = coroutineScope.launch {
            delay(2000.milliseconds)
            isScrubbing = false
        }
        videoPlayerState.showControls()
    }

    val screensaverSlides =
        remember(uiState.suggestions, uiState.currentItemTitle, uiState.currentImages) {
            uiState.suggestions
                .map { it.toScreensaverSlide() }
                .ifEmpty {
                    listOf(toScreensaverSlide(uiState.currentItemTitle, uiState.currentImages))
                }
        }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black)
                .focusRequester(playerFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    lastInteractionTime = System.currentTimeMillis()
                    if (isScreensaverVisible) {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            isScreensaverVisible = false
                            isDismissingScreensaver = true
                            videoPlayerState.showControls()
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (isDismissingScreensaver) {
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            isDismissingScreensaver = false
                        }
                        return@onPreviewKeyEvent true
                    }
                    when (keyEvent.key) {
                        Key.Settings,
                        Key.Menu -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (sidePanel != PlayerSidePanelType.NONE) {
                                    sidePanel = PlayerSidePanelType.NONE
                                } else {
                                    panelOpenedFromSettings = false
                                    sidePanel = PlayerSidePanelType.SETTINGS
                                }
                            }
                            true
                        }
                        Key.Captions -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                toggleSidePanel(PlayerSidePanelType.SUBTITLES)
                            }
                            true
                        }
                        Key.MediaAudioTrack -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                toggleSidePanel(PlayerSidePanelType.AUDIO)
                            }
                            true
                        }
                        Key.MediaFastForward,
                        Key.MediaSkipForward,
                        Key.MediaStepForward -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                seekRelativeWithTrickplay(10_000L)
                            }
                            true
                        }
                        Key.MediaRewind,
                        Key.MediaSkipBackward,
                        Key.MediaStepBackward -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                seekRelativeWithTrickplay(-10_000L)
                            }
                            true
                        }
                        Key.MediaPlay -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                viewModel.player.play()
                                videoPlayerState.showControls()
                            }
                            true
                        }
                        Key.MediaPause,
                        Key.MediaStop -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                viewModel.player.pause()
                                videoPlayerState.showControls()
                            }
                            true
                        }
                        Key.MediaPlayPause -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (viewModel.player.isPlaying) viewModel.player.pause()
                                else viewModel.player.play()
                                videoPlayerState.showControls()
                            }
                            true
                        }
                        Key.DirectionUp,
                        Key.DirectionDown,
                        Key.DirectionLeft,
                        Key.DirectionRight,
                        Key.DirectionCenter,
                        Key.Enter,
                        Key.NumPadEnter -> {
                            if (
                                !isPanelOpen &&
                                    !videoPlayerState.controlsVisible &&
                                    !showFloatingSkipButton
                            ) {
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    videoPlayerState.showControls()
                                }
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
    ) {
        val effectiveResizeMode =
            if (isPanelOpen) AspectRatioFrameLayout.RESIZE_MODE_FIT else resizeMode

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
            val shouldApplyAspectRatio = isPanelOpen || videoCornerRadius > 0.dp
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

        AnimatedVisibility(
            visible = (isBuffering || !uiState.fileLoaded) && !isScrubbing && !isPanelOpen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center).zIndex(1f),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                modifier = Modifier.size(56.dp),
            )
        }

        AnimatedVisibility(
            visible = isScrubbing,
            enter =
                fadeIn(animationSpec = tween(200)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
            exit =
                fadeOut(animationSpec = tween(150)) +
                    scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = trickplayBottomPadding)
                    .zIndex(5f),
        ) {
            TrickplayScrubber(
                trickplay = uiState.currentTrickplay,
                currentPositionMs = scrubPositionMs,
                durationMs = viewModel.player.duration,
                segments = uiState.segments,
                chapters = uiState.currentChapters,
                seekDeltaMs = scrubPositionMs - scrubStartingPositionMs,
                videoAspectRatio = videoAspectRatio,
            )
        }

        AnimatedVisibility(
            visible = isPanelOpen,
            enter =
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(300)),
            exit =
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier.align(Alignment.CenterEnd).zIndex(10f),
        ) {
            PlayerSidePanel(
                panelType = sidePanel,
                onPanelTypeChange = {
                    panelOpenedFromSettings = true
                    sidePanel = it
                },
                viewModel = viewModel,
                currentResizeMode = resizeMode,
                onResizeModeChange = { resizeMode = it },
                onClose = ::closeOrStepBackSidePanel,
            )
        }

        if (!isPanelOpen) {
            val playPauseFocusRequester = remember { FocusRequester() }
            val seekerFocusRequester = remember { FocusRequester() }

            VideoPlayerOverlay(
                modifier = Modifier.fillMaxSize().zIndex(2f),
                state = videoPlayerState,
                isPlaying = isPlaying,
                isScrubbing = isScrubbing,
                controls = {
                    VideoPlayerControls(
                        title = uiState.currentItemTitle,
                        subtitle = uiState.currentItemSubtitle,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        contentCurrentPosition = currentPosition,
                        player = viewModel.player,
                        state = videoPlayerState,
                        playPauseFocusRequester = playPauseFocusRequester,
                        seekerFocusRequester = seekerFocusRequester,
                        chapters =
                            if (uiState.showChapterMarkers) uiState.currentChapters
                            else emptyList(),
                        segments =
                            if (uiState.showChapterMarkers) uiState.segments else emptyList(),
                        isScrubbing = isScrubbing,
                        scrubPositionMs = scrubPositionMs,
                        onScrubbingChanged = { scrubbing, pos ->
                            if (scrubbing && !isScrubbing) {
                                scrubStartingPositionMs = currentPosition
                            }
                            isScrubbing = scrubbing
                            scrubPositionMs = pos
                        },
                        onSeek = { seekFraction ->
                            val safeDuration = viewModel.player.duration.coerceAtLeast(0L)
                            val targetMs =
                                (safeDuration * seekFraction).toLong().coerceIn(0L, safeDuration)
                            viewModel.player.seekTo(targetMs)
                            currentPosition = targetMs
                            scrubPositionMs = targetMs
                        },
                        onOpenSidePanel = { panel ->
                            panelOpenedFromSettings = false
                            sidePanel = panel
                        },
                        isFavorite = uiState.isFavorite,
                        onToggleFavorite = { viewModel.toggleFavorite() },
                        seasonEpisodes = uiState.seasonEpisodes,
                        currentEpisodeId = uiState.currentEpisodeId,
                        seriesId = uiState.seriesId,
                        onNavigateToShow = onNavigateToShow,
                        isMovie = isMovie,
                        onPlayEpisode = { viewModel.playEpisode(it) },
                        segment = segment,
                        currentSkipButtonStringRes = uiState.currentSkipButtonStringRes,
                        onSkipSegment = { if (segment != null) viewModel.skipSegment(segment) },
                        hideEpisodeSpoilers = uiState.hideEpisodeSpoilers,
                    )
                },
            )
        }

        AnimatedVisibility(
            visible = isScreensaverEnabled && isScreensaverVisible,
            enter = fadeIn(animationSpec = tween(600)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize().zIndex(20f),
        ) {
            AppScreensaver(
                slides = screensaverSlides,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoPlayerControls(
    title: String,
    subtitle: String? = null,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    contentCurrentPosition: Long,
    player: Player,
    state: VideoPlayerState,
    playPauseFocusRequester: FocusRequester,
    seekerFocusRequester: FocusRequester,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
    isScrubbing: Boolean = false,
    scrubPositionMs: Long = 0L,
    onScrubbingChanged: (isScrubbing: Boolean, scrubPositionMs: Long) -> Unit = { _, _ -> },
    onSeek: ((Float) -> Unit)? = null,
    onOpenSidePanel: (PlayerSidePanelType) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    seasonEpisodes: List<FindroidEpisode> = emptyList(),
    currentEpisodeId: UUID? = null,
    seriesId: UUID? = null,
    onNavigateToShow: ((UUID) -> Unit)? = null,
    isMovie: Boolean = false,
    onPlayEpisode: (FindroidEpisode) -> Unit = {},
    segment: FindroidSegment? = null,
    currentSkipButtonStringRes: Int = CoreR.string.skip,
    onSkipSegment: () -> Unit = {},
    hideEpisodeSpoilers: Boolean = false,
) {
    var isShelfFocused by remember { mutableStateOf(false) }

    val activeEpisodeIndex =
        remember(seasonEpisodes, currentEpisodeId) {
            seasonEpisodes.indexOfFirst { it.id == currentEpisodeId }.coerceAtLeast(0)
        }
    var focusedShelfIndex by
        remember(seasonEpisodes, currentEpisodeId) { mutableIntStateOf(activeEpisodeIndex) }
    val shelfItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isShelfFocused) {
        if (isShelfFocused) {
            state.showControls(seconds = Int.MAX_VALUE)
        } else {
            focusedShelfIndex = activeEpisodeIndex
            if (isPlaying) {
                state.showControls()
            }
        }
    }

    LaunchedEffect(segment) {
        if (segment == null && state.controlsVisible) {
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val shelfOffset by
        animateDpAsState(
            targetValue = if (seasonEpisodes.isNotEmpty() && !isShelfFocused) 130.dp else 0.dp,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "shelfOffset",
        )

    val mediaControlFocusModifier = Modifier.focusProperties {
        up = seekerFocusRequester
        if (seasonEpisodes.isNotEmpty()) {
            down = shelfItemFocusRequester
        }
    }

    VideoPlayerControlsLayout(
        mediaTitle = { VideoPlayerMediaTitle(title = title, subtitle = subtitle) },
        seeker = {
            val safeDurationMs = player.duration.coerceAtLeast(0L)
            val safeProgressMs = contentCurrentPosition.coerceAtLeast(0L)
            VideoPlayerSeeker(
                focusRequester = seekerFocusRequester,
                state = state,
                onSeek = onSeek ?: { player.seekTo((safeDurationMs * it).toLong()) },
                contentProgress = safeProgressMs.milliseconds,
                contentDuration = safeDurationMs.milliseconds,
                chapters = chapters,
                segments = segments,
                isScrubbing = isScrubbing,
                scrubPositionMs = scrubPositionMs,
                onScrubbingChanged = onScrubbingChanged,
                onDown = { playPauseFocusRequester.requestFocus() },
                onOpenChapters = { onOpenSidePanel(PlayerSidePanelType.CHAPTERS) },
                playPauseFocusRequester = playPauseFocusRequester,
                isBuffering = isBuffering,
            )
        },
        shelfOffset = shelfOffset,
        mediaActions = {
            Row(
                modifier =
                    Modifier.fillMaxWidth().focusProperties {
                        if (seasonEpisodes.isNotEmpty()) {
                            down = shelfItemFocusRequester
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // LEFT: Playback controls [Previous, Play/Pause, Next / Skip Segment]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isMovie) {
                        VideoPlayerMediaButton(
                            icon = painterResource(id = CoreR.drawable.ic_skip_back),
                            onClick = {
                                if (player.hasPreviousMediaItem()) {
                                    player.seekToPreviousMediaItem()
                                } else {
                                    player.seekTo(
                                        (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                    )
                                }
                            },
                            modifier = mediaControlFocusModifier,
                        )
                    }
                    VideoPlayerMediaButton(
                        icon =
                            painterResource(
                                id =
                                    if (isPlaying) CoreR.drawable.ic_pause
                                    else CoreR.drawable.ic_play
                            ),
                        isPrimaryAction = true,
                        onClick = { if (isPlaying) player.pause() else player.play() },
                        modifier =
                            Modifier.focusRequester(playPauseFocusRequester)
                                .then(mediaControlFocusModifier),
                    )
                    // Use AnimatedContent with SizeTransform(clip = false) instead of
                    // Modifier.animateContentSize() so the Skip/Next button is NEVER clipped when
                    // focused!
                    AnimatedContent(
                        targetState = segment != null,
                        transitionSpec = {
                            (fadeIn(tween(220)) +
                                    scaleIn(initialScale = 0.92f, animationSpec = tween(220)))
                                .togetherWith(
                                    fadeOut(tween(150)) +
                                        scaleOut(targetScale = 0.92f, animationSpec = tween(150))
                                )
                                .using(
                                    SizeTransform(
                                        clip = false,
                                        sizeAnimationSpec = { _, _ ->
                                            tween(
                                                durationMillis = 280,
                                                easing = FastOutSlowInEasing,
                                            )
                                        },
                                    )
                                )
                        },
                        contentAlignment = Alignment.Center,
                        label = "nextOrSkipSegmentButton",
                    ) { hasActiveSegment ->
                        if (hasActiveSegment) {
                            PlayerSkipButton(
                                stringRes = currentSkipButtonStringRes,
                                onClick = {
                                    try {
                                        playPauseFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                    onSkipSegment()
                                },
                                modifier = mediaControlFocusModifier,
                            )
                        } else if (!isMovie) {
                            VideoPlayerMediaButton(
                                icon = painterResource(id = CoreR.drawable.ic_skip_forward),
                                onClick = {
                                    if (player.hasNextMediaItem()) {
                                        player.seekToNextMediaItem()
                                    } else {
                                        val targetPos =
                                            if (player.duration > 0L) {
                                                (player.currentPosition + 10_000L).coerceAtMost(
                                                    player.duration
                                                )
                                            } else {
                                                player.currentPosition + 10_000L
                                            }
                                        player.seekTo(targetPos)
                                    }
                                },
                                modifier = mediaControlFocusModifier,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // RIGHT: Actions [Like, Audio, Subtitles, Settings]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoPlayerMediaButton(
                        icon =
                            painterResource(
                                id =
                                    if (isFavorite) CoreR.drawable.ic_heart_filled
                                    else CoreR.drawable.ic_heart
                            ),
                        onClick = onToggleFavorite,
                        modifier = mediaControlFocusModifier,
                    )
                    VideoPlayerMediaButton(
                        icon = painterResource(id = CoreR.drawable.ic_audio),
                        onClick = { onOpenSidePanel(PlayerSidePanelType.AUDIO) },
                        modifier = mediaControlFocusModifier,
                    )
                    VideoPlayerMediaButton(
                        icon = painterResource(id = CoreR.drawable.ic_closed_caption),
                        onClick = { onOpenSidePanel(PlayerSidePanelType.SUBTITLES) },
                        modifier = mediaControlFocusModifier,
                    )
                    VideoPlayerMediaButton(
                        icon = painterResource(id = CoreR.drawable.ic_settings),
                        onClick = { onOpenSidePanel(PlayerSidePanelType.SETTINGS) },
                        modifier = mediaControlFocusModifier,
                    )
                }
            }
        },
        episodesShelf =
            if (seasonEpisodes.isNotEmpty()) {
                {
                    val lazyListState =
                        rememberLazyListState(initialFirstVisibleItemIndex = activeEpisodeIndex)
                    val pivotController = rememberTvPivotRowController(lazyListState)
                    val itemWidth = 180.dp
                    val startPadding = EpisodeShelfStartPadding
                    val trailingSpace =
                        rememberTvTrailingSpace(itemWidth = itemWidth, startPadding = startPadding)

                    LaunchedEffect(activeEpisodeIndex, isShelfFocused) {
                        if (!isShelfFocused) {
                            if (
                                lazyListState.firstVisibleItemIndex != activeEpisodeIndex ||
                                    lazyListState.firstVisibleItemScrollOffset != 0
                            ) {
                                lazyListState.animateScrollToItem(activeEpisodeIndex, 0)
                            }
                        }
                    }

                    CompositionLocalProvider(
                        LocalBringIntoViewSpec provides NoOpBringIntoViewSpec
                    ) {
                        Column(
                            modifier =
                                Modifier.fillMaxWidth().onFocusChanged {
                                    pivotController.isRowFocused = it.hasFocus
                                    isShelfFocused = it.hasFocus
                                }
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.padding(horizontal = startPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val currentSeasonEpisode =
                                    seasonEpisodes.firstOrNull { it.id == currentEpisodeId }
                                        ?: seasonEpisodes.firstOrNull()
                                val seasonTitle =
                                    currentSeasonEpisode?.seasonName?.ifBlank { null }
                                        ?: currentSeasonEpisode
                                            ?.parentIndexNumber
                                            ?.takeIf { it > 0 }
                                            ?.let {
                                                stringResource(id = CoreR.string.season_number, it)
                                            }
                                        ?: stringResource(id = CoreR.string.seasons)

                                Icon(
                                    painter = painterResource(id = CoreR.drawable.ic_tv),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White.copy(alpha = 0.85f),
                                )
                                Text(
                                    text = seasonTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))

                            LazyRow(
                                state = pivotController.lazyListState,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding =
                                    PaddingValues(
                                        start = startPadding,
                                        top = 8.dp,
                                        bottom = 10.dp,
                                    ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                itemsIndexed(seasonEpisodes, key = { _, ep -> ep.id }) { index, ep
                                    ->
                                    var isItemFocused by remember { mutableStateOf(false) }
                                    val isLastItem =
                                        index == seasonEpisodes.size - 1 && seriesId == null

                                    TvPivotItem(
                                        index = index,
                                        controller = pivotController,
                                        isItemFocused = isItemFocused,
                                        transformOrigin = TransformOrigin(0f, 0.35f),
                                    ) {
                                        EpisodeShelfItemCard(
                                            episode = ep,
                                            isCurrent = ep.id == currentEpisodeId,
                                            isShelfTarget = index == focusedShelfIndex,
                                            isFirstItem = index == 0,
                                            isLastItem = isLastItem,
                                            shelfItemFocusRequester = shelfItemFocusRequester,
                                            upFocusRequester = playPauseFocusRequester,
                                            onClick = { onPlayEpisode(ep) },
                                            onFocusChanged = { focused ->
                                                isItemFocused = focused
                                                if (focused) {
                                                    focusedShelfIndex = index
                                                    state.showControls(seconds = Int.MAX_VALUE)
                                                    pivotController.onItemFocused(index)
                                                }
                                            },
                                            hideEpisodeSpoilers = hideEpisodeSpoilers,
                                        )
                                    }
                                }

                                if (seriesId != null) {
                                    val goToSeriesIndex = seasonEpisodes.size
                                    item(key = "go_to_series_$seriesId") {
                                        var isCardFocused by remember { mutableStateOf(false) }

                                        TvPivotItem(
                                            index = goToSeriesIndex,
                                            controller = pivotController,
                                            isItemFocused = isCardFocused,
                                            transformOrigin = TransformOrigin(0f, 0.35f),
                                        ) {
                                            GoToSeriesShelfCard(
                                                seriesName =
                                                    seasonEpisodes.firstOrNull()?.seriesName,
                                                isShelfTarget =
                                                    goToSeriesIndex == focusedShelfIndex,
                                                shelfItemFocusRequester = shelfItemFocusRequester,
                                                upFocusRequester = playPauseFocusRequester,
                                                onClick = { onNavigateToShow?.invoke(seriesId) },
                                                onFocusChanged = { focused ->
                                                    isCardFocused = focused
                                                    if (focused) {
                                                        focusedShelfIndex = goToSeriesIndex
                                                        state.showControls(seconds = Int.MAX_VALUE)
                                                        pivotController.onItemFocused(
                                                            goToSeriesIndex
                                                        )
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }

                                item(key = "trailing_space") {
                                    Spacer(modifier = Modifier.width(trailingSpace))
                                }
                            }
                        }
                    }
                }
            } else null,
    )
}

@Composable
private fun ShelfCardContainer(
    isShelfTarget: Boolean,
    shelfItemFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    label: String,
    sublabel: String,
    modifier: Modifier = Modifier,
    colors: ClickableSurfaceColors =
        ClickableSurfaceDefaults.colors(
            containerColor = PlayerTvSurface,
            focusedContainerColor = PlayerTvSurfaceFocused,
        ),
    border: ClickableSurfaceBorder =
        ClickableSurfaceDefaults.border(
            focusedBorder =
                Border(
                    BorderStroke(2.dp, Color.White),
                    shape = RoundedCornerShape(12.dp),
                )
        ),
    sublabelColor: Color = MaterialTheme.colorScheme.primary,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.width(180.dp)) {
        Surface(
            onClick = onClick,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = colors,
            border = border,
            scale = ClickableSurfaceScale.None,
            modifier =
                Modifier.width(180.dp)
                    .height(102.dp)
                    .then(
                        if (isShelfTarget) Modifier.focusRequester(shelfItemFocusRequester)
                        else Modifier
                    )
                    .focusProperties { up = upFocusRequester }
                    .onPreviewKeyEvent(onPreviewKeyEvent)
                    .onFocusChanged { onFocusChanged(it.isFocused) },
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = sublabel,
            style = MaterialTheme.typography.labelSmall,
            color = sublabelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EpisodeShelfItemCard(
    episode: FindroidEpisode,
    isCurrent: Boolean,
    isShelfTarget: Boolean,
    isFirstItem: Boolean,
    isLastItem: Boolean,
    shelfItemFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    hideEpisodeSpoilers: Boolean = false,
) {
    val image = episode.images.primary ?: episode.images.backdrop
    val imageUri = image?.uri.toOptimizedImageUri(widthDp = 180.dp, heightDp = 102.dp)
    val blurPlaceholder =
        remember(image?.blurHash) {
            image?.blurHash.toBlurHashPainter(width = 64, height = 36, punch = 1.25f)
        }
    val isEpisodeSpoiler = hideEpisodeSpoilers && !episode.played
    val hasBlurHash = image?.blurHash != null

    val indexNumberEnd = episode.indexNumberEnd
    val epNumberStr =
        if (indexNumberEnd != null && indexNumberEnd > episode.indexNumber) {
            "${episode.indexNumber}-$indexNumberEnd"
        } else {
            "${episode.indexNumber}"
        }
    val epNumberText = "E$epNumberStr"

    ShelfCardContainer(
        isShelfTarget = isShelfTarget,
        shelfItemFocusRequester = shelfItemFocusRequester,
        upFocusRequester = upFocusRequester,
        onClick = onClick,
        onFocusChanged = onFocusChanged,
        label = "$epNumberText • ${episode.name}",
        sublabel = if (isCurrent) stringResource(id = PlayerR.string.now_playing) else " ",
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        BorderStroke(2.dp, Color.White),
                        shape = RoundedCornerShape(12.dp),
                    ),
                border =
                    if (isCurrent) {
                        Border(
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                        )
                    } else {
                        Border.None
                    },
            ),
        onPreviewKeyEvent = { keyEvent ->
            keyEvent.type == KeyEventType.KeyDown &&
                ((isFirstItem && keyEvent.key == Key.DirectionLeft) ||
                    (isLastItem && keyEvent.key == Key.DirectionRight))
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SpoilerMask(
                enabled = isEpisodeSpoiler,
                shape = RoundedCornerShape(12.dp),
                blurRadius = if (hasBlurHash) 4.dp else 16.dp,
            ) {
                if (isEpisodeSpoiler && blurPlaceholder != null) {
                    Image(
                        painter = blurPlaceholder,
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = imageUri,
                        placeholder = blurPlaceholder,
                        error = blurPlaceholder,
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (episode.played) {
                ProgressBadge(
                    item = episode,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            if (
                !episode.played && episode.playbackPositionTicks > 0L && episode.runtimeTicks > 0L
            ) {
                val progressFraction =
                    (episode.playbackPositionTicks.toFloat() / episode.runtimeTicks.toFloat())
                        .coerceIn(0f, 1f)
                val progressWidth = ((180 - 16) * progressFraction).dp
                if (progressWidth > 0.dp) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(8.dp)
                                .height(4.dp)
                                .width(progressWidth)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun GoToSeriesShelfCard(
    seriesName: String?,
    isShelfTarget: Boolean,
    shelfItemFocusRequester: FocusRequester,
    upFocusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    ShelfCardContainer(
        isShelfTarget = isShelfTarget,
        shelfItemFocusRequester = shelfItemFocusRequester,
        upFocusRequester = upFocusRequester,
        onClick = onClick,
        onFocusChanged = onFocusChanged,
        label = stringResource(id = PlayerR.string.go_to_series),
        sublabel = if (!seriesName.isNullOrBlank()) seriesName else " ",
        sublabelColor = Color.White.copy(alpha = 0.7f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.1f),
                focusedContainerColor = Color.White,
                contentColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        onPreviewKeyEvent = { keyEvent ->
            keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight
        },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_tv),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
fun PlayerSkipButton(
    stringRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier,
        scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1.06f),
        colors =
            ButtonDefaults.colors(
                containerColor = PlayerOverlayBackground.copy(alpha = 0.70f),
                focusedContainerColor = Color.White,
            ),
        border =
            ButtonDefaults.border(
                border =
                    Border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        shape = CircleShape,
                    ),
                focusedBorder = Border.None,
            ),
        glow =
            ButtonDefaults.glow(
                glow = Glow.None,
                focusedGlow = Glow.None,
            ),
        shape = ButtonDefaults.shape(CircleShape),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(id = stringRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) Color.Black else Color.White,
            )
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_skip_forward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isFocused) Color.Black else Color.White,
            )
        }
    }
}
