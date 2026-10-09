package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import kotlinx.coroutines.launch

@Composable
fun HomeView(
    view: HomeItem.ViewItem,
    itemsPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    showLogos: Boolean = false,
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val itemWidth = 150.dp
    val startPadding = MaterialTheme.spacings.large
    val trailingSpace = maxOf(0.dp, screenWidth - itemWidth - startPadding)

    Column(modifier = modifier) {
        Text(
            text = stringResource(id = FilmR.string.latest_library, view.view.name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(itemsPadding),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
        LazyRow(
            state = lazyListState,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            contentPadding =
                PaddingValues(
                    start = startPadding,
                    end = 0.dp,
                    top = 16.dp,
                    bottom = 16.dp,
                ),
        ) {
            itemsIndexed(view.view.items.distinctBy { it.id }, key = { _, it -> it.id }) {
                index,
                item ->
                ItemCard(
                    item = item,
                    direction = Direction.VERTICAL,
                    showLogos = showLogos,
                    onClick = { onAction(HomeAction.OnItemClick(it)) },
                    onFocus = { isFocused ->
                        if (isFocused) {
                            coroutineScope.launch { lazyListState.animateScrollToItem(index) }
                        }
                    },
                )
            }
            if (view.view.items.isNotEmpty()) {
                item(key = "trailing_space") { Spacer(modifier = Modifier.width(trailingSpace)) }
            }
        }
    }
}
