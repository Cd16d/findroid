package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.collection.CollectionAction
import dev.jdtech.jellyfin.film.presentation.collection.CollectionState
import dev.jdtech.jellyfin.film.presentation.favorites.FavoritesViewModel
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.film.components.CollectionGrid
import dev.jdtech.jellyfin.presentation.film.components.PlaceholderScreen
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun FavoritesScreen(
    onItemClick: (item: FindroidItem) -> Unit,
    navigateBack: () -> Unit,
    onExploreLibraryClick: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadItems() }

    FavoritesScreenLayout(
        state = state,
        onExploreLibraryClick = onExploreLibraryClick,
        onAction = { action ->
            when (action) {
                is CollectionAction.OnItemClick -> onItemClick(action.item)
                is CollectionAction.OnBackClick -> navigateBack()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesScreenLayout(
    state: CollectionState,
    onExploreLibraryClick: () -> Unit,
    onAction: (CollectionAction) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier =
            Modifier.fillMaxSize()
                .recalculateWindowInsets()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.title_favorite)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(CollectionAction.OnBackClick) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        PlaceholderScreen(
            title = stringResource(CoreR.string.no_favorites),
            buttonText = stringResource(CoreR.string.explore_library),
            onButtonClick = onExploreLibraryClick,
            isEmpty = state.sections.isEmpty(),
            modifier = Modifier.padding(innerPadding),
        ) {
            CollectionGrid(
                sections = state.sections,
                innerPadding = innerPadding,
                onAction = onAction,
            )
        }
    }
}

@PreviewScreenSizes
@Preview(name = "Split Screen Portrait", widthDp = 411, heightDp = 350)
@Preview(name = "Split Screen Landscape", widthDp = 411, heightDp = 380)
@Composable
private fun FavoritesScreenLayoutEmptyPreview() {
    FindroidTheme {
        FavoritesScreenLayout(
            state = CollectionState(sections = emptyList()),
            onAction = {},
            onExploreLibraryClick = {},
        )
    }
}

@PreviewScreenSizes
@Composable
private fun FavoritesScreenLayoutPreview() {
    FindroidTheme {
        FavoritesScreenLayout(
            state =
                CollectionState(
                    sections =
                        listOf(
                            CollectionSection(
                                id = 0,
                                name = UiText.StringResource(CoreR.string.title_favorite),
                                items = dummyMovies,
                            )
                        )
                ),
            onAction = {},
            onExploreLibraryClick = {},
        )
    }
}
