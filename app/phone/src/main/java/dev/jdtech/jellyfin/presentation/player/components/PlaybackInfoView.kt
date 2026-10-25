package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.core.presentation.theme.PlayerCardBackground
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlaybackInfoData
import dev.jdtech.jellyfin.player.core.presentation.PlaybackInfoFormatter
import dev.jdtech.jellyfin.player.core.presentation.drawStatGraph
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Stream statistics, transcoding details, and media metadata display view.
 *
 * @param infoData Playback statistics and stream metadata.
 * @param modifier Optional [Modifier] applied to the view.
 * @param onOpened Callback triggered when the playback info view is first displayed.
 * @param onRefresh Callback triggered periodically to refresh playback statistics.
 */
@Composable
fun PlaybackInfoView(
    infoData: PlaybackInfoData,
    modifier: Modifier = Modifier,
    onOpened: () -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        onOpened()
        while (isActive) {
            onRefresh()
            delay(1000L.milliseconds)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Playback Info
        item {
            PlaybackInfoSectionCard(
                title = stringResource(id = PlayerR.string.playback_info_playback_section)
            ) {
                PlaybackInfoRow(
                    label = stringResource(id = PlayerR.string.playback_info_player),
                    value = infoData.playerBackend,
                )
                PlaybackInfoRow(
                    label = stringResource(id = PlayerR.string.playback_info_stream_type),
                    value = infoData.streamType,
                )
                if (infoData.playerResolution.isNotBlank()) {
                    PlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_player_dimensions),
                        value = infoData.playerResolution,
                    )
                }
            }
        }

        // 2. Transcode Info (if transcoding)
        if (infoData.isTranscoding) {
            item {
                PlaybackInfoSectionCard(
                    title = stringResource(id = PlayerR.string.playback_info_transcode_section)
                ) {
                    infoData.transcodeVideoCodec?.let {
                        PlaybackInfoRow(
                            label =
                                stringResource(
                                    id = PlayerR.string.playback_info_transcode_video_codec
                                ),
                            value = it,
                        )
                    }
                    infoData.transcodeAudioCodec?.let {
                        PlaybackInfoRow(
                            label =
                                stringResource(
                                    id = PlayerR.string.playback_info_transcode_audio_codec
                                ),
                            value = it,
                        )
                    }
                    infoData.transcodeResolution?.let {
                        PlaybackInfoRow(
                            label =
                                stringResource(
                                    id = PlayerR.string.playback_info_transcode_resolution
                                ),
                            value = it,
                        )
                    }
                    infoData.transcodeBitrate?.let {
                        PlaybackInfoRow(
                            label =
                                stringResource(id = PlayerR.string.playback_info_transcode_bitrate),
                            value = it,
                        )
                    }
                    if (infoData.transcodeReasons.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
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
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                    if (
                        infoData.transcodeProgressRatio != null ||
                            !infoData.transcodeProgressText.isNullOrBlank()
                    ) {
                        ConversionProgressRow(
                            ratio = infoData.transcodeProgressRatio ?: 0f,
                            text = infoData.transcodeProgressText.orEmpty(),
                        )
                    }
                    if (!infoData.transcodeSpeed.isNullOrBlank()) {
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
                                        stringResource(
                                            id = PlayerR.string.playback_info_transcode_speed_graph
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium,
                                )

                                Text(
                                    text = infoData.transcodeSpeed.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            SimpleStatGraph(
                                data = infoData.transcodeSpeedHistory,
                                lineColor = MaterialTheme.colorScheme.primary,
                                unit = "fps",
                            )
                        }
                    }
                }
            }
        }

        // 3. Original Content Info
        item {
            PlaybackInfoSectionCard(
                title = stringResource(id = PlayerR.string.playback_info_original_section)
            ) {
                infoData.container?.let {
                    PlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_container),
                        value = it,
                    )
                }
                infoData.sizeText?.let {
                    PlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_size),
                        value = it,
                    )
                }
                infoData.bitrateText?.let {
                    PlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_bitrate),
                        value = it,
                    )
                }
                if (infoData.hasVideoInfo) {
                    PlaybackInfoSubSection(
                        title = stringResource(id = PlayerR.string.playback_info_video_section)
                    ) {
                        infoData.videoCodec?.let {
                            PlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_codec),
                                value = it,
                            )
                        }
                        infoData.videoProfile?.let {
                            PlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_profile),
                                value = it,
                            )
                        }
                        infoData.videoFrameRate?.let {
                            PlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_framerate),
                                value = it,
                            )
                        }
                        infoData.videoBitDepth?.let {
                            PlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_bit_depth),
                                value = it,
                            )
                        }
                        infoData.dynamicRange?.let {
                            PlaybackInfoRow(
                                label =
                                    stringResource(id = PlayerR.string.playback_info_dynamic_range),
                                value = it,
                            )
                        }
                    }
                }
                if (infoData.hasAudioInfo) {
                    PlaybackInfoSubSection(
                        title = stringResource(id = PlayerR.string.playback_info_audio_section)
                    ) {
                        infoData.audioCodec?.let {
                            PlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_codec),
                                value = it,
                            )
                        }
                        infoData.audioChannels?.let {
                            PlaybackInfoRow(
                                label = stringResource(id = PlayerR.string.playback_info_channels),
                                value = it,
                            )
                        }
                        infoData.audioSampleRate?.let {
                            PlaybackInfoRow(
                                label =
                                    stringResource(id = PlayerR.string.playback_info_sample_rate),
                                value = it,
                            )
                        }
                    }
                }
            }
        }

        // 4. Nerd Stats
        item {
            PlaybackInfoSectionCard(
                title = stringResource(id = PlayerR.string.playback_info_nerd_stats_section)
            ) {
                infoData.playbackStatus?.let {
                    PlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_playback_status),
                        value = it,
                    )
                }
                infoData.audioOutput?.let {
                    PlaybackInfoRow(
                        label = stringResource(id = PlayerR.string.playback_info_audio_output),
                        value = it,
                    )
                }
                PlaybackInfoRow(
                    label = stringResource(id = PlayerR.string.playback_info_dropped_frames),
                    value = infoData.droppedFrames,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text =
                            stringResource(id = PlayerR.string.playback_info_buffer_health_graph),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium,
                    )
                    SimpleStatGraph(
                        data = infoData.bufferHealthHistory,
                        lineColor = MaterialTheme.colorScheme.tertiary,
                        unit = "s",
                    )
                }
            }
        }
    }
}

