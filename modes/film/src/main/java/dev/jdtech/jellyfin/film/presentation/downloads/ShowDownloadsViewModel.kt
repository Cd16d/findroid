package dev.jdtech.jellyfin.film.presentation.downloads

import android.content.Context
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloadQueue
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.StorageTransferProgress
import dev.jdtech.jellyfin.models.diskSize
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ShowDownloadsViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val database: ServerDatabaseDao,
    private val downloader: Downloader,
    private val downloadQueue: DownloadQueue,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ShowDownloadsState())
    val state = _state.asStateFlow()

    private var currentSeriesId: UUID? = null
    private var lastCompletedCount = -1

    init {
        viewModelScope.launch {
            downloadQueue.entries.collect { entries ->
                _state.update { it.copy(activeDownloads = entries) }
                val currentId = currentSeriesId
                val completedCount = entries.count {
                    it.state is DownloadQueue.EntryState.Completed
                }
                if (lastCompletedCount == -1) {
                    lastCompletedCount = completedCount
                } else if (currentId != null && completedCount > lastCompletedCount) {
                    lastCompletedCount = completedCount
                    loadShow(currentId, showLoading = false)
                }
            }
        }
    }

    fun onAction(action: ShowDownloadsAction) {
        when (action) {
            is ShowDownloadsAction.DeleteEpisode -> deleteEpisode(action.episode)
            is ShowDownloadsAction.StageDeleteEpisode -> stageDeleteEpisode(action.episode)
            is ShowDownloadsAction.UndoDelete -> undoDelete(action.id)
            is ShowDownloadsAction.ToggleSelection -> toggleSelection(action.id)
            is ShowDownloadsAction.ToggleSeasonSelection -> toggleSeasonSelection(action.episodeIds)
            is ShowDownloadsAction.SelectAll -> selectAll()
            is ShowDownloadsAction.ClearSelection -> clearSelection()
            is ShowDownloadsAction.DeleteSelected -> deleteSelected()
            is ShowDownloadsAction.PauseOrResumeSelected -> pauseOrResumeSelected(action.pause)
            is ShowDownloadsAction.EnterSelectionMode ->
                _state.update { it.copy(isSelectionMode = true) }
            is ShowDownloadsAction.ExitSelectionMode -> clearSelection()
            is ShowDownloadsAction.MoveEpisodeStorage ->
                moveEpisodeStorage(action.episode, action.targetStorageIndex)
            is ShowDownloadsAction.MoveSelected -> moveSelected(action.targetStorageIndex)
            is ShowDownloadsAction.CancelDownload -> downloadQueue.cancel(action.id)
            is ShowDownloadsAction.PauseDownload -> downloadQueue.pause(action.id)
            is ShowDownloadsAction.ResumeDownload -> downloadQueue.resume(action.id)
        }
    }

    fun loadShow(seriesId: UUID, showLoading: Boolean = true) {
        currentSeriesId = seriesId
        viewModelScope.launch {
            if (showLoading) {
                _state.update { it.copy(isLoading = true, error = null) }
            }
            try {
                val currentUserId = repository.getUserId()
                withContext(Dispatchers.IO) {
                    val dirs = context.getExternalFilesDirs(null)
                    val hasSdCard =
                        dirs.size > 1 &&
                            dirs[1] != null &&
                            android.os.Environment.getExternalStorageState(dirs[1]) ==
                                android.os.Environment.MEDIA_MOUNTED
                    val showDto =
                        try {
                            database.getShow(seriesId)
                        } catch (_: Exception) {
                            null
                        }
                    val show =
                        showDto?.toFindroidShow(database, currentUserId)
                            ?: run {
                                val queueEp =
                                    downloadQueue.entries.value
                                        .map { it.item }
                                        .filterIsInstance<FindroidEpisode>()
                                        .firstOrNull { it.seriesId == seriesId }
                                if (queueEp != null) {
                                    FindroidShow(
                                        id = seriesId,
                                        name = queueEp.seriesName,
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
                                        images = queueEp.images,
                                    )
                                } else null
                            }

                    val episodes =
                        try {
                            database
                                .getDownloadedEpisodesByShowAndUser(seriesId, currentUserId)
                                .map { it.toFindroidEpisode(database, currentUserId) }
                        } catch (_: Exception) {
                            emptyList()
                        }

                    val totalBytes = episodes.sumOf { it.diskSize() }
                    val totalSizeFormatted = Formatter.formatFileSize(context, totalBytes)

                    val groupedBySeason =
                        episodes
                            .groupBy { it.parentIndexNumber }
                            .toSortedMap()
                            .map { (seasonNum, seasonEps) ->
                                val epCount = seasonEps.size
                                val epStr =
                                    context.resources.getQuantityString(
                                        CoreR.plurals.episodes_count,
                                        epCount,
                                        epCount,
                                    )
                                val seasonTitle =
                                    if (seasonNum > 0)
                                        context.getString(
                                            CoreR.string.season_range_single,
                                            seasonNum,
                                        )
                                    else context.getString(CoreR.string.specials)
                                val headerTitle = "$seasonTitle • $epStr"
                                SeasonEpisodeGroup(
                                    seasonNumber = seasonNum,
                                    headerTitle = headerTitle,
                                    episodes = seasonEps,
                                )
                            }

                    _state.update {
                        it.copy(
                            isLoading = false,
                            show = show,
                            seasonGroups = groupedBySeason,
                            totalEpisodesCount = episodes.size,
                            totalDiskSizeFormatted = totalSizeFormatted,
                            hasSdCard = hasSdCard,
                            displayExtraInfo =
                                appPreferences.getValue(appPreferences.displayExtraInfo),
                            error = null,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e) }
            }
        }
    }

    private val deletionJobs = mutableMapOf<UUID, Job>()

    private suspend fun deleteMediaItem(episode: FindroidEpisode, currentUserId: UUID) {
        val source =
            episode.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                ?: episode.sources.firstOrNull()
                ?: return
        downloader.deleteItem(episode, source, currentUserId)
    }

    private fun deleteEpisode(episode: FindroidEpisode) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentUserId = repository.getUserId()
            downloadQueue.cancel(episode.id)
            deleteMediaItem(episode, currentUserId)
            currentSeriesId?.let { loadShow(it, showLoading = false) }
        }
    }

    private fun stageDeleteEpisode(episode: FindroidEpisode) {
        val id = episode.id
        val currentUserId = repository.getUserId()
        deletionJobs[id]?.cancel()
        val currentMap = _state.value.pendingDeletionIds.toMutableMap()
        currentMap[id] = 5
        _state.update { it.copy(pendingDeletionIds = currentMap) }

        val job = viewModelScope.launch {
            for (sec in 4 downTo 1) {
                delay(1000L.milliseconds)
                _state.update { it.copy(pendingDeletionIds = it.pendingDeletionIds + (id to sec)) }
            }
            delay(1000L.milliseconds)
            _state.update { it.copy(pendingDeletionIds = it.pendingDeletionIds - id) }
            deletionJobs.remove(id)
            withContext(Dispatchers.IO) {
                downloadQueue.cancel(episode.id)
                deleteMediaItem(episode, currentUserId)
            }
            currentSeriesId?.let { loadShow(it, showLoading = false) }
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

    private fun toggleSelection(id: UUID) {
        val current = _state.value.selectedEpisodeIds
        val updated = if (current.contains(id)) current - id else current + id
        _state.update {
            it.copy(
                selectedEpisodeIds = updated,
                isSelectionMode = updated.isNotEmpty(),
            )
        }
    }

    private fun toggleSeasonSelection(episodeIds: Set<UUID>) {
        if (episodeIds.isEmpty()) return
        val current = _state.value.selectedEpisodeIds
        val allSelected = episodeIds.all { current.contains(it) }
        val updated = if (allSelected) current - episodeIds else current + episodeIds
        _state.update {
            it.copy(
                selectedEpisodeIds = updated,
                isSelectionMode = updated.isNotEmpty(),
            )
        }
    }

    private fun selectAll() {
        val currentSeries = currentSeriesId
        val queuedIds =
            _state.value.activeDownloads
                .map { it.item }
                .filterIsInstance<FindroidEpisode>()
                .filter { currentSeries == null || it.seriesId == currentSeries }
                .map { it.id }
        val dbIds = _state.value.seasonGroups.flatMap { it.episodes }.map { it.id }
        val allIds = (dbIds + queuedIds).toSet()

        if (_state.value.selectedEpisodeIds.size == allIds.size && allIds.isNotEmpty()) {
            clearSelection()
        } else {
            _state.update {
                it.copy(
                    selectedEpisodeIds = allIds,
                    isSelectionMode = true,
                )
            }
        }
    }

    private fun pauseOrResumeSelected(pause: Boolean) {
        val selected = _state.value.selectedEpisodeIds
        for (id in selected) {
            if (pause) downloadQueue.pause(id) else downloadQueue.resume(id)
        }
    }

    private fun clearSelection() {
        _state.update {
            it.copy(
                selectedEpisodeIds = emptySet(),
                isSelectionMode = false,
            )
        }
    }

    private fun deleteSelected() {
        val selected = _state.value.selectedEpisodeIds
        if (selected.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            for (id in selected) {
                downloadQueue.cancel(id)
            }
            val currentUserId = repository.getUserId()
            for (episode in
                _state.value.seasonGroups
                    .flatMap { it.episodes }
                    .filter { selected.contains(it.id) }) {
                deleteMediaItem(episode, currentUserId)
            }
            clearSelection()
            currentSeriesId?.let { loadShow(it, showLoading = false) }
        }
    }

    private suspend fun performMoveStorage(
        id: UUID,
        episode: FindroidEpisode,
        targetStorageIndex: Int,
    ) {
        _state.update {
            it.copy(
                activeTransfers = it.activeTransfers + (id to StorageTransferProgress(itemId = id))
            )
        }
        downloader.moveItemStorage(episode, targetStorageIndex) { bytesTransferred, totalBytes ->
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
        _state.update { it.copy(activeTransfers = it.activeTransfers - id) }
    }

    private fun moveEpisodeStorage(episode: FindroidEpisode, targetStorageIndex: Int) {
        viewModelScope.launch {
            performMoveStorage(episode.id, episode, targetStorageIndex)
            currentSeriesId?.let { loadShow(it, showLoading = false) }
        }
    }

    private fun moveSelected(targetStorageIndex: Int) {
        viewModelScope.launch {
            val selected = _state.value.selectedEpisodeIds.toList()
            val allEpisodes = _state.value.seasonGroups.flatMap { it.episodes }
            clearSelection()
            for (id in selected) {
                val ep = allEpisodes.firstOrNull { it.id == id } ?: continue
                performMoveStorage(id, ep, targetStorageIndex)
            }
            currentSeriesId?.let { loadShow(it, showLoading = false) }
        }
    }

    override fun onCleared() {
        for (job in deletionJobs.values) {
            job.cancel()
        }
        deletionJobs.clear()
    }
}
