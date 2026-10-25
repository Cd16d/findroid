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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.UUID

/**
 * Horizontal row component rendering a home screen section with a title header.
 *
 * @param section HomeSection model containing section title and items.
 * @param itemsPadding Padding applied around row items.
 * @param onAction Callback invoked when an item in the section is interacted with.
 * @param modifier Optional modifier applied to column layout. Defaults to [Modifier].
 * @param hideEpisodeSpoilers Whether episode thumbnails/overviews should be blurred. Defaults to
 *   `false`.
 */
@Composable
fun HomeSection(
    section: HomeSection,
    itemsPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    hideEpisodeSpoilers: Boolean = false,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().height(42.dp).padding(itemsPadding)) {
            Text(
                text = section.name.asString(),
                modifier = Modifier.align(Alignment.CenterStart),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        LazyRow(
            contentPadding = itemsPadding,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        ) {
            items(section.items.distinctBy { it.id }, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    direction = Direction.HORIZONTAL,
                    onClick = { onAction(HomeAction.OnItemClick(item)) },
                    hideEpisodeSpoilers = hideEpisodeSpoilers,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeSectionPreview() {
    FindroidTheme {
        HomeSection(
            section =
                HomeSection(
                    id = UUID.randomUUID(),
                    name = UiText.DynamicString("Continue Watching"),
                    items = listOf(dummyMovie),
                ),
            itemsPadding = PaddingValues(16.dp),
            onAction = {},
        )
    }
}
