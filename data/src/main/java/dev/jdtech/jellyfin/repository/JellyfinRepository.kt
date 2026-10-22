package dev.jdtech.jellyfin.repository

import androidx.paging.PagingData
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
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.SeriesStatus
import org.jellyfin.sdk.model.api.UserConfiguration
import org.jellyfin.sdk.model.api.VideoType

interface JellyfinRepository {
    suspend fun getAdditionalParts(itemId: UUID): List<FindroidPart>

    suspend fun getPublicSystemInfo(): PublicSystemInfo

    suspend fun authorizeQuickConnect(code: String): Boolean

    suspend fun getUserViews(): List<BaseItemDto>

    suspend fun getEpisode(itemId: UUID): FindroidEpisode

    suspend fun getMovie(itemId: UUID): FindroidMovie

    suspend fun getShow(itemId: UUID): FindroidShow

    suspend fun getSeason(itemId: UUID): FindroidSeason

    suspend fun getLibraries(): List<FindroidCollection>

    suspend fun getItem(itemId: UUID): FindroidItem?

    suspend fun getItems(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        filters: List<ItemFilter>? = null,
        startIndex: Int? = null,
        limit: Int? = null,
    ): List<FindroidItem>

    suspend fun getItemsPaging(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        filters: List<ItemFilter>? = null,
    ): Flow<PagingData<FindroidItem>>

    suspend fun getPerson(personId: UUID): FindroidPerson

    suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = true,
    ): List<FindroidItem>

    suspend fun getFavoriteItems(): List<FindroidItem>

    suspend fun getSearchItems(query: String): List<FindroidItem>

    suspend fun getSearchSuggestions(): List<String>

    suspend fun getAvailableContentTypes(): List<BaseItemKind>

    suspend fun getAvailableVideoQualities(): List<String>

    suspend fun getAvailableItemFilters(): List<ItemFilter>

    suspend fun getAvailableSeriesStatuses(): List<SeriesStatus>

    suspend fun getAvailableFeatures(): List<String>

    suspend fun getGenres(includeItemTypes: List<BaseItemKind>? = null): List<String>

    suspend fun getTags(includeItemTypes: List<BaseItemKind>? = null): List<String>

    suspend fun getStudios(includeItemTypes: List<BaseItemKind>? = null): List<String>

    suspend fun getOfficialRatings(includeItemTypes: List<BaseItemKind>? = null): List<String>

    suspend fun getYears(includeItemTypes: List<BaseItemKind>? = null): List<Int>

    suspend fun getAudioLanguages(includeItemTypes: List<BaseItemKind>? = null): List<String>

    suspend fun getSubtitleLanguages(includeItemTypes: List<BaseItemKind>? = null): List<String>

    suspend fun getSearchItemsFiltered(
        query: String,
        includeItemTypes: List<BaseItemKind>? = null,
        seriesStatuses: List<SeriesStatus>? = null,
        genres: List<String>? = null,
        tags: List<String>? = null,
        officialRatings: List<String>? = null,
        years: List<Int>? = null,
        studios: List<String>? = null,
        audioLanguages: List<String>? = null,
        subtitleLanguages: List<String>? = null,
        filters: List<ItemFilter>? = null,
        is3D: Boolean? = null,
        hasSubtitles: Boolean? = null,
        is4K: Boolean? = null,
        isHD: Boolean? = null,
        videoTypes: List<VideoType>? = null,
        hasTrailer: Boolean? = null,
        hasSpecialFeatures: Boolean? = null,
        hasThemeSong: Boolean? = null,
        hasThemeVideo: Boolean? = null,
        minCommunityRating: Double? = null,
        startIndex: Int? = null,
        limit: Int? = null,
    ): List<FindroidItem>

    fun getSearchItemsFilteredPaging(
        query: String,
        includeItemTypes: List<BaseItemKind>? = null,
        seriesStatuses: List<SeriesStatus>? = null,
        genres: List<String>? = null,
        tags: List<String>? = null,
        officialRatings: List<String>? = null,
        years: List<Int>? = null,
        studios: List<String>? = null,
        audioLanguages: List<String>? = null,
        subtitleLanguages: List<String>? = null,
        filters: List<ItemFilter>? = null,
        is3D: Boolean? = null,
        hasSubtitles: Boolean? = null,
        is4K: Boolean? = null,
        isHD: Boolean? = null,
        videoTypes: List<VideoType>? = null,
        hasTrailer: Boolean? = null,
        hasSpecialFeatures: Boolean? = null,
        hasThemeSong: Boolean? = null,
        hasThemeVideo: Boolean? = null,
        minCommunityRating: Double? = null,
    ): Flow<PagingData<FindroidItem>>

    suspend fun getRandomItem(): FindroidItem?

    suspend fun getSuggestions(forceRefresh: Boolean = false): List<FindroidItem>

    suspend fun getResumeItems(): List<FindroidItem>

    suspend fun getLatestMedia(parentId: UUID): List<FindroidItem>

    suspend fun getSeasons(seriesId: UUID, offline: Boolean = false): List<FindroidSeason>

    suspend fun getNextUp(seriesId: UUID? = null): List<FindroidEpisode>

    suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>? = null,
        startItemId: UUID? = null,
        limit: Int? = null,
        offline: Boolean = false,
    ): List<FindroidEpisode>

    suspend fun getMediaSources(itemId: UUID, includePath: Boolean = false): List<FindroidSource>

    suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String

    suspend fun getSegments(itemId: UUID): List<FindroidSegment>

    suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray?

    suspend fun postCapabilities()

    suspend fun postPlaybackStart(
        itemId: UUID,
        positionTicks: Long? = null,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
        mediaSourceId: String? = null,
        playSessionId: String? = null,
    )

    suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
        mediaSourceId: String? = null,
        playSessionId: String? = null,
    )

    suspend fun postPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
        mediaSourceId: String? = null,
        playSessionId: String? = null,
    )

    suspend fun markAsFavorite(itemId: UUID)

    suspend fun unmarkAsFavorite(itemId: UUID)

    suspend fun markAsPlayed(itemId: UUID)

    suspend fun markAsUnplayed(itemId: UUID)

    fun getBaseUrl(): String

    suspend fun updateDeviceName(name: String)

    suspend fun getUserConfiguration(): UserConfiguration?

    suspend fun getDownloads(): List<FindroidItem>

    fun getUserId(): UUID

    suspend fun getCurrentServer(): Server?

    suspend fun refreshUser(userId: UUID): String?

    suspend fun setCurrentUser(userId: UUID)

    suspend fun canTranscode(): Boolean
}
