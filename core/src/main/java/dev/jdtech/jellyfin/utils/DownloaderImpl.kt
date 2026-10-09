package dev.jdtech.jellyfin.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.DownloadQualityPresets
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.UserDownloadDto
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidEpisodeDto
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidMovieDto
import dev.jdtech.jellyfin.models.toFindroidPartDto
import dev.jdtech.jellyfin.models.toFindroidSeasonDto
import dev.jdtech.jellyfin.models.toFindroidSegmentsDto
import dev.jdtech.jellyfin.models.toFindroidShowDto
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.models.toFindroidSourceDto
import dev.jdtech.jellyfin.models.toFindroidUserDataDto
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.download.DownloadStatus
import dev.jdtech.jellyfin.utils.download.MediaDownloadEngine
import dev.jdtech.jellyfin.work.ImagesDownloaderWorker
import dev.jdtech.jellyfin.work.MediaAttachmentsWorker
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

class DownloaderImpl(
    private val context: Context,
    private val database: ServerDatabaseDao,
    private val jellyfinRepository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val workManager: WorkManager,
    private val engine: MediaDownloadEngine,
) : Downloader {

    private val idCounter = AtomicLong(System.currentTimeMillis())
    private val partDownloadMap = ConcurrentHashMap<Long, List<Long>>()

    private suspend fun getAssociatedPartDownloadIds(downloadId: Long): List<Long> {
        partDownloadMap[downloadId]?.let {
            return it
        }
        return try {
            val source = database.getSourceByDownloadId(downloadId) ?: return emptyList()
            val partIds =
                try {
                    database.getMovie(source.itemId).additionalPartIds
                } catch (_: Exception) {
                    try {
                        database.getEpisode(source.itemId).additionalPartIds
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
            if (partIds.isEmpty()) return emptyList()
            val ids = partIds.flatMap { pId ->
                database.getSources(pId).mapNotNull { it.downloadId }
            }
            if (ids.isNotEmpty()) {
                partDownloadMap[downloadId] = ids
            }
            ids
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String?,
        storageIndex: Int,
        presetId: String?,
        downloadExternalAudio: Boolean,
        audioStreamIndex: Int?,
    ): Pair<Long, UiText?> = coroutineScope {
        try {
            val additionalParts =
                item.additionalParts.ifEmpty {
                    try {
                        jellyfinRepository.getAdditionalParts(item.id)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to get additional parts for ${item.name}")
                        emptyList()
                    }
                }
            val effectiveItem =
                when {
                    item.additionalParts.isEmpty() && additionalParts.isNotEmpty() -> {
                        when (item) {
                            is FindroidMovie -> item.copy(additionalParts = additionalParts)
                            is FindroidEpisode -> item.copy(additionalParts = additionalParts)
                            else -> item
                        }
                    }
                    else -> item
                }

            val sources = jellyfinRepository.getMediaSources(effectiveItem.id, true)
            val source =
                if (sourceId != null) {
                    sources.firstOrNull { it.id == sourceId } ?: sources.firstOrNull()
                } else {
                    sources.firstOrNull()
                }
                    ?: return@coroutineScope Pair(
                        -1L,
                        UiText.StringResource(CoreR.string.unknown_error),
                    )

            val (storageLocation, effectiveIndex) =
                resolveStorageLocation(storageIndex)
                    ?: return@coroutineScope Pair(
                        -1L,
                        UiText.StringResource(CoreR.string.storage_unavailable),
                    )

            val currentUserId = jellyfinRepository.getUserId()

            if (linkExistingDiskDownload(effectiveItem, currentUserId)) {
                return@coroutineScope Pair(0L, null)
            }

            val partsWithParents = additionalParts.map { part ->
                if (part.parentName.isEmpty()) {
                    part.copy(
                        parentName = effectiveItem.name,
                        images =
                            if (part.images.primary == null) {
                                part.images.copy(
                                    primary = effectiveItem.images.primary,
                                    backdrop =
                                        part.images.backdrop ?: effectiveItem.images.backdrop,
                                    logo = part.images.logo ?: effectiveItem.images.logo,
                                )
                            } else {
                                part.images
                            },
                    )
                } else {
                    part
                }
            }

            val partsWithSources = partsWithParents.mapNotNull { part ->
                val partSources =
                    try {
                        jellyfinRepository.getMediaSources(part.id, true)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to get media sources for part ${part.name}")
                        emptyList()
                    }
                val partSource = partSources.firstOrNull()
                if (partSource != null) part to partSource else null
            }

            val activePresetId =
                presetId ?: appPreferences.getValue(appPreferences.defaultTranscodePresetId)
            val downloadUrl =
                resolveDownloadUrl(effectiveItem, source, activePresetId, audioStreamIndex)

            val isTranscoding =
                DownloadQualityPresets.isTranscodingPreset(activePresetId, appPreferences) &&
                    downloadUrl != source.path
            val estimatedBytes =
                calculateEstimatedBytes(effectiveItem, source, activePresetId, isTranscoding)

            val destFile =
                File(storageLocation, "downloads/${effectiveItem.id}.${source.id}.download")
            destFile.parentFile?.mkdirs()

            val allowMetered = appPreferences.getValue(appPreferences.downloadOverMobileData)
            val allowRoaming = appPreferences.getValue(appPreferences.downloadWhenRoaming)

            val partRequests = partsWithSources.map { (part, partSource) ->
                val partDestFile =
                    File(storageLocation, "downloads/${part.id}.${partSource.id}.download")
                partDestFile.parentFile?.mkdirs()
                val existingPartSourceDto =
                    database.getSourceByDownloadId(partSource.downloadId ?: -1L)
                val partDownloadId =
                    existingPartSourceDto?.downloadId ?: idCounter.incrementAndGet()

                val pDownloadUrl =
                    resolveDownloadUrl(part, partSource, activePresetId, audioStreamIndex)
                val pIsTranscoding =
                    DownloadQualityPresets.isTranscodingPreset(
                        activePresetId,
                        appPreferences,
                    ) && pDownloadUrl != partSource.path
                val pEstimatedBytes =
                    calculateEstimatedBytes(part, partSource, activePresetId, pIsTranscoding)

                Triple(
                    part,
                    partSource,
                    MediaDownloadEngine.Request(
                        id = partDownloadId,
                        url = pDownloadUrl,
                        destFile = partDestFile,
                        allowMetered = allowMetered,
                        allowRoaming = allowRoaming,
                        estimatedTotalBytes = pEstimatedBytes,
                    ),
                )
            }

            val totalEstimatedBytes =
                estimatedBytes + partRequests.sumOf { it.third.estimatedTotalBytes }
            val totalFallbackSize = source.size + partRequests.sumOf { it.second.size }

            val storageError =
                checkAvailableStorageSpace(storageLocation, totalEstimatedBytes, totalFallbackSize)
            if (storageError != null) {
                return@coroutineScope Pair(-1L, storageError)
            }

            val existingSourceDto = database.getSourceByDownloadId(source.downloadId ?: -1L)
            val downloadId = existingSourceDto?.downloadId ?: idCounter.incrementAndGet()

            engine.start(
                MediaDownloadEngine.Request(
                    id = downloadId,
                    url = downloadUrl,
                    destFile = destFile,
                    allowMetered = allowMetered,
                    allowRoaming = allowRoaming,
                    estimatedTotalBytes = estimatedBytes,
                )
            )

            for ((_, _, partRequest) in partRequests) {
                engine.start(partRequest)
            }
            val partDlIds = partRequests.map { it.third.id }
            if (partDlIds.isNotEmpty()) {
                partDownloadMap[downloadId] = partDlIds
            }

            val serverId = appPreferences.getValue(appPreferences.currentServer)
            insertItemMetadataToDb(effectiveItem, serverId)

            val sourceDto = source.toFindroidSourceDto(effectiveItem.id, destFile.absolutePath)
            database.insertSource(sourceDto.copy(downloadId = downloadId))
            database.insertUserData(effectiveItem.toFindroidUserDataDto(currentUserId))
            database.insertUserDownload(
                UserDownloadDto(userId = currentUserId, itemId = effectiveItem.id)
            )

            startAttachmentsWorker(effectiveItem, source.id, effectiveIndex, downloadExternalAudio)

            for ((part, partSource, partRequest) in partRequests) {
                val partSourceDto =
                    partSource.toFindroidSourceDto(part.id, partRequest.destFile.absolutePath)
                database.insertSource(partSourceDto.copy(downloadId = partRequest.id))
                database.insertPart(part.toFindroidPartDto(serverId))
                database.insertUserData(part.toFindroidUserDataDto(currentUserId))
                database.insertUserDownload(
                    UserDownloadDto(userId = currentUserId, itemId = part.id)
                )
                startAttachmentsWorker(part, partSource.id, effectiveIndex, downloadExternalAudio)
            }

            val segments = jellyfinRepository.getSegments(effectiveItem.id)
            segments.forEach { database.insertSegment(it.toFindroidSegmentsDto(effectiveItem.id)) }

            Timber.i(
                "downloadItem enqueued for ${effectiveItem.name}, downloadId=$downloadId, parts=${partDlIds.size}"
            )

            Pair(downloadId, null)
        } catch (e: Exception) {
            Timber.e(e, "downloadItem failed for ${item.name}")
            Pair(
                -1L,
                if (e.message != null) UiText.DynamicString(e.message!!)
                else UiText.StringResource(CoreR.string.unknown_error),
            )
        }
    }

    private fun resolveDownloadUrl(
        item: FindroidItem,
        source: FindroidSource,
        presetId: String,
        audioStreamIndex: Int? = null,
    ): String {
        if (!DownloadQualityPresets.isTranscodingPreset(presetId, appPreferences)) {
            return source.path
        }

        val preset = DownloadQualityPresets.getById(presetId, appPreferences)
        val videoStream = source.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }
        val originalHeight = videoStream?.height ?: 1080
        val originalBitrate =
            if (item.runtimeTicks > 0 && source.size > 0) {
                (source.size * 8) / (item.runtimeTicks / 10_000_000L).coerceAtLeast(1)
            } else {
                0L
            }

        // Smart fallback: if original file is already smaller/equal resolution & bitrate, download
        // original directly
        if (originalBitrate in 1..preset.maxBitrateBps && originalHeight <= preset.maxHeight) {
            Timber.i(
                "Original file bitrate ($originalBitrate bps) <= preset (${preset.maxBitrateBps} bps); skipping transcode and downloading original."
            )
            return source.path
        }

        val audioCodec = preset.audioCodec.ifBlank { "aac" }
        val audioChannels = preset.audioChannels.coerceIn(1, 8)
        val audioBitrate = preset.audioBitrateBps

        val supportedVideoCodecs = DeviceCodecCapabilities.getSupportedVideoCodecsForJellyfin()

        val audioStreamParam =
            if (audioStreamIndex != null) "&audioStreamIndex=$audioStreamIndex" else ""

        // Progressive MP4 transcode stream from Jellyfin with complete video and audio parameters,
        // allowing remux when compatible
        val baseUrl = jellyfinRepository.getBaseUrl()
        return "$baseUrl/Videos/${item.id}/stream.mp4?static=false&mediaSourceId=${source.id}&videoCodec=$supportedVideoCodecs&audioCodec=$audioCodec&videoBitRate=${preset.maxBitrateBps}&audioBitRate=$audioBitrate&maxWidth=${preset.maxWidth}&maxHeight=${preset.maxHeight}&audioChannels=$audioChannels&transcodingMaxAudioChannels=$audioChannels&audioSampleRate=${preset.audioSampleRate}&allowVideoStreamCopy=true&allowAudioStreamCopy=true&breakOnNonKeyFrames=true$audioStreamParam"
    }

    private fun startAttachmentsWorker(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
        downloadExternalAudio: Boolean,
    ) {
        val request =
            OneTimeWorkRequestBuilder<MediaAttachmentsWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setInputData(
                    workDataOf(
                        MediaAttachmentsWorker.KEY_ITEM_ID to item.id.toString(),
                        MediaAttachmentsWorker.KEY_SOURCE_ID to sourceId,
                        MediaAttachmentsWorker.KEY_STORAGE_INDEX to storageIndex,
                        MediaAttachmentsWorker.KEY_DOWNLOAD_EXTERNAL_AUDIO to downloadExternalAudio,
                    )
                )
                .build()
        workManager.enqueue(request)
    }

    override suspend fun cancelDownload(item: FindroidItem, downloadId: Long) {
        val allIds = listOf(downloadId) + getAssociatedPartDownloadIds(downloadId)
        allIds.forEach { engine.cancel(it) }
        partDownloadMap.remove(downloadId)
        val source =
            database.getSourceByDownloadId(downloadId)?.toFindroidSource(database) ?: return
        deleteItem(item, source)
    }

    override suspend fun pauseDownload(downloadId: Long) {
        val allIds = listOf(downloadId) + getAssociatedPartDownloadIds(downloadId)
        allIds.forEach { engine.pause(it) }
    }

    override suspend fun resumeDownload(downloadId: Long) {
        val allIds = listOf(downloadId) + getAssociatedPartDownloadIds(downloadId)
        allIds.forEach { engine.resume(it) }
    }

    private suspend fun insertItemMetadataToDb(
        item: FindroidItem,
        serverId: String?,
        wrapShowInTryCatch: Boolean = false,
    ) {
        when (item) {
            is FindroidMovie -> database.insertMovie(item.toFindroidMovieDto(serverId))
            is FindroidEpisode -> {
                if (wrapShowInTryCatch) {
                    try {
                        val show = jellyfinRepository.getShow(item.seriesId)
                        database.insertShow(show.toFindroidShowDto(serverId))
                        val season = jellyfinRepository.getSeason(item.seasonId)
                        database.insertSeason(season.toFindroidSeasonDto())
                    } catch (e: Exception) {
                        Timber.w(e, "Error inserting show/season for existing download")
                    }
                } else {
                    val show = jellyfinRepository.getShow(item.seriesId)
                    database.insertShow(show.toFindroidShowDto(serverId))
                    val season = jellyfinRepository.getSeason(item.seasonId)
                    database.insertSeason(season.toFindroidSeasonDto())
                }
                database.insertEpisode(item.toFindroidEpisodeDto(serverId))
            }
        }
    }

    private fun resolveStorageLocation(storageIndex: Int): Pair<File, Int>? {
        val preferredStorageIndex =
            appPreferences.getValue(appPreferences.defaultDownloadStorageIndex).toIntOrNull() ?: -1
        val effectiveIndex =
            if (storageIndex >= 0) storageIndex
            else if (preferredStorageIndex >= 0) preferredStorageIndex else 0
        val dirs = context.getExternalFilesDirs(null)
        val storageLocation = dirs.getOrNull(effectiveIndex) ?: dirs.getOrNull(0)
        return if (
            storageLocation != null &&
                Environment.getExternalStorageState(storageLocation) == Environment.MEDIA_MOUNTED
        ) {
            storageLocation to effectiveIndex
        } else {
            null
        }
    }

    private suspend fun linkExistingDiskDownload(item: FindroidItem, currentUserId: UUID): Boolean {
        val existingSources =
            database.getSources(item.id).filter {
                !it.path.endsWith(".download") && File(it.path).exists()
            }
        if (existingSources.isNotEmpty()) {
            Timber.i(
                "downloadItem: item ${item.name} already exists on disk, linking to user $currentUserId"
            )
            val serverId = appPreferences.getValue(appPreferences.currentServer)
            insertItemMetadataToDb(
                item,
                serverId,
                wrapShowInTryCatch = true,
            )
            database.insertUserDownload(UserDownloadDto(userId = currentUserId, itemId = item.id))
            database.insertUserData(item.toFindroidUserDataDto(currentUserId))

            val additionalParts =
                when (item) {
                    is FindroidMovie -> item.additionalParts
                    is FindroidEpisode -> item.additionalParts
                    else -> emptyList()
                }
            for (part in additionalParts) {
                database.insertPart(part.toFindroidPartDto(serverId))
                database.insertUserDownload(
                    UserDownloadDto(userId = currentUserId, itemId = part.id)
                )
                database.insertUserData(part.toFindroidUserDataDto(currentUserId))
            }
            return true
        }
        return false
    }

    private fun calculateEstimatedBytes(
        item: FindroidItem,
        source: FindroidSource,
        presetId: String,
        isTranscoding: Boolean,
    ): Long {
        return if (isTranscoding && item.runtimeTicks > 0) {
            val preset = DownloadQualityPresets.getById(presetId, appPreferences)
            val durationSec = (item.runtimeTicks / 10_000_000L).coerceAtLeast(1)
            ((preset.maxBitrateBps + preset.audioBitrateBps) * durationSec) / 8L
        } else {
            source.size
        }
    }

    private fun checkAvailableStorageSpace(
        storageLocation: File,
        estimatedBytes: Long,
        fallbackSourceSize: Long,
    ): UiText? {
        val requiredStorage = if (estimatedBytes > 0) estimatedBytes else fallbackSourceSize
        val stats = StatFs(storageLocation.path)
        return if (requiredStorage > 0 && stats.availableBytes < requiredStorage) {
            UiText.StringResource(
                CoreR.string.not_enough_storage,
                Formatter.formatFileSize(context, requiredStorage),
                Formatter.formatFileSize(context, stats.availableBytes),
            )
        } else {
            null
        }
    }

    private fun calculateProgressPercentage(bytesDownloaded: Long, totalBytes: Long): Int {
        return if (totalBytes > 0) {
            (bytesDownloaded * 100 / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    private suspend fun cleanupShowData(seriesId: UUID) {
        database.deleteShow(seriesId)
        if (database.countUserDataToBeSynced(seriesId) == 0) {
            database.deleteUserData(seriesId)
        }
        File(context.filesDir, "trickplay/$seriesId").deleteRecursively()
        File(context.filesDir, "images/$seriesId").deleteRecursively()
    }

    private suspend fun cleanupSeasonData(seasonId: UUID) {
        database.deleteSeason(seasonId)
        if (database.countUserDataToBeSynced(seasonId) == 0) {
            database.deleteUserData(seasonId)
        }
        File(context.filesDir, "trickplay/$seasonId").deleteRecursively()
        File(context.filesDir, "images/$seasonId").deleteRecursively()
    }

    private suspend fun cleanupEpisodeParents(item: FindroidEpisode) {
        val remainingEpisodes = database.getEpisodesBySeasonId(item.seasonId)
        if (remainingEpisodes.isEmpty()) {
            cleanupSeasonData(item.seasonId)
            val remainingSeasons = database.getSeasonsByShowId(item.seriesId)
            if (remainingSeasons.isEmpty()) {
                cleanupShowData(item.seriesId)
            }
        }
    }

    private suspend fun deleteSourcesAndStreamsFiles(
        itemId: UUID,
        fallbackSource: FindroidSource? = null,
    ) {
        val allSources = database.getSources(itemId)
        val sourcesToDelete = allSources.ifEmpty {
            if (fallbackSource != null) {
                listOf(fallbackSource.toFindroidSourceDto(itemId, fallbackSource.path))
            } else {
                emptyList()
            }
        }
        for (s in sourcesToDelete) {
            database.deleteSource(s.id)
            File(s.path).delete()
            val mediaStreams = database.getMediaStreamsBySourceId(s.id)
            for (mediaStream in mediaStreams) {
                File(mediaStream.path).delete()
            }
            database.deleteMediaStreamsBySourceId(s.id)
        }
        if (fallbackSource != null) {
            database.deleteSource(fallbackSource.id)
            File(fallbackSource.path).delete()
        }
    }

    override suspend fun deleteItem(item: FindroidItem, source: FindroidSource, userId: UUID?) {
        val targetUserId =
            userId
                ?: try {
                    jellyfinRepository.getUserId()
                } catch (_: Exception) {
                    null
                }

        val additionalPartIds =
            when (item) {
                is FindroidMovie ->
                    item.additionalParts
                        .map { it.id }
                        .ifEmpty {
                            try {
                                database.getMovie(item.id).additionalPartIds ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                is FindroidEpisode ->
                    item.additionalParts
                        .map { it.id }
                        .ifEmpty {
                            try {
                                database.getEpisode(item.id).additionalPartIds ?: emptyList()
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                else -> emptyList()
            }

        if (targetUserId != null) {
            database.deleteUserDownload(targetUserId, item.id)
            for (pId in additionalPartIds) {
                database.deleteUserDownload(targetUserId, pId)
            }
        } else {
            database.deleteUserDownloadsByItemId(item.id)
            for (pId in additionalPartIds) {
                database.deleteUserDownloadsByItemId(pId)
            }
        }

        if (database.countUserDownloads(item.id) > 0) {
            Timber.i(
                "deleteItem: item ${item.name} still downloaded by other user(s), preserving physical files"
            )
            return
        }

        engine.cancel(source.downloadId ?: -1L)

        when (item) {
            is FindroidMovie -> database.deleteMovie(item.id)
            is FindroidEpisode -> {
                database.deleteEpisode(item.id)
                cleanupEpisodeParents(item)
            }
        }

        deleteSourcesAndStreamsFiles(item.id, source)

        for (partId in additionalPartIds) {
            val partSources = database.getSources(partId)
            for (partSource in partSources) {
                val dlId = partSource.downloadId
                if (dlId != null) {
                    engine.cancel(dlId)
                }
            }
            deleteSourcesAndStreamsFiles(partId)
            database.deletePart(partId)
            if (database.countUserDataToBeSynced(partId) == 0) {
                database.deleteUserData(partId)
            }
            File(context.filesDir, "trickplay/$partId").deleteRecursively()
            File(context.filesDir, "images/$partId").deleteRecursively()
        }

        if (database.countUserDataToBeSynced(item.id) == 0) {
            database.deleteUserData(item.id)
        }
        File(context.filesDir, "trickplay/${item.id}").deleteRecursively()
        File(context.filesDir, "images/${item.id}").deleteRecursively()
    }

    override suspend fun getProgress(downloadId: Long?): Downloader.Progress {
        if (downloadId == null) return Downloader.Progress(DownloadStatus.FAILED, 0, -1L, -1L)
        val partIds = getAssociatedPartDownloadIds(downloadId)
        if (partIds.isEmpty()) {
            val snap =
                engine.snapshot(downloadId)
                    ?: return Downloader.Progress(DownloadStatus.FAILED, 0, -1L, -1L)
            val progress = calculateProgressPercentage(snap.bytesDownloaded, snap.totalBytes)
            return Downloader.Progress(
                status = snap.status,
                progress = progress,
                bytesDownloaded = snap.bytesDownloaded,
                totalBytes = snap.totalBytes,
            )
        }

        val allIds = listOf(downloadId) + partIds
        val snaps = allIds.mapNotNull { engine.snapshot(it) }
        if (snaps.isEmpty()) return Downloader.Progress(DownloadStatus.FAILED, 0, -1L, -1L)

        val aggregatedStatus =
            when {
                snaps.any { it.status == DownloadStatus.FAILED } -> DownloadStatus.FAILED
                snaps.any { it.status == DownloadStatus.PAUSED } -> DownloadStatus.PAUSED
                snaps.all { it.status == DownloadStatus.SUCCESSFUL } -> DownloadStatus.SUCCESSFUL
                snaps.any { it.status == DownloadStatus.RUNNING } -> DownloadStatus.RUNNING
                else -> snaps.firstOrNull()?.status ?: DownloadStatus.PENDING
            }

        val totalBytesDownloaded = snaps.sumOf { it.bytesDownloaded }
        val totalBytes = snaps.sumOf { it.totalBytes.coerceAtLeast(0L) }
        val progress = calculateProgressPercentage(totalBytesDownloaded, totalBytes)

        return Downloader.Progress(
            status = aggregatedStatus,
            progress = progress,
            bytesDownloaded = totalBytesDownloaded,
            totalBytes = totalBytes,
        )
    }

    override suspend fun getProgress(downloadIds: List<Long>): Map<Long, Downloader.Progress> {
        return downloadIds.associateWith { id -> getProgress(id) }
    }

    override suspend fun getActiveDownloads(): List<Pair<FindroidItem, Long>> =
        withContext(Dispatchers.IO) {
            val currentUserId = jellyfinRepository.getUserId()
            val incompleteSources = database.getIncompleteSources()
            incompleteSources.mapNotNull { source ->
                val dlId = source.downloadId ?: return@mapNotNull null
                val movie =
                    try {
                        database.getMovie(source.itemId).toFindroidMovie(database, currentUserId)
                    } catch (_: Exception) {
                        null
                    }
                val episode =
                    if (movie == null) {
                        try {
                            database
                                .getEpisode(source.itemId)
                                .toFindroidEpisode(database, currentUserId)
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                val item = movie ?: episode
                if (item != null) item to dlId else null
            }
        }

    override suspend fun finalizeDownload(downloadId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val partIds = getAssociatedPartDownloadIds(downloadId)
            val allIds = listOf(downloadId) + partIds
            var allSuccess = true
            for (id in allIds) {
                val success = finalizeSingleDownload(id)
                if (!success) {
                    allSuccess = false
                }
            }
            if (allSuccess) {
                partDownloadMap.remove(downloadId)
            }
            allSuccess
        }

    private suspend fun finalizeSingleDownload(downloadId: Long): Boolean =
        withContext(Dispatchers.IO) {
            val source = database.getSourceByDownloadId(downloadId) ?: return@withContext false
            if (!source.path.endsWith(".download")) return@withContext true
            val partialFile = File(source.path)

            val basePath = source.path.removeSuffix(".download")
            val isFragmented =
                if (partialFile.exists()) Mp4Remuxer.isFragmentedMp4(partialFile) else false
            val finalFile: File =
                if (isFragmented || !basePath.substringAfterLast('/', "").contains('.')) {
                    File("$basePath.mp4")
                } else {
                    File(basePath)
                }

            if (!partialFile.exists() || partialFile.length() == 0L) {
                if (finalFile.exists() && finalFile.length() > 0L) {
                    database.setSourcePath(source.id, finalFile.absolutePath)
                    return@withContext true
                }
                Timber.w(
                    "finalizeDownload: partialFile ${partialFile.absolutePath} does not exist or is empty"
                )
                return@withContext false
            }

            var success = false
            if (isFragmented) {
                val stats =
                    try {
                        StatFs(partialFile.parentFile?.path ?: "")
                    } catch (_: Exception) {
                        null
                    }
                val hasSpace = stats == null || stats.availableBytes >= partialFile.length()
                if (hasSpace) {
                    Timber.i(
                        "Remuxing fragmented MP4 for download $downloadId into seekable MP4: ${finalFile.name}"
                    )
                    val remuxed = Mp4Remuxer.remuxToStandardMp4(partialFile, finalFile)
                    if (remuxed && finalFile.exists() && finalFile.length() > 0L) {
                        partialFile.delete()
                        success = true
                    } else {
                        Timber.w(
                            "Remux failed for download $downloadId; falling back to direct rename"
                        )
                        if (finalFile.exists() && finalFile.length() == 0L) {
                            finalFile.delete()
                        }
                    }
                } else {
                    Timber.w(
                        "Insufficient storage to remux download $downloadId (available=${stats.availableBytes}, needed=${partialFile.length()}); falling back to rename"
                    )
                }
            }

            if (!success) {
                if (finalFile.exists() && finalFile.canonicalPath != partialFile.canonicalPath) {
                    if (finalFile.length() == 0L) {
                        finalFile.delete()
                    } else if (partialFile.exists() && partialFile.length() > 0L) {
                        finalFile.delete()
                    }
                }
                success = partialFile.renameTo(finalFile)
                if (!success && partialFile.exists()) {
                    try {
                        partialFile.copyTo(finalFile, overwrite = true)
                        partialFile.delete()
                        success = true
                    } catch (e: Exception) {
                        Timber.e(
                            e,
                            "Failed to copy partialFile to finalFile: ${finalFile.absolutePath}",
                        )
                    }
                }
            }

            if (success && finalFile.exists() && finalFile.length() > 0L) {
                database.setSourcePath(source.id, finalFile.absolutePath)
                true
            } else {
                Timber.e(
                    "finalizeDownload failed for $downloadId: finalFile exists=${finalFile.exists()} length=${finalFile.length()}"
                )
                false
            }
        }

    override suspend fun moveItemStorage(
        item: FindroidItem,
        targetStorageIndex: Int,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)?,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val dirs = context.getExternalFilesDirs(null)
                val targetDir =
                    dirs.getOrNull(targetStorageIndex)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Target storage unavailable")
                        )
                if (Environment.getExternalStorageState(targetDir) != Environment.MEDIA_MOUNTED) {
                    return@withContext Result.failure(
                        IllegalStateException("Target storage not mounted")
                    )
                }
                val targetDownloadsDir = File(targetDir, "downloads").apply { mkdirs() }

                val totalBytes = calculateTotalItemBytes(item)
                var transferredBytes = 0L

                val progressCallback: (Long) -> Unit = { chunkBytes ->
                    transferredBytes += chunkBytes
                    onProgress?.invoke(transferredBytes, totalBytes)
                }

                when (item) {
                    is FindroidMovie -> {
                        moveSourcesAndStreams(item.id, targetDownloadsDir, progressCallback)
                        val partIds =
                            item.additionalParts
                                .map { it.id }
                                .ifEmpty {
                                    try {
                                        database.getMovie(item.id).additionalPartIds ?: emptyList()
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                }
                        for (pId in partIds) {
                            moveSourcesAndStreams(pId, targetDownloadsDir, progressCallback)
                        }
                    }
                    is FindroidEpisode -> {
                        moveSourcesAndStreams(item.id, targetDownloadsDir, progressCallback)
                        val partIds =
                            item.additionalParts
                                .map { it.id }
                                .ifEmpty {
                                    try {
                                        database.getEpisode(item.id).additionalPartIds
                                            ?: emptyList()
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                }
                        for (pId in partIds) {
                            moveSourcesAndStreams(pId, targetDownloadsDir, progressCallback)
                        }
                    }
                    is FindroidShow -> {
                        val epList =
                            database.getDownloadedEpisodesByShowId(item.id).firstOrNull()
                                ?: emptyList()
                        for (ep in epList) {
                            moveSourcesAndStreams(ep.id, targetDownloadsDir, progressCallback)
                            val partIds = ep.additionalPartIds ?: emptyList()
                            for (pId in partIds) {
                                moveSourcesAndStreams(pId, targetDownloadsDir, progressCallback)
                            }
                        }
                    }
                }
                onProgress?.invoke(totalBytes, totalBytes)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to move storage for ${item.name}")
                Result.failure(e)
            }
        }

    private suspend fun calculateTotalItemBytes(item: FindroidItem): Long {
        var total = 0L
        val itemIds =
            when (item) {
                is FindroidMovie ->
                    listOf(item.id) +
                        (item.additionalParts
                            .map { it.id }
                            .ifEmpty {
                                try {
                                    database.getMovie(item.id).additionalPartIds ?: emptyList()
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            })
                is FindroidEpisode ->
                    listOf(item.id) +
                        (item.additionalParts
                            .map { it.id }
                            .ifEmpty {
                                try {
                                    database.getEpisode(item.id).additionalPartIds ?: emptyList()
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            })
                is FindroidShow ->
                    database.getDownloadedEpisodesByShowId(item.id).firstOrNull()?.flatMap { ep ->
                        listOf(ep.id) + (ep.additionalPartIds ?: emptyList())
                    } ?: emptyList()
                else -> emptyList()
            }
        for (id in itemIds) {
            val sources = database.getSources(id)
            for (source in sources) {
                val f = File(source.path)
                if (f.exists()) total += f.length()
                val streams = database.getMediaStreamsBySourceId(source.id)
                for (s in streams) {
                    val sf = File(s.path)
                    if (sf.exists()) total += sf.length()
                }
            }
        }
        return if (total > 0L) total else 1L
    }

    private suspend fun moveSourcesAndStreams(
        itemId: UUID,
        targetDir: File,
        onBytesCopied: ((Long) -> Unit)? = null,
    ) {
        val sources = database.getSources(itemId)
        for (source in sources) {
            val oldFile = File(source.path)
            if (oldFile.exists() && oldFile.length() > 0) {
                val newFile = File(targetDir, oldFile.name)
                val stats = StatFs(targetDir.path)
                if (stats.availableBytes < oldFile.length()) {
                    throw IllegalStateException("Not enough storage on target volume")
                }
                copyWithProgress(oldFile, newFile, onBytesCopied)
                database.setSourcePath(source.id, newFile.absolutePath)
                oldFile.delete()
            }

            val streams = database.getMediaStreamsBySourceId(source.id)
            for (stream in streams) {
                val oldStreamFile = File(stream.path)
                if (oldStreamFile.exists() && oldStreamFile.length() > 0) {
                    val newStreamFile = File(targetDir, oldStreamFile.name)
                    copyWithProgress(oldStreamFile, newStreamFile, onBytesCopied)
                    database.setMediaStreamPath(stream.id, newStreamFile.absolutePath)
                    oldStreamFile.delete()
                }
            }
        }
    }

    private fun copyWithProgress(
        source: File,
        destination: File,
        onBytesCopied: ((Long) -> Unit)? = null,
    ) {
        val buffer = ByteArray(256 * 1024)
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } >= 0) {
                    if (bytesRead > 0) {
                        output.write(buffer, 0, bytesRead)
                        onBytesCopied?.invoke(bytesRead.toLong())
                    }
                }
                output.flush()
            }
        }
    }

    override fun downloadUserImage(userId: UUID, imageTag: String?) {
        val request =
            OneTimeWorkRequestBuilder<ImagesDownloaderWorker>()
                .setInputData(
                    workDataOf(
                        ImagesDownloaderWorker.KEY_ITEM_ID to userId.toString(),
                        ImagesDownloaderWorker.KEY_TYPE to ImagesDownloaderWorker.TYPE_USER,
                        ImagesDownloaderWorker.KEY_IMAGE_TAG to imageTag,
                    )
                )
                .build()

        workManager.enqueue(request)
    }
}
