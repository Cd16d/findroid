package dev.jdtech.jellyfin.presentation.film.search.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.search.FeatureFlag
import dev.jdtech.jellyfin.film.presentation.search.FilterCategory
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchState
import dev.jdtech.jellyfin.film.presentation.search.VideoQuality
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.ranges.rangeTo
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.SeriesStatus

/**
 * Modal bottom sheet displaying filter options for the selected active filter category.
 *
 * @param state Current search state containing available options and current filter selections.
 * @param onAction Callback triggered when user performs search or filter actions.
 * @param onDismissRequest Callback invoked when user requests to dismiss the bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val category = state.activeBottomSheetCategory ?: FilterCategory.VIDEO_TYPE
    var filterSearchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacings.default)
                    .padding(bottom = MaterialTheme.spacings.large)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = getCategoryLabel(category),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (state.isCategoryActive(category)) {
                    TextButton(
                        onClick = { onAction(SearchAction.ClearCategoryFilters(category)) }
                    ) {
                        Text(text = stringResource(CoreR.string.filter_clear_all))
                    }
                }
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))

            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                val config = category.config
                val options = config.options?.invoke(state)

                if (options != null && config.onToggle != null) {
                    FilterCardList(
                        items = options,
                        onToggle = { id -> config.onToggle.invoke(id, onAction) },
                        showSearch = config.showSearch,
                        searchQuery = filterSearchQuery,
                        onSearchQueryChange = { filterSearchQuery = it },
                        isMetadataLoading = state.isMetadataLoading,
                    )
                } else if (category == FilterCategory.COMMUNITY_RATING) {
                    CommunityRatingRangeView(state = state, onAction = onAction)
                } else if (category == FilterCategory.YEARS) {
                    YearRangeView(state = state, onAction = onAction)
                }
            }
        }
    }
}

/**
 * Range slider layout for selecting minimum and maximum community rating bounds (0.0 to 10.0).
 *
 * @param state Current search state containing selected rating range.
 * @param onAction Callback to dispatch rating range selection changes.
 */
@Composable
private fun CommunityRatingRangeView(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
) {
    val minRating = 0f
    val maxRating = 10f

    var ratingSliderRange by
        remember(state.selectedCommunityRatingRange) {
            mutableStateOf(state.selectedCommunityRatingRange ?: (minRating..maxRating))
        }

    val startRating = (ratingSliderRange.start * 10).roundToInt() / 10f
    val endRating = (ratingSliderRange.endInclusive * 10).roundToInt() / 10f

    val startText = if (startRating == 0f) "Unrated" else "★ %.1f".format(startRating)
    val endText = "★ %.1f".format(endRating)

    val ratingDisplay =
        if (startRating == endRating) {
            if (startRating == 0f) "Unrated" else "★ %.1f".format(startRating)
        } else {
            "$startText – $endText"
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
    ) {
        Text(
            text = ratingDisplay,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        RangeSlider(
            value = ratingSliderRange,
            onValueChange = { ratingSliderRange = it },
            valueRange = minRating..maxRating,
            onValueChangeFinished = {
                if (ratingSliderRange != state.selectedCommunityRatingRange) {
                    onAction(SearchAction.SetCommunityRatingRange(ratingSliderRange))
                }
            },
        )
    }
}

/**
 * Range slider layout for selecting release year bounds.
 *
 * @param state Current search state containing selected year range.
 * @param onAction Callback to dispatch year range selection changes.
 */
@Composable
private fun YearRangeView(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
) {
    val minYearVal = state.availableYears.minOrNull() ?: 1900
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val maxYearVal = state.availableYears.maxOrNull() ?: currentYear

    val minYear = minYearVal.toFloat()
    val maxYear = if (maxYearVal <= minYearVal) minYear + 10f else maxYearVal.toFloat()

    val initialRange = state.selectedYearRange ?: (minYear..maxYear)

    var yearSliderRange by
        remember(state.selectedYearRange, minYear, maxYear) { mutableStateOf(initialRange) }

    val startYear = yearSliderRange.start.roundToInt()
    val endYear = yearSliderRange.endInclusive.roundToInt()

    val yearDisplay = if (startYear == endYear) "$startYear" else "$startYear – $endYear"

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacings.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
    ) {
        Text(
            text = yearDisplay,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        RangeSlider(
            value = yearSliderRange,
            onValueChange = { yearSliderRange = it },
            valueRange = minYear..maxYear,
            onValueChangeFinished = {
                val newRange =
                    if (startYear.toFloat() <= minYear && endYear.toFloat() >= maxYear) {
                        null
                    } else {
                        startYear.toFloat()..endYear.toFloat()
                    }
                if (newRange != state.selectedYearRange) {
                    onAction(SearchAction.SetYearRange(newRange))
                }
            },
        )
    }
}

private data class FilterOptionItem(
    val id: String,
    val title: String,
    val isSelected: Boolean,
)

/**
 * Searchable list displaying available filter option items.
 *
 * @param items List of filter options to present.
 * @param onToggle Callback triggered when an item option is toggled by ID.
 * @param showSearch Whether to render a search field above the list.
 * @param searchQuery Current search text query entered in filter search field.
 * @param onSearchQueryChange Callback when search query changes.
 * @param isMetadataLoading Whether metadata options are loading from the server.
 */
@Composable
private fun FilterCardList(
    items: List<FilterOptionItem>,
    onToggle: (String) -> Unit,
    showSearch: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    isMetadataLoading: Boolean = false,
) {
    val filteredList =
        remember(searchQuery, items, showSearch) {
            if (!showSearch || searchQuery.isBlank()) items
            else
                items.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                        it.id.contains(searchQuery, ignoreCase = true)
                }
        }

    if (isMetadataLoading) {
        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
    } else if (items.isEmpty()) {
        EmptyFilterOptionsView()
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(text = stringResource(CoreR.string.search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = CircleShape,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            ) {
                items(filteredList, key = { it.id }) { option ->
                    SelectableFilterCard(
                        title = option.title,
                        isSelected = option.isSelected,
                        onClick = { onToggle(option.id) },
                    )
                }
            }
        }
    }
}

