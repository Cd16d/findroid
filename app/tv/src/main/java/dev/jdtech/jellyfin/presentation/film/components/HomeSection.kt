package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun HomeSection(
    section: HomeSection,
    itemsPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    showLogos: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onItemFocused: ((FindroidItem) -> Unit)? = null,
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val itemWidth = 160.dp
    val startPadding = MaterialTheme.spacings.large
    val trailingSpace = maxOf(0.dp, screenWidth - itemWidth - startPadding)

    var scrollJob by remember { mutableStateOf<Job?>(null) }

    Column(modifier = modifier) {
        Text(
            text = section.name.asString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(itemsPadding),
        )
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            state = lazyListState,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            contentPadding =
                PaddingValues(
                    start = startPadding,
                    end = 0.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
        ) {
            itemsIndexed(section.items.distinctBy { it.id }, key = { _, it -> it.id }) { index, item
                ->
                ItemCard(
                    item = item,
                    direction = Direction.HORIZONTAL,
                    showLogos = showLogos,
                    focusRequester = if (index == 0) firstItemFocusRequester else null,
                    upFocusRequester = upFocusRequester,
                    onClick = { onAction(HomeAction.OnItemClick(it)) },
                    onFocus = { isFocused ->
                        if (isFocused) {
                            onItemFocused?.invoke(item)
                            val wasScrolling =
                                scrollJob?.isActive == true || lazyListState.isScrollInProgress
                            scrollJob?.cancel()
                            scrollJob = coroutineScope.launch {
                                val visibleItem =
                                    lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
                                        it.index == index
                                    }
                                if (visibleItem != null) {
                                    val beforePadding =
                                        lazyListState.layoutInfo.beforeContentPadding.toFloat()
                                    val delta = visibleItem.offset - beforePadding
                                    if (kotlin.math.abs(delta) > 2f) {
                                        lazyListState.animateScrollBy(
                                            value = delta,
                                            animationSpec =
                                                tween<Float>(
                                                    durationMillis = if (wasScrolling) 140 else 200,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                        )
                                    }
                                } else {
                                    lazyListState.animateScrollToItem(
                                        index = index,
                                        scrollOffset = 0,
                                    )
                                }
                            }
                        }
                    },
                )
            }
            if (section.items.isNotEmpty()) {
                item(key = "trailing_space") { Spacer(modifier = Modifier.width(trailingSpace)) }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun HomeSectionPreview() {
    FindroidTheme {
        HomeSection(
            section = dummyHomeSection.homeSection,
            itemsPadding = PaddingValues(horizontal = 24.dp),
            onAction = {},
        )
    }
}
