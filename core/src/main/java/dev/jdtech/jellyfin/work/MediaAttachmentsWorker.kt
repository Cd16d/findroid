package dev.jdtech.jellyfin.work

import android.content.Context
import android.os.StatFs
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.models.toFindroidMediaStreamDto
import dev.jdtech.jellyfin.models.toFindroidTrickplayInfoDto
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

/**
 * Worker dedicated to downloading ancillary media assets in the background:
 * - High-resolution poster, backdrop, and logo images (for item, parent season, and parent show).
 * - External subtitle tracks (.srt, .vtt).
 * - Optional external audio tracks (only when confirmed by the user).
 *
 * Runs with NetworkType.CONNECTED constraints and automatically retries with exponential backoff,
 * preventing corrupted 0-byte images and blurry placeholder fallbacks.
 */
@HiltWorker
class MediaAttachmentsWorker
@AssistedInject
constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val repository: JellyfinRepository,
    private val database: ServerDatabaseDao,
    private val client: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // Secondary constructor for testing with custom dispatcher
    constructor(
        appContext: Context,
        params: WorkerParameters,
        repository: JellyfinRepository,
        database: ServerDatabaseDao,
        client: OkHttpClient,
        ioDispatcher: CoroutineDispatcher,
    ) : this(appContext, params, repository, database, client) {
        this.ioDispatcher = ioDispatcher
    }

    companion object {
        const val KEY_ITEM_ID = "item_id"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_STORAGE_INDEX = "storage_index"
        const val KEY_DOWNLOAD_EXTERNAL_AUDIO = "download_external_audio"
        const val KEY_PROGRESS = "progress"
        const val MAX_RETRIES = 3
    }

    override suspend fun doWork(): Result =
        withContext(ioDispatcher) {
            val itemIdStr =
                params.inputData.getString(KEY_ITEM_ID) ?: return@withContext Result.failure()
            val itemId =
                try {
                    UUID.fromString(itemIdStr)
                } catch (e: IllegalArgumentException) {
                    Timber.e(e, "Invalid UUID string passed to MediaAttachmentsWorker: $itemIdStr")
                    return@withContext Result.failure()
                } catch (e: Exception) {
                    return@withContext Result.failure()
                }
            val sourceId = params.inputData.getString(KEY_SOURCE_ID).orEmpty()
            val storageIndex = params.inputData.getInt(KEY_STORAGE_INDEX, 0)
            val downloadExternalAudio =
                params.inputData.getBoolean(KEY_DOWNLOAD_EXTERNAL_AUDIO, false)

            if (runAttemptCount >= MAX_RETRIES) {
                Timber.e(
                    "MediaAttachmentsWorker exceeded max retry limit ($MAX_RETRIES) for item $itemId"
                )
                return@withContext Result.failure()
            }

            try {
                safeSetProgress(0)
                currentCoroutineContext().ensureActive()
                if (isStopped) return@withContext Result.retry()

                val item = repository.getItem(itemId)
                if (item == null) {
                    Timber.w("Item $itemId could not be resolved from repository, retrying")
                    return@withContext if (runAttemptCount + 1 >= MAX_RETRIES) {
                        Result.failure()
                    } else {
                        Result.retry()
                    }
                }

                // 1. Download Images (Item + Parent Season & Show if episode)
                downloadItemImages(item)
                currentCoroutineContext().ensureActive()
                if (isStopped) return@withContext Result.retry()
                safeSetProgress(33)

                // 2. Download External Subtitles and optionally External Audio
                if (sourceId.isNotEmpty()) {
                    downloadExternalStreams(item, sourceId, storageIndex, downloadExternalAudio)
                }
                currentCoroutineContext().ensureActive()
                if (isStopped) return@withContext Result.retry()
                safeSetProgress(66)

                // 3. Download Trickplay Data asynchronously in worker
                if (sourceId.isNotEmpty()) {
                    downloadTrickplay(item, sourceId)
                }
                currentCoroutineContext().ensureActive()
                if (isStopped) return@withContext Result.retry()
                safeSetProgress(100)

                Result.success()
            } catch (e: CancellationException) {
                Timber.d("MediaAttachmentsWorker cancelled for item $itemId")
                throw e
            } catch (e: IOException) {
                Timber.w(
                    e,
                    "MediaAttachmentsWorker encountered I/O error for item $itemId (attempt $runAttemptCount)",
                )
                if (runAttemptCount + 1 >= MAX_RETRIES) Result.failure() else Result.retry()
            } catch (e: Exception) {
                Timber.e(e, "MediaAttachmentsWorker failed for item $itemId")
                if (runAttemptCount + 1 >= MAX_RETRIES) Result.failure() else Result.retry()
            }
        }

    private suspend fun safeSetProgress(progress: Int) {
        if (!isStopped) {
            try {
                setProgress(workDataOf(KEY_PROGRESS to progress))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.d(e, "Failed to update worker progress")
            }
        }
    }

    private suspend fun downloadItemImages(item: FindroidItem) {
        // Current item images
        saveItemImages(
            item.id,
            mapOf(
                "primary" to item.images.primary?.uri?.toString(),
                "backdrop" to item.images.backdrop?.uri?.toString(),
                "logo" to item.images.logo?.uri?.toString(),
            ),
        )

        // For episodes, also ensure parent show and season images are downloaded
        if (item is FindroidEpisode) {
            try {
                currentCoroutineContext().ensureActive()
                if (isStopped) return
                val season = repository.getSeason(item.seasonId)
                saveItemImages(
                    season.id,
                    mapOf(
                        "primary" to season.images.primary?.uri?.toString(),
                        "backdrop" to season.images.backdrop?.uri?.toString(),
                    ),
                )

                currentCoroutineContext().ensureActive()
                if (isStopped) return
                val show = repository.getShow(item.seriesId)
                saveItemImages(
                    show.id,
                    mapOf(
                        "primary" to show.images.primary?.uri?.toString(),
                        "backdrop" to show.images.backdrop?.uri?.toString(),
                        "logo" to show.images.logo?.uri?.toString(),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to download parent show/season images for episode ${item.name}")
            }
        }
    }

    private suspend fun saveItemImages(id: UUID, imageMap: Map<String, String?>) {
        val baseDir = File(appContext.filesDir, "images/$id")
        baseDir.mkdirs()

        for ((name, url) in imageMap) {
            currentCoroutineContext().ensureActive()
            if (isStopped) return

            if (url.isNullOrBlank()) continue
            val file = File(baseDir, name)
            if (file.exists() && file.length() > 0L) continue

            val tempFile = File(baseDir, "$name.tmp")
            val request = Request.Builder().url(url).build()
            var moveSuccessful = false
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w(
                            "Failed to download image $name for item $id: HTTP ${response.code}"
                        )
                        return@use
                    }
                    val body = response.body
                    val contentLength = body.contentLength()
                    if (contentLength > 0L && !hasEnoughSpace(baseDir, contentLength)) {
                        Timber.w(
                            "Insufficient storage for image $name on item $id: required $contentLength bytes"
                        )
                        return@use
                    }
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tempFile.exists() && tempFile.length() > 0L) {
                        moveSuccessful = atomicMove(tempFile, file)
                    }
                }
            } catch (e: CancellationException) {
                tempFile.delete()
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to download image $name for item $id: ${e.message}")
            } finally {
                if (!moveSuccessful && tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    private suspend fun downloadExternalStreams(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
        downloadExternalAudio: Boolean,
    ) {
        val externalDirs = appContext.getExternalFilesDirs(null)?.filterNotNull().orEmpty()
        val storageLocation =
            externalDirs.getOrNull(storageIndex)
                ?: externalDirs.firstOrNull()
                ?: appContext.filesDir
        val sources = repository.getMediaSources(item.id, true)
        val source = sources.firstOrNull { it.id == sourceId } ?: return

        val downloadsDir = File(storageLocation, "downloads")
        downloadsDir.mkdirs()

        val existingStreams =
            try {
                database.getMediaStreamsBySourceId(source.id)
            } catch (e: Exception) {
                emptyList()
            }

        for (stream in source.mediaStreams.filter { it.isExternal }) {
            currentCoroutineContext().ensureActive()
            if (isStopped) return

            // Check stream type: always download subtitles; only download audio if requested
            val isSubtitle = stream.type == MediaStreamType.SUBTITLE
            val isAudio = stream.type == MediaStreamType.AUDIO

            if (!isSubtitle && (!isAudio || !downloadExternalAudio)) {
                continue
            }

            val streamUrl = stream.path ?: continue
            val extension =
                if (isSubtitle) stream.codec.ifEmpty { "srt" } else stream.codec.ifEmpty { "m4a" }

            val existing = existingStreams.firstOrNull {
                it.type == stream.type &&
                    it.codec == stream.codec &&
                    it.title == stream.title &&
                    it.language == stream.language
            }

            val streamId =
                existing?.id
                    ?: UUID.nameUUIDFromBytes(
                        "${source.id}_${stream.index}_${stream.title}_${stream.language}_${stream.codec}_${stream.type}"
                            .toByteArray()
                    )

            val streamFile = File(downloadsDir, "${item.id}.${source.id}.${streamId}.$extension")

            if (streamFile.exists() && streamFile.length() > 0L) {
                if (existing == null) {
                    database.insertMediaStream(
                        stream.toFindroidMediaStreamDto(
                            streamId,
                            source.id,
                            streamFile.absolutePath,
                        )
                    )
                }
                continue
            }

            val tempStreamFile =
                File(downloadsDir, "${item.id}.${source.id}.${streamId}.$extension.tmp")

            val request = Request.Builder().url(streamUrl).build()
            var moveSuccessful = false
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w(
                            "Failed to download external stream ${stream.title} for item ${item.id}: HTTP ${response.code}"
                        )
                        return@use
                    }
                    val body = response.body
                    val contentLength = body.contentLength()
                    if (contentLength > 0L && !hasEnoughSpace(downloadsDir, contentLength)) {
                        Timber.w(
                            "Insufficient storage for external stream ${stream.title} on item ${item.id}: required $contentLength bytes"
                        )
                        return@use
                    }
                    body.byteStream().use { input ->
                        tempStreamFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tempStreamFile.exists() && tempStreamFile.length() > 0L) {
                        moveSuccessful = atomicMove(tempStreamFile, streamFile)
                        if (moveSuccessful) {
                            database.insertMediaStream(
                                stream.toFindroidMediaStreamDto(
                                    streamId,
                                    source.id,
                                    streamFile.absolutePath,
                                )
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                tempStreamFile.delete()
                throw e
            } catch (e: Exception) {
                Timber.w(
                    e,
                    "Failed to download external stream ${stream.title} for item ${item.id}",
                )
            } finally {
                if (!moveSuccessful && tempStreamFile.exists()) {
                    tempStreamFile.delete()
                }
            }
        }
    }

    private suspend fun downloadTrickplay(item: FindroidItem, sourceId: String) {
        if (item !is FindroidSources) return
        val trickplayInfo = item.trickplayInfo?.get(sourceId) ?: return
        try {
            val maxIndex =
                ceil(
                        trickplayInfo.thumbnailCount
                            .toDouble()
                            .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                    )
                    .toInt()
            val basePath = "trickplay/${item.id}/$sourceId"
            val dir = File(appContext.filesDir, basePath)
            dir.mkdirs()
            var tileCount = 0
            for (i in 0..maxIndex) {
                currentCoroutineContext().ensureActive()
                if (isStopped) break

                val targetFile = File(dir, i.toString())
                if (targetFile.exists() && targetFile.length() > 0L) {
                    tileCount++
                    continue
                }

                val byteArray =
                    repository.getTrickplayData(item.id, trickplayInfo.width, i) ?: continue
                if (byteArray.isEmpty()) continue

                if (!hasEnoughSpace(dir, byteArray.size.toLong())) {
                    Timber.w("Insufficient storage for trickplay tile $i of item ${item.id}")
                    break
                }

                val tempFile = File(dir, "$i.tmp")
                var moveSuccessful = false
                try {
                    tempFile.writeBytes(byteArray)
                    if (tempFile.exists() && tempFile.length() > 0L) {
                        moveSuccessful = atomicMove(tempFile, targetFile)
                        if (moveSuccessful) {
                            tileCount++
                        }
                    }
                } finally {
                    if (!moveSuccessful && tempFile.exists()) {
                        tempFile.delete()
                    }
                }
            }
            if (tileCount > 0) {
                database.insertTrickplayInfo(trickplayInfo.toFindroidTrickplayInfoDto(sourceId))
                Timber.i(
                    "Trickplay data downloaded successfully for item ${item.id} ($tileCount tiles)"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to download trickplay data for item ${item.id}")
        }
    }

    private fun hasEnoughSpace(dir: File, requiredBytes: Long): Boolean {
        return try {
            dir.mkdirs()
            val stat = StatFs(dir.absolutePath)
            stat.availableBytes >= requiredBytes
        } catch (e: Exception) {
            Timber.w(e, "StatFs check failed for path ${dir.absolutePath}")
            true
        }
    }

    private fun atomicMove(source: File, dest: File): Boolean {
        if (!source.exists()) return false
        return try {
            Files.move(
                source.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        } catch (e: Exception) {
            try {
                Files.move(
                    source.toPath(),
                    dest.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
                true
            } catch (e2: Exception) {
                if (dest.exists()) {
                    dest.delete()
                }
                if (source.renameTo(dest)) {
                    true
                } else {
                    try {
                        source.copyTo(dest, overwrite = true)
                        source.delete()
                        true
                    } catch (e3: Exception) {
                        Timber.w(
                            e3,
                            "Failed to move file from ${source.absolutePath} to ${dest.absolutePath}",
                        )
                        false
                    }
                }
            }
        }
    }
}
