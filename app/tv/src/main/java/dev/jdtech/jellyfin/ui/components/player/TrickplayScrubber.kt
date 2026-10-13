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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

@Composable
fun TrickplayScrubber(
    trickplay: Trickplay?,
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    segments: List<FindroidSegment> = emptyList(),
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
        val cardSpacingPx = with(density) { 260.dp.toPx() }

        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    0.0f to Color.Transparent,
                                    0.05f to Color.Black,
                                    0.95f to Color.Black,
                                    1.0f to Color.Transparent,
                                ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            contentAlignment = Alignment.TopCenter,
        ) {
            val minIdx = (floor(animatedIndex).toInt() - 2).coerceAtLeast(0)
            val maxIdx = (ceil(animatedIndex).toInt() + 2).coerceAtMost(totalImages - 1)

            for (i in minIdx..maxIdx) {
                val delta = i - animatedIndex
                val distance = abs(delta)
                if (distance > 2.8f) continue

                val scale = (1f - distance * 0.12f).coerceIn(0.72f, 1f)
                val alpha = (1f - (distance * 0.17f)).coerceIn(0.60f, 1f)
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
                            .width(240.dp)
                            .height(135.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black)
                            .then(
                                if (isFocusedFrame) {
                                    val framePos =
                                        if (intervalMs > 0) i * intervalMs else currentPositionMs
                                    val matchingSegment = segments.firstOrNull { seg ->
                                        framePos >= seg.startTicks && framePos <= seg.endTicks
                                    }
                                    val segmentColor = getSegmentColor(matchingSegment?.type)
                                    val focusedBorderColor =
                                        segmentColor ?: MaterialTheme.colorScheme.primary
                                    Modifier.border(
                                        width = 3.5.dp,
                                        color = focusedBorderColor,
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
                }
            }
        }
    }
}
