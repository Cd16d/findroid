package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * Horizontal row component rendering a media library view section with a title and view-all button.
 *
 * @param view HomeItem.ViewItem model representing library view items.
 * @param itemsPadding Padding applied around row items.
 * @param onAction Callback invoked for view navigation or item selection actions.
 * @param modifier Optional modifier applied to column layout. Defaults to [Modifier].
 * @param hideEpisodeSpoilers Whether episode thumbnails should be blurred. Defaults to `false`.
 */
@Composable
fun HomeView(
    view: HomeItem.ViewItem,
    itemsPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    hideEpisodeSpoilers: Boolean = false,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(42.dp).padding(itemsPadding)) {
            Text(
                text = stringResource(FilmR.string.latest_library, view.view.name),
                modifier = Modifier.align(Alignment.CenterStart),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                onClick = {
                    onAction(
                        HomeAction.OnLibraryClick(
                            FindroidCollection(
                                id = view.view.id,
                                name = view.view.name,
                                images = FindroidImages(),
                                type = view.view.type,
                            )
                        )
                    )
                },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_arrow_right),
                    contentDescription = null,
                )
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        LazyRow(
            contentPadding = itemsPadding,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        ) {
            items(view.view.items.distinctBy { it.id }, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    direction = Direction.VERTICAL,
                    onClick = { onAction(HomeAction.OnItemClick(item)) },
                    hideEpisodeSpoilers = hideEpisodeSpoilers,
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun HomeViewPreview() {
    dev.jdtech.jellyfin.presentation.theme.FindroidTheme {
        HomeView(
            view =
                HomeItem.ViewItem(
                    view =
                        dev.jdtech.jellyfin.models.View(
                            id = java.util.UUID.randomUUID(),
                            name = "Movies",
                            items = listOf(dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie),
                            type = dev.jdtech.jellyfin.models.CollectionType.Movies,
                        )
                ),
            itemsPadding = PaddingValues(16.dp),
            onAction = {},
        )
    }
}
