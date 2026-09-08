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
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.models.UiText
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
import dev.jdtech.jellyfin.models.UserDownloadDto
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
            val source = if (sourceId != null) {
                sources.firstOrNull { it.id == sourceId } ?: sources.firstOrNull()
            } else {
                sources.firstOrNull()
            } ?: return@coroutineScope Pair(-1L, UiText.StringResource(CoreR.string.unknown_error))

            val effectiveSourceId = source.id
            val segments = jellyfinRepository.getSegments(item.id)

            val preferredStorageIndex =
                appPreferences.getValue(appPreferences.defaultDownloadStorageIndex).toIntOrNull() ?: -1
            val effectiveIndex =
                if (storageIndex >= 0) storageIndex
                else if (preferredStorageIndex >= 0) preferredStorageIndex
                else 0
            val dirs = context.getExternalFilesDirs(null)
            val storageLocation = dirs.getOrNull(effectiveIndex) ?: dirs.getOrNull(0)
            if (
                storageLocation == null ||
                Environment.getExternalStorageState(storageLocation) != Environment.MEDIA_MOUNTED
            ) {
                return@coroutineScope Pair(
                    -1L,
                    UiText.StringResource(CoreR.string.storage_unavailable),
                )
            }

            val currentUserId = jellyfinRepository.getUserId()

            // Implementation B: Shared disk storage check!
            // If the item is already completely downloaded on disk by any profile:
            val existingSources = database.getSources(item.id).filter { !it.path.endsWith(".download") && File(it.path).exists() }
            if (existingSources.isNotEmpty()) {
                Timber.i("downloadItem: item ${item.name} already exists on disk, linking to user $currentUserId")
                database.insertUserDownload(UserDownloadDto(userId = currentUserId, itemId = item.id))
                database.insertUserData(item.toFindroidUserDataDto(currentUserId))
                return@coroutineScope Pair(0L, null)
            }

            val activePresetId = presetId ?: appPreferences.getValue(appPreferences.defaultTranscodePresetId)
            val downloadUrl = resolveDownloadUrl(item, source, activePresetId, audioStreamIndex)

            val isTranscoding = DownloadQualityPresets.isTranscodingPreset(activePresetId, appPreferences) && downloadUrl != source.path
            val estimatedBytes = if (isTranscoding && item.runtimeTicks > 0) {
                val preset = DownloadQualityPresets.getById(activePresetId, appPreferences)
                val durationSec = (item.runtimeTicks / 10_000_000L).coerceAtLeast(1)
                ((preset.maxBitrateBps + preset.audioBitrateBps) * durationSec) / 8L
            } else {
                source.size
            }

            val destFile = File(storageLocation, "downloads/${item.id}.${source.id}.download")
            destFile.parentFile?.mkdirs()

            val requiredStorage = if (estimatedBytes > 0) estimatedBytes else source.size
            val stats = StatFs(storageLocation.path)
            if (requiredStorage > 0 && stats.availableBytes < requiredStorage) {
                return@coroutineScope Pair(
                    -1L,
                    UiText.StringResource(
                        CoreR.string.not_enough_storage,
                        Formatter.formatFileSize(context, requiredStorage),
                        Formatter.formatFileSize(context, stats.availableBytes),
                    ),
                )
            }

            // Check if there's an existing download ID for resume
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

            when (item) {
                is FindroidMovie -> {
                    database.insertMovie(
                        item.toFindroidMovieDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                }

                is FindroidEpisode -> {
                    val show = jellyfinRepository.getShow(item.seriesId)
                    database.insertShow(
                        show.toFindroidShowDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                    val season = jellyfinRepository.getSeason(item.seasonId)
                    database.insertSeason(season.toFindroidSeasonDto())
                    database.insertEpisode(
                        item.toFindroidEpisodeDto(
                            appPreferences.getValue(appPreferences.currentServer)
                        )
                    )
                }
            }

            val sourceDto = source.toFindroidSourceDto(item.id, destFile.absolutePath)
            database.insertSource(sourceDto.copy(downloadId = downloadId))
            database.insertUserData(item.toFindroidUserDataDto(currentUserId))
            database.insertUserDownload(UserDownloadDto(userId = currentUserId, itemId = item.id))

            // Delegate images, external subtitle/audio tracks, and trickplay to MediaAttachmentsWorker
            startAttachmentsWorker(item, source.id, effectiveIndex, downloadExternalAudio)

            segments.forEach { database.insertSegment(it.toFindroidSegmentsDto(item.id)) }

            Timber.i("downloadItem enqueued for ${item.name}, downloadId=$downloadId")

            Pair(downloadId, null)
        } catch (e: Exception) {
            Timber.e(e, "downloadItem failed for ${item.name}")
            Pair(-1L, if (e.message != null) UiText.DynamicString(e.message!!) else UiText.StringResource(CoreR.string.unknown_error))
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
        val originalBitrate = if (item.runtimeTicks > 0 && source.size > 0) {
            (source.size * 8) / (item.runtimeTicks / 10_000_000L).coerceAtLeast(1)
        } else {
            0L
        }

        // Smart fallback: if original file is already smaller/equal resolution & bitrate, download original directly
        if (originalBitrate in 1..preset.maxBitrateBps && originalHeight <= preset.maxHeight) {
            Timber.i("Original file bitrate ($originalBitrate bps) <= preset (${preset.maxBitrateBps} bps); skipping transcode and downloading original.")
            return source.path
        }

        val audioCodec = preset.audioCodec.ifBlank { "aac" }
        val audioChannels = preset.audioChannels.coerceIn(1, 8)
        val audioBitrate = preset.audioBitrateBps

        val supportedVideoCodecs = DeviceCodecCapabilities.getSupportedVideoCodecsForJellyfin()

        val audioStreamParam = if (audioStreamIndex != null) "&audioStreamIndex=$audioStreamIndex" else ""

        // Progressive MP4 transcode stream from Jellyfin with complete video and audio parameters, allowing remux when compatible
        val baseUrl = jellyfinRepository.getBaseUrl()
        return "$baseUrl/Videos/${item.id}/stream.mp4?static=false&mediaSourceId=${source.id}&videoCodec=$supportedVideoCodecs&audioCodec=$audioCodec&videoBitRate=${preset.maxBitrateBps}&audioBitRate=$audioBitrate&maxWidth=${preset.maxWidth}&maxHeight=${preset.maxHeight}&audioChannels=$audioChannels&transcodingMaxAudioChannels=$audioChannels&audioSampleRate=${preset.audioSampleRate}&allowVideoStreamCopy=true&allowAudioStreamCopy=true&breakOnNonKeyFrames=true$audioStreamParam"
    }

    private fun startAttachmentsWorker(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
        downloadExternalAudio: Boolean,
    ) {
        val request = OneTimeWorkRequestBuilder<MediaAttachmentsWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
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
        val source = database.getSourceByDownloadId(downloadId)?.toFindroidSource(database) ?: return
        deleteItem(item, source)
    }

    override suspend fun pauseDownload(downloadId: Long) {
        engine.pause(downloadId)
    }

    override suspend fun resumeDownload(downloadId: Long) {
        engine.resume(downloadId)
    }

    override suspend fun deleteItem(item: FindroidItem, source: FindroidSource, userId: UUID?) {
        val targetUserId = userId ?: try { jellyfinRepository.getUserId() } catch (_: Exception) { null }
        if (targetUserId != null) {
            database.deleteUserDownload(targetUserId, item.id)
        } else {
            database.deleteUserDownloadsByItemId(item.id)
        }

        // Implementation B: Shared file check!
        // If other profiles still have this item in their downloads, do NOT delete physical files!
        if (database.countUserDownloads(item.id) > 0) {
            Timber.i("deleteItem: item ${item.name} still downloaded by other user(s), preserving physical files")
            return
        }

        engine.cancel(source.downloadId ?: -1L)

        when (item) {
            is FindroidMovie -> database.deleteMovie(item.id)
            is FindroidEpisode -> {
                database.deleteEpisode(item.id)
                val remainingEpisodes = database.getEpisodesBySeasonId(item.seasonId)
                if (remainingEpisodes.isEmpty()) {
                    database.deleteSeason(item.seasonId)
                    if (database.countUserDataToBeSynced(item.seasonId) == 0) {
                        database.deleteUserData(item.seasonId)
                    }
                    File(context.filesDir, "trickplay/${item.seasonId}").deleteRecursively()
                    File(context.filesDir, "images/${item.seasonId}").deleteRecursively()
                    val remainingSeasons = database.getSeasonsByShowId(item.seriesId)
                    if (remainingSeasons.isEmpty()) {
                        database.deleteShow(item.seriesId)
                        if (database.countUserDataToBeSynced(item.seriesId) == 0) {
                            database.deleteUserData(item.seriesId)
                        }
                        File(context.filesDir, "trickplay/${item.seriesId}").deleteRecursively()
                        File(context.filesDir, "images/${item.seriesId}").deleteRecursively()
                    }
                }
            }
        }

        database.deleteSource(source.id)
        File(source.path).delete()

        val mediaStreams = database.getMediaStreamsBySourceId(source.id)
        for (mediaStream in mediaStreams) {
            File(mediaStream.path).delete()
        }
        database.deleteMediaStreamsBySourceId(source.id)

        if (database.countUserDataToBeSynced(item.id) == 0) {
            database.deleteUserData(item.id)
        }
        File(context.filesDir, "trickplay/${item.id}").deleteRecursively()
        File(context.filesDir, "images/${item.id}").deleteRecursively()
    }

    override suspend fun getProgress(downloadId: Long?): Downloader.Progress {
        if (downloadId == null) return Downloader.Progress(DownloadStatus.FAILED, 0, -1L, -1L)
        val snap = engine.snapshot(downloadId)
            ?: return Downloader.Progress(DownloadStatus.FAILED, 0, -1L, -1L)
        val progress = if (snap.totalBytes > 0) {
            (snap.bytesDownloaded * 100 / snap.totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
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
                val progress = if (snap.totalBytes > 0) {
                    (snap.bytesDownloaded * 100 / snap.totalBytes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
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

    override suspend fun getActiveDownloads(): List<Pair<FindroidItem, Long>> = withContext(Dispatchers.IO) {
        val currentUserId = jellyfinRepository.getUserId()
        val incompleteSources = database.getIncompleteSources()
        incompleteSources.mapNotNull { source ->
            val dlId = source.downloadId ?: return@mapNotNull null
            val movie = try {
                database.getMovie(source.itemId).toFindroidMovie(database, currentUserId)
            } catch (_: Exception) {
                null
            }
            val episode = if (movie == null) {
                try {
                    database.getEpisode(source.itemId).toFindroidEpisode(database, currentUserId)
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

    override suspend fun finalizeDownload(downloadId: Long): Boolean = withContext(Dispatchers.IO) {
        val source = database.getSourceByDownloadId(downloadId) ?: return@withContext false
        if (!source.path.endsWith(".download")) return@withContext true
        val partialFile = File(source.path)
        if (!partialFile.exists() || partialFile.length() == 0L) return@withContext false

        val isFragmented = Mp4Remuxer.isFragmentedMp4(partialFile)
        val basePath = source.path.removeSuffix(".download")
        val finalFile = if (isFragmented) File("$basePath.mp4") else File(basePath)

        var success = false
        if (isFragmented) {
            val stats = try { StatFs(partialFile.parentFile?.path ?: "") } catch (_: Exception) { null }
            val hasSpace = stats == null || stats.availableBytes >= partialFile.length()
            if (hasSpace) {
                Timber.i("Remuxing fragmented MP4 for download $downloadId into seekable MP4: ${finalFile.name}")
                val remuxed = Mp4Remuxer.remuxToStandardMp4(partialFile, finalFile)
                if (remuxed) {
                    partialFile.delete()
                    success = true
                } else {
                    Timber.w("Remux failed for download $downloadId; falling back to direct rename")
                }
            } else {
                Timber.w("Insufficient storage to remux download $downloadId (available=${stats.availableBytes}, needed=${partialFile.length()}); falling back to rename")
            }
        }

        if (!success) {
            if (finalFile.exists()) finalFile.delete()
            success = partialFile.renameTo(finalFile)
        }

        if (success) {
            database.setSourcePath(source.id, finalFile.absolutePath)
            true
        } else {
            false
        }
    }

    override suspend fun moveItemStorage(
        item: FindroidItem,
        targetStorageIndex: Int,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dirs = context.getExternalFilesDirs(null)
            val targetDir = dirs.getOrNull(targetStorageIndex)
                ?: return@withContext Result.failure(IllegalStateException("Target storage unavailable"))
            if (Environment.getExternalStorageState(targetDir) != Environment.MEDIA_MOUNTED) {
                return@withContext Result.failure(IllegalStateException("Target storage not mounted"))
            }
            val targetDownloadsDir = File(targetDir, "downloads").apply { mkdirs() }

            val totalBytes = calculateTotalItemBytes(item)
            var transferredBytes = 0L

            val progressCallback: (Long) -> Unit = { chunkBytes ->
                transferredBytes += chunkBytes
                onProgress?.invoke(transferredBytes, totalBytes)
            }

            when (item) {
                is FindroidMovie -> moveSourcesAndStreams(item.id, targetDownloadsDir, progressCallback)
                is FindroidEpisode -> moveSourcesAndStreams(item.id, targetDownloadsDir, progressCallback)
                is FindroidShow -> {
                    val epList = database.getDownloadedEpisodesByShowId(item.id).firstOrNull() ?: emptyList()
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
        val itemIds = when (item) {
            is FindroidMovie, is FindroidEpisode -> listOf(item.id)
            is FindroidShow -> database.getDownloadedEpisodesByShowId(item.id).firstOrNull()?.map { it.id } ?: emptyList()
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

    private fun moveSourcesAndStreams(itemId: UUID, targetDir: File, onBytesCopied: ((Long) -> Unit)? = null) {
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

    private fun copyWithProgress(source: File, destination: File, onBytesCopied: ((Long) -> Unit)? = null) {
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
                .setInputData(workDataOf(
                    ImagesDownloaderWorker.KEY_ITEM_ID to userId.toString(),
                    ImagesDownloaderWorker.KEY_TYPE to ImagesDownloaderWorker.TYPE_USER,
                    ImagesDownloaderWorker.KEY_IMAGE_TAG to imageTag
                ))
                .build()

        workManager.enqueue(request)
    }
}
