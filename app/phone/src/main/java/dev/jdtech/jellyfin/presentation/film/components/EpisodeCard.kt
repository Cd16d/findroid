package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.presentation.components.SpoilerMask
import dev.jdtech.jellyfin.presentation.components.rememberSpoilerState
import dev.jdtech.jellyfin.presentation.components.spoilerCardGesture
import dev.jdtech.jellyfin.presentation.download.components.DownloadedBadge
import dev.jdtech.jellyfin.presentation.download.components.DownloadingBadge
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun EpisodeCard(
    episode: FindroidEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloading: Boolean = false,
    isPending: Boolean = false,
    downloadProgress: Float? = null,
    hideEpisodeSpoilers: Boolean = false,
) {
    var isRevealed by rememberSaveable(episode.id) { mutableStateOf(false) }
    val isEpisodeSpoiler = hideEpisodeSpoilers && !episode.played

    val spoilerState =
        rememberSpoilerState(
            enabled = isEpisodeSpoiler,
            isRevealed = isRevealed,
            onRevealChange = { isRevealed = it },
        )

    val density = LocalDensity.current
    val posterWidthPx = remember(density) { with(density) { (84.dp * (16f / 9f)).toPx() } }
    val spacerWidthPx =
        remember(density) { with(density) { (MaterialTheme.spacings.default / 2).toPx() } }
    val columnOffsetX = posterWidthPx + spacerWidthPx

    Row(
        modifier =
            modifier
                .height(84.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .spoilerCardGesture(
                    state = spoilerState,
                    onClick = onClick,
                )
    ) {
        val hasBlurHash =
            episode.images.backdrop?.blurHash != null || episode.images.primary?.blurHash != null
        Box {
            SpoilerMask(
                state = spoilerState,
                isTarget = { offset -> offset.x <= posterWidthPx },
                shape = MaterialTheme.shapes.small,
                blurRadius = if (hasBlurHash) 4.dp else 16.dp,
            ) {
                ItemPoster(
                    item = episode,
                    direction = Direction.HORIZONTAL,
                    modifier = Modifier.clip(MaterialTheme.shapes.small),
                    blurOnly = isEpisodeSpoiler && !isRevealed,
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(MaterialTheme.spacings.small),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
            ) {
                if (isDownloading || isPending) {
                    DownloadingBadge(progress = downloadProgress, isPending = isPending)
                } else if (episode.isDownloaded()) {
                    DownloadedBadge()
                }
                if (episode.played) PlayedBadge()
            }
        }
        Spacer(Modifier.width(MaterialTheme.spacings.default / 2))
        Column(modifier = Modifier.fillMaxHeight()) {
            Text(
                text =
                    stringResource(
                        id = dev.jdtech.jellyfin.core.R.string.episode_name,
                        episode.indexNumber,
                        episode.name,
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            SpoilerMask(
                state = spoilerState,
                isTarget = { offset -> offset.x > posterWidthPx },
                touchOffsetCorrection = { offset -> Offset(offset.x - columnOffsetX, offset.y) },
                contentAlphaWhenMasked = 0f,
            ) {
                Text(
                    text = episode.overview,
                    modifier = Modifier.alpha(0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EpisodeCardPreview() {
    FindroidTheme { EpisodeCard(episode = dummyEpisode, onClick = {}) }
}
