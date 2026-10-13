package dev.jdtech.jellyfin.ui

import android.content.Context
import android.view.accessibility.CaptioningManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.AppScreensaver
import dev.jdtech.jellyfin.ui.components.ProgressBadge
import dev.jdtech.jellyfin.ui.components.player.PlayerSidePanel
import dev.jdtech.jellyfin.ui.components.player.PlayerSidePanelType
import dev.jdtech.jellyfin.ui.components.player.TrickplayScrubber
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerControlsLayout
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerMediaButton
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerMediaTitle
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerOverlay
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerSeeker
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerState
import dev.jdtech.jellyfin.ui.components.player.rememberVideoPlayerState
import dev.jdtech.jellyfin.ui.components.toScreensaverSlide
import dev.jdtech.jellyfin.utils.handleDPadKeyEvents
import dev.jdtech.jellyfin.utils.toBlurHashPainter
import dev.jdtech.jellyfin.utils.toOptimizedImageUri
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

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
    val currentView = LocalView.current

    LaunchedEffect(Unit) {
        viewModel.eventsChannelFlow.collect { event ->
            when (event) {
                is PlayerEvents.NavigateBack -> onBack()
                else -> Unit
            }
        }
    }

    // Keep the screen on while player is shown, pause on dispose
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
            viewModel.player.pause()
        }
    }

    var mediaSession by remember { mutableStateOf<MediaSession?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val session =
            try {
                MediaSession.Builder(context, viewModel.player)
                    .setId("findroid_tv_${UUID.randomUUID()}")
                    .build()
            } catch (e: Exception) {
                Timber.e(e, "Error creating MediaSession")
                null
            }
        mediaSession = session

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                session?.release()
                mediaSession = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            session?.release()
            mediaSession = null
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val videoPlayerState = rememberVideoPlayerState()

    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(viewModel.player.isPlaying) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(300.milliseconds)
            currentPosition = viewModel.player.currentPosition
            isPlaying = viewModel.player.isPlaying
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000.milliseconds)
            viewModel.updateCurrentSegment()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000.milliseconds)
            viewModel.updatePlaybackProgress()
        }
    }

    // Side panel state (Audio, Subtitles, Settings, Speed)
    var sidePanel by remember { mutableStateOf(PlayerSidePanelType.NONE) }
    var panelOpenedFromSettings by remember { mutableStateOf(false) }
    val isPanelOpen = sidePanel != PlayerSidePanelType.NONE

    // Trickplay scrubbing state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }
    var isShelfFocused by remember { mutableStateOf(false) }

    // Video aspect ratio / resize mode
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var videoAspectRatio by remember {
        val size = viewModel.player.videoSize
        mutableFloatStateOf(
            if (size.height > 0 && size.width > 0) {
                val pixelRatio =
                    if (size.pixelWidthHeightRatio > 0f) size.pixelWidthHeightRatio else 1f
                (size.width.toFloat() * pixelRatio) / size.height.toFloat()
            } else {
                16f / 9f
            }
        )
    }

    DisposableEffect(viewModel.player) {
        val listener =
            object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.height > 0 && videoSize.width > 0) {
                        val pixelRatio =
                            if (videoSize.pixelWidthHeightRatio > 0f)
                                videoSize.pixelWidthHeightRatio
                            else 1f
                        videoAspectRatio =
                            (videoSize.width.toFloat() * pixelRatio) / videoSize.height.toFloat()
                    }
                }
            }
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }

    val isMovie =
        remember(itemKind) {
            itemKind.equals(BaseItemKind.MOVIE.serialName, ignoreCase = true) ||
                itemKind.equals("Movie", ignoreCase = true)
        }
    val hasEpisodesShelf = uiState.seasonEpisodes.isNotEmpty() && !isMovie
    val trickplayBottomPadding by
        animateDpAsState(
            targetValue = if (hasEpisodesShelf) 230.dp else 145.dp,
            label = "trickplayBottomPadding",
        )

    var isScreensaverVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = isScreensaverVisible) {
        isScreensaverVisible = false
        videoPlayerState.showControls()
    }

    BackHandler(enabled = isPanelOpen) {
        if (panelOpenedFromSettings && sidePanel != PlayerSidePanelType.SETTINGS) {
            sidePanel = PlayerSidePanelType.SETTINGS
        } else {
            panelOpenedFromSettings = false
            sidePanel = PlayerSidePanelType.NONE
        }
    }

    BackHandler(enabled = isScrubbing && !isPanelOpen && !isScreensaverVisible) {
        isScrubbing = false
    }

    BackHandler(
        enabled =
            videoPlayerState.controlsVisible &&
                !isPanelOpen &&
                !isScrubbing &&
                !isScreensaverVisible
    ) {
        videoPlayerState.hideControls()
    }

    LaunchedEffect(isScrubbing) {
        if (isScrubbing) {
            videoPlayerState.showControls(seconds = Int.MAX_VALUE)
        } else {
            videoPlayerState.showControls()
        }
    }

    // Smooth YouTube TV-style video shrink animation
    val videoPaddingEnd by
        animateDpAsState(
            targetValue = if (isPanelOpen) 400.dp else 0.dp,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "videoPaddingEnd",
        )
    val videoPaddingVertical by
        animateDpAsState(
            targetValue = if (isPanelOpen) 32.dp else 0.dp,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "videoPaddingVertical",
        )
    val videoPaddingStart by
        animateDpAsState(
            targetValue = if (isPanelOpen) 32.dp else 0.dp,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "videoPaddingStart",
        )
    val videoCornerRadius by
        animateDpAsState(
            targetValue = if (isPanelOpen) 24.dp else 0.dp,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "videoCornerRadius",
        )

    // Media Segments
    val segment = uiState.currentSegment
    val showFloatingSkipButton =
        segment != null && !videoPlayerState.controlsVisible && !isPanelOpen
    val skipButtonFocusRequester = remember { FocusRequester() }
    val playerFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = showFloatingSkipButton,
        enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.9f, animationSpec = tween(250)),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200)),
        modifier = Modifier.zIndex(10f),
    ) {
        SkipButton(
            stringRes = uiState.currentSkipButtonStringRes,
            onClick = {
                try {
                    playerFocusRequester.requestFocus()
                } catch (_: Exception) {}
                if (segment != null) viewModel.skipSegment(segment)
            },
            skipButtonFocusRequester = skipButtonFocusRequester,
            onOtherKeyEvent = { videoPlayerState.showControls() },
        )
    }

    LaunchedEffect(showFloatingSkipButton) {
        if (showFloatingSkipButton) {
            skipButtonFocusRequester.requestFocus()
        } else if (!videoPlayerState.controlsVisible && !isPanelOpen) {
            try {
                playerFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(videoPlayerState.controlsVisible, isPanelOpen, segment) {
        if (!videoPlayerState.controlsVisible && !isPanelOpen && segment == null) {
            try {
                playerFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    var trickplayDebounceJob by remember { mutableStateOf<Job?>(null) }

    fun seekRelativeWithTrickplay(deltaMs: Long) {
        val currentPos = viewModel.player.currentPosition
        val newPos = (currentPos + deltaMs).coerceIn(0L, viewModel.player.duration)
        viewModel.player.seekTo(newPos)
        currentPosition = newPos
        scrubPositionMs = newPos
        isScrubbing = true
        videoPlayerState.showControls()
        trickplayDebounceJob?.cancel()
        trickplayDebounceJob = coroutineScope.launch {
            delay(2000.milliseconds)
            isScrubbing = false
        }
    }

    val screensaverSlides =
        remember(uiState.suggestions, uiState.currentItemTitle, uiState.currentImages) {
            val suggestionSlides = uiState.suggestions.map { it.toScreensaverSlide() }
            if (suggestionSlides.isNotEmpty()) {
                suggestionSlides
            } else {
                listOf(toScreensaverSlide(uiState.currentItemTitle, uiState.currentImages))
            }
        }

    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isPlaying) {
        while (true) {
            delay(1000L)
            if (!isPlaying && !isScreensaverVisible) {
                if (System.currentTimeMillis() - lastInteractionTime >= 60_000L) {
                    isScreensaverVisible = true
                }
            } else if (isPlaying) {
                isScreensaverVisible = false
                lastInteractionTime = System.currentTimeMillis()
            }
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
                            videoPlayerState.showControls()
                        }
                        return@onPreviewKeyEvent true
                    }
                    when (keyEvent.key) {
                        Key.Settings,
                        Key.Menu -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                sidePanel =
                                    if (sidePanel != PlayerSidePanelType.NONE) {
                                        PlayerSidePanelType.NONE
                                    } else {
                                        panelOpenedFromSettings = false
                                        PlayerSidePanelType.SETTINGS
                                    }
                            }
                            true
                        }

                        Key.Captions -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                sidePanel =
                                    if (sidePanel == PlayerSidePanelType.SUBTITLES) {
                                        PlayerSidePanelType.NONE
                                    } else {
                                        panelOpenedFromSettings = false
                                        PlayerSidePanelType.SUBTITLES
                                    }
                            }
                            true
                        }

                        Key.MediaAudioTrack -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                sidePanel =
                                    if (sidePanel == PlayerSidePanelType.AUDIO) {
                                        PlayerSidePanelType.NONE
                                    } else {
                                        panelOpenedFromSettings = false
                                        PlayerSidePanelType.AUDIO
                                    }
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

                        Key.MediaPause -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                viewModel.player.pause()
                                videoPlayerState.showControls()
                            }
                            true
                        }

                        Key.MediaPlayPause -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (viewModel.player.isPlaying) {
                                    viewModel.player.pause()
                                } else {
                                    viewModel.player.play()
                                }
                                videoPlayerState.showControls()
                            }
                            true
                        }

                        Key.MediaStop -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                viewModel.player.pause()
                                videoPlayerState.showControls()
                            }
                            true
                        }

                        else -> false
                    }
                }
                .dPadEvents(
                    videoPlayerState = videoPlayerState,
                    isPanelOpen = isPanelOpen,
                    onSeekRelative = { seekRelativeWithTrickplay(it) },
                )
    ) {
        val effectiveResizeMode =
            if (isPanelOpen) AspectRatioFrameLayout.RESIZE_MODE_FIT else resizeMode

        // Video player view with animated bounds, aspect ratio preservation, and rounded corners
        BoxWithConstraints(
            modifier =
                Modifier.fillMaxSize()
                    .padding(
                        start = videoPaddingStart,
                        end = videoPaddingEnd,
                        top = videoPaddingVertical,
                        bottom = videoPaddingVertical,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val shouldApplyAspectRatio = isPanelOpen || videoCornerRadius > 0.dp
            val targetWidth: androidx.compose.ui.unit.Dp
            val targetHeight: androidx.compose.ui.unit.Dp
            if (shouldApplyAspectRatio && videoAspectRatio > 0f && maxHeight.value > 0f) {
                val containerRatio = maxWidth.value / maxHeight.value
                if (containerRatio > videoAspectRatio) {
                    targetHeight = maxHeight
                    targetWidth = maxHeight * videoAspectRatio
                } else {
                    targetWidth = maxWidth
                    targetHeight = maxWidth / videoAspectRatio
                }
            } else {
                targetWidth = maxWidth
                targetHeight = maxHeight
            }

            Box(
                modifier =
                    Modifier.size(targetWidth, targetHeight)
                        .clip(RoundedCornerShape(videoCornerRadius))
                        .background(Color.Black)
            ) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).also { playerView ->
                            playerView.player = viewModel.player
                            playerView.useController = false
                            playerView.resizeMode = effectiveResizeMode
                            val captioningManager =
                                context.getSystemService(Context.CAPTIONING_SERVICE)
                                    as? CaptioningManager
                            playerView.subtitleView?.apply {
                                if (captioningManager != null && captioningManager.isEnabled) {
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

        // Trickplay scrubber preview (positioned above seekbar while scrubbing)
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
            )
        }

        // Side panel for Audio, Subtitles, Settings, Speed (YouTube TV style)
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
                onClose = {
                    if (panelOpenedFromSettings && sidePanel != PlayerSidePanelType.SETTINGS) {
                        sidePanel = PlayerSidePanelType.SETTINGS
                    } else {
                        panelOpenedFromSettings = false
                        sidePanel = PlayerSidePanelType.NONE
                    }
                },
            )
        }

        // Player controls overlay (hidden when side panel is open)
        if (!isPanelOpen) {
            val playPauseFocusRequester = remember { FocusRequester() }
            val seekerFocusRequester = remember { FocusRequester() }
            val trickplayIntervalMs =
                (uiState.currentTrickplay?.interval ?: 0).let {
                    if (it > 1000) it.toLong() else it.toLong() * 1000L
                }

            VideoPlayerOverlay(
                modifier = Modifier.fillMaxSize().zIndex(2f),
                focusRequester = playPauseFocusRequester,
                state = videoPlayerState,
                isPlaying = isPlaying,
                isScrubbing = isScrubbing,
                isShelfFocused = isShelfFocused,
                controls = {
                    VideoPlayerControls(
                        title = uiState.currentItemTitle,
                        isPlaying = isPlaying,
                        contentCurrentPosition = currentPosition,
                        player = viewModel.player,
                        state = videoPlayerState,
                        playPauseFocusRequester = playPauseFocusRequester,
                        seekerFocusRequester = seekerFocusRequester,
                        chapters = uiState.currentChapters,
                        segments = uiState.segments,
                        trickplayIntervalMs = trickplayIntervalMs,
                        isScrubbing = isScrubbing,
                        scrubPositionMs = scrubPositionMs,
                        onScrubbingChanged = { scrubbing, pos ->
                            isScrubbing = scrubbing
                            scrubPositionMs = pos
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
                        isShelfFocused = isShelfFocused,
                        onShelfFocusedChanged = { isShelfFocused = it },
                    )
                },
            )
        }

        // In-Player Pause Screensaver
        AnimatedVisibility(
            visible = isScreensaverVisible,
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerControls(
    title: String,
    isPlaying: Boolean,
    contentCurrentPosition: Long,
    player: Player,
    state: VideoPlayerState,
    playPauseFocusRequester: FocusRequester,
    seekerFocusRequester: FocusRequester,
    chapters: List<PlayerChapter> = emptyList(),
    segments: List<FindroidSegment> = emptyList(),
    trickplayIntervalMs: Long = 0L,
    isScrubbing: Boolean = false,
    scrubPositionMs: Long = 0L,
    onScrubbingChanged: (isScrubbing: Boolean, scrubPositionMs: Long) -> Unit = { _, _ -> },
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
    isShelfFocused: Boolean = false,
    onShelfFocusedChanged: (Boolean) -> Unit = {},
) {
    var focusedShelfIndex by remember(seasonEpisodes) { mutableIntStateOf(0) }
    val shelfItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isShelfFocused) {
        if (isShelfFocused) {
            state.showControls(seconds = Int.MAX_VALUE)
        } else {
            focusedShelfIndex = 0
            if (isPlaying) {
                state.showControls()
            }
        }
    }

    BackHandler(enabled = isShelfFocused) { playPauseFocusRequester.requestFocus() }

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

    VideoPlayerControlsLayout(
        mediaTitle = { VideoPlayerMediaTitle(title = title, subtitle = null) },
        seeker = {
            val safeDurationMs = if (player.duration > 0L) player.duration else 0L
            val safeProgressMs = if (contentCurrentPosition > 0L) contentCurrentPosition else 0L
            VideoPlayerSeeker(
                focusRequester = seekerFocusRequester,
                state = state,
                onSeek = { player.seekTo(safeDurationMs.times(it).toLong()) },
                contentProgress = safeProgressMs.milliseconds,
                contentDuration = safeDurationMs.milliseconds,
                chapters = chapters,
                segments = segments,
                trickplayIntervalMs = trickplayIntervalMs,
                isScrubbing = isScrubbing,
                scrubPositionMs = scrubPositionMs,
                onScrubbingChanged = onScrubbingChanged,
                onDown = { playPauseFocusRequester.requestFocus() },
                onOpenChapters = { onOpenSidePanel(PlayerSidePanelType.CHAPTERS) },
                playPauseFocusRequester = playPauseFocusRequester,
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
                            state = state,
                            isPlaying = isPlaying,
                            onClick = {
                                if (player.hasPreviousMediaItem()) {
                                    player.seekToPreviousMediaItem()
                                } else {
                                    player.seekTo(
                                        (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                    )
                                }
                            },
                            modifier =
                                Modifier.focusProperties {
                                    up = seekerFocusRequester
                                    if (seasonEpisodes.isNotEmpty()) {
                                        down = shelfItemFocusRequester
                                    }
                                },
                        )
                    }
                    VideoPlayerMediaButton(
                        icon =
                            painterResource(
                                id =
                                    if (isPlaying) CoreR.drawable.ic_pause
                                    else CoreR.drawable.ic_play
                            ),
                        state = state,
                        isPlaying = isPlaying,
                        isPrimaryAction = true,
                        onClick = { if (isPlaying) player.pause() else player.play() },
                        modifier =
                            Modifier.focusRequester(playPauseFocusRequester).focusProperties {
                                up = seekerFocusRequester
                                if (seasonEpisodes.isNotEmpty()) {
                                    down = shelfItemFocusRequester
                                }
                            },
                    )
                    Box(
                        modifier =
                            Modifier.animateContentSize(
                                animationSpec =
                                    tween(durationMillis = 300, easing = FastOutSlowInEasing)
                            )
                    ) {
                        if (segment != null) {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isSkipFocused by interactionSource.collectIsFocusedAsState()
                            LaunchedEffect(isSkipFocused && isPlaying) {
                                if (isSkipFocused && isPlaying) {
                                    state.showControls()
                                }
                            }
                            Button(
                                onClick = {
                                    try {
                                        playPauseFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                    onSkipSegment()
                                },
                                interactionSource = interactionSource,
                                scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
                                colors =
                                    ButtonDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        focusedContainerColor = Color.White,
                                    ),
                                border =
                                    ButtonDefaults.border(
                                        border = Border.None,
                                        focusedBorder = Border.None,
                                    ),
                                glow =
                                    ButtonDefaults.glow(
                                        glow = Glow.None,
                                        focusedGlow = Glow.None,
                                    ),
                                shape = ButtonDefaults.shape(CircleShape),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier =
                                    Modifier.focusProperties {
                                        up = seekerFocusRequester
                                        if (seasonEpisodes.isNotEmpty()) {
                                            down = shelfItemFocusRequester
                                        }
                                    },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(id = currentSkipButtonStringRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSkipFocused) Color.Black else Color.White,
                                    )
                                    Icon(
                                        painter =
                                            painterResource(id = CoreR.drawable.ic_skip_forward),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isSkipFocused) Color.Black else Color.White,
                                    )
                                }
                            }
                        } else if (!isMovie) {
                            VideoPlayerMediaButton(
                                icon = painterResource(id = CoreR.drawable.ic_skip_forward),
                                state = state,
                                isPlaying = isPlaying,
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
                                modifier =
                                    Modifier.focusProperties {
                                        up = seekerFocusRequester
                                        if (seasonEpisodes.isNotEmpty()) {
                                            down = shelfItemFocusRequester
                                        }
                                    },
                            )
                        }
                    }
                }

                // CENTER: Empty space per user request ("lasciando il centro vuoto")
                Spacer(modifier = Modifier.weight(1f))

                // RIGHT: Actions [Like, Audio, Subtitles, Settings]
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Like / Favorite button
                    VideoPlayerMediaButton(
                        icon =
                            painterResource(
                                id =
                                    if (isFavorite) CoreR.drawable.ic_heart_filled
                                    else CoreR.drawable.ic_heart
                            ),
                        state = state,
                        isPlaying = isPlaying,
                        onClick = onToggleFavorite,
                    )
                    // Audio
                    VideoPlayerMediaButton(
                        icon = painterResource(id = CoreR.drawable.ic_speaker),
                        state = state,
                        isPlaying = isPlaying,
                        onClick = { onOpenSidePanel(PlayerSidePanelType.AUDIO) },
                    )
                    // Subtitles
                    VideoPlayerMediaButton(
                        icon = painterResource(id = CoreR.drawable.ic_closed_caption),
                        state = state,
                        isPlaying = isPlaying,
                        onClick = { onOpenSidePanel(PlayerSidePanelType.SUBTITLES) },
                    )
                    // Settings
                    VideoPlayerMediaButton(
                        icon = painterResource(id = CoreR.drawable.ic_settings),
                        state = state,
                        isPlaying = isPlaying,
                        onClick = { onOpenSidePanel(PlayerSidePanelType.SETTINGS) },
                    )
                }
            }
        },
        episodesShelf =
            if (seasonEpisodes.isNotEmpty()) {
                {
                    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = 0)
                    val coroutineScope = rememberCoroutineScope()
                    var scrollJob by remember { mutableStateOf<Job?>(null) }
                    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                    val itemWidth = 180.dp
                    val startPadding = 24.dp
                    val trailingSpace = maxOf(0.dp, screenWidth - itemWidth - startPadding)

                    Column(
                        modifier =
                            Modifier.fillMaxWidth().onFocusChanged {
                                onShelfFocusedChanged(it.hasFocus)
                            }
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = PlayerR.drawable.ic_tv),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.White.copy(alpha = 0.85f),
                            )
                            Text(
                                text = stringResource(id = PlayerR.string.season_episodes),
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))

                        LazyRow(
                            state = lazyListState,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding =
                                PaddingValues(start = 24.dp, end = 0.dp, bottom = 6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            itemsIndexed(seasonEpisodes, key = { _, ep -> ep.id }) { index, ep ->
                                val isCurrent = ep.id == currentEpisodeId
                                val isShelfTarget = index == focusedShelfIndex
                                val image = ep.images.primary ?: ep.images.backdrop
                                val imageUri =
                                    image
                                        ?.uri
                                        .toOptimizedImageUri(
                                            widthDp = 180.dp,
                                            heightDp = 102.dp,
                                        )
                                val blurPlaceholder =
                                    remember(image?.blurHash) {
                                        image?.blurHash.toBlurHashPainter()
                                    }

                                val indexNumberEnd = ep.indexNumberEnd
                                val epNumberText =
                                    if (indexNumberEnd != null && indexNumberEnd > ep.indexNumber) {
                                        "E" + (ep.indexNumber..indexNumberEnd).joinToString(", ")
                                    } else {
                                        "E${ep.indexNumber}"
                                    }

                                Column(modifier = Modifier.width(180.dp)) {
                                    Surface(
                                        onClick = { onPlayEpisode(ep) },
                                        shape =
                                            ClickableSurfaceDefaults.shape(
                                                RoundedCornerShape(12.dp)
                                            ),
                                        colors =
                                            ClickableSurfaceDefaults.colors(
                                                containerColor = Color(0xFF202024),
                                                focusedContainerColor = Color(0xFF2C2C32),
                                            ),
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
                                                            BorderStroke(
                                                                2.dp,
                                                                MaterialTheme.colorScheme.primary,
                                                            ),
                                                            shape = RoundedCornerShape(12.dp),
                                                        )
                                                    } else {
                                                        Border.None
                                                    },
                                            ),
                                        scale = ClickableSurfaceScale.None,
                                        modifier =
                                            Modifier.width(180.dp)
                                                .height(102.dp)
                                                .then(
                                                    if (isShelfTarget)
                                                        Modifier.focusRequester(
                                                            shelfItemFocusRequester
                                                        )
                                                    else Modifier
                                                )
                                                .focusProperties { up = playPauseFocusRequester }
                                                .onPreviewKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        if (
                                                            index == 0 &&
                                                                keyEvent.key == Key.DirectionLeft
                                                        ) {
                                                            true
                                                        } else if (
                                                            index == seasonEpisodes.size - 1 &&
                                                                seriesId == null &&
                                                                keyEvent.key == Key.DirectionRight
                                                        ) {
                                                            true
                                                        } else {
                                                            false
                                                        }
                                                    } else {
                                                        false
                                                    }
                                                }
                                                .onFocusChanged {
                                                    if (it.isFocused) {
                                                        focusedShelfIndex = index
                                                        state.showControls(seconds = Int.MAX_VALUE)
                                                        val wasScrolling =
                                                            scrollJob?.isActive == true ||
                                                                lazyListState.isScrollInProgress
                                                        scrollJob?.cancel()
                                                        scrollJob = coroutineScope.launch {
                                                            val visibleItem =
                                                                lazyListState.layoutInfo
                                                                    .visibleItemsInfo
                                                                    .firstOrNull {
                                                                        it.index == index
                                                                    }
                                                            if (visibleItem != null) {
                                                                val itemSize =
                                                                    visibleItem.size.toFloat()
                                                                val spacing =
                                                                    lazyListState.layoutInfo
                                                                        .mainAxisItemSpacing
                                                                        .toFloat()
                                                                val beforePadding =
                                                                    lazyListState.layoutInfo
                                                                        .beforeContentPadding
                                                                        .toFloat()
                                                                val targetOffset =
                                                                    if (index == 0) {
                                                                        beforePadding
                                                                    } else {
                                                                        (itemSize * 0.5f) + spacing
                                                                    }
                                                                val delta =
                                                                    visibleItem.offset -
                                                                        targetOffset
                                                                if (kotlin.math.abs(delta) > 2f) {
                                                                    lazyListState.animateScrollBy(
                                                                        value = delta,
                                                                        animationSpec =
                                                                            tween<Float>(
                                                                                durationMillis =
                                                                                    if (
                                                                                        wasScrolling
                                                                                    )
                                                                                        140
                                                                                    else 200,
                                                                                easing =
                                                                                    FastOutSlowInEasing,
                                                                            ),
                                                                    )
                                                                }
                                                            } else {
                                                                if (index == 0) {
                                                                    lazyListState
                                                                        .animateScrollToItem(0)
                                                                } else {
                                                                    lazyListState
                                                                        .animateScrollToItem(
                                                                            index = index - 1,
                                                                            scrollOffset =
                                                                                (180.dp.value *
                                                                                        0.5f)
                                                                                    .toInt(),
                                                                        )
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            AsyncImage(
                                                model = imageUri,
                                                placeholder = blurPlaceholder,
                                                error = blurPlaceholder,
                                                contentDescription = ep.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                            if (ep.played) {
                                                ProgressBadge(
                                                    item = ep,
                                                    modifier =
                                                        Modifier.align(Alignment.TopEnd)
                                                            .padding(6.dp),
                                                )
                                            }
                                            if (
                                                !ep.played &&
                                                    ep.playbackPositionTicks > 0L &&
                                                    ep.runtimeTicks > 0L
                                            ) {
                                                val progressFraction =
                                                    (ep.playbackPositionTicks.toFloat() /
                                                            ep.runtimeTicks.toFloat())
                                                        .coerceIn(0f, 1f)
                                                Box(
                                                    modifier =
                                                        Modifier.fillMaxWidth()
                                                            .height(4.dp)
                                                            .background(
                                                                Color.Black.copy(alpha = 0.5f)
                                                            )
                                                            .align(Alignment.BottomCenter)
                                                ) {
                                                    Box(
                                                        modifier =
                                                            Modifier.fillMaxWidth(progressFraction)
                                                                .fillMaxHeight()
                                                                .background(
                                                                    MaterialTheme.colorScheme
                                                                        .primary
                                                                )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "$epNumberText • ${ep.name}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text =
                                            if (isCurrent) {
                                                stringResource(id = PlayerR.string.now_playing)
                                            } else {
                                                " "
                                            },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            if (seriesId != null) {
                                item(key = "go_to_series_${seriesId}") {
                                    val seriesName = seasonEpisodes.firstOrNull()?.seriesName
                                    val isGoToSeriesTarget =
                                        seasonEpisodes.size == focusedShelfIndex

                                    Column(modifier = Modifier.width(180.dp)) {
                                        Surface(
                                            onClick = { onNavigateToShow?.invoke(seriesId) },
                                            shape =
                                                ClickableSurfaceDefaults.shape(
                                                    RoundedCornerShape(12.dp)
                                                ),
                                            colors =
                                                ClickableSurfaceDefaults.colors(
                                                    containerColor = Color.White.copy(alpha = 0.1f),
                                                    focusedContainerColor = Color.White,
                                                    contentColor = Color.White,
                                                    focusedContentColor = Color.Black,
                                                ),
                                            border =
                                                ClickableSurfaceDefaults.border(
                                                    focusedBorder =
                                                        Border(
                                                            BorderStroke(2.dp, Color.White),
                                                            shape = RoundedCornerShape(12.dp),
                                                        )
                                                ),
                                            scale = ClickableSurfaceScale.None,
                                            modifier =
                                                Modifier.width(180.dp)
                                                    .height(102.dp)
                                                    .then(
                                                        if (isGoToSeriesTarget)
                                                            Modifier.focusRequester(
                                                                shelfItemFocusRequester
                                                            )
                                                        else Modifier
                                                    )
                                                    .focusProperties {
                                                        up = playPauseFocusRequester
                                                    }
                                                    .onPreviewKeyEvent { keyEvent ->
                                                        if (
                                                            keyEvent.type == KeyEventType.KeyDown &&
                                                                keyEvent.key == Key.DirectionRight
                                                        ) {
                                                            true
                                                        } else {
                                                            false
                                                        }
                                                    }
                                                    .onFocusChanged {
                                                        if (it.isFocused) {
                                                            focusedShelfIndex = seasonEpisodes.size
                                                            state.showControls(
                                                                seconds = Int.MAX_VALUE
                                                            )
                                                            val wasScrolling =
                                                                scrollJob?.isActive == true ||
                                                                    lazyListState.isScrollInProgress
                                                            scrollJob?.cancel()
                                                            scrollJob = coroutineScope.launch {
                                                                val visibleItem =
                                                                    lazyListState.layoutInfo
                                                                        .visibleItemsInfo
                                                                        .firstOrNull {
                                                                            it.index ==
                                                                                seasonEpisodes.size
                                                                        }
                                                                if (visibleItem != null) {
                                                                    val itemSize =
                                                                        visibleItem.size.toFloat()
                                                                    val spacing =
                                                                        lazyListState.layoutInfo
                                                                            .mainAxisItemSpacing
                                                                            .toFloat()
                                                                    val targetOffset =
                                                                        (itemSize * 0.5f) + spacing
                                                                    val delta =
                                                                        visibleItem.offset -
                                                                            targetOffset
                                                                    if (
                                                                        kotlin.math.abs(delta) > 2f
                                                                    ) {
                                                                        lazyListState
                                                                            .animateScrollBy(
                                                                                value = delta,
                                                                                animationSpec =
                                                                                    tween<Float>(
                                                                                        durationMillis =
                                                                                            if (
                                                                                                wasScrolling
                                                                                            )
                                                                                                140
                                                                                            else
                                                                                                200,
                                                                                        easing =
                                                                                            FastOutSlowInEasing,
                                                                                    ),
                                                                            )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize(),
                                            ) {
                                                Icon(
                                                    painter =
                                                        painterResource(
                                                            id = PlayerR.drawable.ic_tv
                                                        ),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp),
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(id = PlayerR.string.go_to_series),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text =
                                                if (!seriesName.isNullOrBlank()) {
                                                    seriesName
                                                } else {
                                                    " "
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
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
            } else null,
    )
}

@Composable
private fun SkipButton(
    stringRes: Int,
    onClick: () -> Unit,
    skipButtonFocusRequester: FocusRequester,
    onOtherKeyEvent: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacings.large).zIndex(10f),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Button(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier =
                Modifier.focusRequester(skipButtonFocusRequester).onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        when (keyEvent.key) {
                            Key.DirectionCenter,
                            Key.Enter,
                            Key.NumPadEnter -> {
                                onClick()
                                true
                            }
                            else -> {
                                onOtherKeyEvent()
                                true
                            }
                        }
                    } else false
                },
            colors =
                ButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color.White,
                ),
            border =
                ButtonDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None,
                ),
            shape = ButtonDefaults.shape(CircleShape),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            scale = ButtonDefaults.scale(scale = 1f, focusedScale = 1f),
            glow =
                ButtonDefaults.glow(
                    glow = Glow.None,
                    focusedGlow = Glow.None,
                ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(stringRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocused) Color.Black else Color.White,
                )
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_skip_forward),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isFocused) Color.Black else Color.White,
                )
            }
        }
    }
}

private fun Modifier.dPadEvents(
    videoPlayerState: VideoPlayerState,
    isPanelOpen: Boolean,
    onSeekRelative: (Long) -> Unit,
): Modifier =
    if (isPanelOpen || videoPlayerState.controlsVisible) this
    else
        this.handleDPadKeyEvents(
            onUp = { videoPlayerState.showControls() },
            onDown = { videoPlayerState.showControls() },
            onLeft = { videoPlayerState.showControls() },
            onRight = { videoPlayerState.showControls() },
            onEnter = { videoPlayerState.showControls() },
        )
