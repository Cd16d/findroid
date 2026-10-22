package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
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
    isFocused: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize().background(Color(0xFF141414))) {
        val windowSize = LocalWindowInfo.current.containerDpSize
        val screenWidth = windowSize.width
        val screenHeight = windowSize.height

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

        // Ambient background using BlurHash spanning the full carousel card
        if (blurPlaceholder != null) {
            Image(
                painter = blurPlaceholder,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.20f),
            )
        }

        // Sharp 16:9 artwork aligned to the right edge with a smooth fade mask on its left edge
        if (imageUri != null) {
            val fadeMaskBrush = remember {
                Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.25f to Color.Black,
                    1.0f to Color.Black,
                )
            }

            AsyncImage(
                model = imageUri,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier.fillMaxHeight()
                        .aspectRatio(16f / 9f)
                        .align(Alignment.CenterEnd)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = fadeMaskBrush,
                                blendMode = BlendMode.DstIn,
                            )
                        },
            )
        }

        // Horizontal gradient from the left for high text readability
        val horizontalScrim = remember {
            Brush.horizontalGradient(
                0.0f to Color.Black.copy(alpha = 0.90f),
                0.40f to Color.Black.copy(alpha = 0.65f),
                0.60f to Color.Black.copy(alpha = 0.15f),
                0.75f to Color.Transparent,
                1.0f to Color.Transparent,
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(horizontalScrim))

        // Subtle bottom grounding scrim
        val bottomScrim = remember {
            Brush.verticalGradient(
                0.0f to Color.Transparent,
                0.65f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.60f),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(bottomScrim))

        // Left side: Title, metadata badges and synopsis
        Column(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth(0.55f)
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
                when (val aspect = logoAspectRatio) {
                    null -> 260.dp to 90.dp
                    else ->
                        when {
                            aspect < 1.6f -> 140.dp to 110.dp
                            aspect > 2.5f -> 280.dp to 75.dp
                            else -> 240.dp to 90.dp
                        }
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

            val episodeSubtitle =
                if (item is FindroidEpisode && item.parentIndexNumber > 0 && item.indexNumber > 0) {
                    val indexEnd = item.indexNumberEnd
                    val epNumberStr =
                        if (indexEnd != null && indexEnd > item.indexNumber) {
                            "${item.indexNumber}-$indexEnd"
                        } else {
                            "${item.indexNumber}"
                        }
                    if (item.name.isNotBlank() && item.name != item.seriesName) {
                        "S${item.parentIndexNumber}:E$epNumberStr • ${item.name}"
                    } else {
                        "S${item.parentIndexNumber}:E$epNumberStr"
                    }
                } else null

            if (episodeSubtitle != null) {
                Text(
                    text = episodeSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier.padding(top = 4.dp)
                        .graphicsLayer {
                            scaleX = if (isFocused) 1.06f else 1.0f
                            scaleY = if (isFocused) 1.06f else 1.0f
                        }
                        .clip(CircleShape)
                        .background(
                            if (isFocused) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.15f)
                        )
                        .border(
                            width = 1.dp,
                            color =
                                if (isFocused) Color.White.copy(alpha = 0.35f)
                                else Color.White.copy(alpha = 0.2f),
                            shape = CircleShape,
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_info),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
                )
                Text(
                    text = stringResource(CoreR.string.view_details),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.White,
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
