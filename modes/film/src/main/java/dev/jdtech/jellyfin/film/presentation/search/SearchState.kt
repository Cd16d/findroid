package dev.jdtech.jellyfin.film.presentation.search

import kotlin.math.roundToInt
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.SeriesStatus

enum class FilterCategory {
    VIDEO_TYPE,
    GENRES,
    YEARS,
    VIDEO_QUALITY,
    COMMUNITY_RATING,
    PARENTAL_RATINGS,
    PLAY_STATE,
    AUDIO_LANGUAGES,
    SUBTITLE_LANGUAGES,
    STUDIOS,
    FEATURES,
    TAGS,
    SERIES_STATUS,
}

/**
 * Represents the UI state for the search screen, including query, available server metadata, active
 * filter selections, and bottom sheet state.
 */
data class SearchState(
    val query: String = "",
    val hideEpisodeSpoilers: Boolean = false,
    val isMetadataLoading: Boolean = true,
    // Available filter values loaded from server
    val availableContentTypes: List<BaseItemKind> = emptyList(),
    val availableVideoQualities: List<VideoQuality> = VideoQuality.entries,
    val availableItemFilters: List<ItemFilter> = emptyList(),
    val availableSeriesStatuses: List<SeriesStatus> = emptyList(),
    val availableFeatures: List<FeatureFlag> = FeatureFlag.entries,
    val availableGenres: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val availableStudios: List<String> = emptyList(),
    val availableRatings: List<String> = emptyList(),
    val availableYears: List<Int> = emptyList(),
    val availableAudioLanguages: List<String> = emptyList(),
    val availableSubtitleLanguages: List<String> = emptyList(),
    val searchSuggestions: List<String> = emptyList(),
    // Active filter selections
    val selectedContentTypes: Set<BaseItemKind> = emptySet(),
    val selectedVideoQualities: Set<VideoQuality> = emptySet(),
    val selectedItemFilters: Set<ItemFilter> = emptySet(),
    val selectedSeriesStatuses: Set<SeriesStatus> = emptySet(),
    val selectedFeatures: Set<FeatureFlag> = emptySet(),
    val selectedAudioLanguages: Set<String> = emptySet(),
    val selectedSubtitleLanguages: Set<String> = emptySet(),
    val selectedGenres: Set<String> = emptySet(),
    val selectedRatings: Set<String> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val selectedYearRange: ClosedFloatingPointRange<Float>? = null,
    val selectedCommunityRatingRange: ClosedFloatingPointRange<Float>? = null,
    val selectedStudios: Set<String> = emptySet(),
    // Bottom sheet state
    val activeBottomSheetCategory: FilterCategory? = null,
    val isFilterSheetOpen: Boolean = false,
) {
    val isSearchStarted: Boolean
        get() = query.isNotBlank() || activeFiltersCount > 0

    val activeFiltersCount: Int
        get() =
            selectedContentTypes.size +
                selectedVideoQualities.size +
                selectedItemFilters.size +
                selectedSeriesStatuses.size +
                selectedFeatures.size +
                selectedAudioLanguages.size +
                selectedSubtitleLanguages.size +
                selectedGenres.size +
                selectedRatings.size +
                selectedTags.size +
                (if (selectedYearRange != null) 1 else 0) +
                (if (selectedCommunityRatingRange != null) 1 else 0) +
                selectedStudios.size

    fun isCategoryActive(category: FilterCategory): Boolean =
        when (category) {
            FilterCategory.PLAY_STATE -> selectedItemFilters.isNotEmpty()
            FilterCategory.SERIES_STATUS -> selectedSeriesStatuses.isNotEmpty()
            FilterCategory.VIDEO_TYPE -> selectedContentTypes.isNotEmpty()
            FilterCategory.VIDEO_QUALITY -> selectedVideoQualities.isNotEmpty()
            FilterCategory.FEATURES -> selectedFeatures.isNotEmpty()
            FilterCategory.AUDIO_LANGUAGES -> selectedAudioLanguages.isNotEmpty()
            FilterCategory.SUBTITLE_LANGUAGES -> selectedSubtitleLanguages.isNotEmpty()
            FilterCategory.GENRES -> selectedGenres.isNotEmpty()
            FilterCategory.PARENTAL_RATINGS -> selectedRatings.isNotEmpty()
            FilterCategory.TAGS -> selectedTags.isNotEmpty()
            FilterCategory.YEARS -> selectedYearRange != null
            FilterCategory.COMMUNITY_RATING -> selectedCommunityRatingRange != null
            FilterCategory.STUDIOS -> selectedStudios.isNotEmpty()
        }

    fun getCategoryBadgeText(category: FilterCategory): String? =
        when (category) {
            FilterCategory.PLAY_STATE -> selectedItemFilters.size.takeIf { it > 0 }?.toString()
            FilterCategory.SERIES_STATUS ->
                selectedSeriesStatuses.size.takeIf { it > 0 }?.toString()
            FilterCategory.VIDEO_TYPE -> selectedContentTypes.size.takeIf { it > 0 }?.toString()
            FilterCategory.VIDEO_QUALITY ->
                selectedVideoQualities.size.takeIf { it > 0 }?.toString()
            FilterCategory.FEATURES -> selectedFeatures.size.takeIf { it > 0 }?.toString()
            FilterCategory.AUDIO_LANGUAGES ->
                selectedAudioLanguages.size.takeIf { it > 0 }?.toString()
            FilterCategory.SUBTITLE_LANGUAGES ->
                selectedSubtitleLanguages.size.takeIf { it > 0 }?.toString()
            FilterCategory.GENRES -> selectedGenres.size.takeIf { it > 0 }?.toString()
            FilterCategory.PARENTAL_RATINGS -> selectedRatings.size.takeIf { it > 0 }?.toString()
            FilterCategory.TAGS -> selectedTags.size.takeIf { it > 0 }?.toString()
            FilterCategory.YEARS,
            FilterCategory.COMMUNITY_RATING -> null
            FilterCategory.STUDIOS -> selectedStudios.size.takeIf { it > 0 }?.toString()
        }
}

