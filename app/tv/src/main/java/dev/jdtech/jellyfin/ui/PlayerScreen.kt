package dev.jdtech.jellyfin.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.spacings
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
import dev.jdtech.jellyfin.utils.handleDPadKeyEvents
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    itemId: UUID,
    itemKind: String,
    startFromBeginning: Boolean,
) {
    val viewModel = hiltViewModel<PlayerViewModel>()

    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val currentView = LocalView.current

    // Keep the screen on while player is shown
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose { currentView.keepScreenOn = false }
    }

    var lifecycle by remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }
    var mediaSession by remember { mutableStateOf<MediaSession?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycle = event

            // Handle creation and release of media session
            when (lifecycle) {
                Lifecycle.Event.ON_STOP -> {
                    mediaSession?.release()
                }

                Lifecycle.Event.ON_START -> {
                    mediaSession = MediaSession.Builder(context, viewModel.player).build()
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val videoPlayerState = rememberVideoPlayerState()

    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(viewModel.player.isPlaying) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(300)
            currentPosition = viewModel.player.currentPosition
            isPlaying = viewModel.player.isPlaying
        }
    }

    // Side panel state (Audio, Subtitles, Settings)
    var sidePanel by remember { mutableStateOf(PlayerSidePanelType.NONE) }
    val isPanelOpen = sidePanel != PlayerSidePanelType.NONE

    // Trickplay scrubbing state
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    // Video aspect ratio / resize mode
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Remote back button handling: dismiss side panel first, then dismiss scrubbing
    BackHandler(enabled = isPanelOpen) { sidePanel = PlayerSidePanelType.NONE }

    BackHandler(enabled = isScrubbing && !isPanelOpen) { isScrubbing = false }

    // Smooth YouTube TV-style video shrink animation
    val videoPaddingEnd by
        animateDpAsState(
            targetValue = if (isPanelOpen) 390.dp else 0.dp,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "videoPaddingEnd",
        )
    val videoPaddingVertical by
        animateDpAsState(
            targetValue = if (isPanelOpen) 28.dp else 0.dp,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "videoPaddingVertical",
        )
    val videoPaddingStart by
        animateDpAsState(
            targetValue = if (isPanelOpen) 28.dp else 0.dp,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "videoPaddingStart",
        )
    val videoCornerRadius by
        animateDpAsState(
            targetValue = if (isPanelOpen) 20.dp else 0.dp,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "videoCornerRadius",
        )

    // Media Segments
    val segment = uiState.currentSegment
    if (segment != null && !videoPlayerState.controlsVisible && !isPanelOpen) {
        val skipButtonFocusRequester = remember { FocusRequester() }

        SkipButton(
            stringRes = uiState.currentSkipButtonStringRes,
            onClick = { viewModel.skipSegment(segment) },
            skipButtonFocusRequester = skipButtonFocusRequester,
        )

        LaunchedEffect(videoPlayerState.controlsVisible) {
            if (!videoPlayerState.controlsVisible) {
                skipButtonFocusRequester.requestFocus()
            }
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .background(Color.Black)
                .dPadEvents(
                    exoPlayer = viewModel.player,
                    videoPlayerState = videoPlayerState,
                    isPanelOpen = isPanelOpen,
                )
                .focusable()
    ) {
        // Video player view with animated bounds, position shift, and rounded corners
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .padding(
                        start = videoPaddingStart,
                        end = videoPaddingEnd,
                        top = videoPaddingVertical,
                        bottom = videoPaddingVertical,
                    )
                    .clip(RoundedCornerShape(videoCornerRadius))
                    .background(Color.Black)
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).also { playerView ->
                        playerView.player = viewModel.player
                        playerView.useController = false
                        playerView.resizeMode = resizeMode
                        viewModel.initializePlayer(
                            itemId = itemId,
                            itemKind = itemKind,
                            startFromBeginning = startFromBeginning,
                        )
                        playerView.setBackgroundColor(
                            context.resources.getColor(android.R.color.black, context.theme)
                        )
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = resizeMode
                    when (lifecycle) {
                        Lifecycle.Event.ON_PAUSE -> {
                            playerView.onPause()
                            playerView.player?.pause()
                        }

                        Lifecycle.Event.ON_RESUME -> {
                            playerView.onResume()
                        }

                        else -> Unit
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Trickplay scrubber preview (centered on screen while scrubbing)
        AnimatedVisibility(
            visible = isScrubbing,
            enter =
                fadeIn(animationSpec = tween(200)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(200)),
            exit =
                fadeOut(animationSpec = tween(150)) +
                    scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.Center).zIndex(5f),
        ) {
            TrickplayScrubber(
                trickplay = uiState.currentTrickplay,
                currentPositionMs = scrubPositionMs,
                durationMs = viewModel.player.duration,
            )
        }

        // Side panel for Audio, Subtitles, Settings (YouTube TV style)
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
                onPanelTypeChange = { sidePanel = it },
                viewModel = viewModel,
                currentResizeMode = resizeMode,
                onResizeModeChange = { resizeMode = it },
                onClose = { sidePanel = PlayerSidePanelType.NONE },
            )
        }

        // Player controls overlay (hidden when side panel is open)
        if (!isPanelOpen) {
            val focusRequester = remember { FocusRequester() }
            val trickplayIntervalMs =
                (uiState.currentTrickplay?.interval ?: 0).let {
                    if (it > 1000) it.toLong() else it.toLong() * 1000L
                }

            VideoPlayerOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                focusRequester = focusRequester,
                state = videoPlayerState,
                isPlaying = isPlaying,
                controls = {
                    VideoPlayerControls(
                        title = uiState.currentItemTitle,
                        isPlaying = isPlaying,
                        contentCurrentPosition = currentPosition,
                        player = viewModel.player,
                        state = videoPlayerState,
                        focusRequester = focusRequester,
                        trickplayIntervalMs = trickplayIntervalMs,
                        isScrubbing = isScrubbing,
                        scrubPositionMs = scrubPositionMs,
                        onScrubbingChanged = { scrubbing, pos ->
                            isScrubbing = scrubbing
                            scrubPositionMs = pos
                        },
                        onOpenSidePanel = { panel -> sidePanel = panel },
                    )
                },
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
    focusRequester: FocusRequester,
    trickplayIntervalMs: Long = 0L,
    isScrubbing: Boolean = false,
    scrubPositionMs: Long = 0L,
    onScrubbingChanged: (isScrubbing: Boolean, scrubPositionMs: Long) -> Unit = { _, _ -> },
    onOpenSidePanel: (PlayerSidePanelType) -> Unit = {},
) {
    val onPlayPauseToggle = { shouldPlay: Boolean ->
        if (shouldPlay) {
            player.play()
        } else {
            player.pause()
        }
    }

    VideoPlayerControlsLayout(
        mediaTitle = { VideoPlayerMediaTitle(title = title, subtitle = null) },
        seeker = {
            VideoPlayerSeeker(
                focusRequester = focusRequester,
                state = state,
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                onSeek = { player.seekTo(player.duration.times(it).toLong()) },
                contentProgress = contentCurrentPosition.milliseconds,
                contentDuration = player.duration.milliseconds,
                trickplayIntervalMs = trickplayIntervalMs,
                isScrubbing = isScrubbing,
                scrubPositionMs = scrubPositionMs,
                onScrubbingChanged = onScrubbingChanged,
            )
        },
        mediaActions = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium)) {
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_speaker),
                    state = state,
                    isPlaying = isPlaying,
                    onClick = { onOpenSidePanel(PlayerSidePanelType.AUDIO) },
                )
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_closed_caption),
                    state = state,
                    isPlaying = isPlaying,
                    onClick = { onOpenSidePanel(PlayerSidePanelType.SUBTITLES) },
                )
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_settings),
                    state = state,
                    isPlaying = isPlaying,
                    onClick = { onOpenSidePanel(PlayerSidePanelType.SETTINGS) },
                )
            }
        },
    )
}

@Composable
private fun SkipButton(
    stringRes: Int,
    onClick: () -> Unit,
    skipButtonFocusRequester: FocusRequester,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacings.large).zIndex(1f),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.focusRequester(skipButtonFocusRequester),
            glow =
                ButtonDefaults.glow(
                    focusedGlow = Glow(elevationColor = Color.Gray, elevation = 20.dp)
                ),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_forward),
                contentDescription = null,
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = stringResource(stringRes), color = Color.Black)
        }
    }
}

private fun Modifier.dPadEvents(
    exoPlayer: Player,
    videoPlayerState: VideoPlayerState,
    isPanelOpen: Boolean,
): Modifier =
    if (isPanelOpen) this
    else
        this.handleDPadKeyEvents(
            onLeft = {},
            onRight = {},
            onUp = { videoPlayerState.showControls() },
            onDown = { videoPlayerState.showControls() },
            onEnter = {
                if (!videoPlayerState.controlsVisible) {
                    videoPlayerState.showControls()
                } else {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                    } else {
                        exoPlayer.play()
                    }
                }
            },
        )
