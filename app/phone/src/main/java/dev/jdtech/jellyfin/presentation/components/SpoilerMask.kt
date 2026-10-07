package dev.jdtech.jellyfin.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SpoilerMask(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    initiallyRevealed: Boolean = false,
    isRevealed: Boolean? = null,
    onRevealChange: ((Boolean) -> Unit)? = null,
    blurRadius: Dp = 16.dp,
    consumeClickOnMask: Boolean = true,
    contentAlphaWhenMasked: Float = 1f,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier.clip(shape)) { content() }
        return
    }

    var internalRevealed by
        rememberSaveable(initiallyRevealed) { mutableStateOf(initiallyRevealed) }
    val revealed = isRevealed ?: internalRevealed

    var tapOffset by remember { mutableStateOf<Offset?>(null) }

    val revealProgress by
        animateFloatAsState(
            targetValue = if (revealed) 1f else 0f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            label = "spoilerRevealProgress",
        )

    val animatedBlurRadius = (blurRadius.value * (1f - revealProgress)).dp
    val animatedContentAlpha =
        contentAlphaWhenMasked + (1f - contentAlphaWhenMasked) * revealProgress

    val isTextMask = contentAlphaWhenMasked < 0.5f

    val clickableModifier =
        if (consumeClickOnMask && !revealed) {
            Modifier.pointerInput(enabled, revealed) {
                detectTapGestures { offset ->
                    tapOffset = offset
                    val nextRevealed = true
                    internalRevealed = nextRevealed
                    onRevealChange?.invoke(nextRevealed)
                }
            }
        } else {
            Modifier
        }

    Box(modifier = modifier.clip(shape).then(clickableModifier)) {
        Box(
            modifier =
                Modifier.clip(shape)
                    .then(
                        if (animatedBlurRadius > 0.dp) {
                            Modifier.blur(animatedBlurRadius)
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer { alpha = animatedContentAlpha }
        ) {
            content()
        }

        if (revealProgress < 1f) {
            val particleColor =
                if (isTextMask) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    Color.White
                }

            SpoilerParticleOverlay(
                revealProgress = revealProgress,
                tapOffset = tapOffset,
                isTextMask = isTextMask,
                particleColor = particleColor,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private class Particle(
    var x: Float = 0f,
    var y: Float = 0f,
    var vecX: Float = 0f,
    var vecY: Float = 0f,
    var velocity: Float = 0f,
    var radiusDp: Float = 0.8f,
    var lifeTime: Float = 1500f,
    var currentTime: Float = 0f,
    var alphaTier: Float = 0.6f,
)

private fun respawnParticle(
    p: Particle,
    width: Float,
    height: Float,
    random: Random,
    density: Float,
) {
    p.x = random.nextFloat() * width
    p.y = random.nextFloat() * height
    val angle = random.nextFloat() * (2f * Math.PI.toFloat())
    p.vecX = cos(angle)
    p.vecY = sin(angle)
    p.velocity = (8f + random.nextFloat() * 12f) * density
    p.radiusDp = 0.65f + random.nextFloat() * 0.35f
    p.lifeTime = 1000f + random.nextFloat() * 2000f
    p.currentTime = random.nextFloat() * p.lifeTime
    p.alphaTier =
        when (random.nextInt(3)) {
            0 -> 0.35f
            1 -> 0.65f
            else -> 1.0f
        }
}

@Composable
private fun SpoilerParticleOverlay(
    revealProgress: Float,
    tapOffset: Offset?,
    isTextMask: Boolean,
    particleColor: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spoilerSparkle")
    val animClock by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 100000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "animClock",
        )

    val particles = remember { Array(2000) { Particle() } }

    val state = remember {
        object {
            var lastTime = 0L
            var lastW = 0f
            var lastH = 0f
            val random = Random(42)
        }
    }

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_VARIABLE") val tick = animClock

        val density = this.density
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val widthDp = w / density
        val heightDp = h / density
        val targetCount = calculateParticleCount(widthDp * heightDp, isTextMask)

        val now = SystemClock.uptimeMillis()
        val dt = if (state.lastTime == 0L) 16L else (now - state.lastTime).coerceIn(1L, 40L)
        state.lastTime = now

        if (state.lastW != w || state.lastH != h) {
            state.lastW = w
            state.lastH = h
            for (i in 0 until targetCount) {
                respawnParticle(particles[i], w, h, state.random, density)
            }
        }

        if (!isTextMask) {
            drawRect(color = Color.Black.copy(alpha = 0.22f * (1f - revealProgress)))
        }

        val maxRadius =
            if (tapOffset != null) {
                hypot(
                    max(tapOffset.x, w - tapOffset.x),
                    max(tapOffset.y, h - tapOffset.y),
                )
            } else {
                0f
            }
        val currentRadius = maxRadius * revealProgress
        val waveBand = 48.dp.toPx()

        for (i in 0 until targetCount) {
            val p = particles[i]
            p.currentTime += dt
            if (p.currentTime >= p.lifeTime || p.x < 0f || p.x > w || p.y < 0f || p.y > h) {
                p.x = state.random.nextFloat() * w
                p.y = state.random.nextFloat() * h
                val angle = state.random.nextFloat() * (2f * Math.PI.toFloat())
                p.vecX = cos(angle)
                p.vecY = sin(angle)
                p.velocity = (8f + state.random.nextFloat() * 12f) * density
                p.radiusDp = 0.65f + state.random.nextFloat() * 0.35f
                p.lifeTime = 1000f + state.random.nextFloat() * 2000f
                p.currentTime = 0f
                p.alphaTier =
                    when (state.random.nextInt(3)) {
                        0 -> 0.35f
                        1 -> 0.65f
                        else -> 1.0f
                    }
            } else {
                val hdt = p.velocity * (dt / 500f)
                p.x += p.vecX * hdt
                p.y += p.vecY * hdt
            }

            val lifeRatio = (p.currentTime / p.lifeTime).coerceIn(0f, 1f)
            val lifeFade =
                if (lifeRatio < 0.15f) {
                    lifeRatio / 0.15f
                } else if (lifeRatio > 0.85f) {
                    (1f - lifeRatio) / 0.15f
                } else {
                    1f
                }

            var drawX = p.x
            var drawY = p.y
            var drawAlpha = p.alphaTier * lifeFade

            if (tapOffset != null && currentRadius > 0f) {
                val dx = p.x - tapOffset.x
                val dy = p.y - tapOffset.y
                val dist = hypot(dx, dy)
                if (dist < currentRadius) {
                    continue
                } else if (dist < currentRadius + waveBand) {
                    val factor = 1f - (dist - currentRadius) / waveBand
                    val push = factor * 32.dp.toPx()
                    val safeDist = dist.coerceAtLeast(1f)
                    drawX += (dx / safeDist) * push
                    drawY += (dy / safeDist) * push
                    drawAlpha = (drawAlpha * (1f + factor * 0.7f)).coerceAtMost(1f)
                }
            }

            drawAlpha = (drawAlpha * (1f - revealProgress)).coerceIn(0f, 1f)
            if (drawAlpha > 0.05f) {
                drawCircle(
                    color = particleColor.copy(alpha = drawAlpha),
                    radius = p.radiusDp * density,
                    center = Offset(drawX, drawY),
                )
            }
        }
    }
}

private fun calculateParticleCount(areaDp: Float, isTextMask: Boolean): Int {
    val baseCount =
        when {
            areaDp < 500f -> 10
            areaDp < 1_000f -> 25
            areaDp < 2_500f -> 50
            areaDp < 5_000f -> 85
            areaDp < 8_000f -> 125
            areaDp < 11_000f -> 165
            areaDp < 14_500f -> 210
            areaDp < 19_000f -> 270
            areaDp < 24_000f -> 340
            areaDp < 30_000f -> 430
            areaDp < 37_000f -> 530
            areaDp < 45_000f -> 640
            areaDp < 54_000f -> 770
            areaDp < 64_000f -> 910
            areaDp < 75_000f -> 1060
            areaDp < 88_000f -> 1230
            areaDp < 102_000f -> 1410
            areaDp < 118_000f -> 1600
            areaDp < 136_000f -> 1800
            else -> 2000
        }
    return if (isTextMask) {
        (baseCount * 1.15f).toInt().coerceAtMost(2000)
    } else {
        baseCount
    }
}
