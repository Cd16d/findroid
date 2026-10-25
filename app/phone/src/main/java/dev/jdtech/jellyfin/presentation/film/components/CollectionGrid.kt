package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.LocalCastPlayerHeight
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.presentation.collection.CollectionAction
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns

/**
 * Vertical grid displaying media collection sections with sticky header title cards.
 *
 * @param sections List of collection sections containing media items.
 * @param innerPadding PaddingValues applied around grid layout.
 * @param onAction Callback invoked for collection grid user actions.
 */
@Composable
fun CollectionGrid(
    sections: List<CollectionSection>,
    innerPadding: PaddingValues,
    onAction: (CollectionAction) -> Unit,
) {
    val castPadding = LocalCastPlayerHeight.current
    val layoutDirection = LocalLayoutDirection.current

    LazyVerticalGrid(
        columns = GridCellsAdaptiveWithMinColumns(minSize = 160.dp, minColumns = 2),
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start =
                    innerPadding.calculateStartPadding(layoutDirection) +
                        MaterialTheme.spacings.default,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacings.default,
                end =
                    innerPadding.calculateEndPadding(layoutDirection) +
                        MaterialTheme.spacings.default,
                bottom = innerPadding.calculateBottomPadding() + castPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
    ) {
        sections.forEach { section ->
            stickyHeader {
                Card {
                    Text(
                        text = section.name.asString(),
                        modifier =
                            Modifier.padding(
                                horizontal = MaterialTheme.spacings.medium,
                                vertical = MaterialTheme.spacings.medium,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            items(
                items = section.items.distinctBy { it.id },
                key = { "${section.id}_${it.id}" },
            ) { item ->
                ItemCard(
                    item = item,
                    direction =
                        if (item is FindroidEpisode) Direction.HORIZONTAL else Direction.VERTICAL,
                    onClick = { onAction(CollectionAction.OnItemClick(item)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CollectionGridPreview() {
    FindroidTheme {
        CollectionGrid(
            sections =
                listOf(
                    CollectionSection(
                        id = 1,
                        name = UiText.DynamicString("Movies"),
                        items = listOf(dummyMovie),
                    )
                ),
            innerPadding = PaddingValues(0.dp),
            onAction = {},
        )
    }
}
