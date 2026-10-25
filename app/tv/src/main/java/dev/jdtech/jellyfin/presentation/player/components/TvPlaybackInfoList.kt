package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.presentation.theme.PlayerTvSidePanelContainer
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.presentation.PlaybackInfoFormatter
import dev.jdtech.jellyfin.player.core.presentation.drawStatGraph
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Stream statistics, transcoding details, and media metadata display list for TV.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param initialFocusRequester [FocusRequester] for setting initial focus on the list.
 */
@Composable
internal fun PlaybackInfoList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
) {
    val uiState by viewModel.uiState.collectAsState()
    val infoData = uiState.playbackInfoData

    LaunchedEffect(Unit) {
        viewModel.onPlaybackInfoOpened()
        while (isActive) {
            viewModel.refreshPlaybackStats()
            delay(1000L.milliseconds)
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = Modifier.focusRequester(initialFocusRequester),
    ) {
        // Playback Info Section
        item {
            TvPlaybackInfoCard(
                title = stringResource(id = PlayerR.string.playback_info_playback_section)
            ) {
                TvPlaybackInfoRow(
                    label = stringResource(id = PlayerR.string.playback_info_player),
                    value = infoData.playerBackend,
                )
                TvPlaybackInfoRow(
                    label = stringResource(id = PlayerR.string.playback_info_stream_type),
                    value = infoData.streamType,
                )
                if (infoData.playerResolution.isNotBlank()) {
                    TvPlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_player_dimensions),
                        value = infoData.playerResolution,
                    )
                }
            }
        }

        // Transcode Info Section (if transcoding)
        if (infoData.isTranscoding) {
            item {
                TvPlaybackInfoCard(
                    title = stringResource(id = PlayerR.string.playback_info_transcode_section)
                ) {
                    infoData.transcodeVideoCodec?.let {
                        TvPlaybackInfoRow(
                            label =
                                stringResource(
                                    id = PlayerR.string.playback_info_transcode_video_codec
                                ),
                            value = it,
                        )
                    }
                    infoData.transcodeAudioCodec?.let {
                        TvPlaybackInfoRow(
                            label =
                                stringResource(
                                    id = PlayerR.string.playback_info_transcode_audio_codec
                                ),
                            value = it,
                        )
                    }
                    infoData.transcodeResolution?.let {
                        TvPlaybackInfoRow(
                            label =
                                stringResource(
                                    id = PlayerR.string.playback_info_transcode_resolution
                                ),
                            value = it,
                        )
                    }
                    if (
                        infoData.transcodeProgressRatio != null ||
                            !infoData.transcodeProgressText.isNullOrBlank()
                    ) {
                        TvConversionProgressRow(
                            ratio = infoData.transcodeProgressRatio ?: 0f,
                            text = infoData.transcodeProgressText.orEmpty(),
                        )
                    }
                    infoData.transcodeBitrate?.let {
                        TvPlaybackInfoRow(
                            label =
                                stringResource(id = PlayerR.string.playback_info_transcode_bitrate),
                            value = it,
                        )
                    }
                    if (infoData.transcodeReasons.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        id = PlayerR.string.playback_info_transcode_reasons
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium,
                            )
                            infoData.transcodeReasons.forEach { reason ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        id = PlayerR.string.playback_info_transcode_speed_graph
                                    ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                fontWeight = FontWeight.Medium,
                            )
                            if (!infoData.transcodeSpeed.isNullOrBlank()) {
                                Text(
                                    text = infoData.transcodeSpeed.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        TvSimpleStatGraph(
                            data = infoData.transcodeSpeedHistory,
                            lineColor = MaterialTheme.colorScheme.primary,
                            unit = "fps",
                        )
                    }
                }
            }
        }

        // Original Content Info Section
        item {
            TvPlaybackInfoCard(
                title = stringResource(id = PlayerR.string.playback_info_original_section)
            ) {
                infoData.container?.let {
                    TvPlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_container),
                        value = it,
                    )
                }
                infoData.sizeText?.let {
                    TvPlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_size),
                        value = it,
                    )
                }
                infoData.bitrateText?.let {
                    TvPlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_bitrate),
                        value = it,
                    )
                }
                if (infoData.hasVideoInfo) {
                    TvPlaybackInfoSubSection(
                        title = stringResource(id = PlayerR.string.playback_info_video_section)
                    ) {
                        infoData.videoCodec?.let {
                            TvPlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_codec),
                                value = it,
                            )
                        }
                        infoData.videoProfile?.let {
                            TvPlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_profile),
                                value = it,
                            )
                        }
                        infoData.videoFrameRate?.let {
                            TvPlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_framerate),
                                value = it,
                            )
                        }
                        infoData.videoBitDepth?.let {
                            TvPlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_bit_depth),
                                value = it,
                            )
                        }
                        infoData.dynamicRange?.let {
                            TvPlaybackInfoRow(
                                label =
                                    stringResource(id = PlayerR.string.playback_info_dynamic_range),
                                value = it,
                            )
                        }
                    }
                }
                if (infoData.hasAudioInfo) {
                    TvPlaybackInfoSubSection(
                        title = stringResource(id = PlayerR.string.playback_info_audio_section)
                    ) {
                        infoData.audioCodec?.let {
                            TvPlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_codec),
                                value = it,
                            )
                        }
                        infoData.audioChannels?.let {
                            TvPlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_channels),
                                value = it,
                            )
                        }
                        infoData.audioSampleRate?.let {
                            TvPlaybackInfoRow(
                                label =
                                    stringResource(id = PlayerR.string.playback_info_sample_rate),
                                value = it,
                            )
                        }
                    }
                }
            }
        }

        // Stats for Nerds Section
        item {
            TvPlaybackInfoCard(
                title = stringResource(id = PlayerR.string.playback_info_nerd_stats_section)
            ) {
                infoData.playbackStatus?.let {
                    TvPlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_playback_status),
                        value = it,
                    )
                }
                TvPlaybackInfoRow(
                    label = stringResource(id = PlayerR.string.playback_info_dropped_frames),
                    value = infoData.droppedFrames,
                )
                infoData.bufferHealth?.let {
                    TvPlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_buffer_health),
                        value = it,
                    )
                }
                infoData.audioOutput?.let {
                    TvPlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_audio_output),
                        value = it,
                    )
                }
                if (infoData.bufferHealthHistory.size >= 2) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    id = PlayerR.string.playback_info_buffer_health_graph
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium,
                        )
                        TvSimpleStatGraph(
                            data = infoData.bufferHealthHistory,
                            lineColor = MaterialTheme.colorScheme.tertiary,
                            unit = "s",
                        )
                    }
                }
            }
        }
    }
}

