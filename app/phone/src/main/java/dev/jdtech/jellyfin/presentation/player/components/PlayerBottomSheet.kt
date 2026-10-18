package dev.jdtech.jellyfin.presentation.player.components

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.AspectRatioFrameLayout
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.intervalMs
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.player.core.presentation.formatDuration
import dev.jdtech.jellyfin.player.core.presentation.getSegmentColor
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel

private val DialogShape = RoundedCornerShape(24.dp)
private val ChipShape = RoundedCornerShape(4.dp)
private val ChapterThumbShape = RoundedCornerShape(8.dp)

private val MenuTopShape =
    RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 4.dp,
        bottomEnd = 4.dp,
    )
private val MenuBottomShape =
    RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 4.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp,
    )
private val MenuMiddleShape = RoundedCornerShape(4.dp)
private val MenuSingleShape = RoundedCornerShape(24.dp)

private fun getItemShape(index: Int, totalCount: Int): Shape {
    return when {
        totalCount <= 1 -> MenuSingleShape
        index == 0 -> MenuTopShape
        index == totalCount - 1 -> MenuBottomShape
        else -> MenuMiddleShape
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerBottomSheet(
    panelType: PlayerSidePanelType,
    onPanelTypeChange: (PlayerSidePanelType) -> Unit,
    viewModel: PlayerViewModel,
    currentResizeMode: Int,
    onResizeModeChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    openedDirectly: Boolean = false,
) {
    if (panelType == PlayerSidePanelType.NONE) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tracks by remember { mutableStateOf(viewModel.player.currentTracks) }

    DisposableEffect(viewModel.player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(newTracks: Tracks) {
                    tracks = newTracks
                }
            }
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }

    val config = LocalConfiguration.current
    val maxContentHeight =
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) 220.dp else 400.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.35f)) },
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
        ) {
            // Header with Back navigation, Title, and Close button
            // Only displayed on submenus (per user request: remove title and X from settings menu)
            if (panelType != PlayerSidePanelType.SETTINGS) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!openedDirectly) {
                        Box(
                            modifier =
                                Modifier.size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true, radius = 18.dp),
                                        onClick = {
                                            onPanelTypeChange(PlayerSidePanelType.SETTINGS)
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = CoreR.drawable.ic_arrow_left),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White,
                            )
                        }
                    }

                    Text(
                        text =
                            stringResource(
                                id =
                                    when (panelType) {
                                        PlayerSidePanelType.AUDIO ->
                                            PlayerR.string.select_audio_track
                                        PlayerSidePanelType.SUBTITLES ->
                                            PlayerR.string.select_subtitle_track
                                        PlayerSidePanelType.SPEED ->
                                            PlayerR.string.select_playback_speed
                                        PlayerSidePanelType.ASPECT_RATIO ->
                                            PlayerR.string.aspect_ratio
                                        PlayerSidePanelType.CHAPTERS -> PlayerR.string.chapters
                                        PlayerSidePanelType.SETTINGS,
                                        PlayerSidePanelType.NONE -> CoreR.string.title_settings
                                    }
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().heightIn(max = maxContentHeight)) {
                when (panelType) {
                    PlayerSidePanelType.SETTINGS -> {
                        PhoneSettingsMenu(
                            viewModel = viewModel,
                            tracks = tracks,
                            currentResizeMode = currentResizeMode,
                            onNavigate = onPanelTypeChange,
                            onLock = {
                                onDismiss()
                                onLock()
                            },
                        )
                    }
                    PlayerSidePanelType.AUDIO -> {
                        PhoneAudioTrackList(viewModel = viewModel, tracks = tracks)
                    }
                    PlayerSidePanelType.SUBTITLES -> {
                        PhoneSubtitleTrackList(viewModel = viewModel, tracks = tracks)
                    }
                    PlayerSidePanelType.SPEED -> {
                        PhoneSpeedList(
                            currentSpeed = viewModel.playbackSpeed,
                            onSpeedSelected = {
                                viewModel.selectSpeed(it)
                                onDismiss()
                            },
                        )
                    }
                    PlayerSidePanelType.ASPECT_RATIO -> {
                        PhoneAspectRatioList(
                            currentResizeMode = currentResizeMode,
                            onResizeModeSelected = {
                                onResizeModeChange(it)
                                onDismiss()
                            },
                        )
                    }
                    PlayerSidePanelType.CHAPTERS -> {
                        PhoneChaptersList(
                            viewModel = viewModel,
                            onChapterSelected = { onDismiss() },
                        )
                    }
                    PlayerSidePanelType.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun PhoneSettingsMenu(
    viewModel: PlayerViewModel,
    tracks: Tracks,
    currentResizeMode: Int,
    onNavigate: (PlayerSidePanelType) -> Unit,
    onLock: () -> Unit,
) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]

    val selectedAudioGroup =
        tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
    val audioTitle =
        if (selectedAudioGroup != null) {
            val track = TrackFormatter.toTrack(selectedAudioGroup, 1)
            TrackFormatter.getTrackDisplayInfo(
                    track = track,
                    context = context,
                    displayExtraInfo = viewModel.displayExtraInfo,
                    currentLocale = currentLocale,
                )
                .name
        } else stringResource(id = PlayerR.string.none)

    val selectedSubGroup =
        tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
    val subTitle =
        if (selectedSubGroup != null) {
            val track = TrackFormatter.toTrack(selectedSubGroup, 1)
            TrackFormatter.getTrackDisplayInfo(
                    track = track,
                    context = context,
                    displayExtraInfo = viewModel.displayExtraInfo,
                    currentLocale = currentLocale,
                )
                .name
        } else stringResource(id = PlayerR.string.none)

    val currentSpeedText = "${viewModel.playbackSpeed}x"
    val currentAspectText =
        when (currentResizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM ->
                stringResource(id = PlayerR.string.aspect_ratio_zoom)
            AspectRatioFrameLayout.RESIZE_MODE_FILL ->
                stringResource(id = PlayerR.string.aspect_ratio_fill)
            else -> stringResource(id = PlayerR.string.aspect_ratio_fit)
        }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.video_quality),
                subtitle = stringResource(id = PlayerR.string.quality_original_direct_play),
                iconRes = CoreR.drawable.ic_film,
                badge = stringResource(id = PlayerR.string.coming_soon),
                enabled = false,
                shape = MenuTopShape,
                onClick = {},
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.audio),
                subtitle = audioTitle,
                iconRes = CoreR.drawable.ic_speaker,
                shape = MenuMiddleShape,
                onClick = { onNavigate(PlayerSidePanelType.AUDIO) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.subtitles),
                subtitle = subTitle,
                iconRes = CoreR.drawable.ic_closed_caption,
                shape = MenuMiddleShape,
                onClick = { onNavigate(PlayerSidePanelType.SUBTITLES) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.playback_speed),
                subtitle = currentSpeedText,
                iconRes = CoreR.drawable.ic_gauge,
                shape = MenuMiddleShape,
                onClick = { onNavigate(PlayerSidePanelType.SPEED) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.aspect_ratio),
                subtitle = currentAspectText,
                iconRes = CoreR.drawable.ic_monitor,
                shape = MenuMiddleShape,
                onClick = { onNavigate(PlayerSidePanelType.ASPECT_RATIO) },
            )
        }
        item {
            PhoneNavigationRow(
                title = stringResource(id = PlayerR.string.lock_screen),
                subtitle = "",
                iconRes = CoreR.drawable.ic_lock,
                shape = MenuBottomShape,
                showChevron = false,
                onClick = onLock,
            )
        }
    }
}

