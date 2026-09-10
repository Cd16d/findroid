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

    override suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String?,
        storageIndex: Int,
        presetId: String?,
        downloadExternalAudio: Boolean,
        audioStreamIndex: Int?,
    ): Pair<Long, UiText?> = coroutineScope {
        try {
            val sources = jellyfinRepository.getMediaSources(item.id, true)
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

            if (linkExistingDiskDownload(item, currentUserId)) {
                return@coroutineScope Pair(0L, null)
            }

            val activePresetId =
                presetId ?: appPreferences.getValue(appPreferences.defaultTranscodePresetId)
            val downloadUrl = resolveDownloadUrl(item, source, activePresetId, audioStreamIndex)

            val isTranscoding =
                DownloadQualityPresets.isTranscodingPreset(activePresetId, appPreferences) &&
                    downloadUrl != source.path
            val estimatedBytes =
                calculateEstimatedBytes(item, source, activePresetId, isTranscoding)

            val destFile = File(storageLocation, "downloads/${item.id}.${source.id}.download")
            destFile.parentFile?.mkdirs()

            val storageError =
                checkAvailableStorageSpace(storageLocation, estimatedBytes, source.size)
            if (storageError != null) {
                return@coroutineScope Pair(-1L, storageError)
            }

            val existingSourceDto = database.getSourceByDownloadId(source.downloadId ?: -1L)
            val downloadId = existingSourceDto?.downloadId ?: idCounter.incrementAndGet()

            val allowMetered = appPreferences.getValue(appPreferences.downloadOverMobileData)
            val allowRoaming = appPreferences.getValue(appPreferences.downloadWhenRoaming)

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

            insertItemMetadataToDb(item, appPreferences.getValue(appPreferences.currentServer))

            val sourceDto = source.toFindroidSourceDto(item.id, destFile.absolutePath)
            database.insertSource(sourceDto.copy(downloadId = downloadId))
            database.insertUserData(item.toFindroidUserDataDto(currentUserId))
            database.insertUserDownload(UserDownloadDto(userId = currentUserId, itemId = item.id))

            startAttachmentsWorker(item, source.id, effectiveIndex, downloadExternalAudio)

            val segments = jellyfinRepository.getSegments(item.id)
            segments.forEach { database.insertSegment(it.toFindroidSegmentsDto(item.id)) }

            Timber.i("downloadItem enqueued for ${item.name}, downloadId=$downloadId")

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
        engine.cancel(downloadId)
        val source =
            database.getSourceByDownloadId(downloadId)?.toFindroidSource(database) ?: return
        deleteItem(item, source)
    }

    override suspend fun pauseDownload(downloadId: Long) {
        engine.pause(downloadId)
    }

    override suspend fun resumeDownload(downloadId: Long) {
        engine.resume(downloadId)
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
            insertItemMetadataToDb(
                item,
                appPreferences.getValue(appPreferences.currentServer),
                wrapShowInTryCatch = true,
            )
            database.insertUserDownload(UserDownloadDto(userId = currentUserId, itemId = item.id))
            database.insertUserData(item.toFindroidUserDataDto(currentUserId))
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

    private fun cleanupShowData(seriesId: UUID) {
        database.deleteShow(seriesId)
        if (database.countUserDataToBeSynced(seriesId) == 0) {
            database.deleteUserData(seriesId)
        }
        File(context.filesDir, "trickplay/$seriesId").deleteRecursively()
        File(context.filesDir, "images/$seriesId").deleteRecursively()
    }

    private fun cleanupSeasonData(seasonId: UUID) {
        database.deleteSeason(seasonId)
        if (database.countUserDataToBeSynced(seasonId) == 0) {
            database.deleteUserData(seasonId)
        }
        File(context.filesDir, "trickplay/$seasonId").deleteRecursively()
        File(context.filesDir, "images/$seasonId").deleteRecursively()
    }

    private fun cleanupEpisodeParents(item: FindroidEpisode) {
        val remainingEpisodes = database.getEpisodesBySeasonId(item.seasonId)
        if (remainingEpisodes.isEmpty()) {
            cleanupSeasonData(item.seasonId)
            val remainingSeasons = database.getSeasonsByShowId(item.seriesId)
            if (remainingSeasons.isEmpty()) {
                cleanupShowData(item.seriesId)
            }
        }
    }

    private fun deleteSourcesAndStreamsFiles(itemId: UUID, source: FindroidSource) {
        val allSources = database.getSources(itemId)
        for (s in allSources.ifEmpty { listOf(source.toFindroidSourceDto(itemId, source.path)) }) {
            database.deleteSource(s.id)
            File(s.path).delete()
            val mediaStreams = database.getMediaStreamsBySourceId(s.id)
            for (mediaStream in mediaStreams) {
                File(mediaStream.path).delete()
            }
            database.deleteMediaStreamsBySourceId(s.id)
        }
        database.deleteSource(source.id)
        File(source.path).delete()
    }

    override suspend fun deleteItem(item: FindroidItem, source: FindroidSource, userId: UUID?) {
        val targetUserId =
            userId
                ?: try {
                    jellyfinRepository.getUserId()
                } catch (_: Exception) {
                    null
                }
        if (targetUserId != null) {
            database.deleteUserDownload(targetUserId, item.id)
        } else {
            database.deleteUserDownloadsByItemId(item.id)
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

        if (database.countUserDataToBeSynced(item.id) == 0) {
            database.deleteUserData(item.id)
        }
        File(context.filesDir, "trickplay/${item.id}").deleteRecursively()
        File(context.filesDir, "images/${item.id}").deleteRecursively()
    }

    override suspend fun getProgress(downloadId: Long?): Downloader.Progress {
        if (downloadId == null) return Downloader.Progress(DownloadStatus.FAILED, 0, -1L, -1L)
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

    override suspend fun getProgress(downloadIds: List<Long>): Map<Long, Downloader.Progress> {
        val snapshots = engine.snapshots(downloadIds)
        return downloadIds.associateWith { id ->
            val snap = snapshots[id]
            if (snap != null) {
                val progress = calculateProgressPercentage(snap.bytesDownloaded, snap.totalBytes)
                Downloader.Progress(
                    status = snap.status,
                    progress = progress,
                    bytesDownloaded = snap.bytesDownloaded,
                    totalBytes = snap.totalBytes,
                )
            } else {
                Downloader.Progress(DownloadStatus.FAILED, 0, -1L, -1L)
            }
        }
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
            val source = database.getSourceByDownloadId(downloadId) ?: return@withContext false
            if (!source.path.endsWith(".download")) return@withContext true
            val partialFile = File(source.path)

            val basePath = source.path.removeSuffix(".download")
            val isFragmented =
                if (partialFile.exists()) Mp4Remuxer.isFragmentedMp4(partialFile) else false
            val finalFile =
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
                    is FindroidMovie ->
                        moveSourcesAndStreams(item.id, targetDownloadsDir, progressCallback)
                    is FindroidEpisode ->
                        moveSourcesAndStreams(item.id, targetDownloadsDir, progressCallback)
                    is FindroidShow -> {
                        val epList =
                            database.getDownloadedEpisodesByShowId(item.id).firstOrNull()
                                ?: emptyList()
                        for (ep in epList) {
                            moveSourcesAndStreams(ep.id, targetDownloadsDir, progressCallback)
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
                is FindroidMovie,
                is FindroidEpisode -> listOf(item.id)
                is FindroidShow ->
                    database.getDownloadedEpisodesByShowId(item.id).firstOrNull()?.map { it.id }
                        ?: emptyList()
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

    private fun moveSourcesAndStreams(
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
