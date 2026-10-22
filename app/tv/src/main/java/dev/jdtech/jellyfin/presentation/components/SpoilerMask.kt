package dev.jdtech.jellyfin.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.components.MAX_PARTICLES
import dev.jdtech.jellyfin.core.presentation.components.Particle
import dev.jdtech.jellyfin.core.presentation.components.particleCount
import dev.jdtech.jellyfin.core.presentation.components.respawnParticle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * TV spoiler mask container that blurs content and renders animated particle effect overlay.
 *
 * @param modifier Optional modifier applied to mask box. Defaults to [Modifier].
 * @param enabled Whether spoiler mask is enabled. Defaults to `true`.
 * @param shape Clipping shape for mask. Defaults to [RectangleShape].
 * @param blurRadius Blur radius when masked. Defaults to `16.dp`.
 * @param content Composable content protected by spoiler mask.
 */
@Composable
fun SpoilerMask(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    blurRadius: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier.clip(shape)) { content() }
        return
    }

    Box(modifier = modifier.clip(shape)) {
        Box(
            modifier =
                Modifier.clip(shape)
                    .then(
                        if (blurRadius > 0.dp) {
                            Modifier.blur(blurRadius)
                        } else {
                            Modifier
                        }
                    )
        ) {
            content()
        }

        SpoilerParticleOverlay(modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun SpoilerParticleOverlay(
    modifier: Modifier = Modifier,
    particleColor: Color = Color.White,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "tvSpoilerSparkle")
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

        drawRect(color = Color.Black.copy(alpha = 0.22f))

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

            val drawX = p.x
            val drawY = p.y
            val drawAlpha = p.alphaTier * lifeFade

            if (drawX !in 0f..w || drawY < 0f || drawY > h) {
                continue
            }

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

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun TvSpoilerMaskPreview() {
    dev.jdtech.jellyfin.presentation.theme.FindroidTheme {
        SpoilerMask(enabled = true) { androidx.tv.material3.Text("TV Spoiler Content") }
    }
}
