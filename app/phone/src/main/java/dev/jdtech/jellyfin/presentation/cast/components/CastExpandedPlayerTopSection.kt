package dev.jdtech.jellyfin.presentation.cast.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.cast.models.CastPlayerState
import dev.jdtech.jellyfin.player.cast.presentation.CastPlayerViewModel
import dev.jdtech.jellyfin.player.core.domain.models.PlayerImage
import dev.jdtech.jellyfin.utils.toBlurHashPainter
import dev.jdtech.jellyfin.utils.toOptimizedImageUri

@Composable
fun PlayerTopSection(
    uiState: CastPlayerViewModel.UiState,
    isScrubbing: Boolean,
    scrubPosition: Float,
    onClose: () -> Unit,
    onDeviceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerState = uiState.playerState

    val poster = uiState.currentItemPoster
    val deviceName = uiState.connectedDevice?.name

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CastExpandedPlayerHeader(
            deviceName = deviceName,
            onClose = onClose,
            onDeviceClick = onDeviceClick,
        )

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PlayerImage(
                uiState = uiState,
                isScrubbing = isScrubbing,
                scrubPosition = scrubPosition,
                poster = poster,
                playbackState = playerState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PlayerImage(
    uiState: CastPlayerViewModel.UiState,
    isScrubbing: Boolean,
    scrubPosition: Float,
    poster: PlayerImage?,
    playbackState: CastPlayerState,
    modifier: Modifier = Modifier,
) {
    val trickplay = uiState.currentTrickplay
    val aspectRatio =
        if (isScrubbing && trickplay != null) {
            uiState.trickplayAspectRatio ?: uiState.defaultAspectRatio
        } else {
            uiState.defaultAspectRatio
        }

    BoxWithConstraints(
        modifier =
            modifier
                .padding(
                    horizontal =
                        if (uiState.isMovie && (!isScrubbing || trickplay == null)) 88.dp else 24.dp
                )
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.DarkGray),
        contentAlignment = Alignment.Center,
    ) {
        if (
            isScrubbing &&
                trickplay != null &&
                playbackState.duration > 0 &&
                trickplay.images.isNotEmpty()
        ) {
            TrickplayThumbnail(
                trickplay = trickplay,
                scrubPosition = scrubPosition,
            )
        } else if (poster != null) {
            val blurPlaceholder = remember(poster.blurHash) { poster.blurHash.toBlurHashPainter() }

            val optimizedUri =
                poster.uri.toOptimizedImageUri(widthDp = maxWidth, heightDp = maxHeight)

            AsyncImage(
                model = optimizedUri,
                contentDescription = "Poster",
                contentScale = ContentScale.Crop,
                placeholder = blurPlaceholder,
                error = blurPlaceholder,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painterResource(CoreR.drawable.ic_cast),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.White,
            )
        }
    }
}
