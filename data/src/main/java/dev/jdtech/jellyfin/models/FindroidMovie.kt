package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayAccess

data class FindroidMovie(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    override val sources: List<FindroidSource>,
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val runtimeTicks: Long,
    override val playbackPositionTicks: Long,
    val premiereDate: LocalDateTime?,
    val people: List<FindroidItemPerson>,
    val genres: List<String>,
    val communityRating: Float?,
    val officialRating: String?,
    val status: String,
    val productionYear: Int?,
    val endDate: LocalDateTime?,
    val trailer: String?,
    override val unplayedItemCount: Int? = null,
    override val images: FindroidImages,
    override val chapters: List<FindroidChapter>,
    override val trickplayInfo: Map<String, FindroidTrickplayInfo>?,
    override val additionalParts: List<FindroidPart> = emptyList(),
) : FindroidItem, FindroidSources

suspend fun BaseItemDto.toFindroidMovie(
    jellyfinRepository: JellyfinRepository,
    serverDatabase: ServerDatabaseDao? = null,
): FindroidMovie {
    val sources = mutableListOf<FindroidSource>()
    sources.addAll(mediaSources?.map { it.toFindroidSource(jellyfinRepository, id) } ?: emptyList())
    if (serverDatabase != null) {
        val currentUserId = try { jellyfinRepository.getUserId() } catch (_: Exception) { null }
        if (currentUserId != null && serverDatabase.isItemDownloadedForUser(currentUserId, id)) {
            sources.addAll(serverDatabase.getSources(id).map { it.toFindroidSource(serverDatabase) })
        }
    }
    return FindroidMovie(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        sources = sources,
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        runtimeTicks = runTimeTicks ?: 0,
        playbackPositionTicks = userData?.playbackPositionTicks ?: 0,
        premiereDate = premiereDate,
        communityRating = communityRating,
        genres = genres ?: emptyList(),
        people = people?.map { it.toFindroidPerson(jellyfinRepository) } ?: emptyList(),
        officialRating = officialRating,
        status = status ?: "Ended",
        productionYear = productionYear,
        endDate = endDate,
        trailer = remoteTrailers?.getOrNull(0)?.url,
        images = toFindroidImages(jellyfinRepository),
        chapters = toFindroidChapters(),
        trickplayInfo =
            trickplay?.mapValues { it.value[it.value.keys.max()]!!.toFindroidTrickplayInfo() },
        additionalParts = if ((partCount ?: 0) > 1) {
            val movieImages = toFindroidImages(jellyfinRepository)
            jellyfinRepository.getAdditionalParts(id).map { part ->
                part.copy(
                    parentName = name.orEmpty(),
                    images = if (part.images.primary == null) {
                        part.images.copy(
                            primary = movieImages.primary,
                            backdrop = part.images.backdrop ?: movieImages.backdrop,
                            logo = part.images.logo ?: movieImages.logo
                        )
                    } else part.images
                )
            }
        } else {
            emptyList()
        },
    )
}

fun FindroidMovieDto.toFindroidMovie(database: ServerDatabaseDao, userId: UUID): FindroidMovie {
    val userData = database.getUserDataOrCreateNew(id, userId)
    val isDownloaded = database.isItemDownloadedForUser(userId, id)
    val sources = if (isDownloaded) database.getSources(id).map { it.toFindroidSource(database) } else emptyList()
    val trickplayInfos = mutableMapOf<String, FindroidTrickplayInfo>()
    for (source in sources) {
        database.getTrickplayInfo(source.id)?.toFindroidTrickplayInfo()?.let {
            trickplayInfos[source.id] = it
        }
    }
    return FindroidMovie(
        id = id,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        played = userData.played,
        favorite = userData.favorite,
        runtimeTicks = runtimeTicks,
        playbackPositionTicks = userData.playbackPositionTicks,
        premiereDate = premiereDate,
        genres = emptyList(),
        people = emptyList(),
        communityRating = communityRating,
        officialRating = officialRating,
        status = status,
        productionYear = productionYear,
        endDate = endDate,
        canDownload = false,
        canPlay = isDownloaded,
        sources = sources,
        trailer = null,
        images = toLocalFindroidImages(itemId = id),
        chapters = chapters ?: emptyList(),
        trickplayInfo = trickplayInfos,
        additionalParts = additionalPartIds?.takeIf { it.isNotEmpty() }?.let { database.getParts(it) }?.map {
            val part = it.toFindroidPart(database, userId).copy(parentName = name)
            val movieImages = toLocalFindroidImages(itemId = id)
            part.copy(
                images = if (part.images.primary == null) {
                    part.images.copy(
                        primary = movieImages.primary,
                        backdrop = part.images.backdrop ?: movieImages.backdrop,
                        logo = part.images.logo ?: movieImages.logo
                    )
                } else part.images
            )
        } ?: emptyList(),
    )
}
