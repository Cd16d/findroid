package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.ResolutionTier
import dev.jdtech.jellyfin.player.core.domain.models.VideoQualityOption
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.components.SelectableTrackItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.math.roundToInt
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Video quality selection list for phone bottom sheet.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param onQualitySelected Callback when a video quality option is selected.
 */
@Composable
internal fun PhoneQualityList(
    viewModel: PlayerViewModel,
    onQualitySelected: (VideoQualityOption) -> Unit,
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

    val autoChips =
        remember(
            uiState.selectedQuality,
            viewModel.displayExtraInfo,
            uiState.autoResolvedQualityLabel,
        ) {
            if (
                viewModel.displayExtraInfo &&
                    uiState.selectedQuality.isAuto &&
                    !uiState.autoResolvedQualityLabel.isNullOrBlank()
            ) {
                listOf(uiState.autoResolvedQualityLabel!!)
            } else {
                emptyList()
            }
        }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 1. Original (Direct Play)
        item {
            SelectableTrackItem(
                title = stringResource(PlayerR.string.quality_original),
                chips = listOf(stringResource(PlayerR.string.quality_direct_play)),
                isSelected = uiState.selectedQuality.isOriginal,
                onClick = { onQualitySelected(VideoQualityOption.ORIGINAL) },
            )
        }

        // 2. Auto
        item {
            AutoQualityCard(
                isSelected = uiState.selectedQuality.isAuto,
                chips = autoChips,
                isSpeedTesting = uiState.isSpeedTesting,
                onSelectAuto = { onQualitySelected(VideoQualityOption.AUTO) },
                onRefreshSpeedTest = { viewModel.refreshAutoQuality() },
            )
        }

        // 3. Fixed Resolution Tiers with Bitrate Slider
        itemsIndexed(items = availableTiers, key = { _, tier -> tier.name }) { _, tier ->
            val isTierSelected =
                uiState.selectedQuality.isFixed &&
                    uiState.selectedQuality.resolutionName.equals(tier.name, ignoreCase = true)

            val currentBitrate =
                if (isTierSelected) {
                    val bps = uiState.selectedQuality.bitrateBps ?: tier.defaultBitrateBps
                    if (bps in tier.bitratesBps) bps else tier.defaultBitrateBps
                } else {
                    tier.defaultBitrateBps
                }

            ResolutionQualityCard(
                tier = tier,
                isSelected = isTierSelected,
                selectedBitrateBps = currentBitrate,
                onSelectResolution = {
                    onQualitySelected(
                        VideoQualityOption.createFixedOption(tier.name, currentBitrate)
                    )
                },
                onBitrateChanged = { newBitrate ->
                    onQualitySelected(VideoQualityOption.createFixedOption(tier.name, newBitrate))
                },
            )
        }
    }
}

/**
 * Selection card for automatic video quality management with speed test refresh option.
 *
 * @param isSelected Whether auto quality is currently selected.
 * @param chips List of chip labels to display.
 * @param isSpeedTesting Whether a network speed test is currently in progress.
 * @param onSelectAuto Callback when the auto quality card is pressed.
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
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        }
    val borderColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        }
    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        shape = CentralShapes.GroupSingle,
        color = containerColor,
        border =
            if (isSelected) BorderStroke(1.5.dp, borderColor) else BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
        onClick = onSelectAuto,
        enabled = !isSelected,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .then(if (isSelected) Modifier.clickable { onSelectAuto() } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = stringResource(PlayerR.string.quality_auto),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (chips.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        chips.forEach { chipText ->
                            Box(
                                modifier =
                                    Modifier.clip(CentralShapes.Chip)
                                        .background(
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primary.copy(
                                                    alpha = 0.15f
                                                )
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = chipText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color =
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isSelected) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                ) {
                    Button(
                        onClick = onRefreshSpeedTest,
                        enabled = !isSpeedTesting,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor =
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        shape = CentralShapes.ItemCard,
                    ) {
                        if (isSpeedTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(PlayerR.string.quality_rerun_speed_test),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Selection card for fixed resolution quality tiers with bitrate adjustment slider.
 *
 * @param tier Resolution tier option.
 * @param isSelected Whether this resolution tier is currently selected.
 * @param selectedBitrateBps Currently selected bitrate in bits per second.
 * @param onSelectResolution Callback when selecting this resolution tier.
 * @param onBitrateChanged Callback when changing the bitrate slider value.
 */
@Composable
private fun ResolutionQualityCard(
    tier: ResolutionTier,
    isSelected: Boolean,
    selectedBitrateBps: Int,
    onSelectResolution: () -> Unit,
    onBitrateChanged: (Int) -> Unit,
) {
    val formattedBitrate = VideoQualityOption.formatBitrate(selectedBitrateBps)
    val bitrates = tier.bitratesBps
    val currentIndex = bitrates.indexOf(selectedBitrateBps).coerceAtLeast(0)

    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        }
    val borderColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        }
    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        shape = CentralShapes.GroupSingle,
        color = containerColor,
        border =
            if (isSelected) BorderStroke(1.5.dp, borderColor) else BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelectResolution,
        enabled = !isSelected,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .then(
                            if (isSelected) Modifier.clickable { onSelectResolution() }
                            else Modifier
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.size(20.dp))
                    }
                    Text(
                        text = tier.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isSelected) {
                    Box(
                        modifier =
                            Modifier.clip(CentralShapes.Chip)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formattedBitrate,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isSelected) {
                Column(
                    modifier =
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Slider(
                        value = currentIndex.toFloat(),
                        onValueChange = { floatVal ->
                            val idx = floatVal.roundToInt().coerceIn(0, bitrates.size - 1)
                            if (idx != currentIndex) {
                                onBitrateChanged(bitrates[idx])
                            }
                        },
                        valueRange = 0f..(bitrates.size - 1).toFloat(),
                        steps = (bitrates.size - 2).coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = VideoQualityOption.formatBitrate(bitrates.first()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = VideoQualityOption.formatBitrate(bitrates.last()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun AutoQualityCardPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutoQualityCard(
                isSelected = true,
                chips = listOf("1080p"),
                isSpeedTesting = false,
                onSelectAuto = {},
                onRefreshSpeedTest = {},
            )
            AutoQualityCard(
                isSelected = false,
                chips = emptyList(),
                isSpeedTesting = false,
                onSelectAuto = {},
                onRefreshSpeedTest = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun ResolutionQualityCardPreview() {
    FindroidTheme {
        val mockTier =
            ResolutionTier("1080p", 1920, 1080, listOf(3_000_000, 6_000_000, 10_000_000), 6_000_000)
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ResolutionQualityCard(
                tier = mockTier,
                isSelected = true,
                selectedBitrateBps = 6_000_000,
                onSelectResolution = {},
                onBitrateChanged = {},
            )
            ResolutionQualityCard(
                tier = mockTier,
                isSelected = false,
                selectedBitrateBps = 6_000_000,
                onSelectResolution = {},
                onBitrateChanged = {},
            )
        }
    }
}
