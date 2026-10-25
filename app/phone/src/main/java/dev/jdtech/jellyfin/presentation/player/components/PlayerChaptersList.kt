package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.components.ChapterCard
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Chapters navigation list with thumbnail previews and segment badges for phone bottom sheet.
 *
 * @param viewModel Active [PlayerViewModel].
 */
@Composable
internal fun PhoneChaptersList(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val chapters = uiState.currentChapters
    val trickplay = uiState.currentTrickplay
    var currentPosition by remember { mutableLongStateOf(viewModel.player.currentPosition) }
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel.player) {
        while (true) {
            currentPosition = viewModel.player.currentPosition
            delay(400.milliseconds)
        }
    }

    DisposableEffect(viewModel.player) {
        val listener =
            object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    currentPosition = newPosition.positionMs
                }
            }
        viewModel.player.addListener(listener)
        onDispose { viewModel.player.removeListener(listener) }
    }

    val currentChapterIndex =
        remember(currentPosition, chapters) {
            chapters.indexOfLast { it.startPosition <= currentPosition }
        }

    LaunchedEffect(currentChapterIndex) {
        if (currentChapterIndex >= 0) {
            listState.animateScrollToItem(currentChapterIndex)
        }
    }

    LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(chapters) { index, chapter ->
            val isCurrent = index == currentChapterIndex
            val nextChapterStart = chapters.getOrNull(index + 1)?.startPosition ?: Long.MAX_VALUE
            ChapterCard(
                chapter = chapter,
                index = index,
                isCurrent = isCurrent,
                nextChapterStart = nextChapterStart,
                segments = uiState.segments,
                durationMs = viewModel.player.duration.coerceAtLeast(0L),
                trickplay = trickplay,
                onClick = {
                    currentPosition = chapter.startPosition
                    viewModel.player.seekTo(chapter.startPosition)
                },
            )
        }
    }
}

/**
 * Chapters navigation list with thumbnail previews and segment badges for tablet side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 */
@Composable
internal fun TabletChaptersList(viewModel: PlayerViewModel) {
    PhoneChaptersList(viewModel = viewModel)
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E1E)
@Composable
private fun PhoneChaptersListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChapterCard(
                chapter = PlayerChapter(name = "Chapter 1: Arrival", startPosition = 0L),
                index = 0,
                isCurrent = true,
                nextChapterStart = 180_000L,
                segments = emptyList(),
                durationMs = 2_400_000L,
                trickplay = null,
                onClick = {},
            )
            ChapterCard(
                chapter = PlayerChapter(name = "Chapter 2: Discovery", startPosition = 180_000L),
                index = 1,
                isCurrent = false,
                nextChapterStart = 600_000L,
                segments = emptyList(),
                durationMs = 2_400_000L,
                trickplay = null,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141416, widthDp = 360)
@Composable
private fun TabletChaptersListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChapterCard(
                chapter = PlayerChapter(name = "Intro", startPosition = 0L),
                index = 0,
                isCurrent = true,
                nextChapterStart = 120_000L,
                segments = emptyList(),
                durationMs = 3_600_000L,
                trickplay = null,
                onClick = {},
            )
            ChapterCard(
                chapter =
                    PlayerChapter(name = "Prologue - The Beginning", startPosition = 120_000L),
                index = 1,
                isCurrent = false,
                nextChapterStart = 600_000L,
                segments = emptyList(),
                durationMs = 3_600_000L,
                trickplay = null,
                onClick = {},
            )
        }
    }
}
