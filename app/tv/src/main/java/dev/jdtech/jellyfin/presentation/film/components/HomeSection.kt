package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
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
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.components.Direction
import dev.jdtech.jellyfin.presentation.components.ItemCard
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.TvHorizontalKeyline
import dev.jdtech.jellyfin.presentation.utils.TvPivotItem
import dev.jdtech.jellyfin.presentation.utils.rememberTvPivotRowController
import dev.jdtech.jellyfin.presentation.utils.rememberTvTrailingSpace

@Composable
fun HomeSection(
    title: String,
    items: List<FindroidItem>,
    modifier: Modifier = Modifier,
    direction: Direction = Direction.HORIZONTAL,
    itemWidth: Dp = if (direction == Direction.HORIZONTAL) 160.dp else 96.dp,
    rowKey: Any = title,
    itemsPadding: PaddingValues = PaddingValues(horizontal = TvHorizontalKeyline),
    firstItemFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    viewAllLabel: String? = null,
    onViewAllClick: (() -> Unit)? = null,
    onViewAllFocus: (() -> Unit)? = null,
    onItemFocused: ((FindroidItem) -> Unit)? = null,
    onAction: (HomeAction) -> Unit,
    hideEpisodeSpoilers: Boolean = false,
) {
    val pivotController = rememberTvPivotRowController()
    val layoutDirection = LocalLayoutDirection.current
    val distinctItems = remember(items) { items.distinctBy { it.id } }
    val hasViewAll = onViewAllClick != null && viewAllLabel != null
    val totalFocusableCount = distinctItems.size + if (hasViewAll) 1 else 0

    val startPadding =
        maxOf(itemsPadding.calculateStartPadding(layoutDirection), TvHorizontalKeyline)
    val endPadding = itemsPadding.calculateEndPadding(layoutDirection)
    val trailingSpace = rememberTvTrailingSpace(itemWidth = itemWidth, startPadding = startPadding)

    var lastFocusedIndex by rememberSaveable(rowKey) { mutableIntStateOf(0) }
    val targetRequesterIndex =
        lastFocusedIndex.coerceIn(0, (totalFocusableCount - 1).coerceAtLeast(0))

    Column(modifier = modifier.onFocusChanged { pivotController.isRowFocused = it.hasFocus }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = startPadding, end = endPadding),
        )
        LazyRow(
            state = pivotController.lazyListState,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            contentPadding =
                PaddingValues(
                    start = startPadding,
                    top = MaterialTheme.spacings.medium,
                    bottom = MaterialTheme.spacings.medium,
                ),
        ) {
            itemsIndexed(distinctItems, key = { _, it -> it.id }) { index, item ->
                var isItemFocused by remember { mutableStateOf(false) }
                val isFirstItem = index == 0
                val isLastItem = !hasViewAll && index == distinctItems.size - 1

                TvPivotItem(
                    index = index,
                    controller = pivotController,
                    isItemFocused = isItemFocused,
                ) {
                    ItemCard(
                        item = item,
                        direction = direction,
                        focusRequester =
                            if (index == targetRequesterIndex) firstItemFocusRequester else null,
                        upFocusRequester = upFocusRequester,
                        onClick = { onAction(HomeAction.OnItemClick(it)) },
                        onFocus = { isFocused ->
                            isItemFocused = isFocused
                            if (isFocused) {
                                lastFocusedIndex = index
                                onItemFocused?.invoke(item)
                                pivotController.onItemFocused(index)
                            }
                        },
                        modifier =
                            Modifier.onPreviewKeyEvent { keyEvent ->
                                (isFirstItem && keyEvent.key == Key.DirectionLeft) ||
                                    (isLastItem && keyEvent.key == Key.DirectionRight)
                            },
                        hideEpisodeSpoilers = hideEpisodeSpoilers,
                    )
                }
            }
            if (onViewAllClick != null && viewAllLabel != null) {
                val viewAllIndex = distinctItems.size
                item(key = "view_all_${rowKey}") {
                    var isViewAllFocused by remember { mutableStateOf(false) }

                    TvPivotItem(
                        index = viewAllIndex,
                        controller = pivotController,
                        isItemFocused = isViewAllFocused,
                    ) {
                        ViewAllCard(
                            label = viewAllLabel,
                            itemWidth = itemWidth,
                            focusRequester =
                                if (viewAllIndex == targetRequesterIndex) firstItemFocusRequester
                                else null,
                            upFocusRequester = upFocusRequester,
                            onClick = onViewAllClick,
                            onFocus = { isFocused ->
                                isViewAllFocused = isFocused
                                if (isFocused) {
                                    lastFocusedIndex = viewAllIndex
                                    onViewAllFocus?.invoke()
                                    pivotController.onItemFocused(viewAllIndex)
                                }
                            },
                            modifier =
                                Modifier.onPreviewKeyEvent { keyEvent ->
                                    keyEvent.key == Key.DirectionRight
                                },
                        )
                    }
                }
            }
            if (distinctItems.isNotEmpty()) {
                item(key = "trailing_space") { Spacer(modifier = Modifier.width(trailingSpace)) }
            }
        }
    }
}

@Composable
private fun ViewAllCard(
    label: String,
    itemWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(BorderStroke(2.5.dp, Color.White), shape = RoundedCornerShape(10.dp))
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
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
                .height(itemWidth * 1.5f)
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                )
                .then(
                    if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester }
                    else Modifier
                )
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
private fun HomeSectionPreview() {
    FindroidTheme {
        HomeSection(
            title = dummyHomeSection.homeSection.name.asString(),
            items = dummyHomeSection.homeSection.items,
            onAction = {},
        )
    }
}
