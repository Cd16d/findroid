package dev.jdtech.jellyfin.presentation.film

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.LocalCastPlayerHeight
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderAction
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderViewModel
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.season.SeasonViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.player.cast.models.CastConnectionState
import dev.jdtech.jellyfin.player.cast.presentation.CastSessionViewModel
import dev.jdtech.jellyfin.presentation.download.components.DownloadedBadge
import dev.jdtech.jellyfin.presentation.download.components.DownloadingBadge
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.EpisodeCard
import dev.jdtech.jellyfin.presentation.film.components.ItemButtonsBar
import dev.jdtech.jellyfin.presentation.film.components.ItemHeader
import dev.jdtech.jellyfin.presentation.film.components.ItemPoster
import dev.jdtech.jellyfin.presentation.film.components.ItemTopBar
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import dev.jdtech.jellyfin.utils.download.DownloadStatus
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

@Composable
fun SeasonScreen(
    seasonId: UUID,
    navigateBack: () -> Unit,
    navigateHome: () -> Unit,
    navigateToItem: (item: FindroidItem) -> Unit,
    navigateToSeries: (seriesId: UUID) -> Unit,
    navigateToDownloadPresets: () -> Unit = {},
    viewModel: SeasonViewModel = hiltViewModel(),
    downloaderViewModel: DownloaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloaderState by downloaderViewModel.state.collectAsStateWithLifecycle()
    val queueEntries by downloaderViewModel.queueEntries.collectAsStateWithLifecycle()

    val castSessionViewModel: CastSessionViewModel = hiltViewModel()
    val castConnectionState by castSessionViewModel.connectionState.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadSeason(seasonId = seasonId) }

    ObserveAsEvents(downloaderViewModel.events) {
        // As episodes are downloaded, reload season to render
        // the download badges
        viewModel.loadSeason(seasonId = seasonId)
    }

    SeasonScreenLayout(
        state = state,
        downloaderState = downloaderState,
        queueEntries = queueEntries,
        onAction = { action ->
            when (action) {
                is SeasonAction.Play -> {
                    if (castConnectionState == CastConnectionState.CONNECTED) {
                        castSessionViewModel.playItem(seasonId, BaseItemKind.SEASON.serialName, action.startFromBeginning)
                    } else {
                        val intent = Intent(context, PlayerActivity::class.java)
                        intent.putExtra("itemId", seasonId.toString())
                        intent.putExtra("itemKind", BaseItemKind.SEASON.serialName)
                        intent.putExtra("startFromBeginning", action.startFromBeginning)
                        context.startActivity(intent)
                    }
                }
                is SeasonAction.OnBackClick -> navigateBack()
                is SeasonAction.OnHomeClick -> navigateHome()
                is SeasonAction.NavigateToItem -> navigateToItem(action.item)
                is SeasonAction.NavigateToSeries -> navigateToSeries(action.seriesId)
                else -> Unit
            }
            viewModel.onAction(action)
        },
        onDownloaderAction = { action -> downloaderViewModel.onAction(action) },
        askPresetBeforeDownload = downloaderViewModel.askPresetBeforeDownload,
        defaultPresetId = downloaderViewModel.defaultTranscodePresetId,
        defaultDownloadExternalAudio = downloaderViewModel.downloadExternalAudio,
        onRememberSettings = { presetId, downloadExternalAudio ->
            downloaderViewModel.saveDownloadSettings(presetId, downloadExternalAudio, true)
        },
        defaultStorageIndex = downloaderViewModel.defaultDownloadStorageIndex,
        userCanTranscode = downloaderViewModel.userCanTranscode,
        presets = downloaderViewModel.presets,
        navigateToDownloadPresets = navigateToDownloadPresets,
    )
}

