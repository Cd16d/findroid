package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

data class FindroidPart(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String = "",
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean = true,
    override val canDownload: Boolean = true,
    override val sources: List<FindroidSource>,
    override val runtimeTicks: Long,
    override val playbackPositionTicks: Long,
    override val unplayedItemCount: Int? = null,
    override val images: FindroidImages,
    override val chapters: List<FindroidChapter> = emptyList(),
    override val trickplayInfo: Map<String, FindroidTrickplayInfo>? = null,
    val parentName: String = "",
    val parentIndexNumber: Int? = null,
    val indexNumber: Int? = null,
    val indexNumberEnd: Int? = null,
    override val additionalParts: List<FindroidPart> = emptyList(),
) : FindroidItem, FindroidSources

suspend fun BaseItemDto.toFindroidPart(
    jellyfinRepository: JellyfinRepository,
    database: ServerDatabaseDao? = null,
): FindroidPart? {
    val sources = mutableListOf<FindroidSource>()
    sources.addAll(mediaSources?.map { it.toFindroidSource(jellyfinRepository, id) } ?: emptyList())
    if (database != null) {
        val currentUserId =
            try {
                jellyfinRepository.getUserId()
            } catch (_: Exception) {
                null
            }
        if (currentUserId != null && database.isItemDownloadedForUser(currentUserId, id)) {
            sources.addAll(database.getSources(id).map { it.toFindroidSource(database) })
        }
    }
    return try {
        FindroidPart(
            id = id,
            name = name.orEmpty(),
            played = userData?.played == true,
            favorite = userData?.isFavorite == true,
            sources = sources,
            runtimeTicks = runTimeTicks ?: 0,
            playbackPositionTicks = userData?.playbackPositionTicks ?: 0L,
            images = toFindroidImages(jellyfinRepository),
            trickplayInfo =
                trickplay
                    ?.mapNotNull { (key, value) ->
                        val maxKey = value?.keys?.maxOrNull() ?: return@mapNotNull null
                        val info = value[maxKey] ?: return@mapNotNull null
                        key to info.toFindroidTrickplayInfo()
                    }
                    ?.toMap(),
        )
    } catch (_: NullPointerException) {
        null
    }
}

suspend fun FindroidPartDto.toFindroidPart(
    database: ServerDatabaseDao,
    userId: UUID,
): FindroidPart {
    val userData = database.getUserDataOrCreateNew(id, userId)
    val isDownloaded = database.isItemDownloadedForUser(userId, id)
    val sources =
        if (isDownloaded) database.getSources(id).map { it.toFindroidSource(database) }
        else emptyList()
    val trickplayInfos = mutableMapOf<String, FindroidTrickplayInfo>()
    for (source in sources) {
        database.getTrickplayInfo(source.id)?.toFindroidTrickplayInfo()?.let {
            trickplayInfos[source.id] = it
        }
    }
    return FindroidPart(
        id = id,
        name = name,
        played = userData.played,
        favorite = userData.favorite,
        sources = sources,
        runtimeTicks = runtimeTicks,
        playbackPositionTicks = userData.playbackPositionTicks,
        images = toLocalFindroidImages(itemId = id),
        trickplayInfo = trickplayInfos,
        parentName = parentName.orEmpty(),
        parentIndexNumber = parentIndexNumber,
        indexNumber = indexNumber,
        indexNumberEnd = indexNumberEnd,
    )
}
