package dev.jdtech.jellyfin.core.presentation.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.DownloadQualityPresets
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.download.DownloadStatus
import dev.jdtech.jellyfin.work.SyncWorker
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Central download scheduler. Holds a list of queued/active downloads, respects the
 * max-concurrent-downloads setting (sequential by default), and drives Downloader.downloadItem() as
 * slots open up. Single source of truth for download state across the app.
 */
@Singleton
class DownloadQueue
@Inject
constructor(
    private val downloader: Downloader,
    private val appPreferences: AppPreferences,
    private val repositoryProvider: Provider<JellyfinRepository>,
    private val database: ServerDatabaseDao,
    @ApplicationContext private val context: Context,
) {
    private val repository: JellyfinRepository
        get() = repositoryProvider.get()

    sealed interface EntryState {
        data object Pending : EntryState
        data object Downloading : EntryState
        data object Converting : EntryState
        data object Paused : EntryState
        data object Completed : EntryState
        data class Failed(val error: UiText?) : EntryState
    }

    data class Entry(
        val id: UUID,
        val item: FindroidItem,
        val addedAt: Long,
        val state: EntryState,
        val downloadId: Long? = null,
        val startedAt: Long? = null,
        val progress: Int = 0,
        val bytesDownloaded: Long = -1L,
        val totalBytes: Long = -1L,
        val totalBytesEstimated: Boolean = false,
        val isTranscode: Boolean = false,
        val bytesPerSecond: Long = 0L,
        val etaSeconds: Long = -1L,
        val retryCount: Int = 0,
        val retryAt: Long? = null,
        val presetId: String? = null,
        val downloadExternalAudio: Boolean = false,
        val storageIndex: Int = -1,
        val audioStreamIndex: Int? = null,
    )

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var pumpJob: Job? = null

    private val speedTrackers = mutableMapOf<Long, DownloadSpeedTracker>()

    suspend fun enqueue(
        item: FindroidItem,
        presetId: String? = null,
        downloadExternalAudio: Boolean = false,
        storageIndex: Int = -1,
        audioStreamIndex: Int? = null,
    ) {
        mutex.withLock {
            if (_entries.value.any { it.id == item.id && it.state !is EntryState.Failed && it.state !is EntryState.Completed }) {
                return@withLock
            }
            val filtered = _entries.value.filterNot { it.id == item.id }
            val isTranscoding = DownloadQualityPresets.isTranscodingPreset(
                presetId ?: appPreferences.getValue(appPreferences.defaultTranscodePresetId),
                appPreferences,
            )
            val newEntry =
                Entry(
                    id = item.id,
                    item = item,
                    addedAt = System.currentTimeMillis(),
                    state = EntryState.Pending,
                    presetId = presetId,
                    downloadExternalAudio = downloadExternalAudio,
                    storageIndex = storageIndex,
                    audioStreamIndex = audioStreamIndex,
                    isTranscode = isTranscoding,
                )
            _entries.value = sort(filtered + newEntry)
        }
        ensurePump()
    }

    suspend fun restoreAll() {
        val active =
            try {
                downloader.getActiveDownloads()
            } catch (e: Exception) {
                Timber.e(e, "Failed to query active downloads for restore")
                emptyList()
            }
        if (active.isEmpty()) return
        mutex.withLock {
            val known = _entries.value.map { it.id }.toSet()
            val now = System.currentTimeMillis()
            val addedActive =
                active.filter { (item, _) -> item.id !in known }.map { (item, dlId) ->
                    Entry(
                        id = item.id,
                        item = item,
                        addedAt = now - 1,
                        state = EntryState.Pending,
                        downloadId = dlId,
                    )
                }
            _entries.value = sort(_entries.value + addedActive)
        }
        ensurePump()
    }

    fun cancel(id: UUID) {
        scope.launch {
            var toCancel: Entry? = null
            mutex.withLock {
                val entry = _entries.value.firstOrNull { it.id == id } ?: return@withLock
                toCancel = entry
                _entries.value = _entries.value.filter { it.id != id }
            }
            toCancel?.let { entry ->
                entry.downloadId?.let { dlId ->
                    speedTrackers.remove(dlId)
                    try {
                        downloader.cancelDownload(entry.item, dlId)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to cancel download ${entry.item.name}")
                    }
                }
            }
        }
    }

    fun retry(id: UUID) {
        scope.launch {
            mutex.withLock {
                _entries.value =
                    sort(
                        _entries.value.map { entry ->
                            if (entry.id == id && entry.state is EntryState.Failed) {
                                entry.copy(
                                    state = EntryState.Pending,
                                    addedAt = System.currentTimeMillis(),
                                    downloadId = null,
                                    startedAt = null,
                                    progress = 0,
                                    retryCount = 0,
                                    retryAt = null,
                                )
                            } else {
                                entry
                            }
                        }
                    )
            }
            ensurePump()
        }
    }

    fun cancelAll() {
        scope.launch {
            val toCancel = mutableListOf<Entry>()
            mutex.withLock {
                toCancel.addAll(_entries.value)
                _entries.value = emptyList()
            }
            for (entry in toCancel) {
                entry.downloadId?.let { dlId ->
                    speedTrackers.remove(dlId)
                    try {
                        downloader.cancelDownload(entry.item, dlId)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to cancel download ${entry.item.name}")
                    }
                }
            }
        }
    }

    fun pause(id: UUID) {
        scope.launch {
            var dlIdToPause: Long? = null
            mutex.withLock {
                _entries.value = sort(_entries.value.map { entry ->
                    if (entry.id == id && (entry.state is EntryState.Downloading || entry.state is EntryState.Converting || entry.state is EntryState.Pending)) {
                        if (entry.state is EntryState.Downloading || entry.state is EntryState.Converting) {
                            dlIdToPause = entry.downloadId
                        }
                        entry.copy(state = EntryState.Paused, bytesPerSecond = 0L)
                    } else entry
                })
            }
            dlIdToPause?.let { dlId ->
                speedTrackers[dlId]?.reset()
                try {
                    downloader.pauseDownload(dlId)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to pause download $dlId")
                }
            }
        }
    }

    fun resume(id: UUID) {
        scope.launch {
            var dlIdToResume: Long? = null
            mutex.withLock {
                _entries.value = sort(_entries.value.map { entry ->
                    if (entry.id == id && entry.state is EntryState.Paused) {
                        if (entry.downloadId != null) {
                            dlIdToResume = entry.downloadId
                            val activeState = if (entry.isTranscode) EntryState.Converting else EntryState.Downloading
                            entry.copy(state = activeState)
                        } else {
                            entry.copy(state = EntryState.Pending)
                        }
                    } else entry
                })
            }
            dlIdToResume?.let { dlId ->
                try {
                    downloader.resumeDownload(dlId)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to resume download $dlId")
                }
            }
            ensurePump()
        }
    }

    fun clearCompleted() {
        scope.launch {
            mutex.withLock {
                _entries.value = _entries.value.filter { it.state !is EntryState.Completed }
            }
        }
    }

    private fun sort(entries: List<Entry>): List<Entry> {
        fun priority(state: EntryState): Int =
            when (state) {
                is EntryState.Downloading, is EntryState.Converting -> 0
                is EntryState.Paused -> 0
                is EntryState.Pending -> 1
                is EntryState.Failed -> 2
                is EntryState.Completed -> 3
            }
        return entries.sortedWith(
            compareBy({ priority(it.state) }, { it.startedAt ?: it.addedAt }, { it.addedAt })
        )
    }

    private suspend fun ensurePump() {
        mutex.withLock {
            if (pumpJob?.isActive == true) return
            DownloadPumpService.start(context)
            pumpJob =
                scope.launch {
                    try {
                        pump()
                    } catch (e: Exception) {
                        Timber.e(e, "DownloadQueue pump crashed")
                    }
                }
        }
    }

    private suspend fun pump() {
        while (true) {
            val active =
                _entries.value.filter {
                    it.state is EntryState.Downloading || it.state is EntryState.Converting || it.state is EntryState.Paused
                }
            if (speedTrackers.isNotEmpty()) {
                val activeDlIds = active.mapNotNull { it.downloadId }.toSet()
                speedTrackers.keys.retainAll(activeDlIds)
            }
            if (active.isNotEmpty()) {
                val updates = mutableMapOf<UUID, Entry>()
                val now = System.currentTimeMillis()
                val dlIds = active.mapNotNull { it.downloadId }
                val snapshots = downloader.getProgress(dlIds)
                for (entry in active) {
                    val dlId = entry.downloadId ?: continue
                    val snapshot =
                        snapshots[dlId]
                            ?: Downloader.Progress(
                                DownloadStatus.FAILED,
                                0,
                                -1L,
                                -1L,
                            )
                    val isUserPaused = entry.state is EntryState.Paused
                    val newState: EntryState? =
                        if (entry.state is EntryState.Converting) {
                            null
                        } else if (isUserPaused) {
                            when (snapshot.status) {
                                DownloadStatus.SUCCESSFUL -> if (entry.isTranscode) EntryState.Converting else EntryState.Completed
                                DownloadStatus.FAILED -> EntryState.Failed(null)
                                else -> null
                            }
                        } else {
                            when (snapshot.status) {
                                DownloadStatus.PENDING,
                                DownloadStatus.RUNNING -> null
                                DownloadStatus.PAUSED -> EntryState.Paused
                                DownloadStatus.SUCCESSFUL -> if (entry.isTranscode) EntryState.Converting else EntryState.Completed
                                DownloadStatus.FAILED -> EntryState.Failed(null)
                                else -> EntryState.Failed(null)
                            }
                        }
                    val originalSize =
                        (entry.item as? FindroidSources)?.sources?.maxOfOrNull { it.size } ?: 0L
                    val estimating =
                        snapshot.totalBytes <= 0L &&
                            originalSize > 0L &&
                            snapshot.bytesDownloaded in 0 until originalSize
                    val effectiveTotal =
                        if (estimating) originalSize else snapshot.totalBytes
                    val newProgress =
                        if (estimating) {
                            (snapshot.bytesDownloaded * 100 / originalSize)
                                .toInt()
                                .coerceIn(0, 99)
                        } else {
                            snapshot.progress.coerceAtLeast(0).coerceAtMost(100)
                        }
                    val isCurrentlyPaused = (newState ?: entry.state) is EntryState.Paused
                    val tracker = speedTrackers.getOrPut(dlId) { DownloadSpeedTracker() }
                    val speed = if (isCurrentlyPaused) {
                        tracker.reset()
                        0L
                    } else {
                        tracker.record(snapshot.bytesDownloaded, now)
                    }
                    val remainingBytes = (effectiveTotal - snapshot.bytesDownloaded).coerceAtLeast(0L)
                    val eta = if (isCurrentlyPaused || newState == EntryState.Completed) -1L else tracker.calculateEtaSeconds(remainingBytes)
                    val bytesChanged =
                        snapshot.bytesDownloaded != entry.bytesDownloaded ||
                            effectiveTotal != entry.totalBytes ||
                            estimating != entry.totalBytesEstimated
                    val speedChanged = speed != entry.bytesPerSecond
                    val etaChanged = eta != entry.etaSeconds
                    if (
                        newState != null ||
                            newProgress != entry.progress ||
                            bytesChanged ||
                            speedChanged ||
                            etaChanged
                    ) {
                        updates[entry.id] =
                            entry.copy(
                                state = newState ?: entry.state,
                                progress = if (newState == EntryState.Completed) 100 else newProgress,
                                bytesDownloaded = snapshot.bytesDownloaded,
                                totalBytes = effectiveTotal,
                                totalBytesEstimated = estimating,
                                bytesPerSecond = if (isCurrentlyPaused || newState == EntryState.Completed) 0L else speed,
                                etaSeconds = eta,
                            )
                    }
                }
                if (updates.isNotEmpty()) {
                    val convertingNow =
                        updates.values.filter { it.state is EntryState.Converting }
                    for (entry in convertingNow) {
                        val dlId = entry.downloadId ?: continue
                        scope.launch(Dispatchers.IO) {
                            try {
                                downloader.finalizeDownload(dlId)
                            } catch (e: Exception) {
                                Timber.e(
                                    e,
                                    "finalizeDownload failed for ${entry.item.name} (id=$dlId)",
                                )
                            }
                            mutex.withLock {
                                _entries.value =
                                    sort(
                                        _entries.value.map {
                                            if (it.id == entry.id) it.copy(state = EntryState.Completed) else it
                                        }
                                    )
                            }
                        }
                    }
                    val completedNow =
                        updates.values.filter { it.state is EntryState.Completed }
                    for (entry in completedNow) {
                        val dlId = entry.downloadId ?: continue
                        try {
                            downloader.finalizeDownload(dlId)
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "finalizeDownload failed for ${entry.item.name} (id=$dlId)",
                            )
                        }
                    }
                    mutex.withLock {
                        _entries.value =
                            sort(_entries.value.map { updates[it.id] ?: it })
                    }
                    val failedEntries =
                        updates.values.filter { it.state is EntryState.Failed }
                    for (failed in failedEntries) {
                        val dlId = failed.downloadId ?: continue
                        speedTrackers.remove(dlId)
                        val err = (failed.state as? EntryState.Failed)?.error
                        Timber.e("Download failed for ${failed.item.name} (id=${failed.id}, retry=${failed.retryCount}/$MAX_AUTO_RETRIES): error=$err")
                    }
                    val retryUpdates = mutableMapOf<UUID, Entry>()
                    for (failed in failedEntries) {
                        if (failed.retryCount < MAX_AUTO_RETRIES) {
                            val backoffMs = RETRY_BACKOFF_MS[failed.retryCount.coerceAtMost(RETRY_BACKOFF_MS.lastIndex)]
                            Timber.w("Scheduling retry #${failed.retryCount + 1} for ${failed.item.name} in ${backoffMs / 1000}s")
                            retryUpdates[failed.id] = failed.copy(
                                state = EntryState.Pending,
                                downloadId = null,
                                startedAt = null,
                                progress = 0,
                                retryCount = failed.retryCount + 1,
                                retryAt = System.currentTimeMillis() + backoffMs,
                            )
                        } else {
                            Timber.e("Max auto-retries reached for ${failed.item.name}, notifying user of failure")
                            notifyFailure(failed.item)
                        }
                    }
                    if (retryUpdates.isNotEmpty()) {
                        mutex.withLock {
                            _entries.value = sort(
                                _entries.value.map { retryUpdates[it.id] ?: it }
                            )
                        }
                    }
                    val completedEntries =
                        updates.values.filter { it.state is EntryState.Completed }
                    for (entry in completedEntries) {
                        entry.downloadId?.let { speedTrackers.remove(it) }
                        val item = entry.item
                        if (item is FindroidEpisode) {
                            scope.launch { smartEnqueueNext(item) }
                        }
                    }
                }
            }

            // 2. Fill free slots from Pending queue
            val maxConcurrent = appPreferences.getValue(appPreferences.maxConcurrentDownloads)
            val currentlyActive =
                _entries.value.count {
                    it.state is EntryState.Downloading || it.state is EntryState.Converting
                }
            val freeSlots = (maxConcurrent - currentlyActive).coerceAtLeast(0)
            if (freeSlots > 0) {
                val now = System.currentTimeMillis()
                val pending = _entries.value.filter {
                    it.state is EntryState.Pending && (it.retryAt == null || it.retryAt <= now)
                }.take(freeSlots)
                for (entry in pending) {
                    startDownload(entry)
                }
            }

            // 3. Keep pumping if active or pending items remain
            val shouldExit =
                mutex.withLock {
                    val snap = _entries.value
                    val hasWork =
                        snap.any {
                            it.state is EntryState.Downloading ||
                                it.state is EntryState.Converting ||
                                it.state is EntryState.Pending ||
                                it.state is EntryState.Paused
                        }
                    if (!hasWork) {
                        pumpJob = null
                        true
                    } else {
                        false
                    }
                }
            if (shouldExit) return
            delay(Constants.DOWNLOAD_POLL_INTERVAL_MS)
        }
    }

    private suspend fun startDownload(entry: Entry) {
        val (downloadId, errorText) =
            try {
                downloader.downloadItem(
                    item = entry.item,
                    storageIndex = entry.storageIndex,
                    presetId = entry.presetId,
                    downloadExternalAudio = entry.downloadExternalAudio,
                    audioStreamIndex = entry.audioStreamIndex,
                )
            } catch (e: Exception) {
                Timber.e(e, "downloadItem threw for ${entry.item.name}")
                Pair(-1L, UiText.StringResource(CoreR.string.downloading_error))
            }

        var orphaned = false
        mutex.withLock {
            val stillPresent = _entries.value.any { it.id == entry.id }
            if (!stillPresent) {
                orphaned = downloadId != -1L
                return@withLock
            }
            val activeState = EntryState.Downloading
            _entries.value =
                sort(
                    _entries.value.map { e ->
                        if (e.id != entry.id) {
                            e
                        } else if (downloadId != -1L) {
                            e.copy(
                                state = activeState,
                                downloadId = downloadId,
                                startedAt = System.currentTimeMillis(),
                            )
                        } else {
                            e.copy(state = EntryState.Failed(errorText))
                        }
                    }
                )
        }
        if (orphaned) {
            try {
                downloader.cancelDownload(entry.item, downloadId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to clean up orphaned download ${entry.item.name}")
            }
        }
    }

    private fun notifyFailure(item: FindroidItem) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(FAILURE_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    FAILURE_CHANNEL_ID,
                    context.getString(CoreR.string.download_failures_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        val title = if (item is FindroidEpisode) {
            "${item.seriesName} · S%02dE%02d".format(item.parentIndexNumber, item.indexNumber)
        } else {
            item.name
        }
        val notification = NotificationCompat.Builder(context, FAILURE_CHANNEL_ID)
            .setSmallIcon(CoreR.drawable.ic_x)
            .setContentTitle(context.getString(CoreR.string.download_failed))
            .setContentText(title)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(item.id.hashCode(), notification)
    }

    private fun isStorageLimitReached(limitGb: Long): Boolean {
        if (limitGb <= 0) return false
        val totalDownloadedBytes = database.getMoviesAndSources().values.flatten().sumOf { s ->
            val f = java.io.File(s.path)
            if (f.exists()) f.length() else 0L
        }
        val limitBytes = limitGb * 1024L * 1024L * 1024L
        return totalDownloadedBytes >= limitBytes
    }

    suspend fun smartEnqueueNext(episode: FindroidEpisode) {
        if (!appPreferences.getValue(appPreferences.smartDownloadNextEpisode)) return
        try {
            val limitGb = appPreferences.getValue(appPreferences.smartDownloadStorageLimitGb).toLongOrNull() ?: 20L
            if (isStorageLimitReached(limitGb)) {
                Timber.i("Smart Downloads: storage limit reached ($limitGb GB), skipping auto-enqueue")
                return
            }

            val targetCount = appPreferences.getValue(appPreferences.smartDownloadNextEpisodesCount).toIntOrNull() ?: 3
            val currentUserId = repository.getUserId()

            // Count existing unwatched episodes (downloaded or queued)
            val downloadedEpisodes = database.getEpisodesByShowId(episode.seriesId)
            val downloadedUnwatchedIds = downloadedEpisodes.filter { ep ->
                database.getSources(ep.id).any { !it.path.endsWith(".download") } &&
                database.getUserData(ep.id, currentUserId)?.played != true
            }.map { it.id }.toSet()

            val queuedForSeries = _entries.value.filter {
                it.item is FindroidEpisode &&
                it.item.seriesId == episode.seriesId &&
                it.state !is EntryState.Completed &&
                it.state !is EntryState.Failed
            }
            val queuedIds = queuedForSeries.map { it.id }.toSet()

            val currentUnwatchedCount = downloadedUnwatchedIds.union(queuedIds).size
            if (currentUnwatchedCount >= targetCount) {
                Timber.d("Smart Downloads: already have $currentUnwatchedCount unwatched episodes (target: $targetCount), skipping")
                return
            }

            val needed = targetCount - currentUnwatchedCount
            val episodes = repository.getEpisodes(
                seriesId = episode.seriesId,
                seasonId = episode.seasonId,
            )
            val currentIdx = episodes.indexOfFirst { it.id == episode.id }
            if (currentIdx == -1) return

            val candidates = episodes.drop(currentIdx + 1).filter { cand ->
                cand.id !in downloadedUnwatchedIds &&
                cand.id !in queuedIds &&
                database.getUserData(cand.id, currentUserId)?.played != true &&
                database.getSources(cand.id).none { !it.path.endsWith(".download") }
            }.take(needed)

            for (next in candidates) {
                if (isStorageLimitReached(limitGb)) {
                    Timber.i("Smart Downloads: storage limit reached ($limitGb GB) while auto-queueing, stopping")
                    break
                }
                Timber.i("Smart Downloads: auto-queueing episode ${next.seriesName} S%02dE%02d".format(next.parentIndexNumber, next.indexNumber))
                enqueue(next)
            }
        } catch (e: Exception) {
            Timber.e(e, "Smart Downloads: failed to fetch next episodes after ${episode.name}")
        }
    }

    suspend fun checkSmartDownloadOnWatched(completedItemId: UUID) {
        if (!appPreferences.getValue(appPreferences.smartDownloadNextEpisode)) return
        try {
            val episodeDto = try { database.getEpisode(completedItemId) } catch (e: Exception) { null } ?: return
            val episode = episodeDto.toFindroidEpisode(database, repository.getUserId())
            smartEnqueueNext(episode)
        } catch (e: Exception) {
            Timber.e(e, "Smart Downloads: failed to trigger on watched episode $completedItemId")
        }
    }

    suspend fun checkAutoDeleteWatched(completedItemId: UUID) {
        if (!appPreferences.getValue(appPreferences.autoDeleteWatched)) return
        try {
            val currentUserId = repository.getUserId()
            val episode = try { database.getEpisode(completedItemId) } catch (e: Exception) { null }
            if (episode != null) {
                val seasonEpisodes = try {
                    repository.getEpisodes(seriesId = episode.seriesId, seasonId = episode.seasonId)
                } catch (e: Exception) {
                    database.getEpisodesBySeasonId(episode.seasonId).map { it.toFindroidEpisode(database, currentUserId) }
                }.sortedBy { it.indexNumber }

                for (candEp in seasonEpisodes) {
                    val candSources = database.getSources(candEp.id).filter { !it.path.endsWith(".download") }
                    if (candSources.isEmpty()) continue

                    val candWatched = candEp.id == completedItemId || database.getUserData(candEp.id, currentUserId)?.played == true
                    if (!candWatched) continue

                    val candIdx = seasonEpisodes.indexOfFirst { it.id == candEp.id }
                    if (candIdx != -1 && candIdx < seasonEpisodes.lastIndex) {
                        val nextEp = seasonEpisodes[candIdx + 1]
                        val nextWatched = nextEp.id == completedItemId || database.getUserData(nextEp.id, currentUserId)?.played == true
                        if (nextWatched) {
                            for (candSource in candSources) {
                                Timber.i("AutoDeleteWatched: safely deleting episode ${candEp.name} (S${candEp.parentIndexNumber}E${candEp.indexNumber}) because next episode ${nextEp.name} is also watched")
                                downloader.deleteItem(
                                    candEp,
                                    candSource.toFindroidSource(database),
                                    currentUserId,
                                )
                            }
                        }
                    }
                }
            } else {
                val movie = try { database.getMovie(completedItemId) } catch (e: Exception) { null }
                if (movie != null) {
                    val sources = database.getSources(completedItemId).filter { !it.path.endsWith(".download") }
                    for (source in sources) {
                        Timber.i("AutoDeleteWatched: deleting watched movie ${movie.name}")
                        downloader.deleteItem(
                            movie.toFindroidMovie(database, currentUserId),
                            source.toFindroidSource(database),
                            currentUserId,
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "AutoDeleteWatched: error while checking auto delete for $completedItemId")
        } finally {
            scheduleUserDataSync()
        }
    }

    fun scheduleUserDataSync() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val syncWorkRequest =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "syncUserData",
                    ExistingWorkPolicy.REPLACE,
                    syncWorkRequest,
                )
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule user data sync")
        }
    }

    companion object {
        private const val FAILURE_CHANNEL_ID = "download_failures"
        private const val MAX_AUTO_RETRIES = 3
        private val RETRY_BACKOFF_MS = longArrayOf(30_000L, 120_000L, 600_000L)
    }
}