@Composable
private fun PhoneNavigationRow(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
    shape: Shape,
    badge: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.4f
    Surface(
        modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha },
        shape = shape,
        color = Color(0xFF242424),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White.copy(alpha = 0.9f),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (badge != null) {
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showChevron && enabled) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun PhoneAudioTrackList(viewModel: PlayerViewModel, tracks: Tracks) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo

    val audioGroups =
        remember(tracks) {
            tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        }

    val selectedTrackIndex =
        remember(tracks) { audioGroups.indexOfFirst { it.isSelected }.takeIf { it >= 0 } }

    var optimisticSelectedIndex by remember(tracks) { mutableStateOf(selectedTrackIndex) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(items = audioGroups) { index, group ->
            val isSelected = optimisticSelectedIndex == index
            val displayInfo =
                remember(group, currentLocale, displayExtraInfo) {
                    TrackFormatter.getTrackDisplayInfo(
                        group = group,
                        context = context,
                        displayExtraInfo = displayExtraInfo,
                        currentLocale = currentLocale,
                        fallbackIndex = index + 1,
                    )
                }

            PhoneSelectableItem(
                title = displayInfo.name,
                chips = displayInfo.chips,
                isSelected = isSelected,
                shape = getItemShape(index, audioGroups.size),
                onClick = {
                    optimisticSelectedIndex = index
                    viewModel.switchToTrack(C.TRACK_TYPE_AUDIO, index)
                },
            )
        }
    }
}