private fun formatLanguageName(code: String): String {
    if (code.isBlank()) return code
    return try {
        val locale = Locale.forLanguageTag(code)
        val display = locale.getDisplayLanguage(Locale.getDefault())
        if (display.isNotBlank() && !display.equals(code, ignoreCase = true)) {
            display.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        } else {
            code.uppercase()
        }
    } catch (_: Exception) {
        code.uppercase()
    }
}

/**
 * Selectable card representing a single filter option item with check indicator.
 *
 * @param title Display title of the filter option.
 * @param isSelected Whether this option is currently selected.
 * @param onClick Callback when the card is clicked.
 * @param modifier Modifier to be applied to the layout.
 */
@Composable
private fun SelectableFilterCard(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        }
    val borderColor =
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        }
    val contentColor =
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border =
            if (isSelected) BorderStroke(1.5.dp, borderColor) else BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isSelected) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Spacer(modifier = Modifier.size(20.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Empty view displayed when no filter options exist or while metadata loads. */
@Composable
private fun EmptyFilterOptionsView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(CoreR.string.no_options_available),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class CategoryUiConfig(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int? = null,
    val showSearch: Boolean = false,
    val options: (@Composable (SearchState) -> List<FilterOptionItem>)? = null,
    val onToggle: ((String, (SearchAction) -> Unit) -> Unit)? = null,
)

private fun stringSetCategoryConfig(
    @StringRes labelRes: Int,
    getAvailable: (SearchState) -> List<String>,
    getSelected: (SearchState) -> Set<String>,
    createAction: (String) -> SearchAction,
    @DrawableRes iconRes: Int? = null,
    formatTitle: ((String) -> String)? = null,
) =
    CategoryUiConfig(
        labelRes = labelRes,
        iconRes = iconRes,
        showSearch = true,
        options = { state ->
            getAvailable(state).map { item ->
                FilterOptionItem(
                    id = item,
                    title = formatTitle?.invoke(item) ?: item,
                    isSelected = getSelected(state).contains(item),
                )
            }
        },
        onToggle = { id, onAction -> onAction(createAction(id)) },
    )

private val CategoryUiConfigs: Map<FilterCategory, CategoryUiConfig> =
    mapOf(
        FilterCategory.PLAY_STATE to
            CategoryUiConfig(
                labelRes = CoreR.string.filter_play_state,
                options = { state ->
                    state.availableItemFilters.map { filter ->
                        FilterOptionItem(
                            id = filter.serialName,
                            title =
                                ItemFilterLabelMap[filter]?.let { stringResource(it) }
                                    ?: filter.serialName,
                            isSelected = state.selectedItemFilters.contains(filter),
                        )
                    }
                },
                onToggle = { id, onAction ->
                    ItemFilter.fromNameOrNull(id)?.let {
                        onAction(SearchAction.ToggleItemFilter(it))
                    }
                },
            ),
        FilterCategory.SERIES_STATUS to
            CategoryUiConfig(
                labelRes = CoreR.string.filter_series_status,
                options = { state ->
                    state.availableSeriesStatuses.map { status ->
                        FilterOptionItem(
                            id = status.serialName,
                            title =
                                SeriesStatusLabelMap[status]?.let { stringResource(it) }
                                    ?: status.serialName,
                            isSelected = state.selectedSeriesStatuses.contains(status),
                        )
                    }
                },
                onToggle = { id, onAction ->
                    SeriesStatus.fromNameOrNull(id)?.let {
                        onAction(SearchAction.ToggleSeriesStatus(it))
                    }
                },
            ),
        FilterCategory.VIDEO_TYPE to
            CategoryUiConfig(
                labelRes = CoreR.string.filter_video_type,
                options = { state ->
                    state.availableContentTypes.map { kind ->
                        FilterOptionItem(
                            id = kind.serialName,
                            title =
                                ItemKindLabelMap[kind]?.let { stringResource(it) }
                                    ?: kind.serialName,
                            isSelected = state.selectedContentTypes.contains(kind),
                        )
                    }
                },
                onToggle = { id, onAction ->
                    BaseItemKind.fromNameOrNull(id)?.let {
                        onAction(SearchAction.ToggleContentType(it))
                    }
                },
            ),
        FilterCategory.VIDEO_QUALITY to
            CategoryUiConfig(
                labelRes = CoreR.string.filter_video_quality,
                iconRes = CoreR.drawable.ic_hd,
                options = { state ->
                    state.availableVideoQualities.map { quality ->
                        FilterOptionItem(
                            id = quality.name,
                            title = quality.displayName,
                            isSelected = state.selectedVideoQualities.contains(quality),
                        )
                    }
                },
                onToggle = { id, onAction ->
                    runCatching { VideoQuality.valueOf(id) }
                        .getOrNull()
                        ?.let { onAction(SearchAction.ToggleVideoQuality(it)) }
                },
            ),
        FilterCategory.FEATURES to
            CategoryUiConfig(
                labelRes = CoreR.string.filter_features,
                iconRes = CoreR.drawable.ic_sparkles,
                options = { state ->
                    state.availableFeatures.map { feature ->
                        FilterOptionItem(
                            id = feature.name,
                            title = stringResource(feature.labelRes),
                            isSelected = state.selectedFeatures.contains(feature),
                        )
                    }
                },
                onToggle = { id, onAction ->
                    runCatching { FeatureFlag.valueOf(id) }
                        .getOrNull()
                        ?.let { onAction(SearchAction.ToggleFeature(it)) }
                },
            ),
        FilterCategory.AUDIO_LANGUAGES to
            stringSetCategoryConfig(
                labelRes = CoreR.string.filter_audio_languages,
                iconRes = CoreR.drawable.ic_audio,
                getAvailable = { it.availableAudioLanguages },
                getSelected = { it.selectedAudioLanguages },
                createAction = SearchAction::ToggleAudioLanguage,
                formatTitle = ::formatLanguageName,
            ),
        FilterCategory.SUBTITLE_LANGUAGES to
            stringSetCategoryConfig(
                labelRes = CoreR.string.filter_subtitle_languages,
                iconRes = CoreR.drawable.ic_closed_caption,
                getAvailable = { it.availableSubtitleLanguages },
                getSelected = { it.selectedSubtitleLanguages },
                createAction = SearchAction::ToggleSubtitleLanguage,
                formatTitle = ::formatLanguageName,
            ),
        FilterCategory.GENRES to
            stringSetCategoryConfig(
                labelRes = CoreR.string.genres,
                getAvailable = { it.availableGenres },
                getSelected = { it.selectedGenres },
                createAction = SearchAction::ToggleGenre,
            ),
        FilterCategory.PARENTAL_RATINGS to
            stringSetCategoryConfig(
                labelRes = CoreR.string.filter_parental_ratings,
                iconRes = CoreR.drawable.ic_id_card,
                getAvailable = { it.availableRatings },
                getSelected = { it.selectedRatings },
                createAction = SearchAction::ToggleRating,
            ),
        FilterCategory.TAGS to
            stringSetCategoryConfig(
                labelRes = CoreR.string.filter_tags,
                iconRes = CoreR.drawable.ic_tag,
                getAvailable = { it.availableTags },
                getSelected = { it.selectedTags },
                createAction = SearchAction::ToggleTag,
            ),
        FilterCategory.YEARS to
            CategoryUiConfig(
                labelRes = CoreR.string.filter_years,
                iconRes = CoreR.drawable.ic_calendar,
            ),
        FilterCategory.COMMUNITY_RATING to
            CategoryUiConfig(
                labelRes = CoreR.string.filter_community_rating,
                iconRes = CoreR.drawable.ic_star,
            ),
        FilterCategory.STUDIOS to
            stringSetCategoryConfig(
                labelRes = CoreR.string.filter_studios,
                iconRes = CoreR.drawable.ic_building_complex,
                getAvailable = { it.availableStudios },
                getSelected = { it.selectedStudios },
                createAction = SearchAction::ToggleStudio,
            ),
    )

private val FilterCategory.config: CategoryUiConfig
    get() = CategoryUiConfigs.getValue(this)

/**
 * Returns localized category label string for a given [FilterCategory].
 *
 * @param category Target filter category.
 */
@Composable
fun getCategoryLabel(category: FilterCategory): String = stringResource(category.config.labelRes)

/**
 * Returns category icon resource ID for a given [FilterCategory].
 *
 * @param category Target filter category.
 */
fun getCategoryIcon(category: FilterCategory): Int =
    category.config.iconRes ?: CoreR.drawable.ic_funnel

private val ItemKindLabelMap: Map<BaseItemKind, Int> =
    mapOf(
        BaseItemKind.MOVIE to CoreR.string.movies_label,
        BaseItemKind.SERIES to CoreR.string.shows_label,
        BaseItemKind.EPISODE to CoreR.string.episodes_label,
        BaseItemKind.SEASON to CoreR.string.seasons_label,
        BaseItemKind.BOX_SET to CoreR.string.collections_label,
        BaseItemKind.TRAILER to CoreR.string.trailers_label,
        BaseItemKind.VIDEO to CoreR.string.videos_label,
        BaseItemKind.MUSIC_VIDEO to CoreR.string.music_videos_label,
        BaseItemKind.MUSIC_ALBUM to CoreR.string.albums_label,
        BaseItemKind.MUSIC_ARTIST to CoreR.string.artists_label,
        BaseItemKind.AUDIO to CoreR.string.tracks_label,
        BaseItemKind.PLAYLIST to CoreR.string.playlists_label,
        BaseItemKind.BOOK to CoreR.string.books_label,
        BaseItemKind.PHOTO to CoreR.string.photos_label,
    )

private val ItemFilterLabelMap: Map<ItemFilter, Int> =
    mapOf(
        ItemFilter.IS_UNPLAYED to CoreR.string.filter_unplayed,
        ItemFilter.IS_PLAYED to CoreR.string.filter_played,
        ItemFilter.IS_FAVORITE to CoreR.string.filter_favorite,
        ItemFilter.IS_RESUMABLE to CoreR.string.filter_resumable,
    )

private val SeriesStatusLabelMap: Map<SeriesStatus, Int> =
    mapOf(
        SeriesStatus.CONTINUING to CoreR.string.series_status_continuing,
        SeriesStatus.ENDED to CoreR.string.series_status_ended,
        SeriesStatus.UNRELEASED to CoreR.string.series_status_unreleased,
    )

@Preview(showBackground = true)
@Composable
private fun SelectableFilterCardPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableFilterCard(
                title = "Action",
                isSelected = true,
                onClick = {},
            )
            SelectableFilterCard(
                title = "Comedy",
                isSelected = false,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CommunityRatingRangeViewPreview() {
    FindroidTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            CommunityRatingRangeView(
                state = SearchState(selectedCommunityRatingRange = 7.0f..9.5f),
                onAction = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun YearRangeViewPreview() {
    FindroidTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            YearRangeView(
                state =
                    SearchState(
                        selectedYearRange = 2006f..2013f,
                        availableYears = (1990..2026).toList(),
                    ),
                onAction = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyFilterOptionsViewPreview() {
    FindroidTheme { Surface(modifier = Modifier.height(100.dp)) { EmptyFilterOptionsView() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun FilterBottomSheetPreview() {
    FindroidTheme {
        FilterBottomSheet(
            state =
                SearchState(
                    isFilterSheetOpen = true,
                    activeBottomSheetCategory = FilterCategory.VIDEO_TYPE,
                    availableContentTypes =
                        listOf(
                            BaseItemKind.MOVIE,
                            BaseItemKind.SERIES,
                            BaseItemKind.EPISODE,
                        ),
                ),
            onAction = {},
            onDismissRequest = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FilterCardListPreview() {
    FindroidTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            FilterCardList(
                items =
                    listOf(
                        FilterOptionItem("1", "4K Ultra HD", true),
                        FilterOptionItem("2", "1080p Full HD", false),
                        FilterOptionItem("3", "720p HD", false),
                    ),
                onToggle = {},
                showSearch = true,
            )
        }
    }
}
