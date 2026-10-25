package dev.jdtech.jellyfin.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * TV grid and list item card component displaying poster artwork and title.
 *
 * @param item Media item model.
 * @param direction Layout orientation direction (`HORIZONTAL` or `VERTICAL`).
 * @param onClick Callback executed on card click.
 * @param modifier Optional modifier applied to card container. Defaults to [Modifier].
 * @param focusRequester Optional FocusRequester for card focus management.
 * @param upFocusRequester Optional FocusRequester directed on D-pad up navigation.
 * @param onFocus Optional callback triggered on focus state changes.
 * @param hideEpisodeSpoilers Whether to hide episode thumbnails/titles to prevent spoilers.
 */
@Composable
fun ItemCard(
    item: FindroidItem,
    direction: Direction,
    onClick: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onFocus: ((Boolean) -> Unit)? = null,
    hideEpisodeSpoilers: Boolean = false,
) {
    val width =
        when (direction) {
            Direction.HORIZONTAL -> 160
            Direction.VERTICAL -> 94
        }
    var isFocused by remember { mutableStateOf(false) }
    val isEpisodeSpoiler = hideEpisodeSpoilers && item is FindroidEpisode && !item.played
    val hasBlurHash =
        item.images.backdrop?.blurHash != null || item.images.primary?.blurHash != null

    Column(modifier = modifier.width(width.dp).zIndex(if (isFocused) 2f else 0f)) {
        Surface(
            onClick = { onClick(item) },
            shape = ClickableSurfaceDefaults.shape(shape = CentralShapes.ItemCard),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(BorderStroke(2.5.dp, Color.White), shape = CentralShapes.ItemCard)
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.14f),
            glow =
                ClickableSurfaceDefaults.glow(
                    focusedGlow =
                        Glow(
                            elevationColor = Color.White.copy(alpha = 0.3f),
                            elevation = 10.dp,
                        )
                ),
            modifier =
                Modifier.then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    )
                    .then(
                        if (upFocusRequester != null)
                            Modifier.focusProperties { up = upFocusRequester }
                        else Modifier
                    )
                    .onFocusChanged {
                        if (isFocused != it.isFocused) {
                            isFocused = it.isFocused
                            onFocus?.invoke(it.isFocused)
                        }
                    },
        ) {
            Box {
                SpoilerMask(
                    enabled = isEpisodeSpoiler,
                    shape = CentralShapes.ItemCard,
                    blurRadius = if (hasBlurHash) 4.dp else 16.dp,
                ) {
                    ItemPoster(
                        item = item,
                        direction = direction,
                        blurOnly = isEpisodeSpoiler,
                    )
                }
                ProgressBadge(
                    item = item,
                    modifier =
                        Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small),
                )
                if (direction == Direction.HORIZONTAL && item.runtimeTicks > 0L) {
                    val progressFraction =
                        (item.playbackPositionTicks.toFloat() / item.runtimeTicks.toFloat())
                            .coerceIn(0f, 1f)
                    val progressWidth = ((width - 16) * progressFraction).dp
                    if (progressWidth > 0.dp) {
                        Column(
                            modifier =
                                Modifier.align(Alignment.BottomStart)
                                    .padding(MaterialTheme.spacings.small)
                        ) {
                            Box(
                                modifier =
                                    Modifier.height(4.dp)
                                        .width(progressWidth)
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
        if (direction == Direction.HORIZONTAL) {
            val titleText =
                if (item is FindroidEpisode) {
                    if (item.parentIndexNumber > 0 && item.indexNumber > 0) {
                        val indexEnd = item.indexNumberEnd
                        if (indexEnd != null && indexEnd > item.indexNumber) {
                            stringResource(
                                id = R.string.episode_name_extended_with_end,
                                item.parentIndexNumber,
                                item.indexNumber,
                                indexEnd,
                                item.name,
                            )
                        } else {
                            stringResource(
                                id = R.string.episode_name_extended,
                                item.parentIndexNumber,
                                item.indexNumber,
                                item.name,
                            )
                        }
                    } else {
                        item.name
                    }
                } else {
                    item.name
                }
            if (titleText.isNotBlank()) {
                Spacer(modifier = Modifier.height(if (isFocused) 12.dp else 8.dp))
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ItemCardPreviewMovie() {
    FindroidTheme { ItemCard(item = dummyMovie, direction = Direction.HORIZONTAL, onClick = {}) }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ItemCardPreviewMovieVertical() {
    FindroidTheme { ItemCard(item = dummyMovie, direction = Direction.VERTICAL, onClick = {}) }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ItemCardPreviewEpisode() {
    FindroidTheme { ItemCard(item = dummyEpisode, direction = Direction.HORIZONTAL, onClick = {}) }
}
