package dev.jdtech.jellyfin.film.presentation.search

import dev.jdtech.jellyfin.models.FindroidItem
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.SeriesStatus

/** Represents user interactions and actions on the search screen and filter bottom sheet. */
sealed interface SearchAction {
    data class Search(val query: String) : SearchAction

    data object OnTabReselected : SearchAction

    data class OnItemClick(val item: FindroidItem) : SearchAction

    data class OnRandomClick(val onResult: (FindroidItem) -> Unit) : SearchAction

    data class OpenFilterBottomSheet(val category: FilterCategory? = null) : SearchAction

    data object CloseFilterBottomSheet : SearchAction

    data class ToggleContentType(val type: BaseItemKind) : SearchAction

    data class ToggleVideoQuality(val quality: VideoQuality) : SearchAction

    data class ToggleFeature(val feature: FeatureFlag) : SearchAction

    data class ToggleItemFilter(val filter: ItemFilter) : SearchAction

    data class ToggleSeriesStatus(val status: SeriesStatus) : SearchAction

    data class ToggleAudioLanguage(val language: String) : SearchAction

    data class ToggleSubtitleLanguage(val language: String) : SearchAction

    data class ToggleGenre(val genre: String) : SearchAction

    data class ToggleRating(val rating: String) : SearchAction

    data class ToggleTag(val tag: String) : SearchAction

    data class SetYearRange(val range: ClosedFloatingPointRange<Float>?) : SearchAction

    data class SetCommunityRatingRange(val range: ClosedFloatingPointRange<Float>?) : SearchAction

    data class ToggleStudio(val studio: String) : SearchAction

    data object ClearAllFilters : SearchAction

    data class ClearCategoryFilters(val category: FilterCategory) : SearchAction
}
