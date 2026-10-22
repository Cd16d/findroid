package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovies
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * TV top hero carousel component displaying rotating media highlights.
 *
 * @param items List of media items displayed in carousel.
 * @param onDetails Callback executed to open item details screen.
 * @param modifier Optional modifier applied to carousel surface. Defaults to [Modifier].
 * @param isActive Whether carousel auto-rotation is active. Defaults to `true`.
 * @param showLogo Whether to display media logo image over backdrop. Defaults to `false`.
 * @param playFocusRequester Optional FocusRequester for carousel focus delegation.
 * @param upFocusRequester Optional FocusRequester directed on D-pad up navigation.
 * @param downFocusRequester Optional FocusRequester directed on D-pad down navigation.
 * @param onFocusChanged Optional callback triggered when carousel focus state changes.
 */
@Composable
fun HomeCarousel(
    items: List<FindroidItem>,
    onDetails: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    showLogo: Boolean = false,
    playFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    var activeIndex by rememberSaveable { mutableIntStateOf(0) }
    var isFocused by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(items) {
        if (activeIndex >= items.size && items.isNotEmpty()) {
            activeIndex = 0
        }
    }

    LaunchedEffect(isActive, activeIndex, items.size, lastInteractionTime) {
        if (isActive && items.size > 1) {
            delay(6000L.milliseconds)
            activeIndex = (activeIndex + 1) % items.size
        }
    }

    Surface(
        onClick = { onDetails(items.getOrElse(activeIndex) { items.first() }) },
        shape = ClickableSurfaceDefaults.shape(CentralShapes.LargeCard),
        border = ClickableSurfaceDefaults.border(Border.None),
        scale = ClickableSurfaceScale.None,
        glow = ClickableSurfaceDefaults.glow(Glow.None),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent,
            ),
        modifier =
            modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(CentralShapes.LargeCard)
                .then(
                    if (playFocusRequester != null) Modifier.focusRequester(playFocusRequester)
                    else Modifier
                )
                .then(
                    if (upFocusRequester != null || downFocusRequester != null) {
                        Modifier.focusProperties {
                            if (upFocusRequester != null) up = upFocusRequester
                            if (downFocusRequester != null) down = downFocusRequester
                        }
                    } else Modifier
                )
                .onFocusChanged {
                    if (isFocused != it.isFocused) {
                        isFocused = it.isFocused
                        lastInteractionTime = System.currentTimeMillis()
                        onFocusChanged?.invoke(it.isFocused)
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            lastInteractionTime = System.currentTimeMillis()
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    if (items.size > 1) {
                                        activeIndex = (activeIndex - 1 + items.size) % items.size
                                    }
                                    try {
                                        playFocusRequester?.requestFocus()
                                    } catch (_: Exception) {}
                                    true
                                }

                                Key.DirectionRight -> {
                                    if (items.size > 1) {
                                        activeIndex = (activeIndex + 1) % items.size
                                    }
                                    try {
                                        playFocusRequester?.requestFocus()
                                    } catch (_: Exception) {}
                                    true
                                }

                                else -> false
                            }
                        }
                        KeyEventType.KeyUp -> {
                            when (keyEvent.key) {
                                Key.DirectionLeft,
                                Key.DirectionRight -> true

                                else -> false
                            }
                        }
                        else -> {
                            false
                        }
                    }
                },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = activeIndex,
                transitionSpec = {
                    fadeIn(animationSpec = tween(350))
                        .togetherWith(fadeOut(animationSpec = tween(350)))
                },
                label = "carouselSlide",
            ) { targetIndex ->
                val currentItem = items.getOrElse(targetIndex) { items.first() }
                HomeCarouselItem(
                    item = currentItem,
                    showLogo = showLogo,
                    isFocused = isFocused,
                )
            }

            // Carousel indicators at bottom-right
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 18.dp),
            ) {
                repeat(items.size) { index ->
                    val isIndicatorActive = activeIndex == index
                    Box(
                        modifier =
                            Modifier.size(if (isIndicatorActive) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isIndicatorActive) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.4f)
                                )
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = false, device = "id:tv_1080p")
private fun HomeCarouselPreview() {
    FindroidTheme {
        HomeCarousel(
            items = dummyMovies,
            onDetails = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
