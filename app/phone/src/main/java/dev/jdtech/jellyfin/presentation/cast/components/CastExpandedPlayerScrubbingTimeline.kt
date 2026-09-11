package dev.jdtech.jellyfin.presentation.cast.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.player.cast.models.CastPlaybackStatus
import dev.jdtech.jellyfin.player.core.R
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.core.domain.utils.TimeUtils
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastScrubbingTimeline(
    playerStatus: CastPlaybackStatus,
    currentProgress: Float,
    duration: Float,
    chapters: List<PlayerChapter>,
    onScrubStart: (Float) -> Unit,
    onScrubStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressContentDescription = stringResource(R.string.player_controls_progress)
    val safeDuration = if (duration.isFinite() && duration > 0f) duration else 0f
    val safeProgress =
        if (currentProgress.isFinite() && currentProgress > 0f) {
            currentProgress.coerceIn(0f, if (safeDuration > 0f) safeDuration else 1f)
        } else {
            0f
        }
    val sliderMax = if (safeDuration > 0f) safeDuration else 1f

    val chapterFractions =
        remember(chapters, safeDuration) {
            if (safeDuration > 0f) {
                chapters.mapNotNull { chapter ->
                    val fraction = chapter.startPosition.toFloat() / safeDuration
                    if (fraction in 0.01f..0.99f) fraction else null
                }
            } else {
                emptyList()
            }
        }

    val currentSeconds = remember(safeProgress) { (safeProgress / 1000).toLong() }
    val formattedCurrentProgress =
        remember(currentSeconds) { TimeUtils.formatTime(safeProgress.toLong()) }
    val durationSeconds = remember(safeDuration) { (safeDuration / 1000).toLong() }
    val formattedDuration =
        remember(durationSeconds) { TimeUtils.formatTime(safeDuration.toLong()) }

    Column(modifier = modifier.padding(horizontal = 24.dp)) {
        if (playerStatus == CastPlaybackStatus.BUFFERING) {
            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                LinearProgressIndicator(
                    modifier =
                        Modifier.fillMaxWidth().height(16.dp).semantics {
                            contentDescription = progressContentDescription
                        }
                )
            }
        } else {
            val stopIndicatorColor = MaterialTheme.colorScheme.surfaceVariant
            Slider(
                value = safeProgress,
                onValueChange = onScrubStart,
                onValueChangeFinished = onScrubStop,
                valueRange = 0f..sliderMax,
                modifier =
                    Modifier.fillMaxWidth().semantics {
                        contentDescription = progressContentDescription
                    },
                enabled = safeDuration > 0f,
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        drawStopIndicator = {
                            chapterFractions.forEach { fraction ->
                                drawCircle(
                                    color = stopIndicatorColor,
                                    radius = 2.dp.toPx(),
                                    center = Offset(size.width * fraction, size.height / 2),
                                )
                            }
                        },
                    )
                },
            )
        }

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .alpha(if (safeDuration > 0f) 1f else 0f),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formattedCurrentProgress,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = formattedDuration,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun TrickplayThumbnail(
    trickplay: Trickplay,
    scrubPosition: Float,
    modifier: Modifier = Modifier,
) {
    if (trickplay.images.isEmpty() || trickplay.interval <= 0) return

    val safePosition = if (scrubPosition.isFinite() && scrubPosition > 0f) scrubPosition else 0f
    val index = (safePosition / trickplay.interval).toInt().coerceIn(0, trickplay.images.size - 1)
    val image = trickplay.images.getOrNull(index) ?: return
    val imageBitmap = remember(image) { image.asImageBitmap() }

    Image(
        bitmap = imageBitmap,
        contentDescription = stringResource(R.string.player_trickplay),
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

@Preview(showBackground = true)
@Composable
private fun CastScrubbingTimelinePreview() {
    FindroidTheme {
        CastScrubbingTimeline(
            playerStatus = CastPlaybackStatus.PLAYING,
            currentProgress = 120000f,
            duration = 600000f,
            chapters =
                listOf(
                    PlayerChapter(0L, "Chapter 1"),
                    PlayerChapter(300000L, "Chapter 2"),
                ),
            onScrubStart = {},
            onScrubStop = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CastScrubbingTimelineBufferingPreview() {
    FindroidTheme {
        CastScrubbingTimeline(
            playerStatus = CastPlaybackStatus.BUFFERING,
            currentProgress = 120000f,
            duration = 600000f,
            chapters = emptyList(),
            onScrubStart = {},
            onScrubStop = {},
        )
    }
}