@Composable
private fun PhoneSubtitleTrackList(viewModel: PlayerViewModel, tracks: Tracks) {
    val context = LocalContext.current
    val currentLocale = LocalConfiguration.current.locales[0]
    val displayExtraInfo = viewModel.displayExtraInfo

    val subtitleGroups =
        remember(tracks) { tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported } }

    val selectedTrackIndex = remember(tracks) { subtitleGroups.indexOfFirst { it.isSelected } }

    var optimisticSelectedIndex by remember(tracks) { mutableStateOf(selectedTrackIndex) }
    val totalCount = subtitleGroups.size + 1

    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            PhoneSelectableItem(
                title = stringResource(id = PlayerR.string.none),
                chips = emptyList(),
                isSelected = optimisticSelectedIndex == -1,
                shape = getItemShape(0, totalCount),
                onClick = {
                    optimisticSelectedIndex = -1
                    viewModel.switchToTrack(C.TRACK_TYPE_TEXT, -1)
                },
            )
        }
        itemsIndexed(items = subtitleGroups) { index, group ->
            val isSelected = optimisticSelectedIndex == index
            val displayInfo =
                remember(group, currentLocale, displayExtraInfo) {
                    TrackFormatter.getTrackDisplayInfo(
                        group = group,
                        context = context,
                        displayExtraInfo = displayExtraInfo,
                        currentLocale = currentLocale,
                        fallbackIndex = index + 1,
                    )
                }

            PhoneSelectableItem(
                title = displayInfo.name,
                chips = displayInfo.chips,
                isSelected = isSelected,
                shape = getItemShape(index + 1, totalCount),
                onClick = {
                    optimisticSelectedIndex = index
                    viewModel.switchToTrack(C.TRACK_TYPE_TEXT, index)
                },
            )
        }
    }
}

@Composable
private fun PhoneSpeedList(currentSpeed: Float, onSpeedSelected: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(speeds) { index, speed ->
            val isSelected = kotlin.math.abs(currentSpeed - speed) < 0.01f
            PhoneSelectableItem(
                title =
                    if (speed == 1f) "1.0x (${stringResource(id = PlayerR.string.normal_speed)})"
                    else "${speed}x",
                chips = emptyList(),
                isSelected = isSelected,
                shape = getItemShape(index, speeds.size),
                onClick = { onSpeedSelected(speed) },
            )
        }
    }
}

@Composable
private fun PhoneAspectRatioList(currentResizeMode: Int, onResizeModeSelected: (Int) -> Unit) {
    val modes =
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to
                stringResource(id = PlayerR.string.aspect_ratio_fit),
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to
                stringResource(id = PlayerR.string.aspect_ratio_zoom),
            AspectRatioFrameLayout.RESIZE_MODE_FILL to
                stringResource(id = PlayerR.string.aspect_ratio_fill),
        )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(modes) { index, (mode, label) ->
            val isSelected = currentResizeMode == mode
            PhoneSelectableItem(
                title = label,
                chips = emptyList(),
                isSelected = isSelected,
                shape = getItemShape(index, modes.size),
                onClick = { onResizeModeSelected(mode) },
            )
        }
    }
}

