package dev.jdtech.jellyfin.presentation.download

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.LocalCastPlayerHeight
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.core.presentation.downloader.formatStableEta
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadedShowItem
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsAction
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.diskSize
import dev.jdtech.jellyfin.models.formatDuration
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.presentation.download.components.ConfirmDeleteDialog
import dev.jdtech.jellyfin.presentation.download.components.DownloadItemCard
import dev.jdtech.jellyfin.presentation.download.components.DownloadSectionHeader
import dev.jdtech.jellyfin.presentation.download.components.FloatingSelectionToolbar
import dev.jdtech.jellyfin.presentation.download.components.StorageSummaryCard
import dev.jdtech.jellyfin.presentation.download.models.DownloadCardActions
import dev.jdtech.jellyfin.presentation.download.models.DownloadItemCardState
import dev.jdtech.jellyfin.presentation.download.models.DownloadStatus
import dev.jdtech.jellyfin.presentation.film.components.PlaceholderScreen
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import java.util.UUID
import dev.jdtech.jellyfin.core.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onMovieClick: (movie: FindroidMovie) -> Unit,
    onShowClick: (show: FindroidShow) -> Unit,
    onStorageClick: () -> Unit,
    onExploreLibraryClick: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val canScrollForward by remember { derivedStateOf { listState.canScrollForward } }

    val safePadding = rememberSafePadding(handleStartInsets = false, handleBottomInsets = false)
    val castPadding = LocalCastPlayerHeight.current
    val paddingStart = safePadding.start + 16.dp
    val paddingEnd = safePadding.end + 16.dp
    val toolbarPadding by animateDpAsState(
        targetValue = if (state.isSelectionMode) 88.dp else 16.dp,
        animationSpec = tween(300),
        label = "toolbarBottomPadding",
    )
    val bottomPadding = safePadding.bottom + castPadding + toolbarPadding

    var movieToDelete by remember { mutableStateOf<FindroidMovie?>(null) }
    var showToDelete by remember { mutableStateOf<DownloadedShowItem?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showMoveStorageDialog by remember { mutableStateOf(false) }
    var itemToMoveId by remember { mutableStateOf<UUID?>(null) }

    LaunchedEffect(true) {
        viewModel.loadItems()
    }

    // Single movie delete confirmation dialog
    movieToDelete?.let { movie ->
        val sizeFormatted = Formatter.formatFileSize(context, movie.diskSize())
        ConfirmDeleteDialog(
            title = stringResource(CoreR.string.delete_download),
            message = stringResource(CoreR.string.delete_item_confirm, movie.name, sizeFormatted),
            onConfirm = {
                viewModel.onAction(DownloadsAction.DeleteMovie(movie))
                movieToDelete = null
            },
            onDismiss = { movieToDelete = null },
        )
    }

    // Single show delete confirmation dialog
    showToDelete?.let { showItem ->
        val sizeFormatted = Formatter.formatFileSize(context, showItem.totalDiskSize)
        ConfirmDeleteDialog(
            title = stringResource(CoreR.string.delete_download),
            message = stringResource(CoreR.string.delete_item_confirm, showItem.show.name, sizeFormatted),
            onConfirm = {
                viewModel.onAction(DownloadsAction.DeleteShow(showItem))
                showToDelete = null
            },
            onDismiss = { showToDelete = null },
        )
    }

    // Batch delete confirmation dialog
    if (showBatchDeleteDialog) {
        val count = state.selectedItemIds.size
        val selectedMovies = state.movies.filter { state.selectedItemIds.contains(it.id) }
        val selectedShows = state.shows.filter { state.selectedItemIds.contains(it.show.id) }
        val totalBytes = selectedMovies.sumOf { it.diskSize() } + selectedShows.sumOf { it.totalDiskSize }
        val sizeFormatted = Formatter.formatFileSize(context, totalBytes)

        ConfirmDeleteDialog(
            title = stringResource(CoreR.string.delete_download),
            message = stringResource(CoreR.string.delete_items_confirm, count, sizeFormatted),
            onConfirm = {
                viewModel.onAction(DownloadsAction.DeleteSelected)
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
                itemToMoveId = null
            },
            title = { Text(text = stringResource(CoreR.string.move_storage)) },
            text = { Text(text = stringResource(CoreR.string.move_storage_prompt)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val singleId = itemToMoveId
                        if (singleId != null) {
                            viewModel.onAction(DownloadsAction.MoveItemStorage(singleId, 1))
                        } else {
                            viewModel.onAction(DownloadsAction.MoveSelected(1))
                        }
                        showMoveStorageDialog = false
                        itemToMoveId = null
                    }
                ) {
                    Text(text = stringResource(CoreR.string.sd_card))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val singleId = itemToMoveId
                        if (singleId != null) {
                            viewModel.onAction(DownloadsAction.MoveItemStorage(singleId, 0))
                        } else {
                            viewModel.onAction(DownloadsAction.MoveSelected(0))
                        }
                        showMoveStorageDialog = false
                        itemToMoveId = null
                    }
                ) {
                    Text(text = stringResource(CoreR.string.internal_storage))
                }
            },
        )
    }

    val allMovies = remember(state.movies, state.activeDownloads) {
        val dbIds = state.movies.map { it.id }.toSet()
        val queuedMovies = state.activeDownloads
            .map { it.item }
            .filterIsInstance<FindroidMovie>()
            .filter { it.id !in dbIds }
        state.movies + queuedMovies
    }

    val allShows = remember(state.shows, state.activeDownloads) {
        val dbShowIds = state.shows.map { it.show.id }.toSet()
        val queuedEpisodeShows = state.activeDownloads
            .asSequence()
            .map { it.item }
            .filterIsInstance<FindroidEpisode>()
            .filter { it.seriesId !in dbShowIds }
            .groupBy { it.seriesId }
            .mapNotNull { (seriesId, eps) ->
                val firstEp = eps.first()
                val fakeShow = FindroidShow(
                    id = seriesId,
                    name = firstEp.seriesName,
                    originalTitle = null,
                    overview = "",
                    sources = emptyList(),
                    seasons = emptyList(),
                    played = false,
                    favorite = false,
                    canPlay = true,
                    canDownload = true,
                    unplayedItemCount = null,
                    genres = emptyList(),
                    people = emptyList(),
                    runtimeTicks = 0L,
                    communityRating = null,
                    officialRating = null,
                    status = "",
                    productionYear = null,
                    endDate = null,
                    trailer = null,
                    images = firstEp.images,
                )
                DownloadedShowItem(
                    show = fakeShow,
                    episodes = eps,
                    totalDiskSize = eps.sumOf { it.diskSize() },
                    seasonsFormatted = "",
                )
            }
            .toList()
        state.shows + queuedEpisodeShows
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().recalculateWindowInsets(),
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
        topBar = {
            if (allMovies.isNotEmpty() || allShows.isNotEmpty() || state.isSelectionMode) {
                TopAppBar(
                    title = {
                        if (state.isSelectionMode) {
                            Text(
                                text = stringResource(CoreR.string.selected_count, state.selectedItemIds.size),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        } else {
                            Text(
                                text = stringResource(CoreR.string.title_download),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    },
                    navigationIcon = {
                        if (state.isSelectionMode) {
                            IconButton(onClick = { viewModel.onAction(DownloadsAction.ExitSelectionMode) }) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_x),
                                    contentDescription = stringResource(CoreR.string.cancel),
                                )
                            }
                        }
                    },
                    actions = {
                        if (state.isSelectionMode) {
                            val totalItemCount = allMovies.size + allShows.size
                            val isAllSelected = totalItemCount > 0 && state.selectedItemIds.size >= totalItemCount
                            TextButton(
                                onClick = { viewModel.onAction(DownloadsAction.SelectAll) },
                                modifier = Modifier.padding(end = 8.dp),
                            ) {
                                Text(text = stringResource(if (isAllSelected) CoreR.string.deselect_all else CoreR.string.select_all))
                            }
                        } else {
                            if (allMovies.isNotEmpty() || allShows.isNotEmpty()) {
                                FilledTonalButton(
                                    onClick = { viewModel.onAction(DownloadsAction.EnterSelectionMode) },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp).padding(end = 12.dp),
                                ) {
                                    Text(
                                        text = stringResource(CoreR.string.select),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (allMovies.isEmpty() && allShows.isEmpty()) {
                PlaceholderScreen(
                    title = stringResource(CoreR.string.no_downloads_title),
                    subtitle = stringResource(CoreR.string.no_downloads),
                    buttonText = stringResource(CoreR.string.explore_library),
                    onButtonClick = onExploreLibraryClick,
                    image = CoreR.drawable.download_page_placeholder,
                    isEmpty = true,
                    modifier = Modifier.fillMaxSize().padding(start = paddingStart, end = paddingEnd),
                ) {}
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(start = paddingStart, end = paddingEnd, top = 8.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                // Storage summary card (text only: "X downloaded • Y free", no progress bar)
                item(key = "storage_summary") {
                    StorageSummaryCard(
                        usedStorageFormatted = state.usedStorageFormatted,
                        freeStorageFormatted = state.freeStorageFormatted,
                        isSmartDownloadsActive = state.isSmartDownloadsActive,
                        onClick = onStorageClick,
                    )
                }

                // Movies Section Header
                if (allMovies.isNotEmpty()) {
                    item(key = "movies_section_header") {
                        val totalMoviesBytes = allMovies.sumOf { it.diskSize() }
                        val moviesSizeFormatted = Formatter.formatFileSize(context, totalMoviesBytes)
                        DownloadSectionHeader(
                            title = stringResource(CoreR.string.movies_section_title),
                            itemCount = allMovies.size,
                            totalSizeFormatted = if (totalMoviesBytes > 0) moviesSizeFormatted else "",
                        )
                    }
                }

                // Movies
                items(
                    items = allMovies,
                    key = { "movie_${it.id}" },
                ) { movie ->
                    val activeEntry = state.activeDownloads.firstOrNull { it.id == movie.id }
                    val isMoviePaused = activeEntry?.state is DownloadQueue.EntryState.Paused
                    val isDownloading = activeEntry?.state is DownloadQueue.EntryState.Downloading ||
                        (isMoviePaused && (activeEntry.bytesDownloaded > 0 || activeEntry.downloadId != null))
                    val isPending = activeEntry?.state is DownloadQueue.EntryState.Pending ||
                        (isMoviePaused && activeEntry.bytesDownloaded <= 0 && activeEntry.downloadId == null)
                    val isFailed = activeEntry?.state is DownloadQueue.EntryState.Failed
                    val isConverting = activeEntry?.state is DownloadQueue.EntryState.Converting
                    val transfer = state.activeTransfers[movie.id]
                    val isTransferring = transfer != null

                    val status = when {
                        isTransferring -> DownloadStatus.TRANSFERRING
                        isConverting -> DownloadStatus.CONVERTING
                        isDownloading -> DownloadStatus.DOWNLOADING
                        isPending -> DownloadStatus.PENDING
                        isFailed -> DownloadStatus.FAILED
                        movie.isDownloading() -> DownloadStatus.DOWNLOADING
                        else -> DownloadStatus.DOWNLOADED
                    }

                    val dlProgress = when {
                        transfer != null -> transfer.progress
                        activeEntry != null -> (activeEntry.progress / 100f).coerceIn(0f, 1f)
                        else -> 0f
                    }
                    val etaFormatted = if (activeEntry != null && activeEntry.bytesPerSecond > 0 && activeEntry.totalBytes > activeEntry.bytesDownloaded) {
                        val remainingSec = (activeEntry.totalBytes - activeEntry.bytesDownloaded) / activeEntry.bytesPerSecond
                        formatStableEta(remainingSec)
                    } else ""

                    val metadataText: String
                    val playbackProgress: Float

                    when (status) {
                        DownloadStatus.TRANSFERRING -> {
                            val transferPercent = (dlProgress * 100).toInt()
                            metadataText = "$transferPercent% • ${stringResource(CoreR.string.moving_storage_short)}"
                            playbackProgress = 0f
                        }
                        DownloadStatus.CONVERTING -> {
                            val convProgressStr = if (dlProgress > 0f) "${(dlProgress * 100).toInt()}% • " else ""
                            metadataText = "$convProgressStr${stringResource(CoreR.string.converting)}"
                            playbackProgress = 0f
                        }
                        DownloadStatus.DOWNLOADING -> {
                            metadataText = when {
                                isMoviePaused -> "${(dlProgress * 100).toInt()}% • ${stringResource(CoreR.string.paused)}"
                                etaFormatted.isNotEmpty() -> "${(dlProgress * 100).toInt()}% • $etaFormatted"
                                else -> "${(dlProgress * 100).toInt()}%"
                            }
                            playbackProgress = 0f
                        }
                        DownloadStatus.PENDING -> {
                            metadataText = stringResource(CoreR.string.pending_in_queue)
                            playbackProgress = 0f
                        }
                        DownloadStatus.FAILED -> {
                            metadataText = stringResource(CoreR.string.downloading_error)
                            playbackProgress = 0f
                        }
                        DownloadStatus.DOWNLOADED -> {
                            val durationText = formatDuration(movie.runtimeTicks)
                            val yearText = movie.productionYear?.toString() ?: ""
                            metadataText = when {
                                yearText.isNotEmpty() && durationText.isNotEmpty() -> "$yearText • $durationText"
                                yearText.isNotEmpty() -> yearText
                                else -> durationText
                            }
                            playbackProgress = if (movie.runtimeTicks > 0) {
                                (movie.playbackPositionTicks.toFloat() / movie.runtimeTicks).coerceIn(0f, 1f)
                            } else 0f
                        }
                    }

                    DownloadItemCard(
                        item = movie,
                        state = DownloadItemCardState(
                            title = movie.name,
                            metadataText = metadataText,
                            sizeBytes = if (state.displayExtraInfo) movie.diskSize() else 0L,
                            status = status,
                            downloadProgress = dlProgress,
                            playbackProgress = playbackProgress,
                            isSelected = state.selectedItemIds.contains(movie.id),
                            isSelectionMode = state.isSelectionMode,
                            displayExtraInfo = state.displayExtraInfo,
                            isPaused = activeEntry?.state is DownloadQueue.EntryState.Paused,
                            pendingDeletionSeconds = state.pendingDeletionIds[movie.id],
                        ),
                        actions = DownloadCardActions(
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.onAction(DownloadsAction.ToggleSelection(movie.id))
                                } else if (status == DownloadStatus.DOWNLOADED) {
                                    onMovieClick(movie)
                                }
                            },
                            onLongClick = {
                                viewModel.onAction(DownloadsAction.EnterSelectionMode)
                                viewModel.onAction(DownloadsAction.ToggleSelection(movie.id))
                            },
                            onSwipeDelete = {
                                viewModel.onAction(DownloadsAction.StageDeleteMovie(movie))
                            },
                            onPauseDownload = {
                                viewModel.onAction(DownloadsAction.PauseDownload(movie.id))
                            },
                            onResumeDownload = {
                                viewModel.onAction(DownloadsAction.ResumeDownload(movie.id))
                            },
                            onRetryDownload = {
                                viewModel.onAction(DownloadsAction.ResumeDownload(movie.id))
                            },
                            onUndoDelete = {
                                viewModel.onAction(DownloadsAction.UndoDelete(movie.id))
                            },
                        ),
                    )
                }

                // Shows Section Header
                if (allShows.isNotEmpty()) {
                    item(key = "shows_section_header") {
                        val totalShowsBytes = allShows.sumOf { it.totalDiskSize }
                        val showsSizeFormatted = Formatter.formatFileSize(context, totalShowsBytes)
                        DownloadSectionHeader(
                            title = stringResource(CoreR.string.shows_section_title),
                            itemCount = allShows.size,
                            totalSizeFormatted = if (totalShowsBytes > 0) showsSizeFormatted else "",
                        )
                    }
                }

                // Shows
                items(
                    items = allShows,
                    key = { "show_${it.show.id}" },
                ) { showItem ->
                    val activeEpisodes = state.activeDownloads.filter {
                        it.item is FindroidEpisode && (it.item as FindroidEpisode).seriesId == showItem.show.id
                    }
                    val isAllPaused = activeEpisodes.isNotEmpty() && activeEpisodes.all { it.state is DownloadQueue.EntryState.Paused }
                    val convertingEp = activeEpisodes.firstOrNull {
                        it.state is DownloadQueue.EntryState.Converting
                    }
                    val downloadingEp = activeEpisodes.firstOrNull {
                        it.state is DownloadQueue.EntryState.Downloading || (isAllPaused && it.bytesDownloaded > 0)
                    }
                    val pendingEps = activeEpisodes.filter {
                        it.state is DownloadQueue.EntryState.Pending || (it.state is DownloadQueue.EntryState.Paused && it != downloadingEp && it != convertingEp)
                    }
                    val failedEps = activeEpisodes.filter { it.state is DownloadQueue.EntryState.Failed }

                    val showTransfer = state.activeTransfers[showItem.show.id]
                        ?: showItem.episodes.firstNotNullOfOrNull { state.activeTransfers[it.id] }
                    val isShowTransferring = showTransfer != null

                    val status = when {
                        isShowTransferring -> DownloadStatus.TRANSFERRING
                        convertingEp != null -> DownloadStatus.CONVERTING
                        downloadingEp != null -> DownloadStatus.DOWNLOADING
                        isAllPaused -> DownloadStatus.DOWNLOADING
                        pendingEps.isNotEmpty() -> DownloadStatus.PENDING
                        failedEps.isNotEmpty() -> DownloadStatus.FAILED
                        else -> DownloadStatus.DOWNLOADED
                    }

                    val effectiveEp = downloadingEp ?: convertingEp
                    val showQueue = state.allQueueEntries.filter {
                        it.item is FindroidEpisode && (it.item as FindroidEpisode).seriesId == showItem.show.id
                    }
                    val completedInQueue = showQueue.count { it.state is DownloadQueue.EntryState.Completed }
                    val activeProgress = if (effectiveEp != null) (effectiveEp.progress / 100f).coerceIn(0f, 1f) else 0f
                    val totalProgress = if (showQueue.isNotEmpty()) {
                        ((completedInQueue.toFloat() + activeProgress) / showQueue.size.toFloat()).coerceIn(0f, 1f)
                    } else {
                        activeProgress
                    }

                    val metadataText: String

                    when (status) {
                        DownloadStatus.TRANSFERRING -> {
                            val transferPercent = ((showTransfer?.progress ?: 0f) * 100).toInt()
                            metadataText = "$transferPercent% • ${stringResource(CoreR.string.moving_storage_short)}"
                        }
                        DownloadStatus.CONVERTING -> {
                            val ep = convertingEp?.item as? FindroidEpisode
                            val epLabel = if (ep != null && ep.indexNumber > 0) "E${ep.indexNumber}" else ep?.name ?: ""
                            val convProgressStr = if (totalProgress > 0f) "${(totalProgress * 100).toInt()}% • " else ""
                            metadataText = if (epLabel.isNotEmpty()) "$epLabel • $convProgressStr${stringResource(CoreR.string.converting)}" else "$convProgressStr${stringResource(CoreR.string.converting)}"
                        }
                        DownloadStatus.DOWNLOADING -> {
                            val ep = downloadingEp?.item as? FindroidEpisode
                            val epLabel = if (ep != null && ep.indexNumber > 0) "E${ep.indexNumber}" else ep?.name ?: ""
                            val isEpPaused = downloadingEp?.state is DownloadQueue.EntryState.Paused || isAllPaused
                            val epEtaFormatted = downloadingEp?.etaSeconds?.let { formatStableEta(it) } ?: ""
                            val totalProgressInt = (totalProgress * 100).toInt()
                            metadataText = when {
                                isEpPaused -> if (epLabel.isNotEmpty()) "$epLabel • $totalProgressInt% • ${stringResource(CoreR.string.paused)}" else stringResource(CoreR.string.paused)
                                epEtaFormatted.isNotEmpty() -> "$epLabel • $totalProgressInt% • $epEtaFormatted"
                                else -> if (epLabel.isNotEmpty()) "$epLabel • $totalProgressInt%" else "$totalProgressInt%"
                            }
                        }
                        DownloadStatus.PENDING -> {
                            metadataText = if (isAllPaused) {
                                stringResource(CoreR.string.paused)
                            } else if (pendingEps.size > 1) {
                                "${pendingEps.size} ${stringResource(CoreR.string.pending_in_queue).lowercase()}"
                            } else {
                                stringResource(CoreR.string.pending_in_queue)
                            }
                        }
                        DownloadStatus.FAILED -> {
                            metadataText = stringResource(CoreR.string.downloading_error)
                        }
                        DownloadStatus.DOWNLOADED -> {
                            val yearText = showItem.show.productionYear?.toString() ?: ""
                            metadataText = when {
                                yearText.isNotEmpty() && showItem.seasonsFormatted.isNotEmpty() ->
                                    "$yearText • ${showItem.seasonsFormatted}"
                                yearText.isNotEmpty() -> yearText
                                else -> showItem.seasonsFormatted
                            }
                        }
                    }

                    DownloadItemCard(
                        item = showItem.show,
                        state = DownloadItemCardState(
                            title = showItem.show.name,
                            metadataText = metadataText,
                            sizeBytes = if (state.displayExtraInfo) showItem.totalDiskSize else 0L,
                            status = status,
                            downloadProgress = totalProgress,
                            playbackProgress = 0f,
                            isSelected = state.selectedItemIds.contains(showItem.show.id),
                            isSelectionMode = state.isSelectionMode,
                            displayExtraInfo = state.displayExtraInfo,
                            isPaused = isAllPaused,
                            pendingDeletionSeconds = state.pendingDeletionIds[showItem.show.id],
                        ),
                        actions = DownloadCardActions(
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.onAction(DownloadsAction.ToggleSelection(showItem.show.id))
                                } else {
                                    onShowClick(showItem.show)
                                }
                            },
                            onLongClick = {
                                viewModel.onAction(DownloadsAction.EnterSelectionMode)
                                viewModel.onAction(DownloadsAction.ToggleSelection(showItem.show.id))
                            },
                            onSwipeDelete = {
                                viewModel.onAction(DownloadsAction.StageDeleteShow(showItem))
                            },
                            onPauseDownload = {
                                viewModel.onAction(DownloadsAction.PauseDownload(showItem.show.id))
                            },
                            onResumeDownload = {
                                viewModel.onAction(DownloadsAction.ResumeDownload(showItem.show.id))
                            },
                            onUndoDelete = {
                                viewModel.onAction(DownloadsAction.UndoDelete(showItem.show.id))
                            },
                        ),
                    )
                }
            }
        }

            // Bottom overflow gradient
            AnimatedVisibility(
                visible = canScrollForward,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface,
                                )
                            )
                        ),
                )
            }

            // Floating Selection Toolbar
            AnimatedVisibility(
                visible = state.isSelectionMode,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(bottom = 8.dp),
            ) {
                val selectedActive = state.activeDownloads.filter { entry ->
                    state.selectedItemIds.contains(entry.id) ||
                        (entry.item is FindroidEpisode && state.selectedItemIds.contains((entry.item as FindroidEpisode).seriesId))
                }
                val anyDownloadingOrPending = if (selectedActive.isNotEmpty()) {
                    selectedActive.any {
                        it.state is DownloadQueue.EntryState.Downloading ||
                            it.state is DownloadQueue.EntryState.Converting ||
                            it.state is DownloadQueue.EntryState.Pending
                    }
                } else null

                FloatingSelectionToolbar(
                    selectedCount = state.selectedItemIds.size,
                    anyDownloadingOrPending = anyDownloadingOrPending,
                    onPauseOrResume = { pause ->
                        viewModel.onAction(DownloadsAction.PauseOrResumeSelected(pause = pause))
                    },
                    hasSdCard = state.hasSdCard,
                    onMoveStorage = {
                        itemToMoveId = null
                        showMoveStorageDialog = true
                    },
                    onDelete = { showBatchDeleteDialog = true },
                )
            }
        }
    }
}
