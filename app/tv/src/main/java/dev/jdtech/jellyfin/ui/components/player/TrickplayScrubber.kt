package dev.jdtech.jellyfin.ui.components.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun TrickplayScrubber(
    trickplay: Trickplay?,
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    if (trickplay != null && trickplay.images.isNotEmpty()) {
        val intervalMs =
            if (trickplay.interval > 1000) trickplay.interval.toLong()
            else trickplay.interval.toLong() * 1000L
        val totalImages = trickplay.images.size
        val targetCenterIndex =
            if (intervalMs > 0) {
                (currentPositionMs / intervalMs).toInt().coerceIn(0, totalImages - 1)
            } else 0

        val animatedIndex by
            animateFloatAsState(
                targetValue = targetCenterIndex.toFloat(),
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                label = "trickplayScrubberIndex",
            )

        val density = LocalDensity.current
        val cardSpacingPx = with(density) { 320.dp.toPx() }

        Box(
            modifier = modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            val minIdx = (floor(animatedIndex).toInt() - 1).coerceAtLeast(0)
            val maxIdx = (ceil(animatedIndex).toInt() + 1).coerceAtMost(totalImages - 1)

            for (i in minIdx..maxIdx) {
                val delta = i - animatedIndex
                val distance = abs(delta)
                if (distance > 2.2f) continue

                val scale = (1f - distance * 0.22f).coerceIn(0.72f, 1f)
                val alpha = (1f - distance * 0.55f).coerceIn(0.35f, 1f)
                val translationXPx = delta * cardSpacingPx
                val isFocusedFrame = distance < 0.35f

                Box(
                    modifier =
                        Modifier.zIndex(10f - distance)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                                translationX = translationXPx
                            }
                            .width(300.dp)
                            .height(170.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .then(
                                if (isFocusedFrame) {
                                    Modifier.border(
                                        width = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                } else {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                }
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    val bitmap = trickplay.images[i]
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    if (isFocusedFrame) {
                        val frameTimeMs = i * intervalMs
                        Box(
                            modifier =
                                Modifier.align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.8f),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .border(
                                        width = 0.5.dp,
                                        color = Color.White.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = formatScrubberTime(frameTimeMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Fallback when trickplay thumbnails are unavailable
        Box(
            modifier = modifier.fillMaxWidth().height(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.background(
                            Color.Black.copy(alpha = 0.85f),
                            RoundedCornerShape(16.dp),
                        )
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatScrubberTime(currentPositionMs),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
            }
        }
    }
}

private fun formatScrubberTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
