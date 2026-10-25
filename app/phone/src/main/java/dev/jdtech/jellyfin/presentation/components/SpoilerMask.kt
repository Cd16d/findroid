package dev.jdtech.jellyfin.presentation.components

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.components.SpoilerParticleOverlay
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * State object driving spoiler mask animations, touch holding progress, and reveal state.
 *
 * @param initialEnabled Initial state indicating whether spoiler mask protection is active.
 * @param initialRevealed Initial state indicating whether content is revealed.
 * @param onRevealChangeCallback Callback invoked when revealed status changes.
 * @param coroutineScope Scope used for launching hold and return animations.
 * @param hapticFeedback Haptic feedback handler for long press reveal event.
 */
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

/**
 * Creates and remembers a [SpoilerState] instance across recompositions.
 *
 * @param enabled Whether spoiler protection is currently active.
 * @param isRevealed Controlled revealed state.
 * @param onRevealChange Callback invoked when revealed state transitions.
 * @return Remembered [SpoilerState] instance.
 */
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

/**
 * Modifier detecting touch interactions for press-and-hold reveal gestures on spoiler cards.
 *
 * @param state State instance managing gesture feedback and progress.
 * @param onClick Optional click listener invoked when card is tapped or already revealed.
 * @return Modified layout with gesture detection attached.
 */
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

/**
 * Interactive spoiler mask container that blurs underlying composable content until revealed.
 *
 * @param modifier Modifier applied to container. Defaults to [Modifier].
 * @param enabled Whether spoiler protection is active. Defaults to `true`.
 * @param shape Mask clipping shape. Defaults to [RectangleShape].
 * @param initiallyRevealed Initial revealed state. Defaults to `false`.
 * @param isRevealed Controlled revealed state if driven externally. Defaults to `null`.
 * @param onRevealChange Callback when revealed state changes. Defaults to `null`.
 * @param blurRadius Blur radius applied when content is masked. Defaults to `16.dp`.
 * @param consumeClickOnMask Whether clicks on mask are consumed. Defaults to `true`.
 * @param revealOnLongPress Whether long press reveals mask. Defaults to `true`.
 * @param onTap Optional callback when tapped. Defaults to `null`.
 * @param contentAlphaWhenMasked Content alpha when masked. Defaults to `1f`.
 * @param state SpoilerState instance. Defaults to `null`.
 * @param isTarget Optional target check lambda. Defaults to `null`.
 * @param touchOffsetCorrection Optional offset correction. Defaults to `null`.
 * @param content Composable content protected by spoiler mask.
 */
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

@Preview(name = "Masked Text", showBackground = true)
@Composable
private fun SpoilerMaskPreview() {
    FindroidTheme {
        SpoilerMask(enabled = true) {
            Text(
                text = "Spoiler Content Text",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(name = "Revealed State", showBackground = true)
@Composable
private fun SpoilerMaskRevealedPreview() {
    FindroidTheme {
        SpoilerMask(
            enabled = true,
            initiallyRevealed = true,
        ) {
            Text(
                text = "Revealed Spoiler Content",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SpoilerMaskDarkModePreview() {
    FindroidTheme(darkTheme = true) {
        Surface {
            SpoilerMask(enabled = true) {
                Text(
                    text = "Dark Mode Spoiler Text",
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Preview(name = "Image Card", showBackground = true)
@Composable
private fun SpoilerMaskImageCardPreview() {
    FindroidTheme {
        SpoilerMask(
            enabled = true,
            shape = RoundedCornerShape(8.dp),
        ) {
            Box(
                modifier =
                    Modifier.padding(8.dp)
                        .size(width = 200.dp, height = 120.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Episode Thumbnail",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