@Composable
private fun SeasonScreenLayout(
    state: SeasonState,
    downloaderState: DownloaderState,
    queueEntries: List<DownloadQueue.Entry> = emptyList(),
    onAction: (SeasonAction) -> Unit,
    onDownloaderAction: (DownloaderAction) -> Unit,
    askPresetBeforeDownload: Boolean = true,
    defaultPresetId: String = "1080p_balanced",
    defaultDownloadExternalAudio: Boolean = false,
    onRememberSettings: (presetId: String, downloadExternalAudio: Boolean) -> Unit = { _, _ -> },
    defaultStorageIndex: Int = -1,
    userCanTranscode: Boolean = true,
    presets: List<dev.jdtech.jellyfin.models.DownloadQualityPreset> = emptyList(),
    navigateToDownloadPresets: () -> Unit = {},
) {
    val safePadding = rememberSafePadding()
    val castPadding = LocalCastPlayerHeight.current

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + castPadding

    val lazyListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        state.season?.let { season ->
            val seasonEpisodeIds = remember(state.episodes) { state.episodes.map { it.id }.toSet() }
            val seasonQueueEntries = remember(queueEntries, seasonEpisodeIds) {
                queueEntries.filter { it.id in seasonEpisodeIds }
            }

            val convertingEp = seasonQueueEntries.firstOrNull { it.state is DownloadQueue.EntryState.Converting }
            val downloadingEp = seasonQueueEntries.firstOrNull { it.state is DownloadQueue.EntryState.Downloading }
            val pendingEps = seasonQueueEntries.filter { it.state is DownloadQueue.EntryState.Pending }
            val failedEps = seasonQueueEntries.filter { it.state is DownloadQueue.EntryState.Failed }
            val completedInQueue = seasonQueueEntries.count { it.state is DownloadQueue.EntryState.Completed }

            val effectiveEp = downloadingEp ?: convertingEp
            val activeProgress = if (effectiveEp != null) (effectiveEp.progress / 100f).coerceIn(0f, 1f) else 0f
            val totalProgress = if (seasonQueueEntries.isNotEmpty()) {
                ((completedInQueue.toFloat() + activeProgress) / seasonQueueEntries.size.toFloat()).coerceIn(0f, 1f)
            } else {
                activeProgress
            }

            val isAllPaused = seasonQueueEntries.isNotEmpty() && seasonQueueEntries.all { it.state is DownloadQueue.EntryState.Paused }
            val allCompleted = seasonQueueEntries.isNotEmpty() && seasonQueueEntries.all { it.state is DownloadQueue.EntryState.Completed }

            val isSeasonDownloading = seasonQueueEntries.any {
                it.state is DownloadQueue.EntryState.Downloading ||
                    it.state is DownloadQueue.EntryState.Converting ||
                    it.state is DownloadQueue.EntryState.Pending ||
                    it.state is DownloadQueue.EntryState.Paused
            }
            val isSeasonFullyDownloaded = state.episodes.isNotEmpty() && state.episodes.all { it.isDownloaded() }
            val seasonDownloadProgress = if (isSeasonDownloading && seasonQueueEntries.isNotEmpty()) totalProgress else null

            val seasonStatus = when {
                allCompleted -> DownloadStatus.SUCCESSFUL
                downloadingEp != null || convertingEp != null -> DownloadStatus.RUNNING
                isAllPaused -> DownloadStatus.PAUSED
                pendingEps.isNotEmpty() -> DownloadStatus.PENDING
                failedEps.isNotEmpty() -> DownloadStatus.FAILED
                else -> DownloadStatus.SUCCESSFUL
            }

            val downloadedPart = when {
                completedInQueue > 0 -> pluralStringResource(CoreR.plurals.episodes_downloaded, completedInQueue, completedInQueue)
                isAllPaused -> stringResource(CoreR.string.paused)
                downloadingEp != null || convertingEp != null -> pluralStringResource(CoreR.plurals.episodes_downloading, 1, 1)
                else -> ""
            }
            val queuePart = if (pendingEps.isNotEmpty()) {
                pluralStringResource(CoreR.plurals.episodes_in_queue, pendingEps.size, pendingEps.size)
            } else ""
            val extraInfo = when {
                downloadedPart.isNotEmpty() && queuePart.isNotEmpty() -> "$downloadedPart • $queuePart"
                downloadedPart.isNotEmpty() -> downloadedPart
                queuePart.isNotEmpty() -> queuePart
                else -> null
            }

            val effectiveDownloaderState = if (seasonQueueEntries.isNotEmpty()) {
                DownloaderState(
                    status = seasonStatus,
                    progress = totalProgress,
                    extraInfo = if (seasonQueueEntries.size > 1) extraInfo else null,
                )
            } else {
                downloaderState
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                state = lazyListState,
                contentPadding = PaddingValues(bottom = paddingBottom),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            ) {
                item {
                    ItemHeader(
                        item = season,
                        lazyListState = lazyListState,
                        content = {
                            Row(
                                modifier =
                                    Modifier.align(Alignment.BottomStart)
                                        .padding(start = paddingStart, end = paddingEnd),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Box {
                                    ItemPoster(
                                        item = season,
                                        direction = Direction.VERTICAL,
                                        modifier =
                                            Modifier.width(120.dp).clip(MaterialTheme.shapes.small),
                                    )
                                    if (isSeasonFullyDownloaded) {
                                        DownloadedBadge(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small)
                                        )
                                    } else if (isSeasonDownloading) {
                                        DownloadingBadge(
                                            progress = seasonDownloadProgress,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small)
                                        )
                                    } else if (state.episodes.any { it.isDownloaded() }) {
                                        DownloadedBadge(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(MaterialTheme.spacings.medium))
                                Column(modifier = Modifier) {
                                    Text(
                                        text = season.seriesName,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = season.name,
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = 3,
                                        style = MaterialTheme.typography.headlineMedium,
                                    )
                                }
                            }
                        },
                    )
                    Spacer(Modifier.height(MaterialTheme.spacings.default.div(2)))
                    // Using any because we want the delete button to be visible if ANY episode
                    // in the season has been downloaded
                    val isSeasonDownloaded = state.episodes.any { it.isDownloaded() }
                    // Only want to show the download button if any episodes still haven't
                    // been downloaded
                    val canSeasonBeDownloaded = state.episodes.any { it.canDownload } && !state.episodes.all { it.isDownloaded() }
                    ItemButtonsBar(
                        item = season,
                        downloaderState = effectiveDownloaderState,
                        defaultStorageIndex = defaultStorageIndex,
                        isItemDownloaded = isSeasonDownloaded,
                        canDownload = canSeasonBeDownloaded,
                        onPlayClick = { startFromBeginning ->
                            onAction(SeasonAction.Play(startFromBeginning = startFromBeginning))
                        },
                        onMarkAsPlayedClick = {
                            when (season.played) {
                                true -> onAction(SeasonAction.UnmarkAsPlayed)
                                false -> onAction(SeasonAction.MarkAsPlayed)
                            }
                        },
                        onMarkAsFavoriteClick = {
                            when (season.favorite) {
                                true -> onAction(SeasonAction.UnmarkAsFavorite)
                                false -> onAction(SeasonAction.MarkAsFavorite)
                            }
                        },
                        onTrailerClick = {},
                        onDownloadClick = { storageIndex, presetId, downloadExternalAudio, audioStreamIndex ->
                            onDownloaderAction(
                                DownloaderAction.DownloadMany(
                                    items = state.episodes,
                                    storageIndex = storageIndex,
                                    presetId = presetId,
                                    downloadExternalAudio = downloadExternalAudio,
                                    audioStreamIndex = audioStreamIndex,
                                )
                            )
                        },
                        onDownloadCancelClick = {
                            onDownloaderAction(
                                DownloaderAction.CancelDownloadMany(state.episodes)
                            )
                        },
                        onDownloadDeleteClick = {
                            onDownloaderAction(
                                DownloaderAction.DeleteDownloadMany(state.episodes)
                            )
                        },
                        askPresetBeforeDownload = askPresetBeforeDownload,
                        userCanTranscode = userCanTranscode,
                        presets = presets,
                        defaultPresetId = defaultPresetId,
                        defaultDownloadExternalAudio = defaultDownloadExternalAudio,
                        onRememberSettings = onRememberSettings,
                        onNavigateToPresets = navigateToDownloadPresets,
                        modifier =
                            Modifier.padding(start = paddingStart, end = paddingEnd).fillMaxWidth(),
                        canPlay = state.episodes.isNotEmpty(),
                    )
                }
                items(items = state.episodes, key = { episode -> episode.id }) { episode ->
                    val queueEntry = queueEntries.firstOrNull { it.id == episode.id }
                    val isDownloading = queueEntry?.state is DownloadQueue.EntryState.Downloading ||
                        queueEntry?.state is DownloadQueue.EntryState.Converting
                    val isPending = queueEntry?.state is DownloadQueue.EntryState.Pending
                    val downloadProgress = if (isDownloading) queueEntry.progress / 100f else null
                    EpisodeCard(
                        episode = episode,
                        onClick = { onAction(SeasonAction.NavigateToItem(episode)) },
                        modifier = Modifier.padding(start = paddingStart, end = paddingEnd),
                        isDownloading = isDownloading,
                        isPending = isPending,
                        downloadProgress = downloadProgress,
                    )
                }
            }
        } ?: run { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }

        ItemTopBar(
            hasBackButton = true,
            hasHomeButton = true,
            onBackClick = { onAction(SeasonAction.OnBackClick) },
            onHomeClick = { onAction(SeasonAction.OnHomeClick) },
        ) {
            Spacer(modifier = Modifier.width(4.dp))
            state.season?.let { season ->
                Button(
                    onClick = { onAction(SeasonAction.NavigateToSeries(season.seriesId)) },
                    modifier = Modifier.alpha(0.7f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color.White,
                        ),
                ) {
                    Text(text = season.seriesName, overflow = TextOverflow.Ellipsis, maxLines = 1)
                }
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun SeasonScreenLayoutPreview() {
    FindroidTheme {
        SeasonScreenLayout(
            state = SeasonState(season = dummySeason),
            downloaderState = DownloaderState(),
            onAction = {},
            onDownloaderAction = {},
        )
    }
}
