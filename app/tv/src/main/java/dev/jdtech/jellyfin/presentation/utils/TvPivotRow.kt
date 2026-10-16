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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
