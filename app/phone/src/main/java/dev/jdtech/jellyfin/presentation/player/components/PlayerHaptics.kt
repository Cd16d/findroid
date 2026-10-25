package dev.jdtech.jellyfin.presentation.player.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Standardized haptic feedback controller for the media player. Provides multisensory tactile
 * feedback adhering to Android Material 3 and iOS HIG media playback patterns.
 *
 * Automatically checks API levels for newer constants (such as CONFIRM, REJECT, and SEGMENT_TICK)
 * and falls back gracefully on older devices.
 */
class PlayerHaptics(private val view: View, private val enabled: Boolean = true) {

    /**
     * Light micro-tick for granular steps, such as double-tap seeking increments (+10s, -10s) and
     * discrete volume button steps.
     */
    fun tick() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Tactile detent / snap sensation when the scrub bar crosses a chapter marker threshold. */
    fun chapterSnap() {
        if (!enabled) return
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
     * - Skip Intro / Outro button pressed
     * - Track / chapter selected from panel
     */
    fun confirm() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    /**
     * Rejection / cancellation feedback when an action is aborted:
     * - Slide-to-unlock released before reaching the end (springs back)
     */
    fun reject() {
        if (!enabled) return
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
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** Subtle tactile tick when committing / ending a continuous touch gesture. */
    fun gestureEnd() {
        if (!enabled) return
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
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

/** Remembers a [PlayerHaptics] instance bound to the current Compose view hierarchy. */
@Composable
fun rememberPlayerHaptics(enabled: Boolean = true): PlayerHaptics {
    val view = LocalView.current
    return remember(view, enabled) { PlayerHaptics(view, enabled) }
}
