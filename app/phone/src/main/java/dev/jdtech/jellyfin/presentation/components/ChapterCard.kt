package dev.jdtech.jellyfin.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.core.presentation.theme.PlayerCardBackground
import dev.jdtech.jellyfin.core.presentation.theme.PlayerCardCurrent
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.core.domain.models.intervalMs
import dev.jdtech.jellyfin.player.core.presentation.findChapterSegment
import dev.jdtech.jellyfin.player.core.presentation.findChapterSegmentType
import dev.jdtech.jellyfin.player.core.presentation.formatDuration
import dev.jdtech.jellyfin.player.core.presentation.getSegmentColor
import dev.jdtech.jellyfin.player.core.presentation.labelRes
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

private val ChapterThumbShape = CentralShapes.SmallCard

/**
 * Shared chapter item card with preview thumbnail, segment border tint, segment badge, and current
 * playback selection indicator.
 *
 * @param chapter Chapter model containing name and start position.
 * @param index Chapter index in media track.
 * @param isCurrent Whether chapter is currently active.
 * @param onClick Callback when chapter card is tapped.
 * @param modifier Optional modifier applied to row. Defaults to [Modifier].
 * @param nextChapterStart Next chapter start timestamp in Ms. Defaults to [Long.MAX_VALUE].
 * @param segments List of media segments. Defaults to `emptyList()`.
 * @param durationMs Total media duration in Ms. Defaults to `0L`.
 * @param trickplay Trickplay model for thumbnail extraction. Defaults to `null`.
 */
@Composable
fun ChapterCard(
    chapter: PlayerChapter,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    nextChapterStart: Long = Long.MAX_VALUE,
    segments: List<FindroidSegment> = emptyList(),
    durationMs: Long = 0L,
    trickplay: Trickplay? = null,
) {
    val startTimeMs = chapter.startPosition
    val formattedTime = formatDuration(startTimeMs)

    val matchedSegment =
        findChapterSegment(
            chapter = chapter,
            nextChapterStart = nextChapterStart,
            segments = segments,
            durationMs = durationMs.coerceAtLeast(0L),
        )
    val segmentType =
        matchedSegment?.type
            ?: findChapterSegmentType(
                chapter = chapter,
                nextChapterStart = nextChapterStart,
                segments = segments,
                durationMs = durationMs.coerceAtLeast(0L),
            )

    val segmentColor =
        when (val matchedColor = matchedSegment?.color) {
            0L -> getSegmentColor(segmentType)
            null -> getSegmentColor(segmentType)
            else -> Color(matchedColor)
        }
    val segmentLabel = segmentType?.labelRes?.let { stringResource(it) }

    val chapterTitle =
        chapter.name?.takeIf { it.isNotBlank() }
            ?: stringResource(id = PlayerR.string.chapter_title, index + 1)

    var isImageError by remember(chapter.imageUri) { mutableStateOf(false) }

    val trickplayBitmap =
        remember(trickplay, startTimeMs) {
            if (trickplay != null && trickplay.images.isNotEmpty()) {
                val intervalMs = trickplay.intervalMs
                val imgIndex =
                    if (intervalMs > 0) {
                        (startTimeMs / intervalMs).toInt().coerceIn(0, trickplay.images.size - 1)
                    } else 0
                trickplay.images.getOrNull(imgIndex)
            } else null
        }

    val imageAspectRatio =
        remember(trickplayBitmap) {
            if (trickplayBitmap != null && trickplayBitmap.height > 0) {
                (trickplayBitmap.width.toFloat() / trickplayBitmap.height.toFloat()).coerceIn(
                    4f / 3f,
                    2.4f,
                )
            } else {
                16f / 9f
            }
        }

    val thumbBorder =
        if (segmentColor != null) {
            BorderStroke(1.5.dp, segmentColor)
        } else {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CentralShapes.GroupSingle,
        color = if (isCurrent) PlayerCardCurrent else PlayerCardBackground,
        border =
            if (isCurrent) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            else BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        onClick = onClick,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Chapter Thumbnail with Dynamic Aspect Ratio and Segment Border
            val hasServerImage = !chapter.imageUri.isNullOrBlank() && !isImageError
            Box(
                modifier =
                    Modifier.height(42.dp)
                        .aspectRatio(if (hasServerImage) 16f / 9f else imageAspectRatio)
                        .clip(ChapterThumbShape)
                        .border(thumbBorder, ChapterThumbShape)
                        .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (hasServerImage) {
                    coil3.compose.AsyncImage(
                        model = chapter.imageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        onError = { isImageError = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (trickplayBitmap != null) {
                    Image(
                        bitmap = trickplayBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = chapterTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (segmentColor != null && segmentLabel != null) {
                        Box(
                            modifier =
                                Modifier.clip(CentralShapes.Tiny)
                                    .background(segmentColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = segmentLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = segmentColor,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChapterCardPreview() {
    FindroidTheme {
        ChapterCard(
            chapter =
                PlayerChapter(
                    name = "Chapter 1",
                    startPosition = 0L,
                ),
            index = 0,
            isCurrent = true,
            onClick = {},
        )
    }
}
