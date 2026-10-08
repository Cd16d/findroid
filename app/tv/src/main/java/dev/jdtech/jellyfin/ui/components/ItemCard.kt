package dev.jdtech.jellyfin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
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
    onFocus: ((Boolean) -> Unit)? = null,
) {
    val width =
        when (direction) {
            Direction.HORIZONTAL -> 260
            Direction.VERTICAL -> 150
        }
    var isFocused by remember { mutableStateOf(false) }

    val logoUri =
        if (showLogos) {
            item.images.logo ?: (if (item is FindroidEpisode) item.images.showLogo else null)
        } else {
            null
        }

    Column(modifier = modifier.width(width.dp).zIndex(if (isFocused) 1f else 0f)) {
        Surface(
            onClick = { onClick(item) },
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(10.dp))
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f, pressedScale = 1.02f),
            glow =
                ClickableSurfaceDefaults.glow(
                    focusedGlow =
                        Glow(
                            elevationColor = Color.White.copy(alpha = 0.25f),
                            elevation = 12.dp,
                        )
                ),
            modifier =
                Modifier.onFocusChanged {
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
                    if (showLogos && logoUri != null) {
                        Box(
                            modifier =
                                Modifier.matchParentSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors =
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.65f),
                                                ),
                                            startY = 50f,
                                        )
                                    )
                        )
                        AsyncImage(
                            model = logoUri,
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier =
                                Modifier.align(Alignment.BottomStart)
                                    .padding(MaterialTheme.spacings.small)
                                    .height(34.dp)
                                    .widthIn(max = 160.dp),
                        )
                    }
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
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
        if (direction == Direction.VERTICAL && showLogos && logoUri != null) {
            AsyncImage(
                model = logoUri,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier =
                    Modifier.height(28.dp)
                        .widthIn(max = width.dp)
                        .padding(top = MaterialTheme.spacings.extraSmall),
            )
        } else {
            Text(
                text = if (item is FindroidEpisode) item.seriesName else item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = if (direction == Direction.HORIZONTAL) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item is FindroidEpisode) {
            Text(
                text =
                    stringResource(
                        id = R.string.episode_name_extended,
                        item.parentIndexNumber,
                        item.indexNumber,
                        item.name,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
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
