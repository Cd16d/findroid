package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.utils.toBlurHashPainter
import dev.jdtech.jellyfin.utils.toOptimizedImageUri
import org.jellyfin.sdk.model.api.ImageType

@Composable
fun HomeCarouselItem(
    item: FindroidItem,
    modifier: Modifier = Modifier,
    showLogo: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val screenHeight = configuration.screenHeightDp.dp

        val backdropImage =
            item.images.backdrop
                ?: item.images.showBackdrop
                ?: item.images.primary
                ?: item.images.showPrimary
        val imageUri =
            backdropImage
                ?.uri
                .toOptimizedImageUri(
                    widthDp = screenWidth,
                    heightDp = screenHeight,
                )
        val blurPlaceholder =
            remember(backdropImage?.blurHash) { backdropImage?.blurHash?.toBlurHashPainter() }

        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                placeholder = blurPlaceholder ?: ColorPainter(Color(0xFF141414)),
                error = blurPlaceholder,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Dark elliptical scrim radiating from bottom-left corner to contrast text without
        // obscuring top
        Box(
            modifier =
                Modifier.fillMaxSize().drawBehind {
                    val pivot = Offset(0f, size.height)
                    scale(
                        scaleX = (size.width / size.height) * 1.5f,
                        scaleY = 1.0f,
                        pivot = pivot,
                    ) {
                        drawRect(
                            brush =
                                Brush.radialGradient(
                                    colors =
                                        listOf(
                                            Color.Black.copy(alpha = 0.9f),
                                            Color.Black.copy(alpha = 0.7f),
                                            Color.Transparent,
                                        ),
                                    center = pivot,
                                    radius = size.height * 0.95f,
                                )
                        )
                    }
                }
        )

        // Left side: Title, metadata badges and synopsis (aligned bottom-left, without Home label)
        Column(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth(0.6f)
                    .padding(start = 36.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val logo = item.images.logo ?: item.images.showLogo
            val logoUri =
                if (showLogo) {
                    logo
                        ?.uri
                        .toOptimizedImageUri(
                            widthDp = 400.dp,
                            heightDp = 120.dp,
                            imageType = ImageType.LOGO,
                        )
                } else null

            var isLogoFailed by remember(logoUri) { mutableStateOf(false) }
            var logoAspectRatio by remember(logoUri) { mutableStateOf<Float?>(null) }

            val (maxLogoWidth, maxLogoHeight) =
                when {
                    logoAspectRatio == null -> 260.dp to 90.dp
                    (logoAspectRatio ?: 1f) < 1.6f -> 140.dp to 110.dp
                    (logoAspectRatio ?: 1f) > 2.5f -> 280.dp to 75.dp
                    else -> 240.dp to 90.dp
                }

            if (logoUri != null && !isLogoFailed) {
                AsyncImage(
                    model = logoUri,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    onSuccess = { state ->
                        val w = state.painter.intrinsicSize.width
                        val h = state.painter.intrinsicSize.height
                        if (w > 0 && h > 0) {
                            logoAspectRatio = w / h
                        }
                    },
                    onError = { isLogoFailed = true },
                    modifier =
                        Modifier.sizeIn(
                                maxWidth = maxLogoWidth,
                                maxHeight = maxLogoHeight,
                            )
                            .padding(bottom = 8.dp),
                )
            } else {
                val title =
                    when (item) {
                        is FindroidEpisode -> item.seriesName
                        is FindroidSeason -> item.seriesName
                        else -> item.name
                    }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val rating =
                    when (item) {
                        is FindroidMovie -> item.communityRating
                        is FindroidShow -> item.communityRating
                        is FindroidEpisode -> item.communityRating
                        else -> null
                    }
                if (rating != null && rating > 0f) {
                    Text(
                        text = "★ %.1f".format(rating),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                    )
                }
                val year =
                    when (item) {
                        is FindroidMovie -> item.productionYear
                        is FindroidShow -> item.productionYear
                        else -> null
                    }
                if (year != null && year > 0) {
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
                val genres =
                    when (item) {
                        is FindroidMovie -> item.genres
                        is FindroidShow -> item.genres
                        else -> emptyList()
                    }
                if (genres.isNotEmpty()) {
                    Text(
                        text = genres.take(2).joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (item.overview.isNotBlank()) {
                Text(
                    text = item.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, device = "id:tv_1080p")
private fun HomeCarouselItemPreview() {
    FindroidTheme { HomeCarouselItem(item = dummyMovie) }
}
