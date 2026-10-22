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

/**
 * Repository interface defining operations for accessing Jellyfin media items, user views, library
 * contents, user metadata, and playback status.
 */
interface JellyfinRepository {
    /** Retrieves additional media parts for a given item ID. */
    suspend fun getAdditionalParts(itemId: UUID): List<FindroidPart>

    /** Fetches public system information from the Jellyfin server. */
    suspend fun getPublicSystemInfo(): PublicSystemInfo

    /** Authorizes a Quick Connect code with the Jellyfin server. */
    suspend fun authorizeQuickConnect(code: String): Boolean

    /** Retrieves top-level user views available to the current user. */
    suspend fun getUserViews(): List<BaseItemDto>

    /** Retrieves episode details for a given item ID. */
    suspend fun getEpisode(itemId: UUID): FindroidEpisode

    /** Retrieves movie details for a given item ID. */
    suspend fun getMovie(itemId: UUID): FindroidMovie

    /** Retrieves show details for a given item ID. */
    suspend fun getShow(itemId: UUID): FindroidShow

    /** Retrieves season details for a given item ID. */
    suspend fun getSeason(itemId: UUID): FindroidSeason

    /** Retrieves media libraries available to the current user. */
    suspend fun getLibraries(): List<FindroidCollection>

    /** Retrieves a generic media item by its ID. */
    suspend fun getItem(itemId: UUID): FindroidItem?

    /** Retrieves a list of items based on filter, pagination, and sorting criteria. */
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

    /** Retrieves paged items matching the given criteria as a [Flow] of [PagingData]. */
    suspend fun getItemsPaging(
        parentId: UUID? = null,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = false,
        sortBy: SortBy = SortBy.defaultValue,
        sortOrder: SortOrder = SortOrder.ASCENDING,
        filters: List<ItemFilter>? = null,
    ): Flow<PagingData<FindroidItem>>

    /** Retrieves details for a person (e.g. actor or director) by ID. */
    suspend fun getPerson(personId: UUID): FindroidPerson

