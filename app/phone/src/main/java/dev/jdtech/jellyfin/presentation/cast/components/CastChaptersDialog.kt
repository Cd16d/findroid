package dev.jdtech.jellyfin.presentation.cast.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.core.R
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.presentation.components.ChapterCard
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CastChaptersDialog(
    visible: Boolean,
    chapters: List<PlayerChapter>,
    currentPositionMs: Long,
    trickplay: Trickplay?,
    onChapterSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    segments: List<FindroidSegment> = emptyList(),
    durationMs: Long = 0L,
) {
    var offsetY by remember { mutableFloatStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = offsetY, label = "offset")

    LaunchedEffect(visible) {
        if (visible) {
            offsetY = 0f
        }
    }

    val listState = rememberLazyListState()

    val currentChapterIndex =
        remember(currentPositionMs, chapters) {
            chapters.indexOfLast { it.startPosition <= currentPositionMs }
        }

    LaunchedEffect(visible, currentChapterIndex) {
        if (visible && currentChapterIndex >= 0) {
            listState.animateScrollToItem(currentChapterIndex)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0f && !listState.canScrollBackward) {
                    offsetY += delta
                    return Offset(0f, delta)
                }
                if (delta < 0f) {
                    if (offsetY > 0f) {
                        val consumed = delta.coerceAtLeast(-offsetY)
                        offsetY += consumed
                        return Offset(0f, consumed)
                    }
                    if (!listState.canScrollForward) {
                        return Offset(0f, delta)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY > 150f) {
                    onDismiss()
                }
                offsetY = 0f
                return Velocity.Zero
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = true)
                        var isDragging = false
                        var dragY = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.isConsumed) break

                            if (change.changedToUp()) {
                                if (!isDragging) {
                                    onDismiss()
                                } else if (offsetY > 150f) {
                                    onDismiss()
                                }
                                offsetY = 0f
                                break
                            }

                            val deltaY = change.position.y - change.previousPosition.y
                            val deltaX = change.position.x - change.previousPosition.x
                            dragY += deltaY
                            if (
                                !isDragging &&
                                    (abs(dragY) > viewConfiguration.touchSlop ||
                                        abs(deltaX) > viewConfiguration.touchSlop)
                            ) {
                                isDragging = true
                            }

                            if (isDragging) {
                                offsetY = dragY.coerceAtLeast(0f)
                            }
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current
            val windowInfo = LocalWindowInfo.current
            val containerHeightDp = with(density) { windowInfo.containerSize.height.toDp() }

            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val screenMaxHeight =
                if (isLandscape) {
                    containerHeightDp
                } else {
                    containerHeightDp * 0.6f
                }

            Surface(
                modifier =
                    modifier
                        .padding(MaterialTheme.spacings.small)
                        .widthIn(max = 500.dp)
                        .then(
                            if (isLandscape) Modifier.fillMaxHeight()
                            else Modifier.heightIn(max = screenMaxHeight)
                        )
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .offset { IntOffset(0, animatedOffset.roundToInt()) }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break

                                    if (change.changedToUp()) {
                                        if (offsetY > 150f) {
                                            onDismiss()
                                        }
                                        offsetY = 0f
                                        break
                                    }

                                    if (!change.isConsumed) {
                                        val deltaY = change.position.y - change.previousPosition.y
                                        val deltaX = change.position.x - change.previousPosition.x

                                        if (abs(deltaY) > 0) {
                                            offsetY = (offsetY + deltaY).coerceAtLeast(0f)
                                            change.consume()
                                        } else if (abs(deltaX) > 0) {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.large,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier =
                        Modifier.nestedScroll(nestedScrollConnection)
                            .padding(MaterialTheme.spacings.medium)
                            .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier)
                ) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.CenterHorizontally)
                                .width(32.dp)
                                .height(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    CircleShape,
                                )
                    )

                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))

                    Text(
                        text = stringResource(R.string.chapters),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(8.dp),
                    )

                    Box(modifier = Modifier.weight(1f, fill = false)) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier.fillMaxWidth()
                                    .graphicsLayer(
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    )
                                    .drawWithContent {
                                        drawContent()
                                        val edgeHeight = 16.dp.toPx()

                                        if (listState.canScrollBackward) {
                                            drawRect(
                                                brush =
                                                    Brush.verticalGradient(
                                                        colors =
                                                            listOf(Color.Transparent, Color.Black),
                                                        startY = 0f,
                                                        endY = edgeHeight,
                                                    ),
                                                blendMode = BlendMode.DstIn,
                                            )
                                        }

                                        if (listState.canScrollForward) {
                                            drawRect(
                                                brush =
                                                    Brush.verticalGradient(
                                                        colors =
                                                            listOf(Color.Black, Color.Transparent),
                                                        startY = size.height - edgeHeight,
                                                        endY = size.height,
                                                    ),
                                                blendMode = BlendMode.DstIn,
                                            )
                                        }
                                    },
                        ) {
                            itemsIndexed(chapters) { index, chapter ->
                                val isCurrent = index == currentChapterIndex
                                val nextChapterStart =
                                    chapters.getOrNull(index + 1)?.startPosition ?: Long.MAX_VALUE
                                ChapterCard(
                                    chapter = chapter,
                                    index = index,
                                    isCurrent = isCurrent,
                                    nextChapterStart = nextChapterStart,
                                    segments = segments,
                                    durationMs = durationMs,
                                    trickplay = trickplay,
                                    onClick = {
                                        onChapterSelected(chapter.startPosition)
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(heightDp = 350)
@Composable
private fun CastChaptersDialogPreview() {
    FindroidTheme {
        CastChaptersDialog(
            visible = true,
            chapters =
                listOf(
                    PlayerChapter(0L, "Prologue"),
                    PlayerChapter(180000L, "Opening"),
                    PlayerChapter(300000L, "Episode 1"),
                ),
            currentPositionMs = 200000L,
            trickplay = null,
            onChapterSelected = {},
            onDismiss = {},
        )
    }
}
