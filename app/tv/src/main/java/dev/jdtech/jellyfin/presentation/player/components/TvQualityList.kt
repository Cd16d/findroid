package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.core.presentation.theme.PlayerTvSidePanelContainer
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.ResolutionTier
import dev.jdtech.jellyfin.player.core.domain.models.VideoQualityOption
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.math.roundToInt
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Video quality selection list for TV player side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param initialFocusRequester [FocusRequester] for setting initial focus on the list.
 * @param onBack Callback to navigate back to the settings menu.
 */
@Composable
internal fun QualityList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val videoStream =
        remember(uiState.mediaStreams) {
            uiState.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }
        }
    val availableTiers =
        remember(videoStream) {
            VideoQualityOption.getAvailableResolutions(
                videoStream?.width,
                videoStream?.height,
            )
        }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        // 1. Original (Direct Play)
        item {
            val isSelected = uiState.selectedQuality.isOriginal
            SelectableItem(
                title = stringResource(PlayerR.string.quality_original),
                chips = listOf(stringResource(PlayerR.string.quality_direct_play)),
                isSelected = isSelected,
                onClick = {
                    viewModel.selectVideoQuality(VideoQualityOption.ORIGINAL)
                    onBack()
                },
                modifier = Modifier.focusRequester(initialFocusRequester),
            )
        }

        // 2. Auto
        item {
            val isSelected = uiState.selectedQuality.isAuto
            val autoChips =
                if (
                    viewModel.displayExtraInfo &&
                        isSelected &&
                        !uiState.autoResolvedQualityLabel.isNullOrBlank()
                ) {
                    listOf(uiState.autoResolvedQualityLabel!!)
                } else {
                    emptyList()
                }
            AutoQualityCard(
                isSelected = isSelected,
                chips = autoChips,
                isSpeedTesting = uiState.isSpeedTesting,
                onSelectAuto = {
                    if (isSelected) {
                        viewModel.refreshAutoQuality()
                    } else {
                        viewModel.selectVideoQuality(VideoQualityOption.AUTO)
                        onBack()
                    }
                },
                onRefreshSpeedTest = { viewModel.refreshAutoQuality() },
            )
        }

        // 3. Fixed Resolution Tiers
        itemsIndexed(items = availableTiers, key = { _, tier -> tier.name }) { _, tier ->
            val isTierSelected =
                uiState.selectedQuality.isFixed &&
                    uiState.selectedQuality.resolutionName.equals(tier.name, ignoreCase = true)

            val currentBitrate =
                if (isTierSelected) {
                    uiState.selectedQuality.bitrateBps ?: tier.defaultBitrateBps
                } else {
                    tier.defaultBitrateBps
                }

            QualityTierItem(
                tier = tier,
                currentBitrate = currentBitrate,
                isSelected = isTierSelected,
                onSelectTier = {
                    if (isTierSelected) {
                        onBack()
                    } else {
                        viewModel.selectVideoQuality(
                            VideoQualityOption.createFixedOption(tier.name, currentBitrate)
                        )
                    }
                },
                onBitrateChange = { newBitrate ->
                    viewModel.selectVideoQuality(
                        VideoQualityOption.createFixedOption(tier.name, newBitrate)
                    )
                },
            )
        }
    }
}

/**
 * Selection card for automatic video quality management with speed test refresh option on TV.
 *
 * @param isSelected Whether auto quality is currently selected.
 * @param chips List of chip labels to display.
 * @param isSpeedTesting Whether a network speed test is currently in progress.
 * @param onSelectAuto Callback when the auto quality option is pressed.
 * @param onRefreshSpeedTest Callback to rerun the auto quality speed test.
 * @param modifier Optional [Modifier] applied to the card container.
 */
