package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
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

    companion object {
        const val KEY_ITEM_ID = "item_id"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_STORAGE_INDEX = "storage_index"
        const val KEY_DOWNLOAD_EXTERNAL_AUDIO = "download_external_audio"
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val itemIdStr =
                params.inputData.getString(KEY_ITEM_ID) ?: return@withContext Result.failure()
            val itemId =
                try {
                    UUID.fromString(itemIdStr)
                } catch (e: Exception) {
                    return@withContext Result.failure()
                }
            val sourceId = params.inputData.getString(KEY_SOURCE_ID) ?: ""
            val storageIndex = params.inputData.getInt(KEY_STORAGE_INDEX, 0)
            val downloadExternalAudio =
                params.inputData.getBoolean(KEY_DOWNLOAD_EXTERNAL_AUDIO, false)

            try {
                val item = repository.getItem(itemId) ?: return@withContext Result.retry()

                // 1. Download Images (Item + Parent Season & Show if episode)
                downloadItemImages(item)

                // 2. Download External Subtitles and optionally External Audio
                if (sourceId.isNotEmpty()) {
                    downloadExternalStreams(item, sourceId, storageIndex, downloadExternalAudio)
                }

                // 3. Download Trickplay Data asynchronously in worker
                if (sourceId.isNotEmpty()) {
                    downloadTrickplay(item, sourceId)
                }

                Result.success()
            } catch (e: IOException) {
                Timber.w(
                    e,
                    "MediaAttachmentsWorker encountered I/O error for item $itemId, retrying",
                )
                Result.retry()
            } catch (e: Exception) {
                Timber.e(e, "MediaAttachmentsWorker failed for item $itemId")
                Result.retry()
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
                val season = repository.getSeason(item.seasonId)
                saveItemImages(
                    season.id,
                    mapOf(
                        "primary" to season.images.primary?.uri?.toString(),
                        "backdrop" to season.images.backdrop?.uri?.toString(),
                    ),
                )

                val show = repository.getShow(item.seriesId)
                saveItemImages(
                    show.id,
                    mapOf(
                        "primary" to show.images.primary?.uri?.toString(),
                        "backdrop" to show.images.backdrop?.uri?.toString(),
                        "logo" to show.images.logo?.uri?.toString(),
                    ),
                )
            } catch (e: Exception) {
                Timber.w(e, "Failed to download parent show/season images for episode ${item.name}")
            }
        }
    }

    private fun saveItemImages(id: UUID, imageMap: Map<String, String?>) {
        val baseDir = File(appContext.filesDir, "images/$id")
        baseDir.mkdirs()

        for ((name, url) in imageMap) {
            if (url.isNullOrBlank()) continue
            val file = File(baseDir, name)
            if (file.exists() && file.length() > 0) continue

            val request = Request.Builder().url(url).build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body.byteStream().use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w("Failed to download image $name for item $id: ${e.message}")
            }
        }
    }

    private suspend fun downloadExternalStreams(
        item: FindroidItem,
        sourceId: String,
        storageIndex: Int,
        downloadExternalAudio: Boolean,
    ) {
        val dirs = appContext.getExternalFilesDirs(null)
        val storageLocation = dirs.getOrNull(storageIndex) ?: dirs.getOrNull(0) ?: return
        val sources = repository.getMediaSources(item.id, true)
        val source = sources.firstOrNull { it.id == sourceId } ?: return

        for (stream in source.mediaStreams.filter { it.isExternal }) {
            // Check stream type: always download subtitles; only download audio if requested
            val isSubtitle = stream.type == MediaStreamType.SUBTITLE
            val isAudio = stream.type == MediaStreamType.AUDIO

            if (!isSubtitle && (!isAudio || !downloadExternalAudio)) {
                continue
            }

            val streamUrl = stream.path ?: continue
            val streamId = UUID.randomUUID()
            val extension =
                if (isSubtitle) stream.codec.ifEmpty { "srt" } else stream.codec.ifEmpty { "m4a" }
            val streamFile =
                File(storageLocation, "downloads/${item.id}.${source.id}.${streamId}.$extension")

            if (streamFile.exists() && streamFile.length() > 0) continue

            val request = Request.Builder().url(streamUrl).build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body.byteStream().use { input ->
                            streamFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (streamFile.exists() && streamFile.length() > 0) {
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
            } catch (e: Exception) {
                Timber.e(e, "Failed to download external stream ${stream.title}")
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
            val byteArrays = mutableListOf<ByteArray>()
            for (i in 0..maxIndex) {
                repository.getTrickplayData(item.id, trickplayInfo.width, i)?.let { byteArray ->
                    byteArrays.add(byteArray)
                }
            }
            val basePath = "trickplay/${item.id}/$sourceId"
            database.insertTrickplayInfo(trickplayInfo.toFindroidTrickplayInfoDto(sourceId))
            File(appContext.filesDir, basePath).mkdirs()
            for ((i, byteArray) in byteArrays.withIndex()) {
                File(appContext.filesDir, "$basePath/$i").writeBytes(byteArray)
            }
            Timber.i(
                "Trickplay data downloaded successfully for item ${item.id} (${byteArrays.size} tiles)"
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to download trickplay data for item ${item.id}")
        }
    }
}
