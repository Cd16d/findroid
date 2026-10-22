package dev.jdtech.jellyfin.core.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Maximum number of particles allocated for the spoiler overlay animation. */
const val MAX_PARTICLES = 1000

/**
 * Represents an individual particle state in the spoiler overlay animation.
 *
 * @property x Current horizontal position in pixels.
 * @property y Current vertical position in pixels.
 * @property vecX Horizontal direction vector component.
 * @property vecY Vertical direction vector component.
 * @property velocity Movement speed scaled by density.
 * @property radiusDp Particle radius in dp.
 * @property lifeTime Total lifetime duration in milliseconds.
 * @property currentTime Current elapsed lifetime in milliseconds.
 * @property alphaTier Baseline transparency level.
 */
class Particle(
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

/**
 * Resets a particle state with random position, direction, speed, and lifetime.
 *
 * @param p Target particle instance to reset.
 * @param width Canvas width in pixels.
 * @param height Canvas height in pixels.
 * @param random Random number generator instance.
 * @param density Display density scaling factor.
 */
fun respawnParticle(
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

/**
 * Calculates particle count based on the surface area in DP.
 *
 * @param areaDp Total surface area in square DP.
 * @return Recommended particle count capped at [MAX_PARTICLES].
 */
fun particleCount(areaDp: Float): Int {
    val count =
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
            areaDp < 54_000f -> 750
            areaDp < 64_000f -> 850
            areaDp < 75_000f -> 920
            areaDp < 88_000f -> 970
            else -> MAX_PARTICLES
        }
    return count.coerceAtMost(MAX_PARTICLES)
}

/**
 * Animated particle overlay rendered over masked spoiler content.
 *
 * @param modifier Modifier applied to the particle overlay canvas.
 * @param revealProgress Progress of full content reveal from 0f (masked) to 1f (revealed).
 * @param holdProgress Progress of hold-to-reveal gesture from 0f to 1f.
 * @param holdCenter Spatial position where touch hold gesture originated.
 * @param tapOffset Spatial position of last tap input.
 * @param touchOffset Spatial position of current active touch pointer.
 * @param isTextMask Whether the mask is applied to lightweight text content rather than image
 *   cards.
 * @param particleColor Base color applied to particle dots.
 */
@Composable
fun SpoilerParticleOverlay(
    modifier: Modifier = Modifier,
    revealProgress: Float = 0f,
    holdProgress: Float = 0f,
    holdCenter: Offset? = null,
    tapOffset: Offset? = null,
    touchOffset: Offset? = null,
    isTextMask: Boolean = false,
    particleColor: Color = Color.White,
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

    val particles = remember { Array(MAX_PARTICLES) { Particle() } }

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
        val targetCount = particleCount(widthDp * heightDp).coerceAtMost(particles.size)

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

        val centerRaw = holdCenter ?: tapOffset ?: touchOffset ?: Offset(w / 2f, h / 2f)
        val center = Offset(centerRaw.x.coerceIn(0f, w), centerRaw.y.coerceIn(0f, h))
        val maxRadius =
            hypot(
                max(center.x, w - center.x),
                max(center.y, h - center.y),
            )
        val holdMaxDist = 280.dp.toPx()
        val holdRadius = holdMaxDist * holdProgress
        val revealRadius = maxRadius * revealProgress
        val effectiveRadius = max(holdRadius, revealRadius)
        val waveBand = 72.dp.toPx()

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

            if (effectiveRadius > 0f) {
                val dx = p.x - center.x
                val dy = p.y - center.y
                val dist = hypot(dx, dy)
                val safeDist = dist.coerceAtLeast(0.5f)
                val dirX = dx / safeDist
                val dirY = dy / safeDist

                val tangentX = -dirY
                val tangentY = dirX

                if (dist < effectiveRadius) {
                    val depth = effectiveRadius - dist
                    val dissolveFactor = (1f - depth / (waveBand * 0.65f)).coerceIn(0f, 1f)

                    val push = dissolveFactor * (waveBand * 0.35f)
                    val swirl =
                        sin(p.currentTime * 0.003f + i * 0.3f) * (waveBand * 0.15f) * dissolveFactor
                    drawX = p.x + dirX * push + tangentX * swirl
                    drawY = p.y + dirY * push + tangentY * swirl

                    drawAlpha *= dissolveFactor * dissolveFactor
                } else if (dist < effectiveRadius + waveBand) {
                    val bandProgress = (dist - effectiveRadius) / waveBand
                    val waveIntensity = (1f - bandProgress).coerceIn(0f, 1f)
                    val smoothFactor = waveIntensity * waveIntensity

                    val push = smoothFactor * (waveBand * 0.45f)
                    val swirl =
                        sin(p.currentTime * 0.003f + i * 0.3f) * (waveBand * 0.2f) * smoothFactor

                    drawX = p.x + dirX * push + tangentX * swirl
                    drawY = p.y + dirY * push + tangentY * swirl

                    drawAlpha *= (0.4f + 0.6f * bandProgress)
                }
            } else if (touchOffset != null) {
                val tdx = drawX - touchOffset.x
                val tdy = drawY - touchOffset.y
                val tdist = hypot(tdx, tdy)
                val repelRadius = 42.dp.toPx()
                if (tdist < repelRadius) {
                    val repelFactor = 1f - (tdist / repelRadius)
                    val repelPush = repelFactor * 26.dp.toPx()
                    val safeTDist = tdist.coerceAtLeast(1f)
                    drawX += (tdx / safeTDist) * repelPush
                    drawY += (tdy / safeTDist) * repelPush
                }
            }

            if (drawX !in 0f..w || drawY < 0f || drawY > h) {
                continue
            }

            val globalHoldFade =
                if (holdProgress < 0.25f) {
                    1f
                } else {
                    (1f - (holdProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                }
            drawAlpha = (drawAlpha * (1f - revealProgress) * globalHoldFade).coerceIn(0f, 1f)
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
