package dev.jdtech.jellyfin.presentation.film

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.LocalCastPlayerHeight
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchState
import dev.jdtech.jellyfin.film.presentation.search.SearchViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.presentation.film.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.FilterChipsRow
import dev.jdtech.jellyfin.presentation.film.components.ItemCard
import dev.jdtech.jellyfin.presentation.film.components.PlaceholderScreenContent
import dev.jdtech.jellyfin.presentation.film.search.components.FilterBottomSheet
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Main search screen using Material 3 Expressive contained search bar design and horizontal filter
 * chips with a Filter Bottom Sheet. Uses horizontal Paging 3 lazy loading per section.
 *
 * @param onItemClick Callback when a search result item is clicked.
 * @param viewModel ViewModel backing the search screen state. Defaults to [hiltViewModel].
 */
@Composable
fun SearchScreen(
    onItemClick: (FindroidItem) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(LocalContext.current as ViewModelStoreOwner),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val movies = viewModel.moviesPagingFlow.collectAsLazyPagingItems()
    val shows = viewModel.showsPagingFlow.collectAsLazyPagingItems()
    val episodes = viewModel.episodesPagingFlow.collectAsLazyPagingItems()

    SearchScreenLayout(
        state = state,
        movies = movies,
        shows = shows,
        episodes = episodes,
        focusRequests = viewModel.focusRequests,
        onAction = { action ->
            when (action) {
                is SearchAction.OnItemClick -> onItemClick(action.item)
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

/**
 * Internal layout for the search screen, rendering the top search header, chip row, paginated media
 * sections, and filter bottom sheet.
 *
 * @param state Current search state.
 * @param movies Paginated movies result set.
 * @param shows Paginated shows result set.
 * @param episodes Paginated episodes result set.
 * @param focusRequests Flow emitting focus requests on tab reselection.
 * @param onAction Callback to handle search actions.
 */
@Composable
private fun SearchScreenLayout(
    state: SearchState,
    movies: LazyPagingItems<FindroidMovie>,
    shows: LazyPagingItems<FindroidItem>,
    episodes: LazyPagingItems<FindroidEpisode>,
    focusRequests: SharedFlow<Unit>,
    onAction: (SearchAction) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val safePadding = rememberSafePadding(handleStartInsets = false, handleBottomInsets = false)
    val castPadding = LocalCastPlayerHeight.current

    val paddingStart = safePadding.start + MaterialTheme.spacings.default
    val paddingEnd = safePadding.end + MaterialTheme.spacings.default
    val paddingBottom = safePadding.bottom + castPadding + MaterialTheme.spacings.default

    val contentPaddingTop = safePadding.top + 130.dp
    val itemsPadding = PaddingValues(start = paddingStart, end = paddingEnd)

    var queryTextFieldValue by
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(state.query))
        }

    LaunchedEffect(state.query) {
        if (state.query.isEmpty() && queryTextFieldValue.text.isNotEmpty()) {
            queryTextFieldValue = TextFieldValue("")
        }
    }

    var displayedSuggestionHint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.searchSuggestions) {
        if (state.searchSuggestions.isNotEmpty()) {
            var currentIndex = 0
            while (true) {
                val targetText = state.searchSuggestions[currentIndex]

                for (i in 1..targetText.length) {
                    displayedSuggestionHint = targetText.substring(0, i)
                    delay(50.milliseconds)
                }

                delay(2000.milliseconds)

                for (i in targetText.length - 1 downTo 0) {
                    displayedSuggestionHint = targetText.substring(0, i)
                    delay(30.milliseconds)
                }

                currentIndex = (currentIndex + 1) % state.searchSuggestions.size
            }
        } else {
            displayedSuggestionHint = null
        }
    }

    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isWideScreen =
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    val onSurpriseMe = {
        onAction(
            SearchAction.OnRandomClick { randomItem ->
                onAction(SearchAction.OnItemClick(randomItem))
            }
        )
    }

    LaunchedEffect(focusRequests) {
        focusRequests.collect {
            focusRequester.requestFocus()
            queryTextFieldValue =
                queryTextFieldValue.copy(selection = TextRange(0, queryTextFieldValue.text.length))
        }
    }

    LaunchedEffect(queryTextFieldValue.text) {
        val newText = queryTextFieldValue.text
        if (newText != state.query) {
            if (newText.isNotBlank()) {
                delay(minOf(50L + (newText.count() * 50L), 300L).milliseconds)
            }
            onAction(SearchAction.Search(newText))
        }
    }

    val hasResults = movies.itemCount > 0 || shows.itemCount > 0 || episodes.itemCount > 0
    val isLoading =
        state.isSearchStarted &&
            (movies.loadState.refresh is LoadState.Loading ||
                shows.loadState.refresh is LoadState.Loading ||
                episodes.loadState.refresh is LoadState.Loading)

    Box(
        modifier =
            Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                )
            }
    ) {
        // Main content area
        AnimatedContent(
            targetState = state.isSearchStarted,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "search_content_transition",
        ) { isSearchStarted ->
            if (!isSearchStarted) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(top = contentPaddingTop, bottom = paddingBottom),
                    contentAlignment = Alignment.Center,
                ) {
                    PlaceholderScreenContent(
                        title = stringResource(FilmR.string.search_placeholder),
                        subtitle = null,
                        image = CoreR.drawable.search_page_placeholder,
                    )
                }
            } else if (!hasResults && !isLoading) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(top = contentPaddingTop, bottom = paddingBottom),
                    contentAlignment = Alignment.Center,
                ) {
                    PlaceholderScreenContent(
                        title = stringResource(CoreR.string.no_search_results),
                        subtitle = null,
                        image = CoreR.drawable.search_page_placeholder,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            top = contentPaddingTop,
                            bottom = paddingBottom,
                        ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                ) {
                    if (movies.itemCount > 0) {
                        item(key = "movies_section") {
                            Column {
                                SectionHeader(
                                    title = stringResource(CoreR.string.movies_label),
                                    modifier = Modifier.padding(itemsPadding),
                                )
                                Spacer(
                                    modifier = Modifier.height(MaterialTheme.spacings.extraSmall)
                                )
                                LazyRow(
                                    contentPadding = itemsPadding,
                                    horizontalArrangement =
                                        Arrangement.spacedBy(MaterialTheme.spacings.default),
                                ) {
                                    items(
                                        count = movies.itemCount,
                                        key = movies.itemKey { it.id },
                                    ) { index ->
                                        val movie = movies[index]
                                        movie?.let {
                                            ItemCard(
                                                item = movie,
                                                direction = Direction.VERTICAL,
                                                onClick = {
                                                    onAction(SearchAction.OnItemClick(movie))
                                                },
                                                hideEpisodeSpoilers = state.hideEpisodeSpoilers,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (shows.itemCount > 0) {
                        item(key = "shows_section") {
                            val isSeasonSection = isSeasonSection(shows, state)
                            Column {
                                SectionHeader(
                                    title =
                                        stringResource(
                                            if (isSeasonSection) CoreR.string.seasons_label
                                            else CoreR.string.shows_label
                                        ),
                                    modifier = Modifier.padding(itemsPadding),
                                )
                                Spacer(
                                    modifier = Modifier.height(MaterialTheme.spacings.extraSmall)
                                )
                                LazyRow(
                                    contentPadding = itemsPadding,
                                    horizontalArrangement =
                                        Arrangement.spacedBy(MaterialTheme.spacings.default),
                                ) {
                                    items(
                                        count = shows.itemCount,
                                        key = shows.itemKey { it.id },
                                    ) { index ->
                                        val show = shows[index]
                                        show?.let {
                                            ItemCard(
                                                item = show,
                                                direction = Direction.VERTICAL,
                                                onClick = {
                                                    onAction(SearchAction.OnItemClick(show))
                                                },
                                                hideEpisodeSpoilers = state.hideEpisodeSpoilers,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (episodes.itemCount > 0) {
                        item(key = "episodes_section") {
                            Column {
                                SectionHeader(
                                    title = stringResource(CoreR.string.episodes_label),
                                    modifier = Modifier.padding(itemsPadding),
                                )
                                Spacer(
                                    modifier = Modifier.height(MaterialTheme.spacings.extraSmall)
                                )
                                LazyRow(
                                    contentPadding = itemsPadding,
                                    horizontalArrangement =
                                        Arrangement.spacedBy(MaterialTheme.spacings.default),
                                ) {
                                    items(
                                        count = episodes.itemCount,
                                        key = episodes.itemKey { it.id },
                                    ) { index ->
                                        val episode = episodes[index]
                                        episode?.let {
                                            ItemCard(
                                                item = episode,
                                                direction = Direction.HORIZONTAL,
                                                onClick = {
                                                    onAction(SearchAction.OnItemClick(episode))
                                                },
                                                hideEpisodeSpoilers = state.hideEpisodeSpoilers,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Top Gradient Scrim Header (Search Bar + Filter Chips Row)
        Column(
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        MaterialTheme.colorScheme.surface,
                                        Color.Transparent,
                                    )
                            )
                    )
                    .padding(top = safePadding.top + MaterialTheme.spacings.small)
                    .padding(bottom = MaterialTheme.spacings.small)
        ) {
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = paddingStart),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContainedSearchBar(
                        value = queryTextFieldValue,
                        onValueChange = { queryTextFieldValue = it },
                        suggestionHint = displayedSuggestionHint,
                        loading = isLoading,
                        onClearQuery = {
                            queryTextFieldValue = TextFieldValue("")
                            onAction(SearchAction.Search(""))
                        },
                        showIntegratedSurpriseButton = false,
                        onSurpriseClick = onSurpriseMe,
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(1f, fill = false).widthIn(max = 640.dp),
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                    Surface(
                        onClick = onSurpriseMe,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_dices),
                                contentDescription = stringResource(CoreR.string.surprise_me),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            } else {
                ContainedSearchBar(
                    value = queryTextFieldValue,
                    onValueChange = { queryTextFieldValue = it },
                    suggestionHint = displayedSuggestionHint,
                    loading = isLoading,
                    onClearQuery = {
                        queryTextFieldValue = TextFieldValue("")
                        onAction(SearchAction.Search(""))
                    },
                    showIntegratedSurpriseButton = true,
                    onSurpriseClick = onSurpriseMe,
                    focusRequester = focusRequester,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = paddingStart),
                )
            }

            Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))

            FilterChipsRow(
                state = state,
                isWideScreen = isWideScreen,
                onOpenFilterCategory = { category ->
                    onAction(SearchAction.OpenFilterBottomSheet(category))
                },
                contentPadding = itemsPadding,
            )
        }

        // Filter Bottom Sheet
        if (state.isFilterSheetOpen) {
            FilterBottomSheet(
                state = state,
                onAction = onAction,
                onDismissRequest = { onAction(SearchAction.CloseFilterBottomSheet) },
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(42.dp)) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.CenterStart),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun isSeasonSection(
    shows: LazyPagingItems<FindroidItem>,
    state: SearchState,
): Boolean {
    val yearRange = state.selectedYearRange
    return (shows.itemCount > 0 && shows[0] is FindroidSeason) ||
        state.selectedContentTypes.contains(BaseItemKind.SEASON) ||
        (state.selectedContentTypes.isEmpty() &&
            (yearRange != null &&
                yearRange.start.roundToInt() == yearRange.endInclusive.roundToInt()))
}

/**
 * Contained search bar component with search icon, text field, clear button, and optional
 * integrated surprise me button.
 *
 * @param value Current text query as TextFieldValue.
 * @param onValueChange Callback when search query changes.
 * @param suggestionHint Dynamic suggestion hint to display when input is empty.
 * @param loading Whether search results are currently loading.
 * @param onClearQuery Callback to clear query text.
 * @param showIntegratedSurpriseButton Whether to show the surprise me dice button inside the bar.
 * @param onSurpriseClick Callback when surprise me button is clicked.
 * @param focusRequester FocusRequester for the text input field.
 * @param modifier Modifier to be applied to layout.
 */
@Composable
private fun ContainedSearchBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    suggestionHint: String?,
    loading: Boolean,
    onClearQuery: () -> Unit,
    showIntegratedSurpriseButton: Boolean,
    onSurpriseClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var isFocused by remember { mutableStateOf(false) }
    val borderAlpha by
        animateFloatAsState(
            targetValue = if (isFocused) 1f else 0f,
            label = "search_bar_border_alpha",
        )

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha)),
        modifier = modifier.height(56.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = MaterialTheme.spacings.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.text.isEmpty()) {
                    val placeholderText =
                        suggestionHint ?: stringResource(FilmR.string.search_placeholder)
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    modifier =
                        Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged {
                            isFocused = it.isFocused
                        },
                )
            }
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.extraSmall))
            }
            if (value.text.isNotEmpty()) {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_x),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (showIntegratedSurpriseButton) {
                IconButton(
                    onClick = onSurpriseClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_dices),
                        contentDescription = stringResource(CoreR.string.surprise_me),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContainedSearchBarPreview() {
    FindroidTheme {
        Surface(modifier = Modifier.padding(16.dp)) {
            ContainedSearchBar(
                value = TextFieldValue("Batman"),
                onValueChange = {},
                suggestionHint = "Inception",
                loading = false,
                onClearQuery = {},
                showIntegratedSurpriseButton = true,
                onSurpriseClick = {},
                focusRequester = remember { FocusRequester() },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    FindroidTheme {
        Surface {
            SectionHeader(
                title = "Movies",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@PreviewScreenSizes
@Composable
private fun SearchScreenLayoutPreview() {
    FindroidTheme {
        SearchScreenLayout(
            state = SearchState(availableGenres = listOf("Action", "Comedy")),
            movies =
                kotlinx.coroutines.flow
                    .flowOf(androidx.paging.PagingData.empty<FindroidMovie>())
                    .collectAsLazyPagingItems(),
            shows =
                kotlinx.coroutines.flow
                    .flowOf(androidx.paging.PagingData.empty<FindroidItem>())
                    .collectAsLazyPagingItems(),
            episodes =
                kotlinx.coroutines.flow
                    .flowOf(androidx.paging.PagingData.empty<FindroidEpisode>())
                    .collectAsLazyPagingItems(),
            focusRequests = kotlinx.coroutines.flow.MutableSharedFlow(),
            onAction = {},
        )
    }
}
