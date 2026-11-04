package dev.jdtech.jellyfin.presentation.player.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Standardized haptic feedback controller for the media player. Provides multisensory tactile
 * feedback adhering to Android Material 3 and iOS HIG media playback patterns.
 *
 * Automatically checks API levels for newer constants (such as CONFIRM, REJECT, and SEGMENT_TICK)
 * and falls back gracefully on older devices.
 */
class PlayerHaptics(private val view: View?, private val enabled: Boolean = true) {

    /**
     * Light micro-tick for granular steps, such as double-tap seeking increments (+10s, -10s),
     * discrete volume button steps, and stationary timeline taps.
     */
    fun tick() {
        if (!enabled || view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Tactile detent / snap sensation when the scrub bar crosses a chapter marker threshold or
     * center notch.
     */
    fun chapterSnap() {
        if (!enabled || view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Crisp positive confirmation for state toggles:
     * - Play / Pause toggle
     * - Slide-to-unlock completed (100% threshold reached)
     * - Lock controls enabled
     * - Screen orientation locked / unlocked
     * - Skip Intro / Outro button pressed
     */
    fun confirm() {
        if (!enabled || view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * Distinct tactile cue for long press activation:
     * - Quick 2× speed hold activation
     */
    fun longPress() {
        if (!enabled || view == null) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /**
     * Rejection / cancellation feedback when an action is aborted:
     * - Slide-to-unlock released before reaching the end (springs back)
     * - Physical / logical boundary limit reached (volume/brightness 0% or 100%)
     */
    fun reject() {
        if (!enabled || view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    /**
     * Subtle tactile tick when beginning a continuous touch gesture (e.g. timeline scrub start).
     */
    fun gestureStart() {
        if (!enabled || view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Subtle tactile tick when committing / ending a continuous touch gesture. */
    fun gestureEnd() {
        if (!enabled || view == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /**
     * Tactile bump when hitting a physical or logical boundary:
     * - Volume reaching 0% (mute) or 100% (max)
     * - Screen brightness reaching 0% or 100%
     */
    fun boundary() {
        reject()
    }
}

/** Remembers a [PlayerHaptics] instance bound to the current Compose view hierarchy. */
@Composable
fun rememberPlayerHaptics(enabled: Boolean = true): PlayerHaptics {
    val view = LocalView.current
    return remember(view, enabled) { PlayerHaptics(view, enabled) }
}

/**
 * CompositionLocal providing [PlayerHaptics] throughout the player hierarchy, ensuring all
 * components respect the user's haptic preferences.
 */
val LocalPlayerHaptics = compositionLocalOf { PlayerHaptics(view = null, enabled = false) }
