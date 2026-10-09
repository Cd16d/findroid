package dev.jdtech.jellyfin.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class SpoilerState
internal constructor(
    initialEnabled: Boolean,
    initialRevealed: Boolean,
    internal var onRevealChangeCallback: ((Boolean) -> Unit)?,
    private val coroutineScope: CoroutineScope,
    private val hapticFeedback: HapticFeedback,
) {
    var enabledState by mutableStateOf(initialEnabled)
        internal set

    var isRevealed by mutableStateOf(initialRevealed)
        internal set

    val holdProgress = Animatable(0f)
    var touchOffset by mutableStateOf<Offset?>(null)
        internal set

    var lastTouchOffset by mutableStateOf<Offset?>(null)
        internal set

    var containerCoordinates by mutableStateOf<LayoutCoordinates?>(null)
        internal set

    private var holdJob: Job? = null
    var isHolding by mutableStateOf(false)
        private set

    var revealedDuringThisHold by mutableStateOf(false)
        private set

    fun onDown(position: Offset) {
        if (!enabledState || isRevealed) return
        touchOffset = position
        lastTouchOffset = position
        isHolding = true
        revealedDuringThisHold = false
        holdJob?.cancel()

        holdJob = coroutineScope.launch {
            delay(100.milliseconds)
            holdProgress.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = 650,
                        easing = CubicBezierEasing(0.35f, 0.0f, 0.15f, 1.0f),
                    ),
            )
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            revealedDuringThisHold = true
            isRevealed = true
            onRevealChangeCallback?.invoke(true)
            isHolding = false
        }
    }

    fun onMove(position: Offset) {
        if (isHolding) {
            touchOffset = position
            lastTouchOffset = position
        }
    }

    fun onCancel() {
        if (isHolding) {
            isHolding = false
            revealedDuringThisHold = false
            holdJob?.cancel()
            coroutineScope.launch { holdProgress.snapTo(0f) }
            touchOffset = null
        }
    }

    fun onUp(elapsed: Long): Boolean {
        if (!isHolding && holdProgress.value == 0f) {
            return false
        }
        isHolding = false
        holdJob?.cancel()

        if (isRevealed) {
            touchOffset = null
            return true
        }

        touchOffset = null
        val returnDuration = (240 * holdProgress.value).toInt().coerceIn(80, 240)
        coroutineScope.launch {
            holdProgress.animateTo(
                targetValue = 0f,
                animationSpec =
                    tween(durationMillis = returnDuration, easing = FastOutSlowInEasing),
            )
        }
        return elapsed >= 200L
    }
}

@Composable
fun rememberSpoilerState(
    enabled: Boolean = true,
    isRevealed: Boolean = false,
    onRevealChange: ((Boolean) -> Unit)? = null,
): SpoilerState {
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val state =
        remember(coroutineScope, hapticFeedback) {
            SpoilerState(
                initialEnabled = enabled,
                initialRevealed = isRevealed,
                onRevealChangeCallback = onRevealChange,
                coroutineScope = coroutineScope,
                hapticFeedback = hapticFeedback,
            )
        }

    SideEffect {
        state.enabledState = enabled
        state.isRevealed = isRevealed
        state.onRevealChangeCallback = onRevealChange
    }

    return state
}

@Composable
fun Modifier.spoilerCardGesture(
    state: SpoilerState,
    onClick: (() -> Unit)? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()
    var pressInteraction by remember { mutableStateOf<PressInteraction.Press?>(null) }
    val currentOnClick by rememberUpdatedState(onClick)

    return this.onGloballyPositioned { coordinates -> state.containerCoordinates = coordinates }
        .indication(interactionSource, ripple())
        .pointerInput(state) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downPos = down.position
                val startTime = SystemClock.uptimeMillis()

                val press = PressInteraction.Press(downPos)
                pressInteraction = press
                coroutineScope.launch { interactionSource.emit(press) }

                val wasAlreadyRevealed = state.isRevealed || !state.enabledState

                if (!wasAlreadyRevealed) {
                    state.onDown(downPos)
                }

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        val elapsed = SystemClock.uptimeMillis() - startTime
                        val currentPress = pressInteraction
                        if (currentPress != null) {
                            coroutineScope.launch {
                                interactionSource.emit(PressInteraction.Release(currentPress))
                            }
                            pressInteraction = null
                        }

                        if (wasAlreadyRevealed) {
                            change?.consume()
                            currentOnClick?.invoke()
                        } else {
                            val justRevealed = state.revealedDuringThisHold
                            state.onUp(elapsed)
                            if (justRevealed) {
                                change?.consume()
                            } else if (elapsed < 200L) {
                                change?.consume()
                                currentOnClick?.invoke()
                            }
                        }
                        break
                    }

                    val dist = (change.position - downPos).getDistance()
                    if (dist > touchSlop || (change.positionChanged() && change.isConsumed)) {
                        if (!wasAlreadyRevealed) {
                            state.onCancel()
                        }
                        val currentPress = pressInteraction
                        if (currentPress != null) {
                            coroutineScope.launch {
                                interactionSource.emit(PressInteraction.Cancel(currentPress))
                            }
                            pressInteraction = null
                        }
                        break
                    }

                    if (!wasAlreadyRevealed) {
                        state.onMove(change.position)
                    }
                }
            }
        }
}