    /** Retrieves media items associated with specific persons. */
    suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>? = null,
        recursive: Boolean = true,
    ): List<FindroidItem>

    /** Retrieves items marked as favorite by the user. */
    suspend fun getFavoriteItems(): List<FindroidItem>

    /** Searches for media items matching a search query. */
    suspend fun getSearchItems(query: String): List<FindroidItem>

    /** Retrieves search suggestions based on recent input or query history. */
    suspend fun getSearchSuggestions(): List<String>

    /** Retrieves available content types for filtering. */
    suspend fun getAvailableContentTypes(): List<BaseItemKind>

    /** Retrieves available video quality options for filtering. */
    suspend fun getAvailableVideoQualities(): List<String>

    /** Retrieves available item filters (e.g., played, unplayed, favorite). */
    suspend fun getAvailableItemFilters(): List<ItemFilter>

    /** Retrieves available series statuses (e.g., continuing, ended). */
    suspend fun getAvailableSeriesStatuses(): List<SeriesStatus>

    /** Retrieves available feature flags for filtering. */
    suspend fun getAvailableFeatures(): List<String>

    /** Retrieves available genres for filtering. */
    suspend fun getGenres(includeItemTypes: List<BaseItemKind>? = null): List<String>

    /** Retrieves available tags for filtering. */
    suspend fun getTags(includeItemTypes: List<BaseItemKind>? = null): List<String>

    /** Retrieves available studios for filtering. */
    suspend fun getStudios(includeItemTypes: List<BaseItemKind>? = null): List<String>

    /** Retrieves available official parental ratings for filtering. */
    suspend fun getOfficialRatings(includeItemTypes: List<BaseItemKind>? = null): List<String>

    /** Retrieves available release years for filtering. */
    suspend fun getYears(includeItemTypes: List<BaseItemKind>? = null): List<Int>

    /** Retrieves available audio languages for filtering. */
    suspend fun getAudioLanguages(includeItemTypes: List<BaseItemKind>? = null): List<String>

    /** Retrieves available subtitle languages for filtering. */
    suspend fun getSubtitleLanguages(includeItemTypes: List<BaseItemKind>? = null): List<String>

    /** Performs an advanced search filtered by multiple criteria. */
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

    /**
     * Performs an advanced search filtered by multiple criteria with pagination as a [Flow] of
     * [PagingData].
     */
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

    /** Retrieves a random media item. */
    suspend fun getRandomItem(): FindroidItem?

    /** Retrieves suggested media items for the current user. */
    suspend fun getSuggestions(forceRefresh: Boolean = false): List<FindroidItem>

    /** Retrieves items that are in progress and can be resumed. */
    suspend fun getResumeItems(): List<FindroidItem>

    /** Retrieves the latest media items added to a specific library parent ID. */
    suspend fun getLatestMedia(parentId: UUID): List<FindroidItem>

    /** Retrieves seasons belonging to a specific series ID. */
    suspend fun getSeasons(seriesId: UUID, offline: Boolean = false): List<FindroidSeason>

    /** Retrieves the next-up episode to watch for a specific series or overall. */
    suspend fun getNextUp(seriesId: UUID? = null): List<FindroidEpisode>

    /** Retrieves episodes for a specific series and season. */
    suspend fun getEpisodes(
        seriesId: UUID,
        seasonId: UUID,
        fields: List<ItemFields>? = null,
        startItemId: UUID? = null,
        limit: Int? = null,
        offline: Boolean = false,
    ): List<FindroidEpisode>

    /** Retrieves media sources for a specific item. */
    suspend fun getMediaSources(itemId: UUID, includePath: Boolean = false): List<FindroidSource>

    /** Retrieves the stream URL for a specific media source. */
    suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String

    /** Retrieves intro/outro segments for a specific item. */
    suspend fun getSegments(itemId: UUID): List<FindroidSegment>

    /** Retrieves trickplay seek preview thumbnail data for scrubbing. */
    suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray?

    /** Posts client capabilities to the Jellyfin server. */
    suspend fun postCapabilities()

    /** Reports playback start for a media item to the Jellyfin server. */
    suspend fun postPlaybackStart(
        itemId: UUID,
        positionTicks: Long? = null,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
        mediaSourceId: String? = null,
        playSessionId: String? = null,
    )

    /** Reports playback stop for a media item to the Jellyfin server. */
    suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
        mediaSourceId: String? = null,
        playSessionId: String? = null,
    )

    /** Reports periodic playback progress for a media item to the Jellyfin server. */
    suspend fun postPlaybackProgress(
        itemId: UUID,
        positionTicks: Long,
        isPaused: Boolean,
        playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
        mediaSourceId: String? = null,
        playSessionId: String? = null,
    )

    /** Marks a media item as a favorite. */
    suspend fun markAsFavorite(itemId: UUID)

    /** Removes an item from favorites. */
    suspend fun unmarkAsFavorite(itemId: UUID)

    /** Marks a media item as played. */
    suspend fun markAsPlayed(itemId: UUID)

    /** Marks a media item as unplayed. */
    suspend fun markAsUnplayed(itemId: UUID)

    /** Returns the base URL of the active server connection. */
    fun getBaseUrl(): String

    /** Updates the registered client device name on the server. */
    suspend fun updateDeviceName(name: String)

    /** Retrieves the user configuration settings for the active user. */
    suspend fun getUserConfiguration(): UserConfiguration?

    /** Retrieves downloaded media items available offline. */
    suspend fun getDownloads(): List<FindroidItem>

    /** Returns the UUID of the currently authenticated user. */
    fun getUserId(): UUID

    /** Retrieves details for the currently active server. */
    suspend fun getCurrentServer(): Server?

    /** Refreshes user details for the given user ID. */
    suspend fun refreshUser(userId: UUID): String?

    /** Sets the current active user ID. */
    suspend fun setCurrentUser(userId: UUID)

    /** Returns whether the server and client support transcoding. */
    suspend fun canTranscode(): Boolean
}
