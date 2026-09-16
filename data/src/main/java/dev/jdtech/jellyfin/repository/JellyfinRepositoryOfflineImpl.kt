package dev.jdtech.jellyfin.repository

import android.content.Context
import androidx.paging.PagingData
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidPart
import dev.jdtech.jellyfin.models.FindroidPerson
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.SortBy
import dev.jdtech.jellyfin.models.SortOrder
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidSeason
import dev.jdtech.jellyfin.models.toFindroidSegment
import dev.jdtech.jellyfin.models.toFindroidShow
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.UserConfiguration

class JellyfinRepositoryOfflineImpl(
    private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : JellyfinRepository {

    override suspend fun getAdditionalParts(itemId: UUID): List<FindroidPart> {
        return emptyList()
    }

    override suspend fun getPublicSystemInfo(): PublicSystemInfo {
        throw Exception("System info not available in offline mode")
    }

    override suspend fun authorizeQuickConnect(code: String): Boolean {
        throw Exception("Quick Connect not available in offline mode")
    }

    override suspend fun getUserViews(): List<BaseItemDto> {
        return emptyList()
    }

    override suspend fun getMovie(itemId: UUID): FindroidMovie =
        withContext(Dispatchers.IO) {
            database.getMovie(itemId).toFindroidMovie(database, jellyfinApi.userId!!)
        }

    override suspend fun getShow(itemId: UUID): FindroidShow =
        withContext(Dispatchers.IO) {
            database.getShow(itemId).toFindroidShow(database, jellyfinApi.userId!!)
        }

    override suspend fun getSeason(itemId: UUID): FindroidSeason =
        withContext(Dispatchers.IO) {
            database.getSeason(itemId).toFindroidSeason(database, jellyfinApi.userId!!)
        }

    override suspend fun getEpisode(itemId: UUID): FindroidEpisode =
        withContext(Dispatchers.IO) {
            database.getEpisode(itemId).toFindroidEpisode(database, jellyfinApi.userId!!)
        }

    override suspend fun getLibraries(): List<FindroidCollection> {
        return emptyList()
    }

    override suspend fun getItem(itemId: UUID): FindroidItem? {
        return null
    }

    override suspend fun getItems(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        filters: List<ItemFilter>?,
        startIndex: Int?,
        limit: Int?,
    ): List<FindroidItem> {
        return emptyList()
    }

    override suspend fun getItemsPaging(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        filters: List<ItemFilter>?,
    ): Flow<PagingData<FindroidItem>> {
        TODO("Not yet implemented")
    }

    override suspend fun getPerson(personId: UUID): FindroidPerson {
        TODO("Not yet implemented")
    }

    override suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
    ): List<FindroidItem> {
        TODO("Not yet implemented")
    }

    override suspend fun getFavoriteItems(): List<FindroidItem> {
        TODO("Not yet implemented")
    }

    override suspend fun getSearchItems(query: String): List<FindroidItem> {
        return withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext emptyList()
            val userId = jellyfinApi.userId ?: return@withContext emptyList()

            val movies =
                database.searchDownloadedMovies(serverId, userId, query).map {
                    it.toFindroidMovie(database, userId)
                }
            val shows =
                database.searchDownloadedShows(serverId, userId, query).map {
                    it.toFindroidShow(database, userId)
                }
            val episodes =
                database.searchDownloadedEpisodes(serverId, userId, query).map {
                    it.toFindroidEpisode(database, userId)
                }
            movies + shows + episodes
        }
    }

    override suspend fun getSuggestions(): List<FindroidItem> {
        return emptyList()
    }

    override suspend fun getResumeItems(): List<FindroidItem> {
        return withContext(Dispatchers.IO) {
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext emptyList()
            val userId = jellyfinApi.userId ?: return@withContext emptyList()

            val movies =
                database
                    .getDownloadedMoviesByServerAndUser(serverId, userId)
                    .map { it.toFindroidMovie(database, userId) }
                    .filter { it.playbackPositionTicks > 0 }
            val episodes =
                database
                    .getDownloadedEpisodesByServerAndUser(serverId, userId)
                    .map { it.toFindroidEpisode(database, userId) }
                    .filter { it.playbackPositionTicks > 0 }
            movies + episodes
        }
    }

    override suspend fun getLatestMedia(parentId: UUID): List<FindroidItem> {
        return emptyList()
    }

    override suspend fun getSeasons(seriesId: UUID, offline: Boolean): List<FindroidSeason> =
        withContext(Dispatchers.IO) {
            val userId = jellyfinApi.userId ?: return@withContext emptyList()
            val downloadedEpisodes = database.getDownloadedEpisodesByShowAndUser(seriesId, userId)
            val seasonIds = downloadedEpisodes.map { it.seasonId }.toSet()
            database
                .getSeasonsByShowId(seriesId)
                .filter { seasonIds.contains(it.id) }
                .map { it.toFindroidSeason(database, userId) }
        }

    override suspend fun getNextUp(seriesId: UUID?): List<FindroidEpisode> {
        return withContext(Dispatchers.IO) {
            val result = mutableListOf<FindroidEpisode>()
            val serverId =
                appPreferences.getValue(appPreferences.currentServer)
                    ?: return@withContext emptyList()
            val userId = jellyfinApi.userId ?: return@withContext emptyList()

            val shows =
                database.getDownloadedShowsByServerAndUser(serverId, userId).filter {
                    if (seriesId != null) it.id == seriesId else true
                }
            for (show in shows) {
                val episodes =
                    database.getDownloadedEpisodesByShowAndUser(show.id, userId).map {
                        it.toFindroidEpisode(database, userId)
                    }
                val indexOfLastPlayed = episodes.indexOfLast { it.played }
                if (indexOfLastPlayed == -1) {
                    episodes.firstOrNull()?.let { result.add(it) }
                } else {
                    episodes.getOrNull(indexOfLastPlayed + 1)?.let { result.add(it) }
                }
            }
            result.filter { it.playbackPositionTicks == 0L }
        }
    }

    override suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>?,
        startItemId: UUID?,
        limit: Int?,
        offline: Boolean,
    ): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            val userId = jellyfinApi.userId ?: return@withContext emptyList()
            val items =
                database.getDownloadedEpisodesBySeasonAndUser(seasonId, userId).map {
                    it.toFindroidEpisode(database, userId)
                }
            if (startItemId != null) return@withContext items.dropWhile { it.id != startItemId }
            items
        }

    override suspend fun getMediaSources(itemId: UUID, includePath: Boolean): List<FindroidSource> =
        withContext(Dispatchers.IO) {
            val userId = jellyfinApi.userId
            if (userId != null && database.isItemDownloadedForUser(userId, itemId)) {
                database.getSources(itemId).map { it.toFindroidSource(database) }
            } else {
                emptyList()
            }
        }

    override suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String {
        TODO("Not yet implemented")
    }

    override suspend fun getSegments(itemId: UUID): List<FindroidSegment> =
        withContext(Dispatchers.IO) { database.getSegments(itemId).map { it.toFindroidSegment() } }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val sources =
                    File(context.filesDir, "trickplay/$itemId").listFiles()
                        ?: return@withContext null
                File(sources.first(), index.toString()).readBytes()
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun postCapabilities() {}

    override suspend fun postPlaybackStart(
        itemId: UUID,
        positionTicks: Long?,
        playMethod: PlayMethod,
        mediaSourceId: String?,
        playSessionId: String?,
    ) {}

    override suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
        mediaSourceId: String?,
        playSessionId: String?,
    ) {
        withContext(ioDispatcher) {
            val userId = jellyfinApi.userId ?: return@withContext
            when {
                playedPercentage < 10 -> {
                    database.setPlaybackPositionTicks(itemId, userId, 0)
                    database.setPlayed(userId, itemId, false)
                }
                playedPercentage > 90 -> {
                    database.setPlaybackPositionTicks(itemId, userId, 0)
                    database.setPlayed(userId, itemId, true)
                }
                else -> {
                    database.setPlaybackPositionTicks(itemId, userId, positionTicks)
                    database.setPlayed(userId, itemId, false)
                }
            }
            database.setUserDataToBeSynced(userId, itemId, true)
        }
    }

    override suspend fun postPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
        playMethod: PlayMethod,
        mediaSourceId: String?,
        playSessionId: String?,
    ) {
        withContext(ioDispatcher) {
            val userId = jellyfinApi.userId ?: return@withContext
            database.setPlaybackPositionTicks(itemId, userId, positionTicks)
            database.setUserDataToBeSynced(userId, itemId, true)
        }
    }

    override suspend fun markAsFavorite(itemId: UUID) {
        withContext(ioDispatcher) {
            val userId = jellyfinApi.userId ?: return@withContext
            database.setFavorite(userId, itemId, true)
            database.setUserDataToBeSynced(userId, itemId, true)
        }
    }

    override suspend fun unmarkAsFavorite(itemId: UUID) {
        withContext(ioDispatcher) {
            val userId = jellyfinApi.userId ?: return@withContext
            database.setFavorite(userId, itemId, false)
            database.setUserDataToBeSynced(userId, itemId, true)
        }
    }

    override suspend fun markAsPlayed(itemId: UUID) {
        withContext(ioDispatcher) {
            val userId = jellyfinApi.userId ?: return@withContext
            database.setPlayed(userId, itemId, true)
            database.setPlaybackPositionTicks(itemId, userId, 0)
            database.setUserDataToBeSynced(userId, itemId, true)
        }
    }

    override suspend fun markAsUnplayed(itemId: UUID) {
        withContext(ioDispatcher) {
            val userId = jellyfinApi.userId ?: return@withContext
            database.setPlayed(userId, itemId, false)
            database.setUserDataToBeSynced(userId, itemId, true)
        }
    }

    override fun getBaseUrl(): String {
        return jellyfinApi.api.baseUrl.orEmpty()
    }

    override suspend fun updateDeviceName(name: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getUserConfiguration(): UserConfiguration? {
        return null
    }

    override suspend fun getDownloads(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            val items = mutableListOf<FindroidItem>()
            val serverId = appPreferences.getValue(appPreferences.currentServer)
            val userId = jellyfinApi.userId
            if (serverId != null && userId != null) {
                items.addAll(
                    database.getDownloadedMoviesByServerAndUser(serverId, userId).map {
                        it.toFindroidMovie(database, userId)
                    }
                )
                items.addAll(
                    database.getDownloadedShowsByServerAndUser(serverId, userId).map {
                        it.toFindroidShow(database, userId)
                    }
                )
            }
            items
        }

    override fun getUserId(): UUID {
        return jellyfinApi.userId!!
    }

    override suspend fun getCurrentServer(): Server? {
        return appPreferences.getValue(appPreferences.currentServer)?.let { id ->
            database.getServer(id)
        }
    }

    override suspend fun refreshUser(userId: UUID): String? {
        return null
    }

    override suspend fun setCurrentUser(userId: UUID) {
        val server = getCurrentServer() ?: return
        val user = database.getUser(userId) ?: return
        server.currentUserId = user.id
        database.updateServer(server)

        jellyfinApi.apply { this.userId = user.id }
    }

    override suspend fun canTranscode(): Boolean = false
}