data class SearchQueryParams(
    val query: String,
    val isSearchStarted: Boolean,
    val selectedContentTypes: Set<BaseItemKind>,
    val selectedVideoQualities: Set<VideoQuality>,
    val selectedItemFilters: Set<ItemFilter>,
    val selectedSeriesStatuses: Set<SeriesStatus>,
    val selectedFeatures: Set<FeatureFlag>,
    val selectedAudioLanguages: Set<String>,
    val selectedSubtitleLanguages: Set<String>,
    val selectedGenres: Set<String>,
    val selectedRatings: Set<String>,
    val selectedTags: Set<String>,
    val selectedYearRange: ClosedFloatingPointRange<Float>?,
    val selectedCommunityRatingRange: ClosedFloatingPointRange<Float>?,
    val selectedStudios: Set<String>,
)

val SearchQueryParams.effectiveYears: List<Int>?
    get() =
        when {
            selectedYearRange != null -> {
                val start = selectedYearRange.start.roundToInt()
                val end = selectedYearRange.endInclusive.roundToInt()
                (start..end).toList()
            }
            else -> null
        }

val SearchState.queryParams: SearchQueryParams
    get() =
        SearchQueryParams(
            query = query,
            isSearchStarted = isSearchStarted,
            selectedContentTypes = selectedContentTypes,
            selectedVideoQualities = selectedVideoQualities,
            selectedItemFilters = selectedItemFilters,
            selectedSeriesStatuses = selectedSeriesStatuses,
            selectedFeatures = selectedFeatures,
            selectedAudioLanguages = selectedAudioLanguages,
            selectedSubtitleLanguages = selectedSubtitleLanguages,
            selectedGenres = selectedGenres,
            selectedRatings = selectedRatings,
            selectedTags = selectedTags,
            selectedYearRange = selectedYearRange,
            selectedCommunityRatingRange = selectedCommunityRatingRange,
            selectedStudios = selectedStudios,
        )
