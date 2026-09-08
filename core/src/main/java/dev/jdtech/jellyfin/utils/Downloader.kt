package dev.jdtech.jellyfin.utils

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.UiText
import java.util.UUID

interface Downloader {
    suspend fun downloadItem(
        item: FindroidItem,
        sourceId: String? = null,
        storageIndex: Int = -1,
        presetId: String? = null,
        downloadExternalAudio: Boolean = false,
        audioStreamIndex: Int? = null,
    ): Pair<Long, UiText?>

    suspend fun cancelDownload(item: FindroidItem, downloadId: Long)

    suspend fun pauseDownload(downloadId: Long)

    suspend fun resumeDownload(downloadId: Long)

    suspend fun deleteItem(item: FindroidItem, source: FindroidSource, userId: UUID? = null)

    data class Progress(
        val status: Int,
        val progress: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    )

    suspend fun getProgress(downloadId: Long?): Progress

    suspend fun getProgress(downloadIds: List<Long>): Map<Long, Progress>

    suspend fun getActiveDownloads(): List<Pair<FindroidItem, Long>>

    suspend fun finalizeDownload(downloadId: Long): Boolean

    suspend fun moveItemStorage(
        item: FindroidItem,
        targetStorageIndex: Int,
        onProgress: ((bytesTransferred: Long, totalBytes: Long) -> Unit)? = null,
    ): Result<Unit>

    fun downloadUserImage(userId: UUID, imageTag: String?)
}
