package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.toBlurHashPainter
import dev.jdtech.jellyfin.utils.toOptimizedImageUri

@Composable
fun HomeCarouselItem(
    item: FindroidItem,
    onAction: (HomeAction) -> Unit,
    showLogos: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { onAction(HomeAction.OnItemClick(item)) },
        shape = ClickableSurfaceDefaults.shape(shape = RectangleShape),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border.None),
        scale = ClickableSurfaceScale.None,
        modifier = Modifier.fillMaxSize().onFocusChanged { isFocused = it.isFocused },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val image = item.images.backdrop
            val imageUri = image?.uri.toOptimizedImageUri(widthDp = maxWidth, heightDp = maxHeight)

            val blurPlaceholder = remember(image?.blurHash) { image?.blurHash.toBlurHashPainter() }
            AsyncImage(
                model = imageUri,
                placeholder = blurPlaceholder ?: ColorPainter(MaterialTheme.colorScheme.surface),
                error = blurPlaceholder,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Top scrim for top bar legibility
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(160.dp)
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = 0.85f),
                                        Color.Black.copy(alpha = 0.4f),
                                        Color.Transparent,
                                    )
                            )
                        )
            )

            // Left horizontal scrim for text legibility
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = 0.9f),
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent,
                                    ),
                                endX = 1400f,
                            )
                        )
            )

            // Bottom gradient fading into app background
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(200.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                        MaterialTheme.colorScheme.background,
                                    )
                            )
                        )
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                modifier =
                    Modifier.padding(horizontal = MaterialTheme.spacings.large)
                        .padding(bottom = MaterialTheme.spacings.large)
                        .align(Alignment.BottomStart),
            ) {
                val genres =
                    when (item) {
                        is FindroidMovie -> item.genres
                        is FindroidShow -> item.genres
                        else -> emptyList()
                    }
                if (genres.isNotEmpty()) {
                    Text(
                        text = genres.take(3).joinToString(" • "),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                val logoUri =
                    if (showLogos) {
                        item.images.logo
                            ?: (if (item is FindroidEpisode) item.images.showLogo else null)
                    } else {
                        null
                    }

                if (logoUri != null) {
                    AsyncImage(
                        model = logoUri,
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(72.dp).widthIn(max = 360.dp),
                    )
                } else {
                    Text(
                        text = item.name,
                        color = Color.White,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 2,
                        style = MaterialTheme.typography.displaySmall,
                    )
                }

                if (item.overview.isNotBlank()) {
                    Text(
                        text = item.overview,
                        modifier = Modifier.widthIn(max = 680.dp),
                        color = Color.LightGray,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 3,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))

                // Action play pill
                Surface(
                    onClick = { onAction(HomeAction.OnItemClick(item)) },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor =
                                if (isFocused) Color.White else Color.White.copy(alpha = 0.2f),
                            focusedContainerColor = Color.White,
                        ),
                    scale =
                        ClickableSurfaceDefaults.scale(
                            focusedScale = 1.05f,
                            pressedScale = 1.0f,
                        ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_play),
                            contentDescription = null,
                            tint = if (isFocused) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                        Text(
                            text = stringResource(id = CoreR.string.play),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isFocused) Color.Black else Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun HomeCarouselItemPreview() {
    FindroidTheme { HomeCarouselItem(item = dummyMovie, onAction = {}) }
}
