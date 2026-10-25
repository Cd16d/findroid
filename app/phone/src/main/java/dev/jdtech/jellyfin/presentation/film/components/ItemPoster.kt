package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.utils.toBlurHashPainter
import dev.jdtech.jellyfin.utils.toOptimizedImageUri

/** Orientation format for artwork displays. */
enum class Direction {
    /** Horizontal aspect ratio (16:9), typical for backdrops and episode thumbnails. */
    HORIZONTAL,

    /** Vertical aspect ratio (2:3), typical for movie and show posters. */
    VERTICAL,
}

/**
 * Artwork image container rendering poster or backdrop images with blurhash placeholders.
 *
 * @param item The [FindroidItem] whose imagery to display.
 * @param direction The layout aspect ratio: [Direction.HORIZONTAL] for 16:9 or [Direction.VERTICAL]
 *   for 2:3.
 * @param modifier Optional [Modifier] applied to the container box.
 * @param blurOnly If true, only renders the blurred placeholder instead of loading the full image.
 */
@Composable
fun ItemPoster(
    item: FindroidItem,
    direction: Direction,
    modifier: Modifier = Modifier,
    blurOnly: Boolean = false,
) {
    val image =
        remember(item, direction) {
            when (direction) {
                Direction.HORIZONTAL -> {
                    if (item is FindroidEpisode) {
                        item.images.primary ?: item.images.backdrop ?: item.images.showBackdrop
                    } else {
                        item.images.backdrop ?: item.images.showBackdrop ?: item.images.primary
                    }
                }
                Direction.VERTICAL -> {
                    if (item is FindroidEpisode) item.images.showPrimary ?: item.images.primary
                    else item.images.primary ?: item.images.showPrimary ?: item.images.backdrop
                }
            }
        }

    BoxWithConstraints(
        modifier =
            modifier
                .aspectRatio(if (direction == Direction.HORIZONTAL) 16f / 9f else 2f / 3f)
                .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        val imageUri = image?.uri.toOptimizedImageUri(widthDp = maxWidth, heightDp = maxHeight)

        val blurPlaceholder =
            remember(image?.blurHash, direction) {
                val (w, h) = if (direction == Direction.HORIZONTAL) 64 to 36 else 36 to 54
                image?.blurHash.toBlurHashPainter(width = w, height = h, punch = 1.25f)
            }

        if (blurOnly && blurPlaceholder != null) {
            Image(
                painter = blurPlaceholder,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = blurPlaceholder,
                error = blurPlaceholder,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview
@Composable
private fun ItemPosterVerticalPreview() {
    FindroidTheme {
        ItemPoster(
            item = dummyMovie,
            direction = Direction.VERTICAL,
        )
    }
}

@Preview
@Composable
private fun ItemPosterHorizontalPreview() {
    FindroidTheme {
        ItemPoster(
            item = dummyMovie,
            direction = Direction.HORIZONTAL,
        )
    }
}
