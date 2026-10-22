package dev.jdtech.jellyfin.presentation.download.components.presets

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.DownloadQualityPreset
import dev.jdtech.jellyfin.models.DownloadQualityPresets
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import java.util.Locale
import org.jellyfin.sdk.model.api.VideoRangeType

/** UI components for download preset selection, quality options, and audio track badges. */

/**
 * Renders badges for default and external audio tracks.
 *
 * @param isDefault Whether the audio track is marked as default.
 * @param isExternal Whether the audio track is external.
 * @param modifier Modifier to be applied to the layout.
 */
@Composable
fun AudioTrackBadges(
    isDefault: Boolean,
    isExternal: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isDefault) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = stringResource(CoreR.string.track_default),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        if (isExternal) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = stringResource(CoreR.string.external),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

/**
 * Card displaying a download quality preset option with details and selection state.
 *
 * @param preset The quality preset model.
 * @param isSelected Whether this preset is currently selected.
 * @param showDetails Whether to expand and show detailed media stream specifications.
 * @param onSelect Callback invoked when the preset card is tapped.
 * @param modifier Modifier to be applied to the layout.
 * @param isOriginalSupported Whether the device supports direct play/download of the original
 *   source codec.
 * @param originalVideoStream The original video stream metadata if available.
 * @param audioStreams Available audio streams for the original source.
 * @param runtimeTicks Media duration in ticks.
 * @param sourceSize Media file size in bytes.
 * @param formatAudioTrackName Formatter function for audio track titles.
 * @param currentLocale Current user locale for language formatting.
 */