@Composable
private fun PhoneChaptersList(viewModel: PlayerViewModel, onChapterSelected: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val chapters = uiState.currentChapters
    val trickplay = uiState.currentTrickplay
    val currentPosition = viewModel.player.currentPosition
    val listState = rememberLazyListState()

    val currentChapterIndex =
        remember(currentPosition, chapters) {
            chapters.indexOfLast { it.startPosition <= currentPosition }
        }

    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex >= 0) {
            listState.animateScrollToItem(currentChapterIndex)
        }
    }

    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(chapters) { index, chapter ->
            val isCurrent = index == currentChapterIndex
            val startTimeMs = chapter.startPosition
            val formattedTime = formatDuration(startTimeMs)

            val nextChapterStart = chapters.getOrNull(index + 1)?.startPosition ?: Long.MAX_VALUE
            val segmentType =
                uiState.segments
                    .firstOrNull { seg ->
                        (chapter.startPosition >= seg.startTicks &&
                            chapter.startPosition < seg.endTicks) ||
                            (seg.startTicks >= chapter.startPosition &&
                                seg.startTicks < nextChapterStart)
                    }
                    ?.type

            val segmentColor = getSegmentColor(segmentType)
            val segmentLabel = getSegmentLabel(segmentType)

            val chapterLabel =
                chapter.name?.takeIf { it.isNotBlank() }
                    ?: stringResource(id = PlayerR.string.chapter_title, index + 1)
            val formattedTitle =
                if (segmentLabel != null) {
                    if (chapterLabel.equals(segmentLabel, ignoreCase = true)) {
                        val fallback = stringResource(id = PlayerR.string.chapter_title, index + 1)
                        "$fallback • $segmentLabel"
                    } else if (chapterLabel.contains(segmentLabel, ignoreCase = true)) {
                        chapterLabel
                    } else {
                        "$chapterLabel • $segmentLabel"
                    }
                } else {
                    chapterLabel
                }

            val chapterBitmap =
                remember(chapter, trickplay) {
                    if (trickplay != null && trickplay.images.isNotEmpty()) {
                        val intervalMs = trickplay.intervalMs
                        val imgIndex =
                            if (intervalMs > 0) {
                                (startTimeMs / intervalMs)
                                    .toInt()
                                    .coerceIn(0, trickplay.images.size - 1)
                            } else 0
                        trickplay.images.getOrNull(imgIndex)
                    } else null
                }

            val thumbBorder =
                if (segmentColor != null) {
                    BorderStroke(1.5.dp, segmentColor)
                } else {
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = getItemShape(index, chapters.size),
                color =
                    if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else Color(0xFF242424),
                border =
                    if (isCurrent)
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    else BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                onClick = {
                    viewModel.player.seekTo(startTimeMs)
                    onChapterSelected()
                },
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Chapter Thumbnail with Segment Border
                    Box(
                        modifier =
                            Modifier.size(width = 68.dp, height = 42.dp)
                                .clip(ChapterThumbShape)
                                .border(thumbBorder, ChapterThumbShape)
                                .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (chapterBitmap != null) {
                            Image(
                                bitmap = chapterBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formattedTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color =
                                if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (isCurrent) {
                                Box(
                                    modifier =
                                        Modifier.size(7.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                            if (segmentColor != null && segmentLabel != null) {
                                Box(
                                    modifier =
                                        Modifier.clip(RoundedCornerShape(4.dp))
                                            .background(segmentColor.copy(alpha = 0.2f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = segmentLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = segmentColor,
                                    )
                                }
                            }
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneSelectableItem(
    title: String,
    chips: List<String>,
    isSelected: Boolean,
    onClick: () -> Unit,
    shape: Shape = MenuSingleShape,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color =
            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else Color(0xFF242424),
        border =
            if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            else BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            if (chips.isNotEmpty()) {
                val scrollState = rememberScrollState()
                Row(
                    modifier =
                        Modifier.weight(1f)
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                            .drawWithContent {
                                drawContent()
                                val edgeWidth = 14.dp.toPx()
                                if (scrollState.value > 0) {
                                    drawRect(
                                        brush =
                                            Brush.horizontalGradient(
                                                colors = listOf(Color.Transparent, Color.Black),
                                                startX = 0f,
                                                endX = edgeWidth,
                                            ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                                if (scrollState.value < scrollState.maxValue) {
                                    drawRect(
                                        brush =
                                            Brush.horizontalGradient(
                                                colors = listOf(Color.Black, Color.Transparent),
                                                startX = size.width - edgeWidth,
                                                endX = size.width,
                                            ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            }
                            .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (chip in chips) {
                        Box(
                            modifier =
                                Modifier.clip(ChipShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            if (isSelected) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
