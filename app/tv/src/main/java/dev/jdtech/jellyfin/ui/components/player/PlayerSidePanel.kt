package dev.jdtech.jellyfin.ui.components.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.core.R as PlayerCoreR
import dev.jdtech.jellyfin.player.local.domain.getTrackNames
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.spacings

enum class PlayerSidePanelType {
    NONE,
    AUDIO,
    SUBTITLES,
    SETTINGS,
}

@Composable
fun PlayerSidePanel(
    panelType: PlayerSidePanelType,
    onPanelTypeChange: (PlayerSidePanelType) -> Unit,
    viewModel: PlayerViewModel,
    currentResizeMode: Int,
    onResizeModeChange: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(panelType) {
        if (panelType != PlayerSidePanelType.NONE) {
            try {
                initialFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .width(380.dp)
                .background(
                    color = Color(0xF2141416),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                )
                .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header
            PanelHeader(
                panelType = panelType,
                onBack = {
                    if (panelType == PlayerSidePanelType.SETTINGS) {
                        onClose()
                    } else {
                        onPanelTypeChange(PlayerSidePanelType.SETTINGS)
                    }
                },
                onClose = onClose,
            )

            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))

            // Body
            when (panelType) {
                PlayerSidePanelType.AUDIO -> {
                    AudioTrackList(
                        viewModel = viewModel,
                        initialFocusRequester = initialFocusRequester,
                    )
                }

                PlayerSidePanelType.SUBTITLES -> {
                    SubtitleTrackList(
                        viewModel = viewModel,
                        initialFocusRequester = initialFocusRequester,
                    )
                }

                PlayerSidePanelType.SETTINGS -> {
                    SettingsList(
                        viewModel = viewModel,
                        currentResizeMode = currentResizeMode,
                        onResizeModeChange = onResizeModeChange,
                        onNavigate = onPanelTypeChange,
                        initialFocusRequester = initialFocusRequester,
                    )
                }

                PlayerSidePanelType.NONE -> Unit
            }
        }
    }
}

@Composable
private fun PanelHeader(
    panelType: PlayerSidePanelType,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val titleRes =
        when (panelType) {
            PlayerSidePanelType.AUDIO -> PlayerCoreR.string.select_audio_track
            PlayerSidePanelType.SUBTITLES -> PlayerCoreR.string.select_subtitle_track
            PlayerSidePanelType.SETTINGS -> CoreR.string.title_settings
            PlayerSidePanelType.NONE -> CoreR.string.title_settings
        }

    val iconRes =
        when (panelType) {
            PlayerSidePanelType.AUDIO -> CoreR.drawable.ic_speaker
            PlayerSidePanelType.SUBTITLES -> CoreR.drawable.ic_closed_caption
            PlayerSidePanelType.SETTINGS -> CoreR.drawable.ic_settings
            PlayerSidePanelType.NONE -> CoreR.drawable.ic_settings
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
            Text(
                text = stringResource(id = titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_x),
                contentDescription = stringResource(id = CoreR.string.close),
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun AudioTrackList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
) {
    val audioGroups =
        remember(viewModel.player.currentTracks) {
            viewModel.player.currentTracks.groups.filter {
                it.type == C.TRACK_TYPE_AUDIO && it.isSupported
            }
        }
    val audioNames = remember(audioGroups) { audioGroups.getTrackNames() }

    if (audioGroups.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(id = CoreR.string.none),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(audioGroups) { index, group ->
                val isSelected = group.isSelected
                val name = audioNames.getOrNull(index) ?: stringResource(id = CoreR.string.audio)
                val isFirst = index == 0

                SelectableItem(
                    title = name,
                    isSelected = isSelected,
                    onClick = { viewModel.switchToTrack(C.TRACK_TYPE_AUDIO, index) },
                    modifier =
                        if (isFirst) Modifier.focusRequester(initialFocusRequester) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun SubtitleTrackList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
) {
    val textGroups =
        remember(viewModel.player.currentTracks) {
            viewModel.player.currentTracks.groups.filter {
                it.type == C.TRACK_TYPE_TEXT && it.isSupported
            }
        }
    val textNames = remember(textGroups) { textGroups.getTrackNames() }
    val isNoneSelected = remember(textGroups) { !textGroups.any { it.isSelected } }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // "None" option
        item {
            SelectableItem(
                title = stringResource(id = PlayerCoreR.string.none),
                isSelected = isNoneSelected,
                onClick = { viewModel.switchToTrack(C.TRACK_TYPE_TEXT, -1) },
                modifier = Modifier.focusRequester(initialFocusRequester),
            )
        }

        // Available subtitle tracks
        itemsIndexed(textGroups) { index, group ->
            val isSelected = group.isSelected
            val name = textNames.getOrNull(index) ?: stringResource(id = CoreR.string.subtitle)

            SelectableItem(
                title = name,
                isSelected = isSelected,
                onClick = { viewModel.switchToTrack(C.TRACK_TYPE_TEXT, index) },
            )
        }
    }
}

@Composable
private fun SettingsList(
    viewModel: PlayerViewModel,
    currentResizeMode: Int,
    onResizeModeChange: (Int) -> Unit,
    onNavigate: (PlayerSidePanelType) -> Unit,
    initialFocusRequester: FocusRequester,
) {
    val speedOptions = remember { listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f) }
    val currentSpeed = viewModel.playbackSpeed

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // Quick Jump to Audio
        item {
            NavigationItem(
                title = stringResource(id = CoreR.string.audio),
                iconRes = CoreR.drawable.ic_speaker,
                onClick = { onNavigate(PlayerSidePanelType.AUDIO) },
                modifier = Modifier.focusRequester(initialFocusRequester),
            )
        }

        // Quick Jump to Subtitles
        item {
            NavigationItem(
                title = stringResource(id = CoreR.string.subtitle),
                iconRes = CoreR.drawable.ic_closed_caption,
                onClick = { onNavigate(PlayerSidePanelType.SUBTITLES) },
            )
        }

        // Playback Speed Section
        item {
            Column {
                Text(
                    text = stringResource(id = PlayerCoreR.string.select_playback_speed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                speedOptions.forEach { speed ->
                    val isSelected = currentSpeed == speed
                    val label = "${speed}x" + if (speed == 1.0f) " (Normal)" else ""
                    SelectableItem(
                        title = label,
                        isSelected = isSelected,
                        onClick = { viewModel.selectSpeed(speed) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        // Aspect Ratio Section
        item {
            Column {
                Text(
                    text = stringResource(id = CoreR.string.aspect_ratio),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val resizeModes =
                    listOf(
                        AspectRatioFrameLayout.RESIZE_MODE_FIT to
                            stringResource(id = CoreR.string.aspect_ratio_fit),
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to
                            stringResource(id = CoreR.string.aspect_ratio_zoom),
                        AspectRatioFrameLayout.RESIZE_MODE_FILL to
                            stringResource(id = CoreR.string.aspect_ratio_fill),
                    )

                resizeModes.forEach { (mode, name) ->
                    val isSelected = currentResizeMode == mode
                    SelectableItem(
                        title = name,
                        isSelected = isSelected,
                        onClick = { onResizeModeChange(mode) },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableItem(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor =
                    if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    else Color.White.copy(alpha = 0.05f),
                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                    ),
                border =
                    if (isSelected) {
                        Border(
                            BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                    } else {
                        Border.None
                    },
            ),
        scale = ClickableSurfaceScale.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NavigationItem(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.07f),
                focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                    )
            ),
        scale = ClickableSurfaceScale.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
            Icon(
                painter = painterResource(id = CoreR.drawable.ic_arrow_right),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
