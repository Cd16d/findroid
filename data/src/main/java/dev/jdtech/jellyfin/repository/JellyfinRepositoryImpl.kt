package dev.jdtech.jellyfin.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
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
import dev.jdtech.jellyfin.models.toFindroidCollection
import dev.jdtech.jellyfin.models.toFindroidEpisode
import dev.jdtech.jellyfin.models.toFindroidItem
import dev.jdtech.jellyfin.models.toFindroidMovie
import dev.jdtech.jellyfin.models.toFindroidPart
import dev.jdtech.jellyfin.models.toFindroidPerson
import dev.jdtech.jellyfin.models.toFindroidSeason
import dev.jdtech.jellyfin.models.toFindroidSegment
import dev.jdtech.jellyfin.models.toFindroidShow
import dev.jdtech.jellyfin.models.toFindroidSource
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceOptionsDto
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SeriesStatus
import org.jellyfin.sdk.model.api.SortOrder as ItemSortOrder
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.UserConfiguration
import org.jellyfin.sdk.model.api.VideoType
import timber.log.Timber

class JellyfinRepositoryImpl(
    private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
) : JellyfinRepository {
    private var cachedSuggestions: List<FindroidItem>? = null
    private var cachedSuggestionsUserId: UUID? = null

    override suspend fun getAdditionalParts(itemId: UUID): List<FindroidPart> =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.videoApi
                    .getAdditionalPart(itemId, jellyfinApi.userId!!)
                    .content
                    .items
                    .mapNotNull { it.toFindroidPart(this@JellyfinRepositoryImpl, database) }
            } catch (e: Exception) {
                Timber.e(e)
                emptyList()
            }
        }

    override suspend fun getPublicSystemInfo(): PublicSystemInfo =
        withContext(Dispatchers.IO) { jellyfinApi.systemApi.getPublicSystemInfo().content }

    override suspend fun authorizeQuickConnect(code: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.authenticationApi
                    .authorizeQuickConnect(code, userId = jellyfinApi.userId)
                    .content
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun getUserViews(): List<BaseItemDto> =
        withContext(Dispatchers.IO) {
            jellyfinApi.userViewApi.getUserViews(jellyfinApi.userId!!).content.items
        }

    override suspend fun getEpisode(itemId: UUID): FindroidEpisode =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidEpisode(this@JellyfinRepositoryImpl, database)!!
        }

    override suspend fun getMovie(itemId: UUID): FindroidMovie =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidMovie(this@JellyfinRepositoryImpl, database)
        }

    override suspend fun getShow(itemId: UUID): FindroidShow =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidShow(this@JellyfinRepositoryImpl)
        }

    override suspend fun getSeason(itemId: UUID): FindroidSeason =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId, jellyfinApi.userId!!)
                .content
                .toFindroidSeason(this@JellyfinRepositoryImpl)
        }

    override suspend fun getLibraries(): List<FindroidCollection> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(jellyfinApi.userId!!)
                .content
                .items
                .mapNotNull { it.toFindroidCollection(this@JellyfinRepositoryImpl) }
                .distinctBy { it.id }
        }

    override suspend fun getItem(itemId: UUID): FindroidItem? =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(itemId = itemId, userId = jellyfinApi.userId!!)
                .content
                .toFindroidItem(this@JellyfinRepositoryImpl)
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
    ): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    parentId = parentId,
                    includeItemTypes = includeTypes,
                    recursive = recursive,
                    sortBy = listOf(ItemSortBy.fromName(sortBy.sortString)),
                    sortOrder = listOf(ItemSortOrder.fromName(sortOrder.sortString)),
                    filters = filters,
                    startIndex = startIndex,
                    limit = limit,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
                .distinctBy { it.id }
        }

    override suspend fun getItemsPaging(
        parentId: UUID?,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
        sortBy: SortBy,
        sortOrder: SortOrder,
        filters: List<ItemFilter>?,
    ): Flow<PagingData<FindroidItem>> {
        return Pager(
                config = PagingConfig(pageSize = 10, enablePlaceholders = false),
                pagingSourceFactory = {
                    ItemsPagingSource(
                        this,
                        parentId,
                        includeTypes,
                        recursive,
                        sortBy,
                        sortOrder,
                        filters,
                    )
                },
            )
            .flow
    }

    override suspend fun getPerson(personId: UUID): FindroidPerson =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItem(personId, jellyfinApi.userId!!)
                .content
                .toFindroidPerson(this@JellyfinRepositoryImpl)
        }

    override suspend fun getPersonItems(
        personIds: List<UUID>,
        includeTypes: List<BaseItemKind>?,
        recursive: Boolean,
    ): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    personIds = personIds,
                    includeItemTypes = includeTypes,
                    recursive = recursive,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
                .distinctBy { it.id }
        }

    override suspend fun getFavoriteItems(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    filters = listOf(ItemFilter.IS_FAVORITE),
                    includeItemTypes =
                        listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
                    recursive = true,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
                .distinctBy { it.id }
        }

    override suspend fun getSearchItems(query: String): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getItems(
                    jellyfinApi.userId!!,
                    searchTerm = query,
                    includeItemTypes =
                        listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE),
                    isMissing = false,
                    recursive = true,
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
                .filter { (it as? FindroidEpisode)?.missing != true }
                .distinctBy { it.id }
        }

    override suspend fun getSearchSuggestions(): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val userId = jellyfinApi.userId ?: return@withContext emptyList()
                val hintsResponse = runCatching {
                    jellyfinApi.searchApi.getSearchHints(
                        userId = userId,
                        searchTerm = "",
                        limit = 20,
                    )
                }
                    .getOrNull()
                val hintNames =
                    hintsResponse
                        ?.content
                        ?.searchHints
                        ?.map { it.name }
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                        .orEmpty()

                if (hintNames.isNotEmpty()) {
                    return@withContext hintNames
                }

                val libraryItemsResponse = runCatching {
                    jellyfinApi.libraryApi.getItems(
                        userId = userId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        limit = 20,
                        sortBy = listOf(ItemSortBy.RANDOM),
                        recursive = true,
                    )
                }
                    .getOrNull()

                libraryItemsResponse
                    ?.content
                    ?.items
                    ?.mapNotNull { it.name }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    .orEmpty()
            } catch (e: Exception) {
                Timber.e(e)
                emptyList()
            }
        }

    override suspend fun getAvailableContentTypes(): List<BaseItemKind> =
        listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE)

    override suspend fun getAvailableVideoQualities(): List<String> =
        listOf("SD", "HD", "4K", "3D", "DVD", "BLU_RAY", "ISO")

    override suspend fun getAvailableItemFilters(): List<ItemFilter> =
        listOf(
            ItemFilter.IS_UNPLAYED,
            ItemFilter.IS_PLAYED,
            ItemFilter.IS_FAVORITE,
            ItemFilter.IS_RESUMABLE,
        )

    override suspend fun getAvailableSeriesStatuses(): List<SeriesStatus> =
        listOf(
            SeriesStatus.CONTINUING,
            SeriesStatus.ENDED,
            SeriesStatus.UNRELEASED,
        )

    override suspend fun getAvailableFeatures(): List<String> =
        listOf(
            "HAS_SUBTITLES",
            "HAS_TRAILER",
            "HAS_EXTRA",
            "HAS_THEME_SONG",
            "HAS_THEME_VIDEO",
        )

    private fun getEffectiveTypes(includeItemTypes: List<BaseItemKind>?): List<BaseItemKind> =
        includeItemTypes?.ifEmpty { null }
            ?: listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE)

    override suspend fun getGenres(includeItemTypes: List<BaseItemKind>?): List<String> =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.genreApi
                    .getGenres(
                        userId = jellyfinApi.userId,
                        includeItemTypes = getEffectiveTypes(includeItemTypes),
                    )
                    .content
                    .items
                    .mapNotNull { it.name }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun getTags(includeItemTypes: List<BaseItemKind>?): List<String> =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.filterApi
                    .getQueryFilters(
                        userId = jellyfinApi.userId,
                        includeItemTypes = getEffectiveTypes(includeItemTypes),
                    )
                    .content
                    .tags
                    .orEmpty()
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun getStudios(includeItemTypes: List<BaseItemKind>?): List<String> =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.studioApi
                    .getStudios(
                        userId = jellyfinApi.userId,
                        includeItemTypes = getEffectiveTypes(includeItemTypes),
                    )
                    .content
                    .items
                    .mapNotNull { it.name }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun getOfficialRatings(includeItemTypes: List<BaseItemKind>?): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val legacyRatings: List<String> = runCatching {
                    jellyfinApi.filterApi
                        .getQueryFiltersLegacy(userId = jellyfinApi.userId)
                        .content
                        .officialRatings
                        .orEmpty()
                }
                    .getOrDefault(emptyList())

                val legacyNoUserRatings: List<String> = runCatching {
                    jellyfinApi.filterApi.getQueryFiltersLegacy().content.officialRatings.orEmpty()
                }
                    .getOrDefault(emptyList())

                val itemRatings: List<String> = runCatching {
                    jellyfinApi.libraryApi
                        .getItems(
                            userId = jellyfinApi.userId!!,
                            includeItemTypes = getEffectiveTypes(includeItemTypes),
                            recursive = true,
                            limit = 500,
                        )
                        .content
                        .items
                        .mapNotNull { it.officialRating }
                        .filter { it.isNotBlank() }
                }
                    .getOrDefault(emptyList())

                val result =
                    (legacyRatings + legacyNoUserRatings + itemRatings)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .sorted()

                if (result.isNotEmpty()) {
                    result
                } else {
                    listOf(
                        "G",
                        "PG",
                        "PG-13",
                        "R",
                        "NC-17",
                        "TV-Y",
                        "TV-Y7",
                        "TV-G",
                        "TV-PG",
                        "TV-14",
                        "TV-MA",
                    )
                }
            } catch (e: Exception) {
                Timber.e(e)
                listOf(
                    "G",
                    "PG",
                    "PG-13",
                    "R",
                    "NC-17",
                    "TV-Y",
                    "TV-Y7",
                    "TV-G",
                    "TV-PG",
                    "TV-14",
                    "TV-MA",
                )
            }
        }

    override suspend fun getYears(includeItemTypes: List<BaseItemKind>?): List<Int> =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.yearApi
                    .getYears(
                        userId = jellyfinApi.userId,
                        includeItemTypes = getEffectiveTypes(includeItemTypes),
                    )
                    .content
                    .items
                    .mapNotNull { it.name?.toIntOrNull() ?: it.productionYear }
                    .distinct()
                    .sortedDescending()
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun getAudioLanguages(includeItemTypes: List<BaseItemKind>?): List<String> =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.filterApi
                    .getQueryFilters(
                        userId = jellyfinApi.userId,
                        includeItemTypes = getEffectiveTypes(includeItemTypes),
                    )
                    .content
                    .audioLanguages
                    .orEmpty()
                    .mapNotNull { item ->
                        val code = item.value?.takeIf { it.isNotBlank() } ?: item.name
                        code?.takeIf { it.isNotBlank() }
                    }
                    .distinct()
                    .sorted()
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun getSubtitleLanguages(includeItemTypes: List<BaseItemKind>?): List<String> =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.filterApi
                    .getQueryFilters(
                        userId = jellyfinApi.userId,
                        includeItemTypes = getEffectiveTypes(includeItemTypes),
                    )
                    .content
                    .subtitleLanguages
                    .orEmpty()
                    .mapNotNull { item ->
                        val code = item.value?.takeIf { it.isNotBlank() } ?: item.name
                        code?.takeIf { it.isNotBlank() }
                    }
                    .distinct()
                    .sorted()
            } catch (_: Exception) {
                emptyList()
            }
        }

    override suspend fun getSearchItemsFiltered(
        query: String,
        includeItemTypes: List<BaseItemKind>?,
        seriesStatuses: List<SeriesStatus>?,
        genres: List<String>?,
        tags: List<String>?,
        officialRatings: List<String>?,
        years: List<Int>?,
        studios: List<String>?,
        audioLanguages: List<String>?,
        subtitleLanguages: List<String>?,
        filters: List<ItemFilter>?,
        is3D: Boolean?,
        hasSubtitles: Boolean?,
        is4K: Boolean?,
        isHD: Boolean?,
        videoTypes: List<VideoType>?,
        hasTrailer: Boolean?,
        hasSpecialFeatures: Boolean?,
        hasThemeSong: Boolean?,
        hasThemeVideo: Boolean?,
        minCommunityRating: Double?,
        startIndex: Int?,
        limit: Int?,
    ): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            try {
                val processedOfficialRatings =
                    officialRatings
                        ?.flatMap { rating ->
                            if (rating.contains("-")) {
                                listOf(rating, rating.substringAfter("-"))
                            } else {
                                listOf(rating)
                            }
                        }
                        ?.distinct()
                        ?.ifEmpty { null }

                val processedAudioLanguages =
                    audioLanguages
                        ?.flatMap { lang ->
                            when (lang.lowercase()) {
                                "english",
                                "eng",
                                "en" -> listOf("English", "eng", "en")
                                "spanish",
                                "spa",
                                "es" -> listOf("Spanish", "spa", "es")
                                "french",
                                "fre",
                                "fra",
                                "fr" -> listOf("French", "fre", "fra", "fr")
                                "german",
                                "ger",
                                "deu",
                                "de" -> listOf("German", "ger", "deu", "de")
                                "japanese",
                                "jpn",
                                "ja" -> listOf("Japanese", "jpn", "ja")
                                "italian",
                                "ita",
                                "it" -> listOf("Italian", "ita", "it")
                                "chinese",
                                "chi",
                                "zho",
                                "zh" -> listOf("Chinese", "chi", "zho", "zh")
                                "korean",
                                "kor",
                                "ko" -> listOf("Korean", "kor", "ko")
                                "russian",
                                "rus",
                                "ru" -> listOf("Russian", "rus", "ru")
                                "portuguese",
                                "por",
                                "pt" -> listOf("Portuguese", "por", "pt")
                                else -> listOf(lang)
                            }
                        }
                        ?.distinct()
                        ?.ifEmpty { null }

                val processedSubtitleLanguages =
                    subtitleLanguages
                        ?.flatMap { lang ->
                            when (lang.lowercase()) {
                                "english",
                                "eng",
                                "en" -> listOf("English", "eng", "en")
                                "spanish",
                                "spa",
                                "es" -> listOf("Spanish", "spa", "es")
                                "french",
                                "fre",
                                "fra",
                                "fr" -> listOf("French", "fre", "fra", "fr")
                                "german",
                                "ger",
                                "deu",
                                "de" -> listOf("German", "ger", "deu", "de")
                                "japanese",
                                "jpn",
                                "ja" -> listOf("Japanese", "jpn", "ja")
                                "italian",
                                "ita",
                                "it" -> listOf("Italian", "ita", "it")
                                "chinese",
                                "chi",
                                "zho",
                                "zh" -> listOf("Chinese", "chi", "zho", "zh")
                                "korean",
                                "kor",
                                "ko" -> listOf("Korean", "kor", "ko")
                                "russian",
                                "rus",
                                "ru" -> listOf("Russian", "rus", "ru")
                                "portuguese",
                                "por",
                                "pt" -> listOf("Portuguese", "por", "pt")
                                else -> listOf(lang)
                            }
                        }
                        ?.distinct()
                        ?.ifEmpty { null }

                jellyfinApi.libraryApi
                    .getItems(
                        userId = jellyfinApi.userId!!,
                        searchTerm = query.ifBlank { null },
                        includeItemTypes =
                            includeItemTypes
                                ?: listOf(
                                    BaseItemKind.MOVIE,
                                    BaseItemKind.SERIES,
                                    BaseItemKind.EPISODE,
                                ),
                        seriesStatus = seriesStatuses?.ifEmpty { null },
                        genres = genres?.ifEmpty { null },
                        tags = tags?.ifEmpty { null },
                        officialRatings = processedOfficialRatings,
                        years = years?.ifEmpty { null },
                        studios = studios?.ifEmpty { null },
                        audioLanguages = processedAudioLanguages,
                        subtitleLanguages = processedSubtitleLanguages,
                        filters = filters?.ifEmpty { null },
                        is3d = is3D,
                        hasSubtitles = hasSubtitles,
                        is4k = is4K,
                        isHd = isHD,
                        videoTypes = videoTypes?.ifEmpty { null },
                        hasTrailer = hasTrailer,
                        hasSpecialFeature = hasSpecialFeatures,
                        hasThemeSong = hasThemeSong,
                        hasThemeVideo = hasThemeVideo,
                        minCommunityRating = minCommunityRating,
                        isMissing = false,
                        recursive = true,
                        startIndex = startIndex,
                        limit = limit,
                    )
                    .content
                    .items
                    .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
                    .filter { (it as? FindroidEpisode)?.missing != true }
                    .distinctBy { it.id }
            } catch (e: Exception) {
                Timber.e(e)
                emptyList()
            }
        }

    override fun getSearchItemsFilteredPaging(
        query: String,
        includeItemTypes: List<BaseItemKind>?,
        seriesStatuses: List<SeriesStatus>?,
        genres: List<String>?,
        tags: List<String>?,
        officialRatings: List<String>?,
        years: List<Int>?,
        studios: List<String>?,
        audioLanguages: List<String>?,
        subtitleLanguages: List<String>?,
        filters: List<ItemFilter>?,
        is3D: Boolean?,
        hasSubtitles: Boolean?,
        is4K: Boolean?,
        isHD: Boolean?,
        videoTypes: List<VideoType>?,
        hasTrailer: Boolean?,
        hasSpecialFeatures: Boolean?,
        hasThemeSong: Boolean?,
        hasThemeVideo: Boolean?,
        minCommunityRating: Double?,
    ): Flow<PagingData<FindroidItem>> {
        return Pager(
                config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                pagingSourceFactory = {
                    SearchItemsPagingSource(
                        jellyfinRepository = this,
                        query = query,
                        includeItemTypes = includeItemTypes,
                        seriesStatuses = seriesStatuses,
                        genres = genres,
                        tags = tags,
                        officialRatings = officialRatings,
                        years = years,
                        studios = studios,
                        audioLanguages = audioLanguages,
                        subtitleLanguages = subtitleLanguages,
                        filters = filters,
                        is3D = is3D,
                        hasSubtitles = hasSubtitles,
                        is4K = is4K,
                        isHD = isHD,
                        videoTypes = videoTypes,
                        hasTrailer = hasTrailer,
                        hasSpecialFeatures = hasSpecialFeatures,
                        hasThemeSong = hasThemeSong,
                        hasThemeVideo = hasThemeVideo,
                        minCommunityRating = minCommunityRating,
                    )
                },
            )
            .flow
    }

    override suspend fun getRandomItem(): FindroidItem? =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.libraryApi
                    .getItems(
                        jellyfinApi.userId!!,
                        includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = 1,
                        recursive = true,
                    )
                    .content
                    .items
                    .firstOrNull()
                    ?.toFindroidItem(this@JellyfinRepositoryImpl, database)
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun getSuggestions(forceRefresh: Boolean): List<FindroidItem> {
        val currentUserId = jellyfinApi.userId
        if (
            !forceRefresh && cachedSuggestions != null && cachedSuggestionsUserId == currentUserId
        ) {
            return cachedSuggestions!!
        }

        return withContext(Dispatchers.IO) {
            val suggestions =
                jellyfinApi.suggestionApi
                    .getSuggestions(
                        currentUserId!!,
                        limit = 6,
                        type = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    )
                    .content
                    .items
                    .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
                    .distinctBy { it.id }

            if (suggestions.isNotEmpty()) {
                cachedSuggestions = suggestions
                cachedSuggestionsUserId = currentUserId
            }
            suggestions
        }
    }

    override suspend fun getResumeItems(): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getResumeItems(
                    jellyfinApi.userId!!,
                    limit = 12,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE),
                    fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES),
                )
                .content
                .items
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
                .distinctBy { it.id }
        }

    override suspend fun getLatestMedia(parentId: UUID): List<FindroidItem> =
        withContext(Dispatchers.IO) {
            jellyfinApi.libraryApi
                .getLatestMedia(
                    jellyfinApi.userId!!,
                    parentId = parentId,
                    fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES),
                    limit = 16,
                )
                .content
                .mapNotNull { it.toFindroidItem(this@JellyfinRepositoryImpl, database) }
                .distinctBy { it.id }
        }

    override suspend fun getSeasons(seriesId: UUID, offline: Boolean): List<FindroidSeason> =
        withContext(Dispatchers.IO) {
            if (!offline) {
                jellyfinApi.showApi
                    .getSeasons(seriesId, jellyfinApi.userId!!)
                    .content
                    .items
                    .map { it.toFindroidSeason(this@JellyfinRepositoryImpl) }
                    .distinctBy { it.id }
            } else {
                database
                    .getSeasonsByShowId(seriesId)
                    .map { it.toFindroidSeason(database, jellyfinApi.userId!!) }
                    .distinctBy { it.id }
            }
        }

    override suspend fun getNextUp(seriesId: UUID?): List<FindroidEpisode> =
        withContext(Dispatchers.IO) {
            jellyfinApi.showApi
                .getNextUp(
                    jellyfinApi.userId!!,
                    limit = 24,
                    seriesId = seriesId,
                    enableResumable = false,
                    fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES),
                )
                .content
                .items
                .mapNotNull { it.toFindroidEpisode(this@JellyfinRepositoryImpl) }
                .distinctBy { it.id }
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
            if (!offline) {
                jellyfinApi.showApi
                    .getEpisodes(
                        seriesId,
                        jellyfinApi.userId!!,
                        seasonId = seasonId,
                        fields = fields,
                        startItemId = startItemId,
                        limit = limit,
                    )
                    .content
                    .items
                    .mapNotNull { it.toFindroidEpisode(this@JellyfinRepositoryImpl, database) }
                    .distinctBy { it.id }
            } else {
                database
                    .getEpisodesBySeasonId(seasonId)
                    .map { it.toFindroidEpisode(database, jellyfinApi.userId!!) }
                    .distinctBy { it.id }
            }
        }

    override suspend fun getMediaSources(
        itemId: UUID,
        includePath: Boolean,
        maxStreamingBitrate: Int?,
        audioStreamIndex: Int?,
        deviceProfile: DeviceProfile?,
        enableDirectPlay: Boolean,
    ): List<FindroidSource> =
        withContext(Dispatchers.IO) {
            val effectiveMaxBitrate = maxStreamingBitrate ?: 120_000_000
            val effectiveProfile =
                deviceProfile
                    ?: DeviceProfile(
                        name = "Findroid Player",
                        maxStaticBitrate = 1_000_000_000,
                        maxStreamingBitrate = effectiveMaxBitrate,
                        codecProfiles = emptyList(),
                        containerProfiles = emptyList(),
                        directPlayProfiles = emptyList(),
                        transcodingProfiles = emptyList(),
                        subtitleProfiles =
                            listOf(
                                SubtitleProfile("srt", SubtitleDeliveryMethod.EXTERNAL),
                                SubtitleProfile("ass", SubtitleDeliveryMethod.EXTERNAL),
                                SubtitleProfile("vtt", SubtitleDeliveryMethod.EXTERNAL),
                            ),
                    )
            val sources = mutableListOf<FindroidSource>()
            try {
                val playbackInfo =
                    jellyfinApi.mediaInfoApi
                        .getPostedPlaybackInfo(
                            itemId,
                            PlaybackInfoDto(
                                userId = jellyfinApi.userId!!,
                                deviceProfile = effectiveProfile,
                                maxStreamingBitrate = effectiveMaxBitrate,
                                audioStreamIndex = audioStreamIndex,
                                enableDirectPlay = enableDirectPlay,
                                enableDirectStream = true,
                                enableTranscoding = true,
                                allowAudioStreamCopy = true,
                                allowVideoStreamCopy = true,
                            ),
                        )
                        .content
                val playSessionId = playbackInfo.playSessionId
                sources.addAll(
                    playbackInfo.mediaSources.map {
                        it.toFindroidSource(
                            this@JellyfinRepositoryImpl,
                            itemId,
                            includePath,
                            playSessionId = playSessionId,
                            audioStreamIndex = audioStreamIndex,
                        )
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error fetching posted playback info")
            }
            val currentUserId = jellyfinApi.userId
            if (currentUserId != null && database.isItemDownloadedForUser(currentUserId, itemId)) {
                sources.addAll(database.getSources(itemId).map { it.toFindroidSource(database) })
            }
            sources
        }

    override suspend fun getStreamUrl(itemId: UUID, mediaSourceId: String): String =
        withContext(Dispatchers.IO) {
            try {
                jellyfinApi.videoApi.getVideoStreamUrl(
                    itemId,
                    static = true,
                    mediaSourceId = mediaSourceId,
                )
            } catch (e: Exception) {
                Timber.e(e)
                ""
            }
        }

    override suspend fun getSegments(itemId: UUID): List<FindroidSegment> =
        withContext(Dispatchers.IO) {
            val databaseSegments = database.getSegments(itemId).map { it.toFindroidSegment() }

            if (databaseSegments.isNotEmpty()) {
                return@withContext databaseSegments
            }

            try {
                val apiSegments =
                    jellyfinApi.mediaSegmentApi.getItemSegments(itemId).content.items.map {
                        it.toFindroidSegment()
                    }

                Timber.d("Segments count: ${apiSegments.size}")

                return@withContext apiSegments
            } catch (e: Exception) {
                Timber.e(e)
                return@withContext emptyList()
            }
        }

    override suspend fun getTrickplayData(itemId: UUID, width: Int, index: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                try {
                    val sources = File(context.filesDir, "trickplay/$itemId").listFiles()
                    if (sources != null) {
                        return@withContext File(sources.first(), index.toString()).readBytes()
                    }
                } catch (_: Exception) {}

                return@withContext jellyfinApi.trickPlayApi
                    .getTrickplayTileImage(itemId, width, index)
                    .content
            } catch (_: Exception) {
                return@withContext null
            }
        }

    override suspend fun postCapabilities() {
        Timber.d("Sending capabilities")
        withContext(Dispatchers.IO) {
            val customName = appPreferences.getValue(appPreferences.customDeviceName)
            if (customName.isNotBlank()) {
                try {
                    updateDeviceName(customName)
                } catch (e: Exception) {
                    Timber.w(e, "Could not sync custom device name on capabilities post")
                }
            }
            jellyfinApi.sessionApi.postCapabilities(
                playableMediaTypes = listOf(MediaType.VIDEO),
                supportedCommands =
                    listOf(
                        GeneralCommandType.VOLUME_UP,
                        GeneralCommandType.VOLUME_DOWN,
                        GeneralCommandType.TOGGLE_MUTE,
                        GeneralCommandType.SET_AUDIO_STREAM_INDEX,
                        GeneralCommandType.SET_SUBTITLE_STREAM_INDEX,
                        GeneralCommandType.MUTE,
                        GeneralCommandType.UNMUTE,
                        GeneralCommandType.SET_VOLUME,
                        GeneralCommandType.DISPLAY_MESSAGE,
                        GeneralCommandType.PLAY,
                        GeneralCommandType.PLAY_STATE,
                        GeneralCommandType.PLAY_NEXT,
                        GeneralCommandType.PLAY_MEDIA_SOURCE,
                    ),
                supportsMediaControl = true,
            )
        }
    }

    override suspend fun postPlaybackStart(
        itemId: UUID,
        positionTicks: Long?,
        playMethod: PlayMethod,
        mediaSourceId: String?,
        playSessionId: String?,
    ) {
        Timber.d("Sending start $itemId, position: $positionTicks")
        withContext(Dispatchers.IO) {
            jellyfinApi.sessionApi.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = itemId,
                    canSeek = true,
                    isPaused = false,
                    isMuted = false,
                    positionTicks = positionTicks,
                    playMethod = playMethod,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                    mediaSourceId = mediaSourceId,
                    playSessionId = playSessionId,
                )
            )
        }
    }

    override suspend fun postPlaybackStop(
        itemId: UUID,
        positionTicks: Long,
        playedPercentage: Int,
        mediaSourceId: String?,
        playSessionId: String?,
    ) {
        Timber.d("Sending stop $itemId, position: $positionTicks")
        withContext(Dispatchers.IO) {
            when {
                playedPercentage < 10 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }

                playedPercentage > 90 -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, 0)
                    database.setPlayed(jellyfinApi.userId!!, itemId, true)
                }

                else -> {
                    database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
                    database.setPlayed(jellyfinApi.userId!!, itemId, false)
                }
            }
            try {
                jellyfinApi.sessionApi.reportPlaybackStopped(
                    PlaybackStopInfo(
                        itemId = itemId,
                        positionTicks = positionTicks,
                        failed = false,
                        mediaSourceId = mediaSourceId,
                        playSessionId = playSessionId,
                    )
                )
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
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
        Timber.d("Posting progress of $itemId, position: $positionTicks")
        withContext(Dispatchers.IO) {
            database.setPlaybackPositionTicks(itemId, jellyfinApi.userId!!, positionTicks)
            try {
                jellyfinApi.sessionApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        itemId = itemId,
                        canSeek = true,
                        isPaused = isPaused,
                        isMuted = false,
                        playMethod = playMethod,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                        positionTicks = positionTicks,
                        mediaSourceId = mediaSourceId,
                        playSessionId = playSessionId,
                    )
                )
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, true)
            try {
                jellyfinApi.userDataApi.markFavoriteItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun unmarkAsFavorite(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setFavorite(jellyfinApi.userId!!, itemId, false)
            try {
                jellyfinApi.userDataApi.unmarkFavoriteItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsPlayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, true)
            try {
                jellyfinApi.userDataApi.markPlayedItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override suspend fun markAsUnplayed(itemId: UUID) {
        withContext(Dispatchers.IO) {
            database.setPlayed(jellyfinApi.userId!!, itemId, false)
            try {
                jellyfinApi.userDataApi.markUnplayedItem(itemId)
            } catch (_: Exception) {
                database.setUserDataToBeSynced(jellyfinApi.userId!!, itemId, true)
            }
        }
    }

    override fun getBaseUrl() = jellyfinApi.api.baseUrl.orEmpty()

    override fun getDeviceName(): String {
        val customName = appPreferences.getValue(appPreferences.customDeviceName)
        if (customName.isNotBlank()) return customName
        return jellyfinApi.jellyfin.deviceInfo?.name.orEmpty()
    }

    override suspend fun updateDeviceName(name: String) {
        withContext(Dispatchers.IO) {
            jellyfinApi.jellyfin.deviceInfo?.id?.let { id ->
                jellyfinApi.deviceApi.updateDeviceOptions(
                    id,
                    DeviceOptionsDto(0, deviceId = id, customName = name),
                )
            }
        }
    }

    override suspend fun getUserConfiguration(): UserConfiguration =
        withContext(Dispatchers.IO) { jellyfinApi.userApi.getCurrentUser().content.configuration!! }

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
        return withContext(Dispatchers.IO) {
            try {
                val userDto = jellyfinApi.userApi.getUserById(userId).content
                val existingUser = database.getUser(userId) ?: return@withContext null

                val updatedUser =
                    existingUser.copy(
                        name = userDto.name ?: existingUser.name,
                        primaryImageTag = userDto.primaryImageTag,
                    )
                database.insertUser(updatedUser)
                userDto.primaryImageTag
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }
    }

    override suspend fun setCurrentUser(userId: UUID) {
        val server = getCurrentServer() ?: return
        val user = database.getUser(userId) ?: return
        server.currentUserId = user.id
        database.updateServer(server)

        jellyfinApi.apply {
            api.update(accessToken = user.accessToken)
            this.userId = user.id
        }
    }

    override suspend fun canTranscode(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val policy = jellyfinApi.userApi.getCurrentUser().content.policy
                (policy?.enableSyncTranscoding != false) &&
                    (policy?.enableVideoPlaybackTranscoding != false)
            } catch (_: Exception) {
                true
            }
        }

    override suspend fun getActiveTranscodingInfo(
        playSessionId: String?
    ): org.jellyfin.sdk.model.api.TranscodingInfo? =
        withContext(Dispatchers.IO) {
            try {
                val sessions =
                    jellyfinApi.sessionApi
                        .getSessions(deviceId = jellyfinApi.jellyfin.deviceInfo?.id)
                        .content
                sessions.firstOrNull { it.id == playSessionId }?.transcodingInfo
                    ?: sessions.firstOrNull { it.transcodingInfo != null }?.transcodingInfo
            } catch (e: Exception) {
                Timber.e(e, "Error fetching active transcoding info")
                null
            }
        }
}
