package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.PlayerTvSidePanelContainer
import dev.jdtech.jellyfin.core.presentation.theme.PlayerTvSidePanelUnselected
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.core.domain.models.intervalMs
import dev.jdtech.jellyfin.player.core.domain.utils.ChapterUtils
import dev.jdtech.jellyfin.player.core.presentation.findChapterSegment
import dev.jdtech.jellyfin.player.core.presentation.findChapterSegmentType
import dev.jdtech.jellyfin.player.core.presentation.getSegmentColor
import dev.jdtech.jellyfin.player.core.presentation.labelRes
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Chapter selection list for TV player side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param initialFocusRequester [FocusRequester] for setting focus on the active chapter.
 * @param onClose Callback to close the side panel after seeking to a selected chapter.
 */
@Composable
internal fun ChapterList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
    onClose: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val chapters = uiState.currentChapters
    val currentPositionMs = viewModel.player.currentPosition
    val currentChapterIndex =
        remember(chapters, currentPositionMs) {
            ChapterUtils.getCurrentChapterIndex(chapters, currentPositionMs) ?: 0
        }
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = (currentChapterIndex - 1).coerceAtLeast(0)
        )

    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex in chapters.indices) {
            listState.scrollToItem(currentChapterIndex)
            repeat(6) {
                try {
                    initialFocusRequester.requestFocus()
                    return@LaunchedEffect
                } catch (_: Exception) {
                    delay(50.milliseconds)
                }
            }
        }
    }

    if (chapters.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(id = PlayerR.string.no_chapters),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    } else {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(
                items = chapters,
                key = { index, chapter -> "${chapter.startPosition}_$index" },
            ) { index, chapter ->
                val isCurrent = index == currentChapterIndex
                val nextChapterStart =
                    chapters.getOrNull(index + 1)?.startPosition ?: Long.MAX_VALUE
                val matchedSegment =
                    findChapterSegment(
                        chapter = chapter,
                        nextChapterStart = nextChapterStart,
                        segments = uiState.segments,
                        durationMs = viewModel.player.duration.coerceAtLeast(0L),
                    )
                val segmentType =
                    matchedSegment?.type
                        ?: findChapterSegmentType(
                            chapter = chapter,
                            nextChapterStart = nextChapterStart,
                            segments = uiState.segments,
                            durationMs = viewModel.player.duration.coerceAtLeast(0L),
                        )
                val segmentColor =
                    when (val matchedColor = matchedSegment?.color) {
                        0L -> getSegmentColor(segmentType)
                        null -> getSegmentColor(segmentType)
                        else -> Color(matchedColor)
                    }

                ChapterItem(
                    chapter = chapter,
                    chapterIndex = index,
                    isCurrent = isCurrent,
                    trickplay = uiState.currentTrickplay,
                    segmentType = segmentType,
                    customSegmentColor = segmentColor,
                    onClick = {
                        viewModel.player.seekTo(chapter.startPosition)
                        onClose()
                    },
                    modifier =
                        if (index == currentChapterIndex) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        },
                )
            }
        }
    }
}

/**
 * Focusable chapter item card with thumbnail preview and segment indicator for TV.
 *
 * @param chapter Chapter metadata.
 * @param chapterIndex Zero-based chapter index.
 * @param isCurrent Whether this chapter is currently playing.
 * @param onClick Callback when selecting this chapter.
 * @param trickplay Optional trickplay thumbnail dataset.
 * @param modifier Optional [Modifier] applied to the item surface.
 * @param segmentType Optional chapter segment type (e.g. Intro, Outro).
 * @param customSegmentColor Optional custom tint color for the segment.
 */
@Composable
internal fun ChapterItem(
    chapter: PlayerChapter,
    chapterIndex: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
    trickplay: Trickplay?,
    modifier: Modifier = Modifier,
    segmentType: FindroidSegmentType? = null,
    customSegmentColor: Color? = null,
) {
    val segmentLabel = segmentType?.labelRes?.let { stringResource(it) }
    val chapterName =
        chapter.name?.takeIf { it.isNotBlank() }
            ?: stringResource(id = PlayerR.string.chapter_title, chapterIndex + 1)
    val timestampString =
        chapter.startPosition.milliseconds.toComponents { h, m, s, _ ->
            if (h > 0) {
                "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            } else {
                "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            }
        }

    var isImageError by remember(chapter.imageUri) { mutableStateOf(false) }

    val trickplayBitmap =
        remember(trickplay, chapter.startPosition) {
            if (trickplay != null && trickplay.images.isNotEmpty()) {
                val intervalMs = trickplay.intervalMs
                if (intervalMs > 0) {
                    val idx =
                        (chapter.startPosition / intervalMs)
                            .toInt()
                            .coerceIn(0, trickplay.images.size - 1)
                    trickplay.images.getOrNull(idx)
                } else {
                    null
                }
            } else {
                null
            }
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

    val segmentColor = customSegmentColor ?: segmentType?.let { getSegmentColor(it) }

    val itemColors =
        ClickableSurfaceDefaults.colors(
            containerColor =
                if (isCurrent) {
                    segmentColor?.copy(alpha = 0.25f) ?: PlayerTvSidePanelUnselected
                } else {
                    segmentColor?.copy(alpha = 0.12f) ?: PlayerTvSidePanelContainer
                },
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black,
        )

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = PanelItemShape),
        colors = itemColors,
        scale = ClickableSurfaceScale.None,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Chapter thumbnail with Dynamic Aspect Ratio
            val hasServerImage = !chapter.imageUri.isNullOrBlank() && !isImageError
            Box(
                modifier =
                    Modifier.height(63.dp)
                        .aspectRatio(imageAspectRatio)
                        .clip(ChapterThumbShape)
                        .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (hasServerImage) {
                    AsyncImage(
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
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_play),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White.copy(alpha = 0.5f),
                    )
                }

                if (isCurrent) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(
                                    (segmentColor ?: MaterialTheme.colorScheme.primary).copy(
                                        alpha = 0.25f
                                    )
                                )
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Right: Chapter Name & Start Timestamp
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = chapterName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (segmentColor != null && segmentLabel != null) {
                        Box(
                            modifier =
                                Modifier.clip(RoundedCornerShape(4.dp))
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
                    text = timestampString,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Preview
@Composable
private fun ChapterListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChapterItem(
                chapter = PlayerChapter(name = "Intro", startPosition = 0L),
                chapterIndex = 0,
                isCurrent = true,
                onClick = {},
                trickplay = null,
            )
            ChapterItem(
                chapter = PlayerChapter(name = "Part 1", startPosition = 120_000L),
                chapterIndex = 1,
                isCurrent = false,
                onClick = {},
                trickplay = null,
            )
        }
    }
}

@Preview
@Composable
private fun ChapterItemPreview() {
    FindroidTheme {
        Box(modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp)) {
            ChapterItem(
                chapter = PlayerChapter(name = "Chapter 1: Arrival", startPosition = 300_000L),
                chapterIndex = 0,
                isCurrent = true,
                onClick = {},
                trickplay = null,
            )
        }
    }
}