/**
 * Minimalist line and area graph for displaying statistic history on TV interface.
 *
 * @param data List of historical stat values.
 * @param modifier Optional [Modifier] applied to the graph container.
 * @param lineColor Line and gradient fill color for the graph.
 * @param unit Measurement unit appended to value labels.
 */
@Composable
private fun TvSimpleStatGraph(
    data: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    unit: String = "",
) {
    val maxVal = max(data.maxOrNull() ?: 0f, 0f)
    val latestVal = data.lastOrNull() ?: 0f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = PlaybackInfoFormatter.formatStatMax(maxVal, unit),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
            )
            Text(
                text = PlaybackInfoFormatter.formatStatLatest(latestVal, unit),
                style = MaterialTheme.typography.labelSmall,
                color = lineColor,
                fontWeight = FontWeight.Bold,
            )
        }
        Canvas(
            modifier =
                Modifier.fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.04f))
        ) {
            drawStatGraph(
                data = data,
                maxVal = maxVal,
                lineColor = lineColor,
            )
        }
    }
}

/**
 * Progress bar row showing transcode conversion progress on TV interface.
 *
 * @param ratio Transcode conversion progress ratio between 0 and 1.
 * @param text Formatted conversion progress text label.
 */
@Composable
private fun TvConversionProgressRow(
    ratio: Float,
    text: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(id = PlayerR.string.playback_info_transcode_conversion_progress),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium,
            )
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.White.copy(alpha = 0.12f),
        )
    }
}

/**
 * Container card for grouping playback information rows on TV interface.
 *
 * @param title Section card header title.
 * @param content Composable slot containing section items.
 */
@Composable
private fun TvPlaybackInfoCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = PanelItemShape,
        colors =
            SurfaceDefaults.colors(
                containerColor = PlayerTvSidePanelContainer,
                contentColor = Color.White,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

/**
 * Container card for nested subsection details within a playback info section on TV interface.
 *
 * @param title Subsection header title.
 * @param content Composable slot containing subsection items.
 */
@Composable
private fun TvPlaybackInfoSubSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        colors =
            SurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.04f),
                contentColor = Color.White,
            ),
        modifier =
            Modifier.fillMaxWidth()
                .padding(top = 4.dp)
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                    RoundedCornerShape(10.dp),
                ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

/**
 * Key-value statistic row for playback info display on TV interface.
 *
 * @param label Description of the statistic field.
 * @param value Formatted string value of the statistic field.
 */
@Composable
private fun TvPlaybackInfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.55f),
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun PlaybackInfoListPreview() {
    FindroidTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
            TvPlaybackInfoCard(title = "Playback Info") {
                TvPlaybackInfoRow(label = "Player", value = "ExoPlayer (Transcode)")
                TvPlaybackInfoRow(label = "Stream Type", value = "HLS (https)")
                TvPlaybackInfoRow(label = "Player Dimensions", value = "1920x1000 / 1920x816")
                TvPlaybackInfoRow(label = "Dropped Frames", value = "1 / 0")
            }
        }
    }
}