@Composable
internal fun AutoQualityCard(
    isSelected: Boolean,
    chips: List<String>,
    isSpeedTesting: Boolean,
    onSelectAuto: () -> Unit,
    onRefreshSpeedTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isSelected) {
        SelectableItem(
            title = stringResource(id = PlayerR.string.quality_auto),
            chips = chips,
            isSelected = false,
            onClick = onSelectAuto,
            modifier = modifier,
        )
    } else {
        Surface(
            shape = PanelItemShape,
            colors =
                SurfaceDefaults.colors(
                    containerColor = PlayerTvSidePanelContainer,
                    contentColor = Color.White,
                ),
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Surface(
                    onClick = onSelectAuto,
                    shape = ClickableSurfaceDefaults.shape(shape = CentralShapes.Tiny),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            contentColor = Color.White,
                            focusedContentColor = Color.Black,
                        ),
                    scale = ClickableSurfaceScale.None,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(id = PlayerR.string.quality_auto),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (chips.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                chips.forEach { chipText -> TrackChip(text = chipText) }
                            }
                        }
                    }
                }

                Surface(
                    onClick = {
                        if (!isSpeedTesting) {
                            onRefreshSpeedTest()
                        }
                    },
                    shape = ClickableSurfaceDefaults.shape(shape = CentralShapes.Chip),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White,
                            contentColor = Color.White,
                            focusedContentColor = Color.Black,
                        ),
                    scale = ClickableSurfaceScale.None,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        if (isSpeedTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = CoreR.drawable.ic_rotate_ccw),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = PlayerR.string.quality_rerun_speed_test),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Focusable resolution tier selection item with DPAD left/right bitrate adjustment for TV.
 *
 * @param tier Resolution tier option.
 * @param currentBitrate Currently selected bitrate in bits per second.
 * @param isSelected Whether this quality tier is currently selected.
 * @param onSelectTier Callback when selecting this quality tier.
 * @param onBitrateChange Callback when changing bitrate with DPAD left or right key presses.
 * @param modifier Optional [Modifier] applied to the item surface.
 */
@Composable
internal fun QualityTierItem(
    tier: ResolutionTier,
    currentBitrate: Int,
    isSelected: Boolean,
    onSelectTier: () -> Unit,
    onBitrateChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex =
        remember(tier.bitratesBps, currentBitrate) {
            tier.bitratesBps.indexOf(currentBitrate).coerceAtLeast(0)
        }
    val bitrateLabel = remember(currentBitrate) { VideoQualityOption.formatBitrate(currentBitrate) }

    Surface(
        onClick = onSelectTier,
        shape = ClickableSurfaceDefaults.shape(shape = PanelItemShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = PlayerTvSidePanelContainer,
                focusedContainerColor = Color.White,
                contentColor = Color.White,
                focusedContentColor = Color.Black,
            ),
        scale = ClickableSurfaceScale.None,
        modifier =
            modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    isSelected &&
                    when (event.key) {
                        Key.DirectionLeft -> {
                            if (currentIndex > 0) {
                                onBitrateChange(tier.bitratesBps[currentIndex - 1])
                            }
                            true
                        }

                        Key.DirectionRight -> {
                            if (currentIndex < tier.bitratesBps.lastIndex) {
                                onBitrateChange(tier.bitratesBps[currentIndex + 1])
                            }
                            true
                        }

                        else -> false
                    }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isSelected) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.size(20.dp))
                }
                Text(
                    text = tier.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    TrackChip(text = bitrateLabel)
                }
            }

            if (isSelected && tier.bitratesBps.size > 1) {
                val bitrates = tier.bitratesBps
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = { floatVal ->
                            val idx = floatVal.roundToInt().coerceIn(0, bitrates.lastIndex)
                            if (idx != currentIndex) {
                                onBitrateChange(bitrates[idx])
                            }
                        },
                        valueRange = 0f..bitrates.lastIndex.toFloat(),
                        steps = (bitrates.size - 2).coerceAtLeast(0),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = LocalContentColor.current,
                                activeTrackColor = LocalContentColor.current,
                                inactiveTrackColor = LocalContentColor.current.copy(alpha = 0.24f),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = VideoQualityOption.formatBitrate(bitrates.first()),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                        Text(
                            text = VideoQualityOption.formatBitrate(bitrates.last()),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun QualityTierItemPreview() {
    FindroidTheme {
        val mockTier =
            ResolutionTier("1080p", 1920, 1080, listOf(3_000_000, 6_000_000, 10_000_000), 6_000_000)
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QualityTierItem(
                tier = mockTier,
                currentBitrate = 6_000_000,
                isSelected = true,
                onSelectTier = {},
                onBitrateChange = {},
            )
            QualityTierItem(
                tier = mockTier,
                currentBitrate = 6_000_000,
                isSelected = false,
                onSelectTier = {},
                onBitrateChange = {},
            )
        }
    }
}

@Preview
@Composable
private fun QualityListPreview() {
    FindroidTheme {
        val mockTier =
            ResolutionTier("1080p", 1920, 1080, listOf(3_000_000, 6_000_000, 10_000_000), 6_000_000)
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableItem(
                title = stringResource(PlayerR.string.quality_original),
                chips = listOf(stringResource(PlayerR.string.quality_direct_play)),
                isSelected = false,
                onClick = {},
            )
            AutoQualityCard(
                isSelected = true,
                chips = listOf("1080p - 10 Mbps"),
                isSpeedTesting = false,
                onSelectAuto = {},
                onRefreshSpeedTest = {},
            )
            QualityTierItem(
                tier = mockTier,
                currentBitrate = 6_000_000,
                isSelected = false,
                onSelectTier = {},
                onBitrateChange = {},
            )
        }
    }
}
