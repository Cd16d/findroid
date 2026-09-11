package dev.jdtech.jellyfin.film.presentation.downloads

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.StorageTransferProgress
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class DownloadsViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
    private val downloadQueue: DownloadQueue,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        repository: JellyfinRepository,
        database: ServerDatabaseDao,
        downloader: Downloader,
        downloadQueue: DownloadQueue,
        appPreferences: AppPreferences,
        context: Context,
        ioDispatcher: CoroutineDispatcher,
    ) : this(repository, database, downloader, downloadQueue, appPreferences, context) {
        this.ioDispatcher = ioDispatcher
    }

    private val _state = MutableStateFlow(DownloadsState())
    val state = _state.asStateFlow()

    private var lastCompletedCount = -1

    init {
        loadItems(showLoading = true)
        viewModelScope.launch {
            downloadQueue.entries.collect { queueEntries ->
                val active = queueEntries.filter { it.state !is DownloadQueue.EntryState.Completed }
                val completedCount = queueEntries.count {
                    it.state is DownloadQueue.EntryState.Completed
                }

                _state.update { it.copy(activeDownloads = active, allQueueEntries = queueEntries) }

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
            is DownloadsAction.EnterSelectionMode ->
                _state.update { it.copy(isSelectionMode = true) }
            is DownloadsAction.ExitSelectionMode -> clearSelection()
            is DownloadsAction.MoveItemStorage ->
                moveItemStorage(action.id, action.targetStorageIndex)
            is DownloadsAction.MoveSelected -> moveSelected(action.targetStorageIndex)
            is DownloadsAction.CancelDownload -> downloadQueue.cancel(action.id)
            is DownloadsAction.ResumeDownload -> resumeDownload(action.id)
            is DownloadsAction.PauseDownload -> pauseDownload(action.id)
        }
    }

    fun loadItems(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            try {
                val currentServerId = appPreferences.getValue(appPreferences.currentServer)
                if (currentServerId == null) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                val currentUserId = repository.getUserId()

                withContext(ioDispatcher) {
                    val movies = fetchDownloadedMovies(currentUserId)
                    val showItems = fetchDownloadedShows(currentUserId)

                    val totalUsedBytes =
                        movies.sumOf { it.diskSize() } + showItems.sumOf { it.totalDiskSize }
                    val storageInfo = computeStorageInfo(totalUsedBytes)

                    _state.update {
                        it.copy(
                            isLoading = false,
                            movies = movies,
                            shows = showItems,
                            usedStorageFormatted = storageInfo.usedStorageFormatted,
                            freeStorageFormatted = storageInfo.freeStorageFormatted,
                            deviceUsedFormatted = storageInfo.deviceUsedFormatted,
                            deviceTotalFormatted = storageInfo.deviceTotalFormatted,
                            downloadedFraction = storageInfo.downloadedFraction,
                            otherUsedFraction = storageInfo.otherUsedFraction,
                            freeFraction = storageInfo.freeFraction,
                            hasSdCard = storageInfo.hasSdCard,
                            displayExtraInfo =
                                appPreferences.getValue(appPreferences.displayExtraInfo),
                            isSmartDownloadsActive =
                                appPreferences.getValue(appPreferences.smartDownloadNextEpisode),
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e) }
            }
        }
    }

    private suspend fun fetchDownloadedMovies(currentUserId: UUID): List<FindroidMovie> {
        val moviesDto = database.getDownloadedMoviesByUser(currentUserId)
        return moviesDto.map { it.toFindroidMovie(database, currentUserId) }
    }

    private suspend fun fetchDownloadedShows(currentUserId: UUID): List<DownloadedShowItem> {
        val showsDto = database.getDownloadedShowsByUser(currentUserId)
        val showItems = mutableListOf<DownloadedShowItem>()
        for (showDto in showsDto) {
            val show = showDto.toFindroidShow(database, currentUserId)
            val episodes =
                database.getDownloadedEpisodesByShowAndUser(show.id, currentUserId).map {
                    it.toFindroidEpisode(database, currentUserId)
                }
            if (episodes.isNotEmpty()) {
                val totalSize = show.totalDiskSize(episodes)
                val seasonNumbers = episodes.mapNotNull { it.parentIndexNumber }.distinct().sorted()
                val seasonsFormatted =
                    formatSeasonsString(
                        seasonNumbers = seasonNumbers,
                        episodeCount = episodes.size,
                        context = context,
                        seasonSingleRes = CoreR.string.season_range_single,
                        seasonPairRes = CoreR.string.season_range_pair,
                        seasonMultipleRes = CoreR.string.season_range_multiple,
                        episodesPluralRes = CoreR.plurals.episodes_count,
                    )
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
        return showItems
    }

    private data class StorageInfo(
        val usedStorageFormatted: String,
        val freeStorageFormatted: String,
        val deviceUsedFormatted: String,
        val deviceTotalFormatted: String,
        val downloadedFraction: Float,
        val otherUsedFraction: Float,
        val freeFraction: Float,
        val hasSdCard: Boolean,
    )

    private fun computeStorageInfo(totalUsedBytes: Long): StorageInfo {
        val usedStorageFormatted = StorageUtils.formatDecimalFileSize(totalUsedBytes)

        val preferredStorageIndex =
            appPreferences.getValue(appPreferences.defaultDownloadStorageIndex).toIntOrNull() ?: 0
        val dirs = context.getExternalFilesDirs(null)
        val hasSdCard =
            dirs.size > 1 &&
                dirs[1] != null &&
                Environment.getExternalStorageState(dirs[1]) == Environment.MEDIA_MOUNTED
        val mountedDirs =
            dirs.filterNotNull().filter {
                Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
            }
        val storageDir =
            dirs.getOrNull(preferredStorageIndex)?.takeIf {
                Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
            } ?: mountedDirs.firstOrNull() ?: context.filesDir
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
        val deviceUsedFormatted =
            if (totalDeviceBytes > 0) StorageUtils.formatDecimalFileSize(deviceUsedBytes) else ""
        val deviceTotalFormatted =
            if (totalDeviceBytes > 0) StorageUtils.formatDecimalFileSize(totalDeviceBytes) else ""

        val downloadedFraction =
            if (totalDeviceBytes > 0) (totalUsedBytes.toFloat() / totalDeviceBytes).coerceIn(0f, 1f)
            else 0f
        val freeFraction =
            if (totalDeviceBytes > 0) (availableBytes.toFloat() / totalDeviceBytes).coerceIn(0f, 1f)
            else 0f
        val otherUsedFraction = (1f - downloadedFraction - freeFraction).coerceAtLeast(0f)

        return StorageInfo(
            usedStorageFormatted = usedStorageFormatted,
            freeStorageFormatted = freeStorageFormatted,
            deviceUsedFormatted = deviceUsedFormatted,
            deviceTotalFormatted = deviceTotalFormatted,
            downloadedFraction = downloadedFraction,
            otherUsedFraction = otherUsedFraction,
            freeFraction = freeFraction,
            hasSdCard = hasSdCard,
        )
    }

    private val deletionJobs = mutableMapOf<UUID, Job>()

    private suspend fun deleteMediaItem(item: FindroidItem, currentUserId: UUID) {
        val source =
            item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                ?: item.sources.firstOrNull()
                ?: return
        downloader.deleteItem(item, source, currentUserId)
    }

    private fun deleteMovie(movie: FindroidMovie) {
        viewModelScope.launch(ioDispatcher) {
            val currentUserId = repository.getUserId()
            downloadQueue.cancel(movie.id)
            deleteMediaItem(movie, currentUserId)
            loadItems(showLoading = false)
        }
    }

    private fun deleteShow(showItem: DownloadedShowItem) {
        viewModelScope.launch(ioDispatcher) {
            val currentUserId = repository.getUserId()
            val queued =
                downloadQueue.entries.value.filter {
                    it.item is FindroidEpisode &&
                        (it.item as FindroidEpisode).seriesId == showItem.show.id
                }
            for (entry in queued) {
                downloadQueue.cancel(entry.id)
            }
            for (episode in showItem.episodes) {
                deleteMediaItem(episode, currentUserId)
            }
            loadItems(showLoading = false)
        }
    }

    private fun stageDeleteMovie(movie: FindroidMovie) {
        val currentUserId = repository.getUserId()
        startPendingDeletion(movie.id) {
            val source =
                movie.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
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
            val queued =
                downloadQueue.entries.value.filter {
                    it.item is FindroidEpisode &&
                        (it.item as FindroidEpisode).seriesId == showItem.show.id
                }
            for (entry in queued) {
                downloadQueue.cancel(entry.id)
            }
            for (episode in showItem.episodes) {
                deleteMediaItem(episode, currentUserId)
            }
        }
    }

    private fun startPendingDeletion(id: UUID, onCommit: suspend () -> Unit) {
        deletionJobs[id]?.cancel()
        val currentMap = _state.value.pendingDeletionIds.toMutableMap()
        currentMap[id] = 5
        _state.update { it.copy(pendingDeletionIds = currentMap) }

        val job = viewModelScope.launch {
            for (sec in 4 downTo 1) {
                delay(1000L)
                _state.update { it.copy(pendingDeletionIds = it.pendingDeletionIds + (id to sec)) }
            }
            delay(1000L)
            _state.update { it.copy(pendingDeletionIds = it.pendingDeletionIds - id) }
            deletionJobs.remove(id)
            withContext(ioDispatcher) { onCommit() }
            loadItems(showLoading = false)
        }
        deletionJobs[id] = job
    }

    private fun undoDelete(id: UUID? = null) {
        if (id != null) {
            deletionJobs[id]?.cancel()
            deletionJobs.remove(id)
            _state.update { it.copy(pendingDeletionIds = it.pendingDeletionIds - id) }
        } else {
            deletionJobs.values.forEach { it.cancel() }
            deletionJobs.clear()
            _state.update { it.copy(pendingDeletionIds = emptyMap()) }
        }
    }

    private fun pauseDownload(id: UUID) {
        downloadQueue.pause(id)
        val queued =
            downloadQueue.entries.value.filter {
                it.item is FindroidEpisode && (it.item as FindroidEpisode).seriesId == id
            }
        for (entry in queued) {
            downloadQueue.pause(entry.id)
        }
    }

    private fun resumeDownload(id: UUID) {
        downloadQueue.resume(id)
        val queued =
            downloadQueue.entries.value.filter {
                it.item is FindroidEpisode && (it.item as FindroidEpisode).seriesId == id
            }
        for (entry in queued) {
            downloadQueue.resume(entry.id)
        }
    }

    private fun toggleSelection(id: UUID) {
        val current = _state.value.selectedItemIds
        val updated = if (current.contains(id)) current - id else current + id
        _state.update {
            it.copy(
                selectedItemIds = updated,
                isSelectionMode = updated.isNotEmpty(),
            )
        }
    }

    private fun selectAll() {
        val activeShowIds =
            _state.value.activeDownloads.mapNotNull { (it.item as? FindroidEpisode)?.seriesId }
        val activeMovieIds =
            _state.value.activeDownloads.mapNotNull { (it.item as? FindroidMovie)?.id }

        val allIds =
            (_state.value.movies.map { it.id } +
                    _state.value.shows.map { it.show.id } +
                    activeShowIds +
                    activeMovieIds)
                .toSet()

        if (_state.value.selectedItemIds.size == allIds.size && allIds.isNotEmpty()) {
            clearSelection()
        } else {
            _state.update {
                it.copy(
                    selectedItemIds = allIds,
                    isSelectionMode = true,
                )
            }
        }
    }

    private fun pauseOrResumeSelected(pause: Boolean) {
        val selected = _state.value.selectedItemIds
        for (id in selected) {
            if (pause) pauseDownload(id) else resumeDownload(id)
        }
    }

    private fun clearSelection() {
        _state.update {
            it.copy(
                selectedItemIds = emptySet(),
                isSelectionMode = false,
            )
        }
    }

    private fun deleteSelected() {
        val selected = _state.value.selectedItemIds
        if (selected.isEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            for (id in selected) {
                coroutineContext.ensureActive()
                downloadQueue.cancel(id)
                val queuedEpisodes =
                    downloadQueue.entries.value.filter {
                        it.item is FindroidEpisode && (it.item as FindroidEpisode).seriesId == id
                    }
                for (entry in queuedEpisodes) {
                    downloadQueue.cancel(entry.id)
                }
            }
            val currentUserId = repository.getUserId()
            for (movie in _state.value.movies.filter { selected.contains(it.id) }) {
                coroutineContext.ensureActive()
                deleteMediaItem(movie, currentUserId)
            }
            for (showItem in _state.value.shows.filter { selected.contains(it.show.id) }) {
                coroutineContext.ensureActive()
                for (episode in showItem.episodes) {
                    coroutineContext.ensureActive()
                    deleteMediaItem(episode, currentUserId)
                }
            }
            clearSelection()
            loadItems()
        }
    }

    private suspend fun performMoveStorage(
        id: UUID,
        item: FindroidItem,
        targetStorageIndex: Int,
    ) {
        _state.update {
            it.copy(
                activeTransfers = it.activeTransfers + (id to StorageTransferProgress(itemId = id))
            )
        }
        try {
            downloader.moveItemStorage(item, targetStorageIndex) { bytesTransferred, totalBytes ->
                val progress =
                    if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f)
                    else 0f
                _state.update {
                    it.copy(
                        activeTransfers =
                            it.activeTransfers +
                                (id to
                                    StorageTransferProgress(
                                        itemId = id,
                                        bytesTransferred = bytesTransferred,
                                        totalBytes = totalBytes,
                                        progress = progress,
                                    ))
                    )
                }
            }
        } finally {
            _state.update { it.copy(activeTransfers = it.activeTransfers - id) }
        }
    }

    private fun moveItemStorage(id: UUID, targetStorageIndex: Int) {
        viewModelScope.launch {
            val movie = _state.value.movies.firstOrNull { it.id == id }
            val showItem = _state.value.shows.firstOrNull { it.show.id == id }
            val item = movie ?: showItem?.show ?: return@launch
            performMoveStorage(id, item, targetStorageIndex)
            loadItems()
        }
    }

    private fun moveSelected(targetStorageIndex: Int) {
        viewModelScope.launch {
            val selected = _state.value.selectedItemIds.toList()
            clearSelection()
            for (id in selected) {
                coroutineContext.ensureActive()
                val movie = _state.value.movies.firstOrNull { it.id == id }
                val showItem = _state.value.shows.firstOrNull { it.show.id == id }
                val item = movie ?: showItem?.show ?: continue
                performMoveStorage(id, item, targetStorageIndex)
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
