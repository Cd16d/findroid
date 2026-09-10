package dev.jdtech.jellyfin.presentation.download.components

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import dev.jdtech.jellyfin.core.presentation.downloader.formatStableEta
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.diskSize
import dev.jdtech.jellyfin.models.formatDuration
import dev.jdtech.jellyfin.presentation.download.models.DownloadCardActions
import dev.jdtech.jellyfin.presentation.download.models.DownloadEpisodeTileState
import dev.jdtech.jellyfin.presentation.download.models.DownloadStatus
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.ItemPoster
import dev.jdtech.jellyfin.presentation.film.components.PlayedBadge
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadEpisodeTile(
    episode: FindroidEpisode,
    state: DownloadEpisodeTileState,
    modifier: Modifier = Modifier,
    actions: DownloadCardActions = DownloadCardActions(),
) {
    val context = LocalContext.current
    val actualSizeBytes = remember(state.sizeBytes, episode) {
        if (state.sizeBytes > 0L) state.sizeBytes
        else episode.diskSize().takeIf { it > 0 } ?: (episode.sources.maxOfOrNull { it.size } ?: 0L)
    }
    val sizeFormatted = remember(actualSizeBytes, context) {
        if (actualSizeBytes > 0L) Formatter.formatFileSize(context, actualSizeBytes) else ""
    }
    val downloadedSizeFormatted = remember(state.downloadedSizeBytes, context) {
        if (state.downloadedSizeBytes > 0L) Formatter.formatFileSize(context, state.downloadedSizeBytes) else ""
    }
    val speedFormatted = remember(state.downloadSpeedBytesPerSec, context) {
        if (state.downloadSpeedBytesPerSec > 0L) "${Formatter.formatFileSize(context, state.downloadSpeedBytesPerSec)}/s" else ""
    }
    val durationTicks = if (state.durationTicks > 0L) state.durationTicks else episode.runtimeTicks
    val durationFormatted = remember(durationTicks) {
        formatDuration(durationTicks)
    }
    val etaFormatted = remember(state.etaSeconds) {
        state.etaSeconds?.let { formatStableEta(it) } ?: ""
    }

    val epNumber = episode.indexNumber
    val titleText = "E$epNumber • ${episode.name.ifEmpty { "Episode $epNumber" }}"
    val cardShape = RoundedCornerShape(16.dp)
    val cardHeight = 76.dp

    DownloadSwipeToDismissBox(
        itemId = episode.id.toString(),
        title = titleText,
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
                        if (state.isSelected) Modifier.border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary,
                            cardShape
                        )
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = actions.onClick,
                        onLongClick = actions.onLongClick,
                    ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = state.isSelectionMode) {
                    Row {
                        Checkbox(
                            checked = state.isSelected,
                            onCheckedChange = { actions.onClick() },
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

                // Poster thumbnail (height 56 dp, width adapts to poster aspect ratio 16:9)
                Box(
                    modifier =
                        Modifier
                            .height(56.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                ) {
                    ItemPoster(
                        item = episode,
                        direction = Direction.HORIZONTAL,
                        modifier = Modifier.fillMaxHeight(),
                    )

                    if (state.status == DownloadStatus.DOWNLOADED) {
                        if (episode.played) {
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
                                        .height(3.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Center: Code, Meta, Title, Download Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val percentStr = "${(state.downloadProgress * 100).toInt()}%"
                    val percentText =
                        if (state.isPaused) "$percentStr • ${stringResource(CoreR.string.paused)}" else percentStr
                    val etaText =
                        if (etaFormatted.isNotEmpty() && !state.isPaused) etaFormatted else ""

                    val metaRow = when (state.status) {
                        DownloadStatus.PENDING -> {
                            buildString {
                                append("E$epNumber")
                                append(" • ")
                                append(
                                    if (state.isPaused) stringResource(CoreR.string.paused) else stringResource(
                                        CoreR.string.pending_in_queue
                                    )
                                )
                                if (sizeFormatted.isNotEmpty()) {
                                    append(" • ")
                                    append(sizeFormatted)
                                }
                            }
                        }

                        DownloadStatus.CONVERTING -> {
                            buildString {
                                append("E$epNumber")
                                append(" • ")
                                append(
                                    if (state.isPaused) stringResource(CoreR.string.paused) else stringResource(
                                        CoreR.string.converting
                                    )
                                )
                                if (sizeFormatted.isNotEmpty()) {
                                    append(" • ")
                                    append(sizeFormatted)
                                }
                            }
                        }

                        DownloadStatus.FAILED -> {
                            "E$epNumber • ${stringResource(CoreR.string.downloading_error)}"
                        }

                        DownloadStatus.DOWNLOADED -> {
                            buildString {
                                append("E$epNumber")
                                if (durationFormatted.isNotEmpty()) {
                                    append(" • ")
                                    append(durationFormatted)
                                }
                                if (sizeFormatted.isNotEmpty()) {
                                    append(" • ")
                                    append(sizeFormatted)
                                }
                            }
                        }

                        DownloadStatus.DOWNLOADING -> {
                            buildString {
                                append("E$epNumber")
                                if (percentText.isNotEmpty()) {
                                    append(" • ")
                                    append(percentText)
                                }
                                if (etaText.isNotEmpty()) {
                                    append(" • ")
                                    append(etaText)
                                }
                            }
                        }

                        DownloadStatus.TRANSFERRING -> {
                            buildString {
                                append("E$epNumber")
                                if (percentText.isNotEmpty()) {
                                    append(" • ")
                                    append(percentText)
                                }
                                append(" • ")
                                append(stringResource(CoreR.string.moving_storage_short))
                            }
                        }
                    }

                    val metaColor = when (state.status) {
                        DownloadStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                        DownloadStatus.CONVERTING -> MaterialTheme.colorScheme.secondary
                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Text(
                        text = metaRow,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = metaColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = episode.name.ifEmpty { "Episode $epNumber" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    val downloadInfo = buildString {
                        val sizePart =
                            if (downloadedSizeFormatted.isNotEmpty() && sizeFormatted.isNotEmpty()) {
                                "$downloadedSizeFormatted / $sizeFormatted"
                            } else sizeFormatted.ifEmpty { downloadedSizeFormatted }

                        if (sizePart.isNotEmpty()) {
                            append(sizePart)
                        }

                        val speedStr = if (state.isPaused) {
                            stringResource(CoreR.string.paused)
                        } else {
                            speedFormatted
                        }

                        if (speedStr.isNotEmpty()) {
                            if (isNotEmpty()) append(" • ")
                            append(speedStr)
                        }
                    }

                    if (state.displayExtraInfo && (state.status == DownloadStatus.DOWNLOADING || state.status == DownloadStatus.TRANSFERRING)) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = downloadInfo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Right action slot
                if (!state.isSelectionMode) {
                    when (state.status) {
                        DownloadStatus.DOWNLOADED -> {}

                        DownloadStatus.TRANSFERRING -> {
                            val tintColor = MaterialTheme.colorScheme.primary
                            val animatedProgress by animateFloatAsState(
                                targetValue = state.downloadProgress,
                                animationSpec = tween(400, easing = FastOutSlowInEasing),
                                label = "epTransferProgress",
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(36.dp),
                            ) {
                                CircularProgressIndicator(
                                    progress = { animatedProgress },
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
                                label = "epDlProgress",
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
                                        modifier = Modifier.size(20.dp)
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
                                            if (state.isPaused) CoreR.drawable.ic_play else CoreR.drawable.ic_hourglass
                                        ),
                                        contentDescription = stringResource(
                                            if (state.isPaused) CoreR.string.resume else CoreR.string.pending_in_queue
                                        ),
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        DownloadStatus.FAILED -> {
                            IconButton(
                                onClick = actions.onRetryDownload,
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                    contentDescription = stringResource(CoreR.string.retry),
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

@Preview(name = "Downloaded State", showBackground = true)
@Composable
private fun DownloadEpisodeTileDownloadedPreview() {
    FindroidTheme {
        DownloadEpisodeTile(
            episode = dummyEpisode,
            state = DownloadEpisodeTileState(
                status = DownloadStatus.DOWNLOADED,
                playbackProgress = 0.3f,
            ),
        )
    }
}

@Preview(name = "Downloading State", showBackground = true)
@Composable
private fun DownloadEpisodeTileDownloadingPreview() {
    FindroidTheme {
        DownloadEpisodeTile(
            episode = dummyEpisode,
            state = DownloadEpisodeTileState(
                status = DownloadStatus.DOWNLOADING,
                downloadProgress = 0.52f,
            ),
        )
    }
}

@Preview(name = "Downloading State Extra Info", showBackground = true)
@Composable
private fun DownloadEpisodeTileDownloadingExtraInfoPreview() {
    FindroidTheme {
        DownloadEpisodeTile(
            episode = dummyEpisode,
            state = DownloadEpisodeTileState(
                status = DownloadStatus.DOWNLOADING,
                downloadProgress = 0.52f,
                displayExtraInfo = true,
            ),
        )
    }
}

@Preview(name = "Pending State", showBackground = true)
@Composable
private fun DownloadEpisodeTilePendingPreview() {
    FindroidTheme {
        DownloadEpisodeTile(
            episode = dummyEpisode,
            state = DownloadEpisodeTileState(
                status = DownloadStatus.PENDING,
            ),
        )
    }
}

@Preview(name = "Failed State", showBackground = true)
@Composable
private fun DownloadEpisodeTileFailedPreview() {
    FindroidTheme {
        DownloadEpisodeTile(
            episode = dummyEpisode,
            state = DownloadEpisodeTileState(
                status = DownloadStatus.FAILED,
            ),
        )
    }
}

@Preview(name = "Selection mode", showBackground = true)
@Composable
private fun DownloadEpisodeTileSelectionPreview() {
    FindroidTheme {
        DownloadEpisodeTile(
            episode = dummyEpisode,
            state = DownloadEpisodeTileState(
                status = DownloadStatus.DOWNLOADED,
                downloadProgress = 0.52f,
                isSelectionMode = true,
            ),
        )
    }
}

@Preview(name = "Selected", showBackground = true)
@Composable
private fun DownloadEpisodeTileSelectedPreview() {
    FindroidTheme {
        DownloadEpisodeTile(
            episode = dummyEpisode,
            state = DownloadEpisodeTileState(
                status = DownloadStatus.DOWNLOADED,
                downloadProgress = 0.52f,
                playbackProgress = 0.5f,
                isSelected = true,
                isSelectionMode = true,
            ),
        )
    }
}

@Preview(name = "Eliminating State", showBackground = true)
@Composable
private fun DownloadEpisodeTileEliminatingPreview() {
    FindroidTheme {
        DownloadEpisodeTile(
            episode = dummyEpisode,
            state = DownloadEpisodeTileState(
                status = DownloadStatus.DOWNLOADED,
                pendingDeletionSeconds = 5,
            ),
        )
    }
}
