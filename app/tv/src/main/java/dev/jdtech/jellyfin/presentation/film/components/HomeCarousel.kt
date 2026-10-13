package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlinx.coroutines.delay

@Composable
fun HomeCarousel(
    items: List<FindroidItem>,
    onDetails: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
    showLogo: Boolean = false,
    playFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    var activeIndex by rememberSaveable { mutableIntStateOf(0) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(items) {
        if (activeIndex >= items.size && items.isNotEmpty()) {
            activeIndex = 0
        }
    }

    LaunchedEffect(activeIndex, items.size) {
        if (items.size > 1) {
            delay(5000L)
            activeIndex = (activeIndex + 1) % items.size
        }
    }

    Surface(
        onClick = { onDetails(items[activeIndex]) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(20.dp))
            ),
        scale = ClickableSurfaceScale.None,
        glow =
            ClickableSurfaceDefaults.glow(
                focusedGlow =
                    Glow(
                        elevationColor = Color.White.copy(alpha = 0.35f),
                        elevation = 16.dp,
                    )
            ),
        modifier =
            modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .let { mod ->
                    var m = mod
                    if (playFocusRequester != null) m = m.focusRequester(playFocusRequester)
                    if (upFocusRequester != null || downFocusRequester != null) {
                        m = m.focusProperties {
                            if (upFocusRequester != null) up = upFocusRequester
                            if (downFocusRequester != null) down = downFocusRequester
                        }
                    }
                    m
                }
                .onFocusChanged {
                    if (isFocused != it.isFocused) {
                        isFocused = it.isFocused
                        onFocusChanged?.invoke(it.isFocused)
                    }
                }
                .onPreviewKeyEvent { keyEvent ->
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    activeIndex =
                                        if (activeIndex > 0) activeIndex - 1 else items.size - 1
                                    true
                                }

                                Key.DirectionRight -> {
                                    activeIndex =
                                        if (activeIndex < items.size - 1) activeIndex + 1 else 0
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
                val currentItem = items.getOrNull(targetIndex) ?: items[0]
                HomeCarouselItem(
                    item = currentItem,
                    showLogo = showLogo,
                )
            }

            // Carousel indicators at bottom-right
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 18.dp),
            ) {
                repeat(items.size) { index ->
                    val isActive = activeIndex == index
                    Box(
                        modifier =
                            Modifier.size(if (isActive) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary
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
