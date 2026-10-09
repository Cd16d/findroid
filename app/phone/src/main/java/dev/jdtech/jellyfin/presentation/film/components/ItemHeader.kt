package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.parallaxLayoutModifier
import dev.jdtech.jellyfin.utils.toBlurHashPainter
import dev.jdtech.jellyfin.utils.toOptimizedImageUri

@Composable
fun ItemHeader(
    item: FindroidItem,
    scrollState: ScrollState,
    showLogo: Boolean = false,
    hideEpisodeSpoilers: Boolean = false,
    isRevealed: Boolean = false,
    onRevealChange: ((Boolean) -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val isEpisodeSpoiler = hideEpisodeSpoilers && item is FindroidEpisode && !item.played
    val hasBlurHash =
        when (item) {
            is FindroidEpisode ->
                item.images.primary?.blurHash != null || item.images.backdrop?.blurHash != null
            else -> item.images.backdrop?.blurHash != null || item.images.primary?.blurHash != null
        }
    val blurRadius by
        animateDpAsState(
            targetValue =
                if (isEpisodeSpoiler && !isRevealed) {
                    if (hasBlurHash) 6.dp else 32.dp
                } else {
                    0.dp
                },
            animationSpec = tween(durationMillis = 350),
            label = "headerBlur",
        )

    ItemHeaderBase(
        item = item,
        showLogo = showLogo,
        hideEpisodeSpoilers = hideEpisodeSpoilers,
        isRevealed = isRevealed,
        backdropImage = {
            val image =
                when (item) {
                    is FindroidEpisode -> item.images.primary
                    else -> item.images.backdrop
                }

            val backdropUri =
                image?.uri.toOptimizedImageUri(widthDp = maxWidth, heightDp = maxHeight)

            val blurPlaceholder =
                remember(image?.blurHash) {
                    image?.blurHash.toBlurHashPainter(width = 64, height = 36, punch = 1.25f)
                }

            if (isEpisodeSpoiler && !isRevealed && blurPlaceholder != null) {
                Image(
                    painter = blurPlaceholder,
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxSize()
                            .parallaxLayoutModifier(scrollState = scrollState, rate = 2)
                            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
                    contentScale = ContentScale.Crop,
                )
            } else {
                AsyncImage(
                    model = backdropUri,
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxSize()
                            .parallaxLayoutModifier(scrollState = scrollState, rate = 2)
                            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
                    placeholder =
                        blurPlaceholder ?: ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                    contentScale = ContentScale.Crop,
                )
            }
        },
        content = content,
    )
}

@Composable
fun ItemHeader(
    item: FindroidItem,
    lazyListState: LazyListState,
    showLogo: Boolean = false,
    hideEpisodeSpoilers: Boolean = false,
    isRevealed: Boolean = false,
    onRevealChange: ((Boolean) -> Unit)? = null,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val isEpisodeSpoiler = hideEpisodeSpoilers && item is FindroidEpisode && !item.played
    val hasBlurHash =
        when (item) {
            is FindroidEpisode ->
                item.images.primary?.blurHash != null || item.images.backdrop?.blurHash != null
            else -> item.images.backdrop?.blurHash != null || item.images.primary?.blurHash != null
        }
    val blurRadius by
        animateDpAsState(
            targetValue =
                if (isEpisodeSpoiler && !isRevealed) {
                    if (hasBlurHash) 6.dp else 32.dp
                } else {
                    0.dp
                },
            animationSpec = tween(durationMillis = 350),
            label = "headerBlur",
        )

    ItemHeaderBase(
        item = item,
        showLogo = showLogo,
        hideEpisodeSpoilers = hideEpisodeSpoilers,
        isRevealed = isRevealed,
        backdropImage = {
            val image =
                when (item) {
                    is FindroidEpisode -> item.images.primary
                    is FindroidSeason -> item.images.showBackdrop
                    else -> item.images.backdrop
                }

            val backdropUri =
                image?.uri.toOptimizedImageUri(widthDp = maxWidth, heightDp = maxHeight)

            val blurPlaceholder =
                remember(image?.blurHash) {
                    image?.blurHash.toBlurHashPainter(width = 64, height = 36, punch = 1.25f)
                }

            if (isEpisodeSpoiler && !isRevealed && blurPlaceholder != null) {
                Image(
                    painter = blurPlaceholder,
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxSize()
                            .parallaxLayoutModifier(lazyListState = lazyListState, rate = 2)
                            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
                    contentScale = ContentScale.Crop,
                )
            } else {
                AsyncImage(
                    model = backdropUri,
                    contentDescription = null,
                    modifier =
                        Modifier.fillMaxSize()
                            .parallaxLayoutModifier(lazyListState = lazyListState, rate = 2)
                            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
                    placeholder =
                        blurPlaceholder ?: ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                    contentScale = ContentScale.Crop,
                )
            }
        },
        content = content,
    )
}

@Composable
private fun ItemHeaderBase(
    item: FindroidItem,
    showLogo: Boolean = false,
    hideEpisodeSpoilers: Boolean = false,
    isRevealed: Boolean = false,
    backdropImage: @Composable (BoxWithConstraintsScope.() -> Unit),
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val backgroundColor = MaterialTheme.colorScheme.background

    val logo =
        when (item) {
            is FindroidEpisode -> item.images.showLogo
            else -> item.images.logo
        }

    val isEpisodeSpoiler = hideEpisodeSpoilers && item is FindroidEpisode && !item.played
    val scrimAlpha by
        animateFloatAsState(
            targetValue = if (isEpisodeSpoiler && !isRevealed) 0.5f else 0.1f,
            animationSpec = tween(durationMillis = 350),
            label = "headerScrim",
        )

    BoxWithConstraints(modifier = Modifier.height(288.dp).clipToBounds()) {
        backdropImage()
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = scrimAlpha))
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, backgroundColor),
                        startY = 0f,
                    )
            )
        }
        content()
        if (showLogo) {
            AsyncImage(
                model = logo?.uri,
                contentDescription = null,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(MaterialTheme.spacings.default)
                        .height(100.dp)
                        .fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        }
        AnimatedVisibility(
            visible = isEpisodeSpoiler && !isRevealed,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_eye_off),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}
