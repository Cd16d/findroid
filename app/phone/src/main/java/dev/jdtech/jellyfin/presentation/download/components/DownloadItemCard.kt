package dev.jdtech.jellyfin.presentation.download.components

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.download.models.DownloadCardActions
import dev.jdtech.jellyfin.presentation.download.models.DownloadItemCardState
import dev.jdtech.jellyfin.presentation.download.models.DownloadStatus
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.ItemPoster
import dev.jdtech.jellyfin.presentation.film.components.PlayedBadge
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadItemCard(
    item: FindroidItem,
    state: DownloadItemCardState,
    modifier: Modifier = Modifier,
    actions: DownloadCardActions = DownloadCardActions(),
) {
    val context = LocalContext.current
    val cardShape = RoundedCornerShape(20.dp)
    val cardHeight = 124.dp

    val title = state.title.ifEmpty { item.name }
    val sizeFormatted = remember(state.sizeBytes, context) {
        if (state.sizeBytes > 0L) Formatter.formatFileSize(context, state.sizeBytes) else ""
    }

    DownloadSwipeToDismissBox(
        itemId = item.id.toString(),
        title = title,
        isSelectionMode = state.isSelectionMode,
        pendingDeletionSeconds = state.pendingDeletionSeconds,
        onSwipeDelete = actions.onSwipeDelete,
        onUndoDelete = actions.onUndoDelete,
        cardShape = cardShape,
        cardHeight = cardHeight,
        modifier = modifier,
    ) {
        Card(
            shape = cardShape,
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        if (state.isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .clip(cardShape)
                    .then(
                        if (state.isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = actions.onClick,
                        onLongClick = actions.onLongClick,
                    ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = state.isSelectionMode) {
                    Row {
                        Checkbox(
                            checked = state.isSelected,
                            onCheckedChange = { actions.onClick() },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                // Poster thumbnail
                Box(
                    modifier =
                        Modifier
                            .size(width = 64.dp, height = 96.dp)
                            .clip(RoundedCornerShape(12.dp))
                ) {
                    ItemPoster(
                        item = item,
                        direction = Direction.VERTICAL,
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (state.status == DownloadStatus.DOWNLOADED) {
                        if (item.played) {
                            PlayedBadge(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                            )
                        } else if (state.playbackProgress in 0.02f..0.98f) {
                            LinearProgressIndicator(
                                progress = { state.playbackProgress },
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Center Details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (state.metadataText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.metadataText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (state.status == DownloadStatus.PENDING) FontWeight.Medium else FontWeight.Normal
                            ),
                            color = if (state.status == DownloadStatus.PENDING) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Chips Row
                    if (state.displayExtraInfo && sizeFormatted.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = sizeFormatted,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    )
                                },
                                colors =
                                    SuggestionChipDefaults.suggestionChipColors(
                                        containerColor =
                                            MaterialTheme.colorScheme.surfaceContainerHigh,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                border = null,
                                modifier = Modifier.height(24.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right Action Slot
                if (!state.isSelectionMode) {
                    when (state.status) {
                        DownloadStatus.DOWNLOADED -> {
                        }
                        DownloadStatus.TRANSFERRING -> {
                            val tintColor = MaterialTheme.colorScheme.primary
                            val animatedDownloadProgress by animateFloatAsState(
                                targetValue = state.downloadProgress,
                                animationSpec = tween(400, easing = FastOutSlowInEasing),
                                label = "itemTransferProgress",
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(36.dp),
                            ) {
                                CircularProgressIndicator(
                                    progress = { animatedDownloadProgress },
                                    modifier = Modifier.fillMaxSize(),
                                    strokeWidth = 3.dp,
                                    color = tintColor,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_hard_drive),
                                    contentDescription = stringResource(CoreR.string.move_storage),
                                    tint = tintColor,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        DownloadStatus.DOWNLOADING,
                        DownloadStatus.CONVERTING -> {
                            val tintColor =
                                if (state.status == DownloadStatus.CONVERTING) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary
                            val animatedDownloadProgress by animateFloatAsState(
                                targetValue = state.downloadProgress,
                                animationSpec = tween(400, easing = FastOutSlowInEasing),
                                label = "itemDlProgress",
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(36.dp),
                            ) {
                                if (state.status == DownloadStatus.CONVERTING && state.downloadProgress <= 0f) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.fillMaxSize(),
                                        strokeWidth = 3.dp,
                                        color = tintColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        progress = { animatedDownloadProgress },
                                        modifier = Modifier.fillMaxSize(),
                                        strokeWidth = 3.dp,
                                        color = tintColor,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = if (state.isPaused) actions.onResumeDownload else actions.onPauseDownload,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (state.isPaused) CoreR.drawable.ic_play else CoreR.drawable.ic_pause
                                        ),
                                        contentDescription = stringResource(
                                            if (state.isPaused) CoreR.string.resume else CoreR.string.pause
                                        ),
                                        tint = tintColor,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        DownloadStatus.PENDING -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(36.dp),
                            ) {
                                IconButton(
                                    onClick = if (state.isPaused) actions.onResumeDownload else actions.onPauseDownload,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (state.isPaused) CoreR.drawable.ic_play else CoreR.drawable.ic_hourglass,
                                        ),
                                        contentDescription = stringResource(
                                            if (state.isPaused) CoreR.string.resume else CoreR.string.pending_in_queue,
                                        ),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        DownloadStatus.FAILED -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.error, CircleShape),
                            ) {
                                IconButton(
                                    onClick = actions.onRetryDownload,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                        contentDescription = stringResource(CoreR.string.retry),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Downloaded State", showBackground = true)
@Composable
private fun DownloadItemCardDownloadedPreview() {
    FindroidTheme {
        DownloadItemCard(
            item = dummyMovie,
            state = DownloadItemCardState(
                title = "Inception",
                metadataText = "2010 • 2h 28m",
                status = DownloadStatus.DOWNLOADED,
                playbackProgress = 0.45f,
            ),
        )
    }
}

@Preview(name = "Downloading State", showBackground = true)
@Composable
private fun DownloadItemCardDownloadingPreview() {
    FindroidTheme {
        DownloadItemCard(
            item = dummyMovie,
            state = DownloadItemCardState(
                title = "Oppenheimer",
                metadataText = "45% • 3.2 MB/s • 1m 20s",
                status = DownloadStatus.DOWNLOADING,
                downloadProgress = 0.45f,
            ),
        )
    }
}

@Preview(name = "Pending State", showBackground = true)
@Composable
private fun DownloadItemCardPendingPreview() {
    FindroidTheme {
        DownloadItemCard(
            item = dummyMovie,
            state = DownloadItemCardState(
                title = "Interstellar",
                metadataText = "In coda",
                status = DownloadStatus.PENDING,
            ),
        )
    }
}

@Preview(name = "Selection Mode", showBackground = true)
@Composable
private fun DownloadItemCardSelectionPreview() {
    FindroidTheme {
        DownloadItemCard(
            item = dummyMovie,
            state = DownloadItemCardState(
                title = "Blade Runner 2049",
                metadataText = "2017 • 2h 44m",
                status = DownloadStatus.DOWNLOADED,
                isSelected = true,
                isSelectionMode = true,
            ),
        )
    }
}

@Preview(name = "Eliminating State", showBackground = true)
@Composable
private fun DownloadItemCardEliminatingPreview() {
    FindroidTheme {
        DownloadItemCard(
            item = dummyMovie,
            state = DownloadItemCardState(
                title = "Inception",
                metadataText = "2010 • 2h 28m",
                status = DownloadStatus.DOWNLOADED,
                playbackProgress = 0.45f,
                pendingDeletionSeconds = 5,
            ),
        )
    }
}

@Preview(name = "Swipe To Dismiss Background", showBackground = true)
@Composable
private fun DownloadItemCardSwipeDismissBackgroundPreview() {
    FindroidTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
        ) {
            DownloadSwipeDismissBackground(
                progress = 1f,
                isThresholdReached = true,
                shape = RoundedCornerShape(20.dp),
            )
        }
    }
}
