package dev.jdtech.jellyfin.presentation.film

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.film.presentation.collection.CollectionAction
import dev.jdtech.jellyfin.film.presentation.collection.CollectionState
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsViewModel
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.film.components.CollectionGrid
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.core.R as CoreR

@Composable
fun DownloadsScreen(
    onItemClick: (item: FindroidItem) -> Unit,
    onExploreLibraryClick: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadItems() }

    DownloadsScreenLayout(
        state = state,
        onLibraryClick = onExploreLibraryClick,
        onAction = { action ->
            when (action) {
                is CollectionAction.OnItemClick -> onItemClick(action.item)
                is CollectionAction.OnBackClick -> Unit
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreenLayout(
    state: CollectionState,
    onLibraryClick: () -> Unit,
    onAction: (CollectionAction) -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val isExpanded = windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
    )

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .recalculateWindowInsets(),
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        if (state.sections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isLandscape && !isExpanded) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Image(
                            painter = painterResource(CoreR.drawable.download_page_placeholder),
                            contentDescription = null,
                            modifier = Modifier.fillMaxHeight(),
                            contentScale = ContentScale.FillHeight,
                            alignment = Alignment.Center,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = MaterialTheme.spacings.default),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(CoreR.string.no_downloads_title),
                                style = MaterialTheme.typography.headlineLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                            Text(
                                text = stringResource(CoreR.string.no_downloads),
                                modifier = Modifier.padding(horizontal = MaterialTheme.spacings.default),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                            Button(
                                onClick = onLibraryClick
                            ) {
                                Text(
                                    text = stringResource(CoreR.string.no_downloads_button)
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = MaterialTheme.spacings.extraLarge),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(CoreR.string.no_downloads_title),
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                        Text(
                            text = stringResource(CoreR.string.no_downloads),
                            modifier = Modifier.padding(horizontal = MaterialTheme.spacings.default),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.spacings.large))
                        Image(
                            painter = painterResource(CoreR.drawable.download_page_placeholder),
                            contentDescription = null,
                            modifier = Modifier.then(
                                if (isExpanded) {
                                    Modifier.weight(1f)
                                } else {
                                    Modifier.fillMaxWidth()
                                },
                            ),
                            contentScale = if (isExpanded) {
                                ContentScale.FillHeight
                            } else {
                                ContentScale.FillWidth
                            },
                            alignment = Alignment.Center,
                        )
                        Spacer(modifier = Modifier.height(MaterialTheme.spacings.large))
                        Button(
                            onClick = onLibraryClick
                        ) {
                            Text(
                                text = stringResource(CoreR.string.no_downloads_button)
                            )
                        }
                    }
                }
            }
        } else {
            CollectionGrid(
                sections = state.sections,
                innerPadding = innerPadding,
                onAction = onAction
            )
        }
    }
}

@PreviewScreenSizes
@Composable
private fun DownloadsScreenLayoutEmptyPreview() {
    FindroidTheme {
        DownloadsScreenLayout(
            state =
                CollectionState(
                    sections = emptyList()
                ),
            onAction = {},
            onLibraryClick = {},
        )
    }
}

@PreviewScreenSizes
@Composable
private fun DownloadsScreenLayoutPreview() {
    FindroidTheme {
        DownloadsScreenLayout(
            state =
                CollectionState(
                    sections =
                        listOf(
                            CollectionSection(
                                id = 0,
                                name = UiText.StringResource(CoreR.string.movies_label),
                                items = dummyMovies,
                            )
                        )
                ),
            onAction = {},
            onLibraryClick = {},
        )
    }
}
