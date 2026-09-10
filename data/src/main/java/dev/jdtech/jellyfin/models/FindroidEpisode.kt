package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.PlayAccess

data class FindroidEpisode(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    val indexNumber: Int,
    val indexNumberEnd: Int?,
    val parentIndexNumber: Int,
    override val sources: List<FindroidSource>,
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val runtimeTicks: Long,
    override val playbackPositionTicks: Long,
    val premiereDate: DateTime?,
    val seriesId: UUID,
    val seriesName: String,
    val seasonId: UUID,
    val seasonName: String?,
    val communityRating: Float?,
    val people: List<FindroidItemPerson>,
    override val unplayedItemCount: Int? = null,
    val missing: Boolean = false,
    override val images: FindroidImages,
    override val chapters: List<FindroidChapter>,
    override val trickplayInfo: Map<String, FindroidTrickplayInfo>?,
    override val additionalParts: List<FindroidPart> = emptyList(),
) : FindroidItem, FindroidSources

suspend fun BaseItemDto.toFindroidEpisode(
    jellyfinRepository: JellyfinRepository,
    database: ServerDatabaseDao? = null,
): FindroidEpisode? {
    val sources = mutableListOf<FindroidSource>()
    sources.addAll(mediaSources?.map { it.toFindroidSource(jellyfinRepository, id) } ?: emptyList())
    if (database != null) {
        val currentUserId = try { jellyfinRepository.getUserId() } catch (_: Exception) { null }
        if (currentUserId != null && database.isItemDownloadedForUser(currentUserId, id)) {
            sources.addAll(database.getSources(id).map { it.toFindroidSource(database) })
        }
    }
    return try {
        FindroidEpisode(
            id = id,
            name = name.orEmpty(),
            originalTitle = originalTitle,
            overview = overview.orEmpty(),
            indexNumber = indexNumber ?: 0,
            indexNumberEnd = indexNumberEnd,
            parentIndexNumber = parentIndexNumber ?: 0,
            sources = sources,
            played = userData?.played == true,
            favorite = userData?.isFavorite == true,
            canPlay = playAccess != PlayAccess.NONE,
            canDownload = canDownload == true,
            runtimeTicks = runTimeTicks ?: 0,
            playbackPositionTicks = userData?.playbackPositionTicks ?: 0L,
            premiereDate = premiereDate,
            seriesId = seriesId!!,
            seriesName = seriesName.orEmpty(),
            seasonId = seasonId!!,
            seasonName = seasonName,
            communityRating = communityRating,
            people = people?.map { it.toFindroidPerson(jellyfinRepository) } ?: emptyList(),
            missing = locationType == LocationType.VIRTUAL,
            images = toFindroidImages(jellyfinRepository),
            chapters = toFindroidChapters(),
            trickplayInfo =
                trickplay?.mapValues { it.value[it.value.keys.max()]!!.toFindroidTrickplayInfo() },
            additionalParts = if ((partCount ?: 0) > 1) {
                val episodeImages = toFindroidImages(jellyfinRepository)
                jellyfinRepository.getAdditionalParts(id).map { part ->
                    part.copy(
                        parentName = name.orEmpty(),
                        parentIndexNumber = parentIndexNumber ?: 0,
                        indexNumber = indexNumber ?: 0,
                        indexNumberEnd = indexNumberEnd,
                        images = if (part.images.primary == null) {
                            part.images.copy(
                                primary = episodeImages.primary,
                                backdrop = part.images.backdrop ?: episodeImages.backdrop,
                                logo = part.images.logo ?: episodeImages.logo,
                                showPrimary = part.images.showPrimary ?: episodeImages.showPrimary,
                                showBackdrop = part.images.showBackdrop ?: episodeImages.showBackdrop,
                                showLogo = part.images.showLogo ?: episodeImages.showLogo
                            )
                        } else part.images
                    )
                }
            } else {
                emptyList()
            },
        )
    } catch (_: NullPointerException) {
        null
    }
}

fun FindroidEpisodeDto.toFindroidEpisode(
    database: ServerDatabaseDao,
    userId: UUID,
): FindroidEpisode {
    val userData = database.getUserDataOrCreateNew(id, userId)
    val isDownloaded = database.isItemDownloadedForUser(userId, id)
    val sources = if (isDownloaded) database.getSources(id).map { it.toFindroidSource(database) } else emptyList()
    val trickplayInfos = mutableMapOf<String, FindroidTrickplayInfo>()
    for (source in sources) {
        database.getTrickplayInfo(source.id)?.toFindroidTrickplayInfo()?.let {
            trickplayInfos[source.id] = it
        }
    }
    return FindroidEpisode(
        id = id,
        name = name,
        originalTitle = "",
        overview = overview,
        indexNumber = indexNumber,
        indexNumberEnd = indexNumberEnd,
        parentIndexNumber = parentIndexNumber,
        sources = sources,
        played = userData.played,
        favorite = userData.favorite,
        canPlay = isDownloaded,
        canDownload = false,
        runtimeTicks = runtimeTicks,
        playbackPositionTicks = userData.playbackPositionTicks,
        premiereDate = premiereDate,
        seriesId = seriesId,
        seriesName = seriesName,
        seasonId = seasonId,
        seasonName = null,
        communityRating = communityRating,
        people = emptyList(),
        images = toLocalFindroidImages(itemId = id),
        chapters = chapters ?: emptyList(),
        trickplayInfo = trickplayInfos,
        additionalParts = additionalPartIds?.takeIf { it.isNotEmpty() }?.let { database.getParts(it) }?.map {
            val part = it.toFindroidPart(database, userId).copy(
                parentName = name,
                parentIndexNumber = parentIndexNumber,
                indexNumber = indexNumber,
                indexNumberEnd = indexNumberEnd
            )
            val episodeImages = toLocalFindroidImages(itemId = id)
            part.copy(
                images = if (part.images.primary == null) {
                    part.images.copy(
                        primary = episodeImages.primary,
                        backdrop = part.images.backdrop ?: episodeImages.backdrop,
                        logo = part.images.logo ?: episodeImages.logo,
                        showPrimary = part.images.showPrimary ?: episodeImages.showPrimary,
                        showBackdrop = part.images.showBackdrop ?: episodeImages.showBackdrop,
                        showLogo = part.images.showLogo ?: episodeImages.showLogo
                    )
                } else part.images
            )
        } ?: emptyList(),
    )
}