@Composable
fun SpoilerMask(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    initiallyRevealed: Boolean = false,
    isRevealed: Boolean? = null,
    onRevealChange: ((Boolean) -> Unit)? = null,
    blurRadius: Dp = 16.dp,
    consumeClickOnMask: Boolean = true,
    revealOnLongPress: Boolean = true,
    onTap: (() -> Unit)? = null,
    contentAlphaWhenMasked: Float = 1f,
    state: SpoilerState? = null,
    isTarget: ((Offset) -> Boolean)? = null,
    touchOffsetCorrection: ((Offset) -> Offset)? = null,
    content: @Composable () -> Unit,
) {
    var internalRevealed by
        rememberSaveable(initiallyRevealed) { mutableStateOf(initiallyRevealed) }
    val effectiveRevealed = isRevealed ?: internalRevealed

    val internalState =
        if (state == null) {
            rememberSpoilerState(
                enabled = enabled,
                isRevealed = effectiveRevealed,
                onRevealChange = {
                    internalRevealed = it
                    onRevealChange?.invoke(it)
                },
            )
        } else {
            null
        }

    val activeState = state ?: internalState!!
    if (!activeState.enabledState) {
        Box(modifier = modifier.clip(shape)) { content() }
        return
    }

    var maskCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val isHoldingTarget =
        if (isTarget != null) {
            val currentTouch = activeState.touchOffset ?: activeState.lastTouchOffset
            currentTouch != null && isTarget(currentTouch)
        } else {
            true
        }

    val gestureModifier =
        if (state == null && !activeState.isRevealed && consumeClickOnMask && revealOnLongPress) {
            Modifier.spoilerCardGesture(activeState, onClick = onTap)
        } else {
            Modifier
        }

    val revealProgress by
        animateFloatAsState(
            targetValue = if (activeState.isRevealed) 1f else 0f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "spoilerRevealProgress",
        )

    val animatedBlurRadius = (blurRadius.value * (1f - revealProgress)).dp
    val animatedContentAlpha =
        contentAlphaWhenMasked + (1f - contentAlphaWhenMasked) * revealProgress

    val isTextMask = contentAlphaWhenMasked < 0.5f

    Box(
        modifier =
            modifier
                .clip(shape)
                .onGloballyPositioned { coordinates -> maskCoordinates = coordinates }
                .then(gestureModifier)
    ) {
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

            val toLocal: (Offset?) -> Offset? = { offset ->
                if (offset == null) {
                    null
                } else {
                    val container = activeState.containerCoordinates
                    val mask = maskCoordinates
                    if (
                        container != null && mask != null && container.isAttached && mask.isAttached
                    ) {
                        mask.localPositionOf(container, offset)
                    } else if (touchOffsetCorrection != null) {
                        touchOffsetCorrection(offset)
                    } else {
                        offset
                    }
                }
            }

            val holdProgressValue = if (isHoldingTarget) activeState.holdProgress.value else 0f
            val holdCenterPos =
                if (isHoldingTarget) toLocal(activeState.touchOffset ?: activeState.lastTouchOffset)
                else null
            val touchOffsetPos = if (isHoldingTarget) toLocal(activeState.touchOffset) else null

            SpoilerParticleOverlay(
                revealProgress = revealProgress,
                holdProgress = holdProgressValue,
                holdCenter = holdCenterPos,
                tapOffset = if (isHoldingTarget) toLocal(activeState.lastTouchOffset) else null,
                touchOffset = touchOffsetPos,
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

private const val MAX_PARTICLES = 2000

@Composable
private fun SpoilerParticleOverlay(
    revealProgress: Float,
    holdProgress: Float,
    holdCenter: Offset?,
    tapOffset: Offset?,
    touchOffset: Offset?,
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
        val targetCount =
            particleCount(widthDp * heightDp).coerceAtMost(particles.size)

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

private fun particleCount(areaDp: Float): Int {
    return when {
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
        else -> MAX_PARTICLES
    }
}
