package dev.jdtech.jellyfin.presentation.download

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.LocalCastPlayerHeight
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.film.presentation.downloads.SeasonEpisodeGroup
import dev.jdtech.jellyfin.film.presentation.downloads.ShowDownloadsAction
import dev.jdtech.jellyfin.film.presentation.downloads.ShowDownloadsState
import dev.jdtech.jellyfin.film.presentation.downloads.ShowDownloadsViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.diskSize
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.presentation.download.components.ConfirmDeleteDialog
import dev.jdtech.jellyfin.presentation.download.components.DownloadEpisodeTile
import dev.jdtech.jellyfin.presentation.download.components.FloatingSelectionToolbar
import dev.jdtech.jellyfin.presentation.download.components.StickySeasonHeader
import dev.jdtech.jellyfin.presentation.download.models.DownloadCardActions
import dev.jdtech.jellyfin.presentation.download.models.DownloadEpisodeTileState
import dev.jdtech.jellyfin.presentation.download.models.DownloadStatus
import dev.jdtech.jellyfin.presentation.film.components.PlaceholderScreen
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShowDownloadsScreen(
    showId: UUID,
    showTitle: String,
    onEpisodeClick: (episode: FindroidEpisode) -> Unit,
    navigateBack: () -> Unit,
    viewModel: ShowDownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(showId) { viewModel.loadShow(showId) }

    ShowDownloadsScreenLayout(
        showId = showId,
        showTitle = showTitle,
        state = state,
        onAction = viewModel::onAction,
        onEpisodeClick = onEpisodeClick,
        navigateBack = navigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ShowDownloadsScreenLayout(
    showId: UUID,
    showTitle: String,
    state: ShowDownloadsState,
    onAction: (ShowDownloadsAction) -> Unit,
    onEpisodeClick: (episode: FindroidEpisode) -> Unit,
    navigateBack: () -> Unit,
) {
    val context = LocalContext.current

    val safePadding = rememberSafePadding()
    val castPadding = LocalCastPlayerHeight.current
    val paddingStart = safePadding.start + 16.dp
    val paddingEnd = safePadding.end + 16.dp
    val toolbarPadding by
        animateDpAsState(
            targetValue = if (state.isSelectionMode) 88.dp else 16.dp,
            animationSpec = tween(300),
            label = "toolbarBottomPadding",
        )
    val bottomPadding = safePadding.bottom + castPadding + toolbarPadding

    val listState = rememberLazyListState()
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }

    var episodeToDelete by remember { mutableStateOf<FindroidEpisode?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMoveStorageDialog by remember { mutableStateOf(false) }
    var episodeToMove by remember { mutableStateOf<FindroidEpisode?>(null) }

    // Single item delete dialog
    episodeToDelete?.let { episode ->
        val sizeFormatted = Formatter.formatFileSize(context, episode.diskSize())
        val epName = episode.name.ifEmpty { "Episode ${episode.indexNumber}" }
        ConfirmDeleteDialog(
            title = stringResource(CoreR.string.delete_download),
            message = stringResource(CoreR.string.delete_item_confirm, epName, sizeFormatted),
            onConfirm = {
                onAction(ShowDownloadsAction.DeleteEpisode(episode))
                episodeToDelete = null
            },
            onDismiss = { episodeToDelete = null },
        )
    }

    // Batch delete dialog
    if (showBatchDeleteDialog) {
        val count = state.selectedEpisodeIds.size
        val allEpisodes = state.seasonGroups.flatMap { it.episodes }
        val selectedEpisodes = allEpisodes.filter { state.selectedEpisodeIds.contains(it.id) }
        val sizeFormatted =
            Formatter.formatFileSize(context, selectedEpisodes.sumOf { it.diskSize() })
        ConfirmDeleteDialog(
            title = stringResource(CoreR.string.delete_download),
            message = stringResource(CoreR.string.delete_items_confirm, count, sizeFormatted),
            onConfirm = {
                onAction(ShowDownloadsAction.DeleteSelected)
                showBatchDeleteDialog = false
            },
            onDismiss = { showBatchDeleteDialog = false },
        )
    }

    // Move storage confirmation dialog
    if (showMoveStorageDialog) {
        AlertDialog(
            onDismissRequest = {
                showMoveStorageDialog = false
                episodeToMove = null
            },
            title = { Text(text = stringResource(CoreR.string.move_storage)) },
            text = { Text(text = stringResource(CoreR.string.move_storage_prompt)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ep = episodeToMove
                        if (ep != null) {
                            onAction(ShowDownloadsAction.MoveEpisodeStorage(ep, 1))
                        } else {
                            onAction(ShowDownloadsAction.MoveSelected(1))
                        }
                        showMoveStorageDialog = false
                        episodeToMove = null
                    }
                ) {
                    Text(text = stringResource(CoreR.string.sd_card))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val ep = episodeToMove
                        if (ep != null) {
                            onAction(ShowDownloadsAction.MoveEpisodeStorage(ep, 0))
                        } else {
                            onAction(ShowDownloadsAction.MoveSelected(0))
                        }
                        showMoveStorageDialog = false
                        episodeToMove = null
                    }
                ) {
                    Text(text = stringResource(CoreR.string.internal_storage))
                }
            },
        )
    }

    val allSeasonGroups =
        remember(state.seasonGroups, state.activeDownloads) {
            val existingEpisodeIds =
                state.seasonGroups.flatMap { it.episodes }.map { it.id }.toSet()
            val queuedEpisodes =
                state.activeDownloads
                    .map { it.item }
                    .filterIsInstance<FindroidEpisode>()
                    .filter { it.seriesId == showId && it.id !in existingEpisodeIds }

            if (queuedEpisodes.isEmpty()) {
                state.seasonGroups
            } else {
                val allEpisodes = state.seasonGroups.flatMap { it.episodes } + queuedEpisodes
                allEpisodes
                    .groupBy { it.parentIndexNumber }
                    .map { (seasonNum, eps) ->
                        val epCount = eps.size
                        val epStr = if (epCount == 1) "1 episode" else "$epCount episodes"
                        val header =
                            if (seasonNum > 0) "Season $seasonNum • $epStr" else "Speciali • $epStr"
                        SeasonEpisodeGroup(
                            seasonNumber = seasonNum,
                            headerTitle = header,
                            episodes = eps.sortedBy { it.indexNumber },
                        )
                    }
                    .sortedBy { it.seasonNumber }
            }
        }

    Scaffold(
        modifier = Modifier.fillMaxSize().recalculateWindowInsets(),
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSelectionMode) {
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.selected_count,
                                    state.selectedEpisodeIds.size,
                                ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    } else {
                        Column {
                            Text(
                                text = showTitle,
                                style =
                                    MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val totalCount = allSeasonGroups.sumOf { it.episodes.size }
                            if (totalCount > 0) {
                                val epStr =
                                    if (totalCount == 1) "1 episode" else "$totalCount episodes"
                                val totalBytes =
                                    allSeasonGroups
                                        .flatMap { it.episodes }
                                        .sumOf { ep ->
                                            val disk = ep.diskSize()
                                            if (disk > 0) disk
                                            else (ep.sources.maxOfOrNull { it.size } ?: 0L)
                                        }
                                val totalSizeStr =
                                    if (totalBytes > 0)
                                        Formatter.formatFileSize(context, totalBytes)
                                    else ""
                                val subtitleText =
                                    if (totalSizeStr.isNotEmpty()) "$epStr • $totalSizeStr"
                                    else epStr
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.isSelectionMode) {
                                onAction(ShowDownloadsAction.ExitSelectionMode)
                            } else {
                                navigateBack()
                            }
                        }
                    ) {
                        Icon(
                            painter =
                                if (state.isSelectionMode) painterResource(CoreR.drawable.ic_x)
                                else painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription =
                                if (state.isSelectionMode) stringResource(CoreR.string.cancel)
                                else null,
                        )
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        val allEps = allSeasonGroups.flatMap { it.episodes }
                        val isAllSelected =
                            allEps.isNotEmpty() &&
                                allEps.all { state.selectedEpisodeIds.contains(it.id) }
                        TextButton(
                            onClick = { onAction(ShowDownloadsAction.SelectAll) },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        if (isAllSelected) CoreR.string.deselect_all
                                        else CoreR.string.select_all
                                    )
                            )
                        }
                    } else if (allSeasonGroups.any { it.episodes.isNotEmpty() }) {
                        FilledTonalButton(
                            onClick = { onAction(ShowDownloadsAction.EnterSelectionMode) },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp).padding(end = 12.dp),
                        ) {
                            Text(
                                text = stringResource(CoreR.string.select),
                                style =
                                    MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (allSeasonGroups.isEmpty()) {
                PlaceholderScreen(
                    title = stringResource(CoreR.string.no_downloads_title),
                    subtitle = stringResource(CoreR.string.no_downloads),
                    buttonText = stringResource(CoreR.string.explore_library),
                    onButtonClick = navigateBack,
                    image = CoreR.drawable.download_page_placeholder,
                    isEmpty = true,
                    modifier =
                        Modifier.fillMaxSize().padding(start = paddingStart, end = paddingEnd),
                ) {}
            } else {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier.fillMaxSize().widthIn(max = 640.dp).align(Alignment.TopCenter),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (seasonGroup in allSeasonGroups) {
                        val seasonEpIds = seasonGroup.episodes.map { it.id }.toSet()
                        val isSeasonSelected =
                            seasonEpIds.isNotEmpty() &&
                                seasonEpIds.all { state.selectedEpisodeIds.contains(it) }

                        stickyHeader(key = "season_${seasonGroup.seasonNumber}") {
                            val isSeasonOverlapping by
                                remember(seasonGroup.seasonNumber, seasonEpIds, listState) {
                                    derivedStateOf {
                                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                                        val headerItem =
                                            visibleItems.firstOrNull {
                                                it.key == "season_${seasonGroup.seasonNumber}"
                                            } ?: return@derivedStateOf false
                                        val headerBottom = headerItem.offset + headerItem.size
                                        visibleItems.any { item ->
                                            item.key in seasonEpIds &&
                                                item.offset < headerBottom &&
                                                (item.offset + item.size) > headerItem.offset
                                        }
                                    }
                                }

                            StickySeasonHeader(
                                title = seasonGroup.headerTitle,
                                contentPadding =
                                    PaddingValues(
                                        start = paddingStart,
                                        end = paddingEnd,
                                        top = 6.dp,
                                        bottom = 6.dp,
                                    ),
                                isSelectionMode = state.isSelectionMode,
                                isSelected = isSeasonSelected,
                                isOverlapping = isSeasonOverlapping,
                                onToggleSelect = {
                                    onAction(ShowDownloadsAction.ToggleSeasonSelection(seasonEpIds))
                                },
                                onLongClick = {
                                    onAction(ShowDownloadsAction.EnterSelectionMode)
                                    onAction(ShowDownloadsAction.ToggleSeasonSelection(seasonEpIds))
                                },
                            )
                        }

                        items(
                            items = seasonGroup.episodes,
                            key = { it.id },
                        ) { episode ->
                            val queueEntry =
                                state.activeDownloads.firstOrNull { it.id == episode.id }
                            val isMoviePaused = queueEntry?.state is DownloadQueue.EntryState.Paused
                            val isDownloading =
                                queueEntry?.state is DownloadQueue.EntryState.Downloading ||
                                    (isMoviePaused &&
                                        (queueEntry.bytesDownloaded > 0 ||
                                            queueEntry.downloadId != null))
                            val isPending =
                                queueEntry?.state is DownloadQueue.EntryState.Pending ||
                                    (isMoviePaused &&
                                        queueEntry.bytesDownloaded <= 0 &&
                                        queueEntry.downloadId == null)
                            val isFailed = queueEntry?.state is DownloadQueue.EntryState.Failed
                            val isConverting =
                                queueEntry?.state is DownloadQueue.EntryState.Converting
                            val transfer = state.activeTransfers[episode.id]
                            val isTransferring = transfer != null

                            val status =
                                when {
                                    isTransferring -> DownloadStatus.TRANSFERRING
                                    isConverting -> DownloadStatus.CONVERTING
                                    isDownloading -> DownloadStatus.DOWNLOADING
                                    isPending -> DownloadStatus.PENDING
                                    isFailed -> DownloadStatus.FAILED
                                    episode.isDownloading() -> DownloadStatus.DOWNLOADING
                                    else -> DownloadStatus.DOWNLOADED
                                }

                            val downloadProgress =
                                when {
                                    transfer != null -> transfer.progress
                                    queueEntry != null ->
                                        (queueEntry.progress / 100f).coerceIn(0f, 1f)
                                    else -> 0f
                                }

                            val downloadedSizeBytes =
                                when {
                                    transfer != null -> transfer.bytesTransferred
                                    queueEntry != null && queueEntry.bytesDownloaded > 0 ->
                                        queueEntry.bytesDownloaded
                                    else -> 0L
                                }
                            val sizeBytes =
                                when {
                                    transfer != null -> transfer.totalBytes
                                    queueEntry?.totalBytes != null && queueEntry.totalBytes > 0 ->
                                        queueEntry.totalBytes
                                    else -> 0L
                                }

                            val playbackProgress =
                                if (
                                    status == DownloadStatus.DOWNLOADED && episode.runtimeTicks > 0
                                ) {
                                    (episode.playbackPositionTicks.toFloat() / episode.runtimeTicks)
                                        .coerceIn(0f, 1f)
                                } else 0f

                            DownloadEpisodeTile(
                                episode = episode,
                                state =
                                    DownloadEpisodeTileState(
                                        status = status,
                                        downloadProgress = downloadProgress,
                                        playbackProgress = playbackProgress,
                                        isSelected = state.selectedEpisodeIds.contains(episode.id),
                                        isSelectionMode = state.isSelectionMode,
                                        sizeBytes = sizeBytes,
                                        durationTicks = episode.runtimeTicks,
                                        downloadSpeedBytesPerSec = queueEntry?.bytesPerSecond ?: 0L,
                                        etaSeconds = queueEntry?.etaSeconds,
                                        downloadedSizeBytes = downloadedSizeBytes,
                                        isPaused = isMoviePaused,
                                        pendingDeletionSeconds =
                                            state.pendingDeletionIds[episode.id],
                                        displayExtraInfo = state.displayExtraInfo,
                                    ),
                                actions =
                                    DownloadCardActions(
                                        onClick = {
                                            if (state.isSelectionMode) {
                                                onAction(
                                                    ShowDownloadsAction.ToggleSelection(episode.id)
                                                )
                                            } else if (status == DownloadStatus.DOWNLOADED) {
                                                onEpisodeClick(episode)
                                            }
                                        },
                                        onLongClick = {
                                            onAction(ShowDownloadsAction.EnterSelectionMode)
                                            onAction(
                                                ShowDownloadsAction.ToggleSelection(episode.id)
                                            )
                                        },
                                        onSwipeDelete = {
                                            onAction(
                                                ShowDownloadsAction.StageDeleteEpisode(episode)
                                            )
                                        },
                                        onPauseDownload = {
                                            onAction(ShowDownloadsAction.PauseDownload(episode.id))
                                        },
                                        onResumeDownload = {
                                            onAction(ShowDownloadsAction.ResumeDownload(episode.id))
                                        },
                                        onRetryDownload = {
                                            onAction(ShowDownloadsAction.ResumeDownload(episode.id))
                                        },
                                        onUndoDelete = {
                                            onAction(ShowDownloadsAction.UndoDelete(episode.id))
                                        },
                                    ),
                                modifier = Modifier.padding(start = paddingStart, end = paddingEnd),
                            )
                        }
                    }
                }
            }

            // Bottom overflow gradient
            AnimatedVisibility(
                visible = canScrollForward,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(80.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface,
                                        )
                                )
                            )
                )
            }

            // Floating Selection Toolbar
            AnimatedVisibility(
                visible = state.isSelectionMode,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                            )
                        )
                        .padding(bottom = 8.dp),
            ) {
                val selectedActive =
                    state.activeDownloads.filter { state.selectedEpisodeIds.contains(it.id) }
                val anyDownloadingOrPending =
                    if (selectedActive.isNotEmpty()) {
                        selectedActive.any {
                            it.state is DownloadQueue.EntryState.Downloading ||
                                it.state is DownloadQueue.EntryState.Converting ||
                                it.state is DownloadQueue.EntryState.Pending
                        }
                    } else null

                FloatingSelectionToolbar(
                    selectedCount = state.selectedEpisodeIds.size,
                    anyDownloadingOrPending = anyDownloadingOrPending,
                    onPauseOrResume = { pause ->
                        onAction(ShowDownloadsAction.PauseOrResumeSelected(pause = pause))
                    },
                    hasSdCard = state.hasSdCard,
                    onMoveStorage = {
                        episodeToMove = null
                        showMoveStorageDialog = true
                    },
                    onDelete = { showBatchDeleteDialog = true },
                )
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun ShowDownloadsScreenLayoutPreview() {
    FindroidTheme {
        ShowDownloadsScreenLayout(
            showId = UUID.randomUUID(),
            showTitle = "Oshi no Ko",
            state =
                ShowDownloadsState(
                    seasonGroups =
                        listOf(
                            SeasonEpisodeGroup(
                                seasonNumber = 1,
                                headerTitle = "Season 1 • 1 episode",
                                episodes = listOf(dummyEpisode),
                            )
                        ),
                    totalEpisodesCount = 1,
                    totalDiskSizeFormatted = "1.2 GB",
                ),
            onAction = {},
            onEpisodeClick = {},
            navigateBack = {},
        )
    }
}
