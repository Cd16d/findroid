package dev.jdtech.jellyfin.presentation.cast.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.cast.models.Device
import dev.jdtech.jellyfin.player.cast.presentation.CastPlayerViewModel
import dev.jdtech.jellyfin.player.core.domain.models.PlayerImage
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
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
    val deviceName = uiState.connectedDevice?.name
    val itemTitle =
        remember(uiState.currentItemTitle) {
            val titleInfo = uiState.currentItemTitle
            if (titleInfo.seriesName != null) {
                "${titleInfo.seriesName} - ${titleInfo.title}"
            } else {
                titleInfo.title
            }
        }

    val trickplay = uiState.currentTrickplay
    val computedAspectRatio =
        if (isScrubbing && trickplay != null) {
            uiState.trickplayAspectRatio ?: uiState.defaultAspectRatio
        } else {
            uiState.defaultAspectRatio
        }
    val safeAspectRatio =
        if (computedAspectRatio.isFinite() && computedAspectRatio > 0f) {
            computedAspectRatio
        } else {
            16f / 10f
        }

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
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            CastPlayerArtwork(
                poster = uiState.currentItemPoster,
                itemTitle = itemTitle,
                isScrubbing = isScrubbing,
                scrubPosition = scrubPosition,
                trickplay = trickplay,
                hasValidDuration = uiState.playerState.duration > 0,
                aspectRatio = safeAspectRatio,
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun CastPlayerArtwork(
    poster: PlayerImage?,
    itemTitle: String,
    isScrubbing: Boolean,
    scrubPosition: Float,
    trickplay: Trickplay?,
    hasValidDuration: Boolean,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .padding(bottom = MaterialTheme.spacings.default)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (isScrubbing && trickplay != null && hasValidDuration && trickplay.images.isNotEmpty()) {
            TrickplayThumbnail(
                trickplay = trickplay,
                scrubPosition = scrubPosition,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (poster != null) {
            val blurPlaceholder = remember(poster.blurHash) { poster.blurHash.toBlurHashPainter() }

            val optimizedUri =
                poster.uri.toOptimizedImageUri(widthDp = maxWidth, heightDp = maxHeight)

            val contentDescription =
                if (itemTitle.isNotBlank()) {
                    stringResource(CoreR.string.image_description_poster, itemTitle)
                } else {
                    stringResource(CoreR.string.series_poster)
                }

            AsyncImage(
                model = optimizedUri,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                placeholder = blurPlaceholder,
                error = blurPlaceholder,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(CoreR.drawable.ic_cast),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun PlayerTopSectionPreview() {
    FindroidTheme {
        PlayerTopSection(
            uiState =
                CastPlayerViewModel.UiState(
                    connectedDevice =
                        Device(
                            id = "device1",
                            name = "Living Room TV",
                            enabled = true,
                            supportsH265 = true,
                        ),
                    currentItemTitle =
                        CastPlayerViewModel.CurrentItemTitle(
                            seriesName = "Big Buck Bunny",
                            title = "The Journey",
                        ),
                    defaultAspectRatio = 16f / 9f,
                ),
            isScrubbing = false,
            scrubPosition = 0f,
            onClose = {},
            onDeviceClick = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun PlayerTopSectionNoDevicePreview() {
    FindroidTheme {
        PlayerTopSection(
            uiState =
                CastPlayerViewModel.UiState(
                    connectedDevice = null,
                    currentItemTitle =
                        CastPlayerViewModel.CurrentItemTitle(title = "Standalone Movie"),
                    defaultAspectRatio = 2f / 3f,
                ),
            isScrubbing = false,
            scrubPosition = 0f,
            onClose = {},
            onDeviceClick = {},
        )
    }
}
