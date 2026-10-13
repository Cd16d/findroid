package dev.jdtech.jellyfin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun ItemCard(
    item: FindroidItem,
    direction: Direction,
    onClick: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
    showLogos: Boolean = false,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
) {
    val width =
        when (direction) {
            Direction.HORIZONTAL -> 160
            Direction.VERTICAL -> 110
        }
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(width.dp).zIndex(if (isFocused) 1f else 0f)) {
        Surface(
            onClick = { onClick(item) },
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
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
            modifier =
                Modifier.let { mod: Modifier ->
                        var m = mod
                        if (focusRequester != null) m = m.focusRequester(focusRequester)
                        if (upFocusRequester != null)
                            m = m.focusProperties { up = upFocusRequester }
                        m
                    }
                    .onFocusChanged {
                        if (isFocused != it.isFocused) {
                            isFocused = it.isFocused
                            onFocus?.invoke(it.isFocused)
                        }
                    },
        ) {
            Box {
                ItemPoster(item = item, direction = direction)
                ProgressBadge(
                    item = item,
                    modifier =
                        Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small),
                )
                if (direction == Direction.HORIZONTAL) {
                    Column(
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(MaterialTheme.spacings.small)
                    ) {
                        Box(
                            modifier =
                                Modifier.height(4.dp)
                                    .width(
                                        item.playbackPositionTicks
                                            .div(item.runtimeTicks.toFloat())
                                            .times(width - 16)
                                            .dp
                                    )
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        if (item is FindroidEpisode) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    if (item.parentIndexNumber > 0 && item.indexNumber > 0) {
                        stringResource(
                            id = R.string.episode_name_extended,
                            item.parentIndexNumber,
                            item.indexNumber,
                            item.name,
                        )
                    } else {
                        item.name
                    },
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview
@Composable
private fun ItemCardPreviewMovie() {
    FindroidTheme { ItemCard(item = dummyMovie, direction = Direction.HORIZONTAL, onClick = {}) }
}

@Preview
@Composable
private fun ItemCardPreviewMovieVertical() {
    FindroidTheme { ItemCard(item = dummyMovie, direction = Direction.VERTICAL, onClick = {}) }
}

@Preview
@Composable
private fun ItemCardPreviewEpisode() {
    FindroidTheme { ItemCard(item = dummyEpisode, direction = Direction.HORIZONTAL, onClick = {}) }
}
