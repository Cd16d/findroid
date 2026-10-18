package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.core.presentation.dummy.dummyShow
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.presentation.components.SpoilerMask
import dev.jdtech.jellyfin.presentation.components.rememberSpoilerState
import dev.jdtech.jellyfin.presentation.components.spoilerCardGesture
import dev.jdtech.jellyfin.presentation.download.components.DownloadedBadge
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.getShowDateString

/**
 * Standard media card component presenting item artwork, title, subtitle, and badges.
 *
 * @param item The [FindroidItem] to display (movie, show, season, episode).
 * @param direction Layout orientation: [Direction.HORIZONTAL] for backdrop cards or
 *   [Direction.VERTICAL] for poster cards.
 * @param onClick Callback triggered when tapping the card.
 * @param modifier Optional [Modifier] applied to the card column.
 * @param hideEpisodeSpoilers When enabled, blurs unwatched episode artwork and obscures
 *   descriptions.
 * @param showParentTitle When true, displays series parent titles for season and episode items.
 */
@Composable
fun ItemCard(
    item: FindroidItem,
    direction: Direction,
    onClick: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
    hideEpisodeSpoilers: Boolean = false,
    showParentTitle: Boolean = true,
) {
    val width =
        when (direction) {
            Direction.HORIZONTAL -> 260
            Direction.VERTICAL -> 150
        }
    var isRevealed by rememberSaveable(item.id) { mutableStateOf(false) }
    val isEpisodeSpoiler = hideEpisodeSpoilers && item is FindroidEpisode && !item.played

    val spoilerState =
        rememberSpoilerState(
            enabled = isEpisodeSpoiler,
            isRevealed = isRevealed,
            onRevealChange = { isRevealed = it },
        )

    Column(modifier = modifier.width(width.dp)) {
        Surface(
            modifier =
                Modifier.clip(MaterialTheme.shapes.small)
                    .spoilerCardGesture(state = spoilerState, onClick = { onClick(item) }),
            shape = MaterialTheme.shapes.small,
        ) {
            val hasBlurHash =
                item.images.backdrop?.blurHash != null || item.images.primary?.blurHash != null
            Box {
                SpoilerMask(
                    state = spoilerState,
                    shape = MaterialTheme.shapes.small,
                    blurRadius = if (hasBlurHash) 4.dp else 16.dp,
                ) {
                    ItemPoster(
                        item = item,
                        direction = direction,
                        blurOnly = isEpisodeSpoiler && !isRevealed,
                    )
                }
                Row(
                    modifier =
                        Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                ) {
                    if (item.isDownloaded()) DownloadedBadge()
                    if (item.played) PlayedBadge()
                    item.unplayedItemCount?.takeIf { it > 0 }?.let { ItemCountBadge(it) }
                }
                if (direction == Direction.HORIZONTAL) {
                    ProgressBar(
                        item = item,
                        width = width,
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(MaterialTheme.spacings.small),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
        val titleText =
            when {
                showParentTitle && item is FindroidEpisode -> item.seriesName
                showParentTitle && item is FindroidSeason -> item.seriesName.ifBlank { item.name }
                else -> item.name
            }
        val subtitleText =
            when (item) {
                is FindroidMovie -> {
                    item.premiereDate?.year.toString()
                }

                is FindroidShow -> {
                    getShowDateString(item)
                }

                is FindroidSeason -> {
                    if (
                        showParentTitle &&
                            item.seriesName.isNotBlank() &&
                            !item.name.equals(item.seriesName, ignoreCase = true)
                    ) {
                        item.name
                    } else {
                        ""
                    }
                }

                is FindroidEpisode -> {
                    stringResource(
                        id = R.string.episode_name_extended,
                        item.parentIndexNumber,
                        item.indexNumber,
                        item.name,
                    )
                }

                else -> {
                    ""
                }
            }
        Text(
            text = titleText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitleText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewMovie() {
    FindroidTheme { ItemCard(item = dummyMovie, direction = Direction.HORIZONTAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewMovieVertical() {
    FindroidTheme { ItemCard(item = dummyMovie, direction = Direction.VERTICAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewShow() {
    FindroidTheme { ItemCard(item = dummyShow, direction = Direction.HORIZONTAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewShowVertical() {
    FindroidTheme { ItemCard(item = dummyShow, direction = Direction.VERTICAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewEpisode() {
    FindroidTheme { ItemCard(item = dummyEpisode, direction = Direction.HORIZONTAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewEpisodeVertical() {
    FindroidTheme { ItemCard(item = dummyEpisode, direction = Direction.VERTICAL, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun ItemCardPreviewSeason() {
    FindroidTheme {
        ItemCard(
            item = dummySeason,
            direction = Direction.VERTICAL,
            onClick = {},
            showParentTitle = false,
        )
    }
}