/**
 * Container card for grouping playback information rows under a section header.
 *
 * @param title Section header title.
 * @param content Composable slot containing the section rows.
 */
@Composable
private fun PlaybackInfoSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CentralShapes.GroupSingle,
        color = PlayerCardBackground,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
 * Container card for nested subsection details within a playback info section.
 *
 * @param title Subsection header title.
 * @param content Composable slot containing the subsection rows.
 */
@Composable
private fun PlaybackInfoSubSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
 * Key-value statistic row for playback info display.
 *
 * @param label Description of the statistic field.
 * @param value Formatted string value of the statistic field.
 */
@Composable
private fun PlaybackInfoRow(
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
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.55f),
        )
    }
}

/**
 * Progress bar row showing transcode conversion progress.
 *
 * @param ratio Transcode conversion progress ratio between 0 and 1.
 * @param text Formatted conversion progress text label.
 */
@Composable
private fun ConversionProgressRow(
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
 * Minimalist line and area graph for displaying statistic history over time.
 *
 * @param data List of historical stat values.
 * @param modifier Optional [Modifier] applied to the graph container.
 * @param lineColor Line and gradient fill color for the graph.
 * @param unit Measurement unit appended to value labels.
 */
@Composable
private fun SimpleStatGraph(
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
                    .clip(
                        RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp,
                        )
                    )
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

@Preview(showBackground = true, backgroundColor = 0xFF141414, widthDp = 400, heightDp = 600)
@Composable
private fun PlaybackInfoViewPreview() {
    FindroidTheme {
        Surface(color = Color(0xFF141414)) {
            Box(modifier = Modifier.padding(16.dp)) {
                PlaybackInfoView(
                    infoData =
                        PlaybackInfoData(
                            playerBackend = "ExoPlayer (Transcode)",
                            streamType = "HLS (https)",
                            playerResolution = "1920x106",
                            droppedFrames = "1 / 0",
                            isTranscoding = true,
                            transcodeVideoCodec = "H264",
                            transcodeAudioCodec = "AAC",
                            transcodeResolution = "1920x1080",
                            transcodeProgressRatio = 0.3f,
                            transcodeProgressText = "30.0%",
                            transcodeSpeed = "1.04×",
                            transcodeSpeedHistory = listOf(24f, 25f, 25.5f, 24.8f, 25.2f),
                            transcodeReasons =
                                listOf(
                                    "Video codec not supported",
                                    "Audio codec not supported",
                                    "Audio channels not supported",
                                ),
                            container = "mkv",
                            sizeText = "5.1 GiB",
                            bitrateText = "4.3 Mbps",
                            videoCodec = "HEVC",
                            videoProfile = "Main",
                            videoFrameRate = "23.98 fps",
                            videoBitDepth = "8 Bit",
                            dynamicRange = "SDR",
                            audioCodec = "OPUS",
                            audioChannels = "2 Ch",
                            audioSampleRate = "48000 Hz",
                            bufferHealth = "15.2s",
                            bufferHealthHistory = listOf(10f, 12f, 13.5f, 15.2f),
                            audioOutput = "AudioTrack",
                        )
                )
            }
        }
    }
}
