package dev.jdtech.jellyfin.film.presentation.downloads

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.diskSize
import dev.jdtech.jellyfin.models.formatSeasonsString
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidShow
import dev.jdtech.jellyfin.models.totalDiskSize
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.utils.StorageUtils
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import timber.log.Timber

sealed interface DownloadsUiEvent {
    data class ShowUndoSnackbar(val message: String) : DownloadsUiEvent
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: JellyfinRepository,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
    private val downloadQueue: DownloadQueue,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DownloadsState())
    val state = _state.asStateFlow()

    private val _uiEvents = MutableSharedFlow<DownloadsUiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    private var pendingDeleteJob: Job? = null
    private var stagedMovies = mutableListOf<FindroidMovie>()
    private var stagedShows = mutableListOf<DownloadedShowItem>()
    private var lastCompletedCount = -1

    init {
        loadItems(showLoading = true)
        viewModelScope.launch {
            downloadQueue.entries.collect { queueEntries ->
                val active = queueEntries.filter { it.state !is DownloadQueue.EntryState.Completed }
                val completedCount = queueEntries.count { it.state is DownloadQueue.EntryState.Completed }

                _state.value = _state.value.copy(activeDownloads = active, allQueueEntries = queueEntries)

                if (lastCompletedCount == -1) {
                    lastCompletedCount = completedCount
                } else if (completedCount > lastCompletedCount) {
                    lastCompletedCount = completedCount
                    loadItems(showLoading = false)
                }
            }
        }
    }

    fun onAction(action: DownloadsAction) {
        when (action) {
            is DownloadsAction.DeleteMovie -> deleteMovie(action.movie)
            is DownloadsAction.DeleteShow -> deleteShow(action.showItem)
            is DownloadsAction.StageDeleteMovie -> stageDeleteMovie(action.movie)
            is DownloadsAction.StageDeleteShow -> stageDeleteShow(action.showItem)
            is DownloadsAction.UndoDelete -> undoDelete(action.id)
            is DownloadsAction.ToggleSelection -> toggleSelection(action.id)
            is DownloadsAction.SelectAll -> selectAll()
            is DownloadsAction.ClearSelection -> clearSelection()
            is DownloadsAction.DeleteSelected -> deleteSelected()
            is DownloadsAction.PauseOrResumeSelected -> pauseOrResumeSelected(action.pause)
            is DownloadsAction.EnterSelectionMode -> _state.value = _state.value.copy(isSelectionMode = true)
            is DownloadsAction.ExitSelectionMode -> clearSelection()
            is DownloadsAction.MoveItemStorage -> moveItemStorage(action.id, action.targetStorageIndex)
            is DownloadsAction.MoveSelected -> moveSelected(action.targetStorageIndex)
            is DownloadsAction.CancelDownload -> downloadQueue.cancel(action.id)
            is DownloadsAction.ResumeDownload -> resumeDownload(action.id)
            is DownloadsAction.PauseDownload -> pauseDownload(action.id)
        }
    }

    fun loadItems(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _state.value = _state.value.copy(isLoading = true, error = null)
            }
            try {
                val currentServerId = appPreferences.getValue(appPreferences.currentServer)
                if (currentServerId == null) {
                    _state.value = _state.value.copy(isLoading = false)
                    return@launch
                }
                val currentUserId = repository.getUserId()

                withContext(Dispatchers.IO) {
                    val moviesDto = database.getDownloadedMoviesByServerAndUser(currentServerId, currentUserId)
                    val movies = moviesDto.map { it.toFindroidMovie(database, currentUserId) }

                    val showsDto = database.getDownloadedShowsByServerAndUser(currentServerId, currentUserId)
                    val showItems = mutableListOf<DownloadedShowItem>()
                    for (showDto in showsDto) {
                        val show = showDto.toFindroidShow(database, currentUserId)
                        val episodes = database.getDownloadedEpisodesByShowAndUser(show.id, currentUserId)
                            .map { it.toFindroidEpisode(database, currentUserId) }
                        if (episodes.isNotEmpty()) {
                            val totalSize = show.totalDiskSize(episodes)
                            val seasonNumbers = episodes.mapNotNull { it.parentIndexNumber }.distinct().sorted()
                            val seasonsFormatted = formatSeasonsString(seasonNumbers, episodes.size)
                            showItems.add(
                                DownloadedShowItem(
                                    show = show,
                                    episodes = episodes,
                                    totalDiskSize = totalSize,
                                    seasonsFormatted = seasonsFormatted,
                                )
                            )
                        }
                    }

                    // Storage calculations
                    val totalUsedBytes = movies.sumOf { it.diskSize() } + showItems.sumOf { it.totalDiskSize }
                    val usedStorageFormatted = StorageUtils.formatDecimalFileSize(totalUsedBytes)

                    val preferredStorageIndex =
                        appPreferences.getValue(appPreferences.defaultDownloadStorageIndex).toIntOrNull() ?: 0
                    val dirs = context.getExternalFilesDirs(null)
                    val hasSdCard = dirs.size > 1 && dirs[1] != null && android.os.Environment.getExternalStorageState(dirs[1]) == android.os.Environment.MEDIA_MOUNTED
                    val mountedDirs = dirs.filterNotNull().filter { android.os.Environment.getExternalStorageState(it) == android.os.Environment.MEDIA_MOUNTED }
                    val storageDir = dirs.getOrNull(preferredStorageIndex)?.takeIf { android.os.Environment.getExternalStorageState(it) == android.os.Environment.MEDIA_MOUNTED }
                        ?: mountedDirs.firstOrNull()
                        ?: context.filesDir
                    var availableBytes = 0L
                    var totalDeviceBytes = 0L
                    try {
                        val statFs = StatFs(storageDir.path)
                        availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
                        totalDeviceBytes = StorageUtils.getTotalStorageBytes(context, storageDir.path)
                    } catch (e: Exception) {
                        Timber.e(e, "Error calculating StatFs in DownloadsViewModel")
                    }
                    val freeStorageFormatted = StorageUtils.formatDecimalFileSize(availableBytes)
                    val deviceUsedBytes = (totalDeviceBytes - availableBytes).coerceAtLeast(0L)
                    val deviceUsedFormatted = if (totalDeviceBytes > 0) StorageUtils.formatDecimalFileSize(deviceUsedBytes) else ""
                    val deviceTotalFormatted = if (totalDeviceBytes > 0) StorageUtils.formatDecimalFileSize(totalDeviceBytes) else ""
                    val displayExtraInfo = appPreferences.getValue(appPreferences.displayExtraInfo)

                    val downloadedFraction = if (totalDeviceBytes > 0) (totalUsedBytes.toFloat() / totalDeviceBytes).coerceIn(0f, 1f) else 0f
                    val freeFraction = if (totalDeviceBytes > 0) (availableBytes.toFloat() / totalDeviceBytes).coerceIn(0f, 1f) else 0f
                    val otherUsedFraction = (1f - downloadedFraction - freeFraction).coerceAtLeast(0f)

                    _state.value = _state.value.copy(
                        isLoading = false,
                        movies = movies,
                        shows = showItems,
                        usedStorageFormatted = usedStorageFormatted,
                        freeStorageFormatted = freeStorageFormatted,
                        deviceUsedFormatted = deviceUsedFormatted,
                        deviceTotalFormatted = deviceTotalFormatted,
                        downloadedFraction = downloadedFraction,
                        otherUsedFraction = otherUsedFraction,
                        freeFraction = freeFraction,
                        hasSdCard = hasSdCard,
                        displayExtraInfo = displayExtraInfo,
                        isSmartDownloadsActive = appPreferences.getValue(appPreferences.smartDownloadNextEpisode),
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e)
            }
        }
    }

    private val deletionJobs = mutableMapOf<UUID, Job>()

    private fun deleteMovie(movie: FindroidMovie) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentUserId = repository.getUserId()
            downloadQueue.cancel(movie.id)
            val source = movie.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                ?: movie.sources.firstOrNull()
            if (source != null) {
                downloader.deleteItem(movie, source, currentUserId)
            }
            loadItems(showLoading = false)
        }
    }

    private fun deleteShow(showItem: DownloadedShowItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentUserId = repository.getUserId()
            // Cancel all queue entries of this series
            val queued = downloadQueue.entries.value.filter {
                it.item is FindroidEpisode && (it.item as FindroidEpisode).seriesId == showItem.show.id
            }
            for (entry in queued) {
                downloadQueue.cancel(entry.id)
            }
            for (episode in showItem.episodes) {
                val source = episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                    ?: episode.sources.firstOrNull()
                if (source != null) {
                    downloader.deleteItem(episode, source, currentUserId)
                }
            }
            loadItems(showLoading = false)
        }
    }

    private fun stageDeleteMovie(movie: FindroidMovie) {
        val currentUserId = repository.getUserId()
        startPendingDeletion(movie.id) {
            val source = movie.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                ?: movie.sources.firstOrNull()
            if (source != null) {
                downloader.deleteItem(movie, source, currentUserId)
            }
            downloadQueue.cancel(movie.id)
        }
    }

    private fun stageDeleteShow(showItem: DownloadedShowItem) {
        val currentUserId = repository.getUserId()
        startPendingDeletion(showItem.show.id) {
            val queued = downloadQueue.entries.value.filter {
                it.item is FindroidEpisode && (it.item as FindroidEpisode).seriesId == showItem.show.id
            }
            for (entry in queued) {
                downloadQueue.cancel(entry.id)
            }
            for (episode in showItem.episodes) {
                val source = episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                    ?: episode.sources.firstOrNull()
                if (source != null) {
                    downloader.deleteItem(episode, source, currentUserId)
                }
            }
        }
    }

    private fun startPendingDeletion(id: UUID, onCommit: suspend () -> Unit) {
        deletionJobs[id]?.cancel()
        val currentMap = _state.value.pendingDeletionIds.toMutableMap()
        currentMap[id] = 5
        _state.value = _state.value.copy(pendingDeletionIds = currentMap)

        val job = viewModelScope.launch {
            for (sec in 4 downTo 1) {
                delay(1000L)
                _state.value = _state.value.copy(
                    pendingDeletionIds = _state.value.pendingDeletionIds + (id to sec)
                )
            }
            delay(1000L)
            _state.value = _state.value.copy(
                pendingDeletionIds = _state.value.pendingDeletionIds - id
            )
            deletionJobs.remove(id)
            withContext(Dispatchers.IO) {
                onCommit()
            }
            loadItems(showLoading = false)
        }
        deletionJobs[id] = job
    }

    private fun undoDelete(id: UUID? = null) {
        if (id != null) {
            deletionJobs[id]?.cancel()
            deletionJobs.remove(id)
            _state.value = _state.value.copy(
                pendingDeletionIds = _state.value.pendingDeletionIds - id
            )
        } else {
            deletionJobs.values.forEach { it.cancel() }
            deletionJobs.clear()
            _state.value = _state.value.copy(pendingDeletionIds = emptyMap())
        }
    }

    private fun pauseDownload(id: UUID) {
        downloadQueue.pause(id)
        val queued = downloadQueue.entries.value.filter {
            it.item is FindroidEpisode && (it.item as FindroidEpisode).seriesId == id
        }
        for (entry in queued) {
            downloadQueue.pause(entry.id)
        }
    }

    private fun resumeDownload(id: UUID) {
        downloadQueue.resume(id)
        val queued = downloadQueue.entries.value.filter {
            it.item is FindroidEpisode && (it.item as FindroidEpisode).seriesId == id
        }
        for (entry in queued) {
            downloadQueue.resume(entry.id)
        }
    }

    private fun toggleSelection(id: UUID) {
        val current = _state.value.selectedItemIds
        val updated = if (current.contains(id)) current - id else current + id
        _state.value = _state.value.copy(
            selectedItemIds = updated,
            isSelectionMode = updated.isNotEmpty(),
        )
    }

    private fun selectAll() {
        val activeShowIds = _state.value.activeDownloads
            .map { it.item }
            .filterIsInstance<FindroidEpisode>()
            .map { it.seriesId }
        val activeMovieIds = _state.value.activeDownloads
            .map { it.item }
            .filterIsInstance<FindroidMovie>()
            .map { it.id }

        val allIds = (_state.value.movies.map { it.id } + _state.value.shows.map { it.show.id } + activeShowIds + activeMovieIds).toSet()

        if (_state.value.selectedItemIds.size == allIds.size && allIds.isNotEmpty()) {
            clearSelection()
        } else {
            _state.value = _state.value.copy(
                selectedItemIds = allIds,
                isSelectionMode = true,
            )
        }
    }

    private fun pauseOrResumeSelected(pause: Boolean) {
        val selected = _state.value.selectedItemIds
        for (id in selected) {
            if (pause) pauseDownload(id) else resumeDownload(id)
        }
    }

    private fun clearSelection() {
        _state.value = _state.value.copy(
            selectedItemIds = emptySet(),
            isSelectionMode = false,
        )
    }

    private fun deleteSelected() {
        val selected = _state.value.selectedItemIds
        if (selected.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            for (id in selected) {
                downloadQueue.cancel(id)
                val queuedEpisodes = downloadQueue.entries.value.filter {
                    it.item is FindroidEpisode && (it.item as FindroidEpisode).seriesId == id
                }
                for (entry in queuedEpisodes) {
                    downloadQueue.cancel(entry.id)
                }
            }
            val moviesToDelete = _state.value.movies.filter { selected.contains(it.id) }
            val showsToDelete = _state.value.shows.filter { selected.contains(it.show.id) }

            val currentUserId = repository.getUserId()
            for (movie in moviesToDelete) {
                val source = movie.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                    ?: movie.sources.firstOrNull()
                if (source != null) {
                    downloader.deleteItem(movie, source, currentUserId)
                }
            }
            for (showItem in showsToDelete) {
                for (episode in showItem.episodes) {
                    val source = episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                        ?: episode.sources.firstOrNull()
                    if (source != null) {
                        downloader.deleteItem(episode, source, currentUserId)
                    }
                }
            }
            clearSelection()
            loadItems()
        }
    }

    private fun moveItemStorage(id: UUID, targetStorageIndex: Int) {
        viewModelScope.launch {
            val movie = _state.value.movies.firstOrNull { it.id == id }
            val showItem = _state.value.shows.firstOrNull { it.show.id == id }
            val item = movie ?: showItem?.show ?: return@launch

            _state.value =
                _state.value.copy(
                    activeTransfers =
                        _state.value.activeTransfers +
                            (id to dev.jdtech.jellyfin.models.StorageTransferProgress(itemId = id))
                )
            downloader.moveItemStorage(item, targetStorageIndex) { bytesTransferred, totalBytes ->
                val progress =
                    if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                _state.value =
                    _state.value.copy(
                        activeTransfers =
                            _state.value.activeTransfers +
                                (id to
                                    dev.jdtech.jellyfin.models.StorageTransferProgress(
                                        itemId = id,
                                        bytesTransferred = bytesTransferred,
                                        totalBytes = totalBytes,
                                        progress = progress,
                                    ))
                    )
            }
            _state.value = _state.value.copy(activeTransfers = _state.value.activeTransfers - id)
            loadItems()
        }
    }

    private fun moveSelected(targetStorageIndex: Int) {
        viewModelScope.launch {
            val selected = _state.value.selectedItemIds.toList()
            clearSelection()
            for (id in selected) {
                val movie = _state.value.movies.firstOrNull { it.id == id }
                val showItem = _state.value.shows.firstOrNull { it.show.id == id }
                val item = movie ?: showItem?.show ?: continue

                _state.value =
                    _state.value.copy(
                        activeTransfers =
                            _state.value.activeTransfers +
                                (id to dev.jdtech.jellyfin.models.StorageTransferProgress(itemId = id))
                    )
                downloader.moveItemStorage(item, targetStorageIndex) { bytesTransferred, totalBytes ->
                    val progress =
                        if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                    _state.value =
                        _state.value.copy(
                            activeTransfers =
                                _state.value.activeTransfers +
                                    (id to
                                        dev.jdtech.jellyfin.models.StorageTransferProgress(
                                            itemId = id,
                                            bytesTransferred = bytesTransferred,
                                            totalBytes = totalBytes,
                                            progress = progress,
                                        ))
                        )
                }
                _state.value = _state.value.copy(activeTransfers = _state.value.activeTransfers - id)
            }
            loadItems()
        }
    }

    override fun onCleared() {
        super.onCleared()
        for (job in deletionJobs.values) {
            job.cancel()
        }
        deletionJobs.clear()
    }
}
