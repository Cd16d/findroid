package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo

data class FindroidSource(
    val id: String,
    val name: String,
    val type: FindroidSourceType,
    val path: String,
    val size: Long,
    val mediaStreams: List<FindroidMediaStream>,
    val downloadId: Long? = null,
    val supportsDirectPlay: Boolean = true,
    val supportsDirectStream: Boolean = true,
    val supportsTranscoding: Boolean = true,
    val transcodingUrl: String? = null,
    val playSessionId: String? = null,
    val container: String? = null,
    val bitrate: Int? = null,
)

suspend fun MediaSourceInfo.toFindroidSource(
    jellyfinRepository: JellyfinRepository,
    itemId: UUID,
    includePath: Boolean = false,
    playSessionId: String? = null,
    audioStreamIndex: Int? = null,
): FindroidSource {
    val effectiveTranscodingUrl =
        if (!transcodingUrl.isNullOrBlank() && audioStreamIndex != null) {
            if (transcodingUrl!!.contains(Regex("(?i)AudioStreamIndex="))) {
                transcodingUrl!!.replace(
                    Regex("(?i)AudioStreamIndex=\\d+"),
                    "AudioStreamIndex=$audioStreamIndex",
                )
            } else {
                val sep = if (transcodingUrl!!.contains("?")) "&" else "?"
                "$transcodingUrl${sep}AudioStreamIndex=$audioStreamIndex"
            }
        } else {
            transcodingUrl
        }

    val path =
        if (includePath) {
            val transUrl = effectiveTranscodingUrl
            if (!transUrl.isNullOrBlank()) {
                val cleanPath = if (transUrl.startsWith("/")) transUrl else "/$transUrl"
                jellyfinRepository.getBaseUrl().removeSuffix("/") + cleanPath
            } else {
                when (protocol) {
                    MediaProtocol.FILE -> {
                        try {
                            jellyfinRepository.getStreamUrl(itemId, id.orEmpty())
                        } catch (_: Exception) {
                            ""
                        }
                    }
                    MediaProtocol.HTTP -> this.path.orEmpty()
                    else -> ""
                }
            }
        } else {
            ""
        }
    return FindroidSource(
        id = id.orEmpty(),
        name = name.orEmpty(),
        type = FindroidSourceType.REMOTE,
        path = path,
        size = size ?: 0,
        mediaStreams =
            mediaStreams?.map { it.toFindroidMediaStream(jellyfinRepository) } ?: emptyList(),
        supportsDirectPlay = supportsDirectPlay,
        supportsDirectStream = supportsDirectStream,
        supportsTranscoding = supportsTranscoding,
        transcodingUrl = effectiveTranscodingUrl,
        playSessionId = playSessionId,
        container = container,
        bitrate = bitrate,
    )
}

suspend fun FindroidSourceDto.toFindroidSource(
    serverDatabaseDao: ServerDatabaseDao
): FindroidSource {
    return FindroidSource(
        id = id,
        name = name,
        type = type,
        path = path,
        size = File(path).length(),
        mediaStreams =
            serverDatabaseDao.getMediaStreamsBySourceId(id).map { it.toFindroidMediaStream() },
        downloadId = downloadId,
    )
}

enum class FindroidSourceType {
    REMOTE,
    LOCAL,
}
