package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeView
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.View
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun HomeView(
    view: HomeItem.ViewItem,
    itemsPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    showLogos: Boolean = false,
    firstItemFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    navigateToLibrary:
        ((libraryId: UUID, libraryName: String, libraryType: CollectionType) -> Unit)? =
        null,
    onItemFocused: ((FindroidItem) -> Unit)? = null,
    onViewFocused: ((View) -> Unit)? = null,
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val itemWidth = 110.dp
    val startPadding = MaterialTheme.spacings.large
    val trailingSpace = maxOf(0.dp, screenWidth - itemWidth - startPadding)

    var scrollJob by remember { mutableStateOf<Job?>(null) }

    Column(modifier = modifier) {
        Text(
            text = stringResource(id = FilmR.string.latest_library, view.view.name),
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
            itemsIndexed(view.view.items.distinctBy { it.id }, key = { _, it -> it.id }) {
                index,
                item ->
                ItemCard(
                    item = item,
                    direction = Direction.VERTICAL,
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
            if (navigateToLibrary != null) {
                item(key = "view_all_${view.view.id}") {
                    ViewAllCard(
                        view = view.view,
                        itemWidth = itemWidth,
                        upFocusRequester = upFocusRequester,
                        onClick = {
                            navigateToLibrary(view.view.id, view.view.name, view.view.type)
                        },
                        onFocus = { isFocused ->
                            if (isFocused) {
                                onViewFocused?.invoke(view.view)
                                val wasScrolling =
                                    scrollJob?.isActive == true || lazyListState.isScrollInProgress
                                scrollJob?.cancel()
                                scrollJob = coroutineScope.launch {
                                    val visibleItem =
                                        lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
                                            it.index == view.view.items.size
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
                                                        durationMillis =
                                                            if (wasScrolling) 140 else 200,
                                                        easing = FastOutSlowInEasing,
                                                    ),
                                            )
                                        }
                                    } else {
                                        lazyListState.animateScrollToItem(
                                            index = view.view.items.size,
                                            scrollOffset = 0,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
            }
            if (view.view.items.isNotEmpty()) {
                item(key = "trailing_space") { Spacer(modifier = Modifier.width(trailingSpace)) }
            }
        }
    }
}

@Composable
private fun ViewAllCard(
    view: View,
    itemWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    val height = itemWidth * 1.5f
    var isFocused by remember { mutableStateOf(false) }

    val label =
        when (view.type) {
            CollectionType.Movies -> stringResource(FilmR.string.go_to_movies)
            CollectionType.TvShows -> stringResource(FilmR.string.go_to_shows)
            else -> stringResource(FilmR.string.go_to_library, view.name)
        }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(BorderStroke(2.5.dp, Color.White), shape = RoundedCornerShape(10.dp))
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        glow =
            ClickableSurfaceDefaults.glow(
                focusedGlow =
                    Glow(
                        elevationColor = Color.White.copy(alpha = 0.3f),
                        elevation = 10.dp,
                    )
            ),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                focusedContainerColor = Color.White.copy(alpha = 0.18f),
            ),
        modifier =
            modifier
                .width(itemWidth)
                .height(height)
                .let { mod ->
                    if (upFocusRequester != null) mod.focusProperties { up = upFocusRequester }
                    else mod
                }
                .onFocusChanged {
                    if (isFocused != it.isFocused) {
                        isFocused = it.isFocused
                        onFocus?.invoke(it.isFocused)
                    }
                },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(36.dp)
                        .background(
                            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
                            shape = CircleShape,
                        ),
            ) {
                Icon(
                    painter = painterResource(id = CoreR.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = if (isFocused) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun HomeViewPreview() {
    FindroidTheme {
        HomeView(
            view = dummyHomeView,
            itemsPadding = PaddingValues(horizontal = 24.dp),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun ViewAllCardPreview() {
    FindroidTheme {
        ViewAllCard(
            view = dummyHomeView.view,
            itemWidth = 110.dp,
            onClick = {},
        )
    }
}
