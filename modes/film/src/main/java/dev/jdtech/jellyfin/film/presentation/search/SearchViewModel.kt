package dev.jdtech.jellyfin.film.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.VideoType
import timber.log.Timber

/**
 * ViewModel managing state, metadata loading, and paginated search queries across movies, shows,
 * and episodes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _focusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val focusRequests: SharedFlow<Unit> = _focusRequests.asSharedFlow()

    private val queryParamsFlow: Flow<SearchQueryParams> =
        state.map { it.queryParams }.distinctUntilChanged()

    val moviesPagingFlow: Flow<PagingData<FindroidMovie>> =
        queryParamsFlow
            .flatMapLatest { params ->
                if (!params.isSearchStarted) {
                    flowOf(PagingData.empty())
                } else if (
                    params.selectedContentTypes.isNotEmpty() &&
                        !params.selectedContentTypes.contains(BaseItemKind.MOVIE)
                ) {
                    flowOf(PagingData.empty())
                } else {
                    createSearchPagingFlow(params, listOf(BaseItemKind.MOVIE)).map { pagingData ->
                        pagingData.filter { it is FindroidMovie }.map { it as FindroidMovie }
                    }
                }
            }
            .cachedIn(viewModelScope)

    val showsPagingFlow: Flow<PagingData<FindroidItem>> =
        queryParamsFlow
            .flatMapLatest { params ->
                if (!params.isSearchStarted) {
                    flowOf(PagingData.empty())
                } else if (
                    params.selectedContentTypes.isNotEmpty() &&
                        !params.selectedContentTypes.contains(BaseItemKind.SERIES) &&
                        !params.selectedContentTypes.contains(BaseItemKind.SEASON)
                ) {
                    flowOf(PagingData.empty())
                } else {
                    val effectiveYears = params.effectiveYears
                    val isSingleYearSelected = effectiveYears?.size == 1

                    val includeTypes =
                        when {
                            params.selectedContentTypes.contains(BaseItemKind.SEASON) ->
                                listOf(BaseItemKind.SEASON)
                            params.selectedContentTypes.contains(BaseItemKind.SERIES) ->
                                listOf(BaseItemKind.SERIES)
                            isSingleYearSelected -> listOf(BaseItemKind.SEASON)
                            else -> listOf(BaseItemKind.SERIES)
                        }

                    createSearchPagingFlow(params, includeTypes).map { pagingData ->
                        pagingData.filter { it is FindroidShow || it is FindroidSeason }
                    }
                }
            }
            .cachedIn(viewModelScope)

    val episodesPagingFlow: Flow<PagingData<FindroidEpisode>> =
        queryParamsFlow
            .flatMapLatest { params ->
                if (!params.isSearchStarted) {
                    flowOf(PagingData.empty())
                } else if (
                    params.selectedContentTypes.isNotEmpty() &&
                        !params.selectedContentTypes.contains(BaseItemKind.EPISODE)
                ) {
                    flowOf(PagingData.empty())
                } else {
                    createSearchPagingFlow(params, listOf(BaseItemKind.EPISODE)).map { pagingData ->
                        pagingData
                            .filter { (it as? FindroidEpisode)?.missing != true }
                            .map { it as FindroidEpisode }
                    }
                }
            }
            .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            appPreferences.observe(appPreferences.hideEpisodeSpoilers).collect { hideSpoilers ->
                _state.update { it.copy(hideEpisodeSpoilers = hideSpoilers) }
            }
        }
        loadServerMetadata()
    }

    private fun loadServerMetadata() {
        viewModelScope.launch {
            _state.update { it.copy(isMetadataLoading = true) }

            val currentState = _state.value
            val selectedTypes = currentState.selectedContentTypes.toList().ifEmpty { null }
            val isInitial = currentState.availableContentTypes.isEmpty()

            val contentTypesJob =
                if (isInitial) async { repository.getAvailableContentTypes() } else null
            val videoQualitiesJob =
                if (isInitial) async { repository.getAvailableVideoQualities() } else null
            val itemFiltersJob =
                if (isInitial) async { repository.getAvailableItemFilters() } else null
            val seriesStatusesJob =
                if (isInitial) async { repository.getAvailableSeriesStatuses() } else null
            val featuresJob = if (isInitial) async { repository.getAvailableFeatures() } else null

            val searchSuggestionsJob =
                if (isInitial) async { repository.getSearchSuggestions() } else null

            val genresJob = async { repository.getGenres(selectedTypes) }
            val yearsJob = async { repository.getYears(selectedTypes) }
            val ratingsJob = async { repository.getOfficialRatings(selectedTypes) }
            val studiosJob = async { repository.getStudios(selectedTypes) }
            val tagsJob = async { repository.getTags(selectedTypes) }
            val audioLanguagesJob = async { repository.getAudioLanguages(selectedTypes) }
            val subtitleLanguagesJob = async { repository.getSubtitleLanguages(selectedTypes) }

            val contentTypes = contentTypesJob.awaitOrDefault(currentState.availableContentTypes)
            val videoQualities =
                videoQualitiesJob?.let { job ->
                    runCatching { job.await() }
                        .getOrDefault(emptyList())
                        .mapNotNull { parseVideoQuality(it) }
                        .ifEmpty { VideoQuality.entries }
                } ?: currentState.availableVideoQualities
            val itemFilters = itemFiltersJob.awaitOrDefault(currentState.availableItemFilters)
            val seriesStatuses =
                seriesStatusesJob.awaitOrDefault(currentState.availableSeriesStatuses)
            val features =
                featuresJob?.let { job ->
                    runCatching { job.await() }
                        .getOrDefault(emptyList())
                        .mapNotNull { f -> runCatching { FeatureFlag.valueOf(f) }.getOrNull() }
                        .ifEmpty { FeatureFlag.entries }
                } ?: currentState.availableFeatures
            val searchSuggestions =
                searchSuggestionsJob?.let { job ->
                    runCatching { job.await() }.getOrDefault(emptyList())
                } ?: currentState.searchSuggestions

            val genres = runCatching { genresJob.await() }.getOrDefault(emptyList())
            val years = runCatching { yearsJob.await() }.getOrDefault(emptyList())
            val ratings = runCatching { ratingsJob.await() }.getOrDefault(emptyList())
            val studios = runCatching { studiosJob.await() }.getOrDefault(emptyList())
            val tags = runCatching { tagsJob.await() }.getOrDefault(emptyList())
            val audioLanguages = runCatching { audioLanguagesJob.await() }.getOrDefault(emptyList())
            val subtitleLanguages = runCatching {
                subtitleLanguagesJob.await()
            }
                .getOrDefault(emptyList())

            _state.update {
                it.copy(
                    availableContentTypes = contentTypes,
                    availableVideoQualities = videoQualities,
                    availableItemFilters = itemFilters,
                    availableSeriesStatuses = seriesStatuses,
                    availableFeatures = features,
                    availableGenres = genres,
                    availableYears = years,
                    availableRatings = ratings,
                    availableStudios = studios,
                    availableTags = tags,
                    availableAudioLanguages = audioLanguages,
                    availableSubtitleLanguages = subtitleLanguages,
                    searchSuggestions = searchSuggestions,
                    isMetadataLoading = false,
                )
            }
        }
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.Search -> {
                _state.update { it.copy(query = action.query) }
            }
            is SearchAction.OnTabReselected -> {
                _focusRequests.tryEmit(Unit)
            }
            is SearchAction.OnRandomClick -> {
                getRandomItem(action.onResult)
            }
            is SearchAction.OpenFilterBottomSheet -> {
                _state.update {
                    it.copy(
                        isFilterSheetOpen = true,
                        activeBottomSheetCategory =
                            action.category
                                ?: it.activeBottomSheetCategory
                                ?: FilterCategory.VIDEO_TYPE,
                    )
                }
            }
            is SearchAction.CloseFilterBottomSheet -> {
                _state.update { it.copy(isFilterSheetOpen = false) }
            }
            is SearchAction.ToggleContentType -> {
                _state.update {
                    it.copy(selectedContentTypes = it.selectedContentTypes.toggle(action.type))
                }
                loadServerMetadata()
            }
            is SearchAction.ToggleVideoQuality -> {
                _state.update {
                    it.copy(
                        selectedVideoQualities = it.selectedVideoQualities.toggle(action.quality)
                    )
                }
            }
            is SearchAction.ToggleFeature -> {
                _state.update {
                    it.copy(selectedFeatures = it.selectedFeatures.toggle(action.feature))
                }
            }
            is SearchAction.ToggleItemFilter -> {
                _state.update {
                    it.copy(selectedItemFilters = it.selectedItemFilters.toggle(action.filter))
                }
            }
            is SearchAction.ToggleSeriesStatus -> {
                _state.update {
                    it.copy(
                        selectedSeriesStatuses = it.selectedSeriesStatuses.toggle(action.status)
                    )
                }
            }
            is SearchAction.ToggleAudioLanguage -> {
                _state.update {
                    it.copy(
                        selectedAudioLanguages = it.selectedAudioLanguages.toggle(action.language)
                    )
                }
            }
            is SearchAction.ToggleSubtitleLanguage -> {
                _state.update {
                    it.copy(
                        selectedSubtitleLanguages =
                            it.selectedSubtitleLanguages.toggle(action.language)
                    )
                }
            }
            is SearchAction.ToggleGenre -> {
                _state.update { it.copy(selectedGenres = it.selectedGenres.toggle(action.genre)) }
            }
            is SearchAction.ToggleRating -> {
                _state.update {
                    it.copy(selectedRatings = it.selectedRatings.toggle(action.rating))
                }
            }
            is SearchAction.ToggleTag -> {
                _state.update { it.copy(selectedTags = it.selectedTags.toggle(action.tag)) }
            }
            is SearchAction.SetYearRange -> {
                _state.update { currentState ->
                    if (currentState.selectedYearRange == action.range) currentState
                    else currentState.copy(selectedYearRange = action.range)
                }
            }
            is SearchAction.SetCommunityRatingRange -> {
                _state.update { currentState ->
                    if (currentState.selectedCommunityRatingRange == action.range) currentState
                    else currentState.copy(selectedCommunityRatingRange = action.range)
                }
            }
            is SearchAction.ToggleStudio -> {
                _state.update {
                    it.copy(selectedStudios = it.selectedStudios.toggle(action.studio))
                }
            }
            is SearchAction.ClearAllFilters -> {
                _state.update {
                    it.copy(
                        selectedContentTypes = emptySet(),
                        selectedVideoQualities = emptySet(),
                        selectedItemFilters = emptySet(),
                        selectedSeriesStatuses = emptySet(),
                        selectedFeatures = emptySet(),
                        selectedAudioLanguages = emptySet(),
                        selectedSubtitleLanguages = emptySet(),
                        selectedGenres = emptySet(),
                        selectedRatings = emptySet(),
                        selectedTags = emptySet(),
                        selectedYearRange = null,
                        selectedCommunityRatingRange = null,
                        selectedStudios = emptySet(),
                    )
                }
                loadServerMetadata()
            }
            is SearchAction.ClearCategoryFilters -> {
                _state.update { currentState ->
                    when (action.category) {
                        FilterCategory.PLAY_STATE ->
                            currentState.copy(selectedItemFilters = emptySet())
                        FilterCategory.SERIES_STATUS ->
                            currentState.copy(selectedSeriesStatuses = emptySet())
                        FilterCategory.VIDEO_TYPE ->
                            currentState.copy(selectedContentTypes = emptySet())
                        FilterCategory.VIDEO_QUALITY ->
                            currentState.copy(selectedVideoQualities = emptySet())
                        FilterCategory.FEATURES -> currentState.copy(selectedFeatures = emptySet())
                        FilterCategory.AUDIO_LANGUAGES ->
                            currentState.copy(selectedAudioLanguages = emptySet())
                        FilterCategory.SUBTITLE_LANGUAGES ->
                            currentState.copy(selectedSubtitleLanguages = emptySet())
                        FilterCategory.GENRES -> currentState.copy(selectedGenres = emptySet())
                        FilterCategory.PARENTAL_RATINGS ->
                            currentState.copy(selectedRatings = emptySet())
                        FilterCategory.TAGS -> currentState.copy(selectedTags = emptySet())
                        FilterCategory.YEARS -> currentState.copy(selectedYearRange = null)
                        FilterCategory.COMMUNITY_RATING ->
                            currentState.copy(selectedCommunityRatingRange = null)
                        FilterCategory.STUDIOS -> currentState.copy(selectedStudios = emptySet())
                    }
                }
                if (action.category == FilterCategory.VIDEO_TYPE) {
                    loadServerMetadata()
                }
            }
            is SearchAction.OnItemClick -> Unit
        }
    }

    private fun createSearchPagingFlow(
        params: SearchQueryParams,
        includeTypes: List<BaseItemKind>,
    ): Flow<PagingData<FindroidItem>> {
        val seriesStatuses = params.selectedSeriesStatuses.toList().ifEmpty { null }

        val is4K = true.takeIf { VideoQuality.FOUR_K in params.selectedVideoQualities }

        val isHD =
            when {
                VideoQuality.HD in params.selectedVideoQualities -> true
                VideoQuality.SD in params.selectedVideoQualities -> false
                else -> null
            }

        val is3D = true.takeIf { VideoQuality.THREE_D in params.selectedVideoQualities }

        val videoTypes = buildList {
            if (VideoQuality.DVD in params.selectedVideoQualities) add(VideoType.DVD)
            if (VideoQuality.BLU_RAY in params.selectedVideoQualities) add(VideoType.BLU_RAY)
            if (VideoQuality.ISO in params.selectedVideoQualities) add(VideoType.ISO)
        }
            .ifEmpty { null }

        val hasSubtitles = true.takeIf { FeatureFlag.HAS_SUBTITLES in params.selectedFeatures }
        val hasTrailer = true.takeIf { FeatureFlag.HAS_TRAILER in params.selectedFeatures }
        val hasSpecialFeatures = true.takeIf { FeatureFlag.HAS_EXTRA in params.selectedFeatures }
        val hasThemeSong = true.takeIf { FeatureFlag.HAS_THEME_SONG in params.selectedFeatures }
        val hasThemeVideo = true.takeIf { FeatureFlag.HAS_THEME_VIDEO in params.selectedFeatures }

        val effectiveYears = params.effectiveYears

        val serverYears =
            if (includeTypes.contains(BaseItemKind.SEASON) && includeTypes.size == 1) {
                null
            } else {
                effectiveYears
            }

        val minCommunityRating =
            params.selectedCommunityRatingRange?.start?.toDouble()?.takeIf { it > 0.0 }
        val maxCommunityRating =
            params.selectedCommunityRatingRange?.endInclusive?.toDouble()?.takeIf { it < 10.0 }

        return repository
            .getSearchItemsFilteredPaging(
                query = params.query,
                includeItemTypes = includeTypes,
                seriesStatuses = seriesStatuses,
                genres = params.selectedGenres.toList().ifEmpty { null },
                tags = params.selectedTags.toList().ifEmpty { null },
                officialRatings = params.selectedRatings.toList().ifEmpty { null },
                years = serverYears,
                studios = params.selectedStudios.toList().ifEmpty { null },
                audioLanguages = params.selectedAudioLanguages.toList().ifEmpty { null },
                subtitleLanguages = params.selectedSubtitleLanguages.toList().ifEmpty { null },
                filters = params.selectedItemFilters.toList().ifEmpty { null },
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
            .map { pagingData ->
                var filtered = pagingData
                if (effectiveYears != null) {
                    val yearSet = effectiveYears.toSet()
                    filtered = filtered.filter { item ->
                        val year =
                            when (item) {
                                is FindroidMovie -> item.productionYear
                                is FindroidShow -> item.productionYear
                                is FindroidSeason -> item.productionYear ?: item.premiereDate?.year
                                is FindroidEpisode -> item.premiereDate?.year
                                else -> null
                            }
                        year == null || yearSet.contains(year)
                    }
                }
                if (maxCommunityRating != null) {
                    filtered = filtered.filter { item ->
                        val rating =
                            when (item) {
                                is FindroidMovie -> item.communityRating
                                is FindroidShow -> item.communityRating
                                is FindroidEpisode -> item.communityRating
                                else -> null
                            }
                        rating == null || rating <= maxCommunityRating
                    }
                }
                filtered
            }
    }

    private fun getRandomItem(onResult: (FindroidItem) -> Unit) {
        viewModelScope.launch {
            try {
                val randomItem = repository.getRandomItem()
                if (randomItem != null) {
                    onResult(randomItem)
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    private fun <T> Set<T>.toggle(element: T): Set<T> =
        if (contains(element)) this - element else this + element

    private suspend fun <T> Deferred<T>?.awaitOrDefault(default: T): T =
        if (this != null) runCatching { await() }.getOrDefault(default) else default

    private fun parseVideoQuality(q: String): VideoQuality? =
        when (q) {
            "SD" -> VideoQuality.SD
            "HD" -> VideoQuality.HD
            "4K" -> VideoQuality.FOUR_K
            "3D" -> VideoQuality.THREE_D
            "DVD" -> VideoQuality.DVD
            "BLU_RAY" -> VideoQuality.BLU_RAY
            "ISO" -> VideoQuality.ISO
            else -> runCatching { VideoQuality.valueOf(q) }.getOrNull()
        }
}