@Composable
fun PresetCard(
    preset: DownloadQualityPreset,
    isSelected: Boolean,
    showDetails: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    isOriginalSupported: Boolean = true,
    originalVideoStream: FindroidMediaStream? = null,
    audioStreams: List<FindroidMediaStream> = emptyList(),
    runtimeTicks: Long = 0L,
    sourceSize: Long = 0L,
    formatAudioTrackName: (FindroidMediaStream, Locale) -> String = { stream, _ ->
        stream.displayTitle ?: ""
    },
    currentLocale: Locale = LocalConfiguration.current.locales[0],
) {
    val context = LocalContext.current
    val isOriginalUnsupported = preset.isOriginal && !isOriginalSupported

    val borderColor by
        animateColorAsState(
            targetValue =
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isOriginalUnsupported -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                },
            label = "presetBorderColor",
        )
    val borderWidth = if (isSelected || isOriginalUnsupported) 2.dp else 1.dp

    val sourceSizeStr = stringResource(CoreR.string.download_preset_source_size)
    val approxSizeStr = preset.displayApproxSize.asString()

    val sizeBadgeText =
        remember(preset, sourceSize, runtimeTicks, context, sourceSizeStr, approxSizeStr) {
            if (preset.isOriginal) {
                if (sourceSize > 0) {
                    Formatter.formatFileSize(context, sourceSize)
                } else {
                    sourceSizeStr
                }
            } else {
                if (runtimeTicks > 0) {
                    val durationSec = (runtimeTicks / 10_000_000L).coerceAtLeast(1)
                    val estimatedBytes = (preset.totalBitrateBps * durationSec) / 8L
                    "~${Formatter.formatFileSize(context, estimatedBytes)}"
                } else {
                    approxSizeStr
                }
            }
        }

    Card(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    when {
                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        isOriginalUnsupported ->
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                        else -> MaterialTheme.colorScheme.surfaceContainer
                    }
            ),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.displayName.asString(),
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight =
                                    if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            ),
                        color =
                            when {
                                isOriginalUnsupported -> MaterialTheme.colorScheme.error
                                isSelected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                    )
                    if (isOriginalUnsupported) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(CoreR.string.download_preset_not_supported),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color =
                        when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            isOriginalUnsupported -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                ) {
                    Text(
                        text = sizeBadgeText,
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                        color =
                            when {
                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                isOriginalUnsupported -> MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    if (preset.isOriginal) {
                        val videoText =
                            remember(originalVideoStream) {
                                if (originalVideoStream != null) {
                                    val parts = mutableListOf<String>()
                                    if (originalVideoStream.codec.isNotBlank())
                                        parts.add(originalVideoStream.codec.uppercase(Locale.US))
                                    if (originalVideoStream.height != null)
                                        parts.add("${originalVideoStream.height}p")
                                    originalVideoStream.videoRangeType
                                        ?.takeIf { it != VideoRangeType.SDR }
                                        ?.let { parts.add(it.name) }
                                    "Video: ${parts.joinToString(" • ")}"
                                } else {
                                    "Video: Direct Stream (Original source)"
                                }
                            }
                        Text(
                            text = videoText,
                            style = MaterialTheme.typography.bodySmall,
                            color =
                                if (isOriginalUnsupported) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (audioStreams.isNotEmpty()) {
                            audioStreams.forEach { stream ->
                                key(stream.index) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text =
                                                stringResource(
                                                    CoreR.string.download_preset_audio,
                                                    formatAudioTrackName(stream, currentLocale),
                                                ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        AudioTrackBadges(
                                            isDefault = stream.isDefault == true,
                                            isExternal = stream.isExternal,
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text =
                                    stringResource(
                                        CoreR.string.download_preset_audio,
                                        "Direct Stream (${stringResource(CoreR.string.download_preset_original_audio)})",
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            text =
                                "Video: ${preset.videoCodec.uppercase(Locale.US)} • ${preset.resolutionText.asString()} • ${preset.bitrateText.asString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.download_preset_audio,
                                    "${preset.audioCodec.uppercase(Locale.US)} • ${preset.audioText.asString()}",
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Row item containing a checkbox, primary label, and optional subtitle.
 *
 * @param checked Checked state of the checkbox.
 * @param onCheckedChange Callback invoked when the row or checkbox is toggled.
 * @param label Main label text for the option.
 * @param modifier Modifier to be applied to the layout.
 * @param subtitle Optional subtitle text for additional description.
 * @param shape Corner shape for clipping and touch feedback.
 */
@Composable
fun CheckboxOptionRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    shape: RoundedCornerShape = RoundedCornerShape(0.dp),
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .toggleable(
                    value = checked,
                    role = Role.Checkbox,
                    onValueChange = onCheckedChange,
                )
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AnimatedVisibility(
                visible = !subtitle.isNullOrBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun PresetCardStandardPreview() {
    FindroidTheme {
        Surface {
            PresetCard(
                preset = DownloadQualityPresets.BALANCED,
                isSelected = false,
                showDetails = false,
                onSelect = {},
            )
        }
    }
}

@Preview
@Composable
private fun PresetCardSelectedPreview() {
    FindroidTheme {
        Surface {
            PresetCard(
                preset = DownloadQualityPresets.BALANCED,
                isSelected = true,
                showDetails = false,
                onSelect = {},
            )
        }
    }
}

@Preview
@Composable
private fun PresetCardUnsupportedPreview() {
    FindroidTheme {
        Surface {
            PresetCard(
                preset = DownloadQualityPresets.ORIGINAL,
                isSelected = false,
                showDetails = false,
                isOriginalSupported = false,
                onSelect = {},
            )
        }
    }
}

@Preview
@Composable
private fun PresetCardExpandedPreview() {
    FindroidTheme {
        Surface {
            PresetCard(
                preset = DownloadQualityPresets.BALANCED,
                isSelected = true,
                showDetails = true,
                onSelect = {},
            )
        }
    }
}

@Preview
@Composable
private fun CheckboxOptionRowPreview() {
    FindroidTheme {
        Surface {
            CheckboxOptionRow(
                checked = true,
                onCheckedChange = {},
                label = "Include external audio",
                subtitle = "Downloads external audio tracks",
            )
        }
    }
}
