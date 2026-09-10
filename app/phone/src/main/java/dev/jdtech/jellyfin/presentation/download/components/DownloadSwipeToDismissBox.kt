package dev.jdtech.jellyfin.presentation.download.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.DeleteContentWhite
import dev.jdtech.jellyfin.core.presentation.theme.DeleteRed
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun DownloadSwipeDismissBackground(
    progress: Float,
    isThresholdReached: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
) {
    val targetScale =
        remember(isThresholdReached, progress) {
            if (isThresholdReached) {
                1.2f
            } else {
                (0.75f + (progress / 0.45f) * 0.25f).coerceIn(0.75f, 1.0f)
            }
        }
    val animatedTrashScale by
        animateFloatAsState(
            targetValue = targetScale,
            animationSpec =
                if (isThresholdReached) {
                    spring(
                        dampingRatio = Spring.DampingRatioHighBouncy,
                        stiffness = Spring.StiffnessMedium,
                    )
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessHigh,
                    )
                },
            label = "trashScale",
        )

    Box(
        modifier =
            modifier.fillMaxSize().clip(shape).background(DeleteRed).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_trash),
            contentDescription = stringResource(CoreR.string.delete),
            tint = DeleteContentWhite,
            modifier =
                Modifier.size(24.dp).graphicsLayer {
                    scaleX = animatedTrashScale
                    scaleY = animatedTrashScale
                },
        )
    }
}

@Composable
fun DownloadEliminatingCard(
    title: String,
    pendingDeletionSeconds: Int,
    onUndoDelete: () -> Unit,
    cardShape: Shape,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val initialSeconds = remember { pendingDeletionSeconds }
    val progress = remember { Animatable((initialSeconds / 5f).coerceIn(0f, 1f)) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec =
                tween(
                    durationMillis = (initialSeconds * 1000).coerceAtLeast(0),
                    easing = LinearEasing,
                ),
        )
    }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = DeleteRed),
        modifier = modifier.fillMaxWidth().height(cardHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_trash),
                    contentDescription = null,
                    tint = DeleteContentWhite,
                    modifier = Modifier.size(24.dp),
                )
                Column {
                    Text(
                        text = title,
                        style =
                            MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = DeleteContentWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            stringResource(
                                CoreR.string.deleting_in_seconds,
                                pendingDeletionSeconds,
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = DeleteContentWhite.copy(alpha = 0.85f),
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp),
            ) {
                CircularProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 3.dp,
                    color = DeleteContentWhite,
                    trackColor = DeleteContentWhite.copy(alpha = 0.25f),
                )
                IconButton(
                    onClick = onUndoDelete,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_undo),
                        contentDescription = stringResource(CoreR.string.undo),
                        tint = DeleteContentWhite,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSwipeToDismissBox(
    itemId: String,
    title: String,
    isSelectionMode: Boolean,
    pendingDeletionSeconds: Int?,
    onSwipeDelete: () -> Unit,
    onUndoDelete: () -> Unit,
    cardShape: Shape,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val dismissState =
        rememberSwipeToDismissBoxState(positionalThreshold = { distance -> distance * 0.45f })

    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) {
            if (!isSelectionMode && pendingDeletionSeconds == null) {
                onSwipeDelete()
            }
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    LaunchedEffect(pendingDeletionSeconds) {
        if (
            pendingDeletionSeconds != null &&
                dismissState.currentValue != SwipeToDismissBoxValue.Settled
        ) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    LaunchedEffect(itemId) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = !isSelectionMode && pendingDeletionSeconds == null,
        modifier = modifier.fillMaxWidth(),
        backgroundContent = {
            if (
                !isSelectionMode &&
                    dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val totalWidthPx = constraints.maxWidth.toFloat()
                    val linearProgress by
                        remember(totalWidthPx) {
                            derivedStateOf {
                                val currentOffset =
                                    try {
                                        dismissState.requireOffset()
                                    } catch (_: Exception) {
                                        0f
                                    }
                                if (totalWidthPx > 0f) {
                                    (-currentOffset / totalWidthPx).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            }
                        }

                    if (linearProgress > 0.01f) {
                        val isThresholdReached by remember {
                            derivedStateOf { linearProgress >= 0.45f }
                        }

                        LaunchedEffect(isThresholdReached) {
                            if (isThresholdReached && !hasTriggeredHaptic) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                hasTriggeredHaptic = true
                            } else if (!isThresholdReached) {
                                hasTriggeredHaptic = false
                            }
                        }

                        DownloadSwipeDismissBackground(
                            progress = linearProgress,
                            isThresholdReached = isThresholdReached,
                            shape = cardShape,
                        )
                    }
                }
            }
        },
        content = {
            if (pendingDeletionSeconds != null) {
                DownloadEliminatingCard(
                    title = title,
                    pendingDeletionSeconds = pendingDeletionSeconds,
                    onUndoDelete = onUndoDelete,
                    cardShape = cardShape,
                    cardHeight = cardHeight,
                )
            } else {
                content()
            }
        },
    )
}

@Preview(name = "Swipe To Dismiss Background")
@Composable
private fun DownloadEpisodeTileSwipeDismissBackgroundPreview() {
    FindroidTheme {
        Box(modifier = Modifier.fillMaxWidth().height(76.dp)) {
            DownloadSwipeDismissBackground(
                progress = 1f,
                isThresholdReached = true,
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

@Preview(name = "Swipe To Dismiss Background Start")
@Composable
private fun DownloadEpisodeTileSwipeDismissBackgroundStartPreview() {
    FindroidTheme {
        Box(modifier = Modifier.fillMaxWidth().height(76.dp)) {
            DownloadSwipeDismissBackground(
                progress = 0f,
                isThresholdReached = false,
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

@Preview(name = "Download Eliminating Card")
@Composable
private fun DownloadEliminatingCardPreview() {
    FindroidTheme {
        DownloadEliminatingCard(
            title = "S1:E1 - Pilot",
            pendingDeletionSeconds = 5,
            onUndoDelete = {},
            cardShape = RoundedCornerShape(16.dp),
            cardHeight = 76.dp,
        )
    }
}
