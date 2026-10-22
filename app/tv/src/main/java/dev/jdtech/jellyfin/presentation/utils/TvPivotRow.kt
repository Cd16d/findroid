package dev.jdtech.jellyfin.presentation.utils

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

val TvHorizontalKeyline = 48.dp

val NoOpBringIntoViewSpec =
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float = 0f
    }

/**
 * Controller managing focus state and keyline pivot scrolling for horizontal TV row lists.
 * Coordinates smooth scrolling of items to the TV horizontal keyline when items gain focus.
 *
 * @param lazyListState [LazyListState] instance managing list scroll position.
 * @param scope [CoroutineScope] used to launch animated scroll coroutines on focus shifts.
 */
@Stable
class TvPivotRowController(
    val lazyListState: LazyListState,
    private val scope: CoroutineScope,
) {
    var isRowFocused by mutableStateOf(false)

    fun onItemFocused(index: Int) {
        val wasScrolling = lazyListState.isScrollInProgress
        // LazyListState's internal MutatorMutex automatically cancels any previous in-flight scroll
        scope.launch { lazyListState.scrollItemToPivot(index = index, wasScrolling = wasScrolling) }
    }
}

/**
 * Creates and remembers a [TvPivotRowController] instance across recompositions.
 *
 * @param lazyListState LazyListState instance managing pivot list scrolling.
 */
@Composable
fun rememberTvPivotRowController(
    lazyListState: LazyListState = rememberLazyListState()
): TvPivotRowController {
    val scope = rememberCoroutineScope()
    return remember(lazyListState, scope) { TvPivotRowController(lazyListState, scope) }
}

@Composable
fun rememberTvTrailingSpace(itemWidth: Dp, startPadding: Dp = TvHorizontalKeyline): Dp {
    val screenWidth = LocalWindowInfo.current.containerDpSize.width
    return maxOf(0.dp, screenWidth - itemWidth - startPadding)
}

suspend fun LazyListState.scrollItemToPivot(
    index: Int,
    wasScrolling: Boolean = isScrollInProgress,
) {
    val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (visibleItem != null) {
        // visibleItem.offset is already relative to beforeContentPadding (0 == startPadding)
        val delta = visibleItem.offset.toFloat()
        if (abs(delta) > 0.5f) {
            animateScrollBy(
                value = delta,
                animationSpec =
                    tween(
                        durationMillis = if (wasScrolling) 75 else 190,
                        easing = if (wasScrolling) LinearEasing else FastOutSlowInEasing,
                    ),
            )
        }
    } else {
        animateScrollToItem(index = index, scrollOffset = 0)
    }
}

/**
 * TV list item wrapper box ensuring focused item z-index depth elevation.
 *
 * @param index Item index in list row.
 * @param controller TvPivotRowController managing pivot scrolling.
 * @param isItemFocused Whether item currently holds focus.
 * @param modifier Optional modifier applied to item container box. Defaults to [Modifier].
 * @param transformOrigin TransformOrigin for focus scale animation. Defaults to
 *   `TransformOrigin(0f, 0.5f)`.
 * @param content Composable item content inside pivot row box.
 */
@Composable
fun TvPivotItem(
    index: Int,
    controller: TvPivotRowController,
    isItemFocused: Boolean,
    modifier: Modifier = Modifier,
    transformOrigin: TransformOrigin = TransformOrigin(0f, 0.5f),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.zIndex(if (isItemFocused) 1f else 0f)) { content() }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun TvPivotItemPreview() {
    FindroidTheme {
        TvPivotItem(
            index = 0,
            controller = rememberTvPivotRowController(),
            isItemFocused = true,
        ) {
            Text("Pivot Item")
        }
    }
}
