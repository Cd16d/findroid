package dev.jdtech.jellyfin.ui.components

import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.player.core.domain.models.PlayerImages
import dev.jdtech.jellyfin.utils.toOptimizedImageUri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType

data class ScreensaverSlide(
    val title: String,
    val backdropUri: Uri?,
    val logoUri: Uri?,
)

fun FindroidItem.toScreensaverSlide(): ScreensaverSlide {
    val rawBackdrop = images.backdrop?.uri ?: images.showBackdrop?.uri ?: images.primary?.uri
    val rawLogo = images.logo?.uri ?: images.showLogo?.uri
    return ScreensaverSlide(
        title = name,
        backdropUri = rawBackdrop,
        logoUri = rawLogo,
    )
}

fun toScreensaverSlide(title: String, images: PlayerImages?): ScreensaverSlide {
    val rawBackdrop = images?.backdrop?.uri ?: images?.showBackdrop?.uri ?: images?.primary?.uri
    val rawLogo = images?.logo?.uri ?: images?.showLogo?.uri
    return ScreensaverSlide(
        title = title,
        backdropUri = rawBackdrop,
        logoUri = rawLogo,
    )
}

@Composable
fun rememberOledBurnInOffset(
    maxOffsetXDp: Float = 16f,
    maxOffsetYDp: Float = 10f,
    durationXMs: Int = 29_000,
    durationYMs: Int = 41_000,
): Offset {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "oledBurnIn")
    val offsetX by
        infiniteTransition.animateFloat(
            initialValue = -maxOffsetXDp,
            targetValue = maxOffsetXDp,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = durationXMs, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "oledBurnInX",
        )
    val offsetY by
        infiniteTransition.animateFloat(
            initialValue = -maxOffsetYDp,
            targetValue = maxOffsetYDp,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = durationYMs, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "oledBurnInY",
        )
    return remember(offsetX, offsetY) {
        with(density) { Offset(offsetX.dp.toPx(), offsetY.dp.toPx()) }
    }
}

@Composable
fun ScreensaverClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000L)
        }
    }

    val timeString =
        remember(currentTime, context) { DateFormat.getTimeFormat(context).format(currentTime) }
    val dateString =
        remember(currentTime) {
            val format = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
            format.format(currentTime).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = timeString,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = dateString,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

@Composable
fun AppScreensaver(
    slides: List<ScreensaverSlide>,
    modifier: Modifier = Modifier,
    cycleIntervalMs: Long = 10_000L,
) {
    if (slides.isEmpty()) return

    var currentIndex by remember(slides) { mutableIntStateOf(0) }

    LaunchedEffect(slides, cycleIntervalMs) {
        if (slides.size > 1) {
            while (true) {
                delay(cycleIntervalMs)
                currentIndex = (currentIndex + 1) % slides.size
            }
        }
    }

    val currentSlide = slides[currentIndex % slides.size]

    val clockOffset =
        rememberOledBurnInOffset(
            maxOffsetXDp = 18f,
            maxOffsetYDp = 12f,
            durationXMs = 29_000,
            durationYMs = 41_000,
        )
    val mediaOffset =
        rememberOledBurnInOffset(
            maxOffsetXDp = 16f,
            maxOffsetYDp = 10f,
            durationXMs = 33_000,
            durationYMs = 47_000,
        )
    val appLogoOffset =
        rememberOledBurnInOffset(
            maxOffsetXDp = 14f,
            maxOffsetYDp = 10f,
            durationXMs = 37_000,
            durationYMs = 43_000,
        )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AnimatedContent(
            targetState = currentSlide,
            transitionSpec = {
                fadeIn(animationSpec = tween(1200)) togetherWith
                    fadeOut(animationSpec = tween(1200))
            },
            label = "screensaverSlide",
        ) { slide ->
            val backdropUri =
                slide.backdropUri.toOptimizedImageUri(
                    widthDp = 1920.dp,
                    heightDp = 1080.dp,
                    quality = 90,
                )
            val logoUri =
                slide.logoUri.toOptimizedImageUri(
                    heightDp = 200.dp,
                    quality = 90,
                    imageType = ImageType.LOGO,
                )

            Box(modifier = Modifier.fillMaxSize()) {
                if (backdropUri != null) {
                    AsyncImage(
                        model = backdropUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Cinematic vignette gradient: dark top and dark bottom for optimal readability
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            Color.Black.copy(alpha = 0.65f),
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.85f),
                                        )
                                )
                            )
                )

                // Top-Right: Date & Time with OLED burn-in drift
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .align(Alignment.TopEnd)
                            .padding(horizontal = 56.dp, vertical = 40.dp)
                            .graphicsLayer {
                                translationX = clockOffset.x
                                translationY = clockOffset.y
                            },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    ScreensaverClock()
                }

                // Bottom row: Media logo/title (left) and Findroid app logo (right) with OLED
                // burn-in drift
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 56.dp, vertical = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(
                        modifier =
                            Modifier.weight(1f, fill = false).graphicsLayer {
                                translationX = mediaOffset.x
                                translationY = mediaOffset.y
                            },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        if (logoUri != null) {
                            AsyncImage(
                                model = logoUri,
                                contentDescription = slide.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.widthIn(max = 380.dp).heightIn(max = 140.dp),
                            )
                        } else {
                            Text(
                                text = slide.title,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }

                    Box(
                        modifier =
                            Modifier.graphicsLayer {
                                translationX = appLogoOffset.x
                                translationY = appLogoOffset.y
                            }
                    ) {
                        Image(
                            painter = painterResource(id = CoreR.drawable.ic_logo),
                            contentDescription = "Findroid",
                            modifier = Modifier.size(54.dp).alpha(0.85f),
                        )
                    }
                }
            }
        }
    }
}
