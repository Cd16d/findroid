package dev.jdtech.jellyfin.player.core.domain.utils

import android.content.Context
import dev.jdtech.jellyfin.player.core.R
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerMode
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerStatus

/** Utility object for formatting sleep timer status into localized subtitles. */
object SleepTimerFormatter {
    /**
     * Formats the sleep timer status into a localized human-readable subtitle.
     *
     * @param context Android [Context] for retrieving string resources.
     * @param sleepTimerStatus Active [SleepTimerStatus].
     * @param isMovie Whether the currently playing item is a movie (or false for series/episode).
     */
    fun formatSleepTimerSubtitle(
        context: Context,
        sleepTimerStatus: SleepTimerStatus,
        isMovie: Boolean,
    ): String {
        return if (sleepTimerStatus.isActive) {
            when (sleepTimerStatus.mode) {
                is SleepTimerMode.EndOfMedia -> {
                    if (isMovie) context.getString(R.string.sleep_timer_end_of_video)
                    else context.getString(R.string.sleep_timer_end_of_episode)
                }
                is SleepTimerMode.Duration -> {
                    val remainingMins = (sleepTimerStatus.remainingSeconds + 59L) / 60L
                    context.getString(R.string.sleep_timer_remaining, remainingMins.toInt())
                }
                else -> context.getString(R.string.sleep_timer_off)
            }
        } else {
            context.getString(R.string.sleep_timer_off)
        }
    }
}
