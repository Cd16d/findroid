package dev.jdtech.jellyfin.player.core.domain.models

/** Represents the operating mode of the playback sleep timer. */
sealed interface SleepTimerMode {
    /** Sleep timer is turned off. */
    data object Off : SleepTimerMode

    /**
     * Sleep timer is set to count down for a fixed duration in minutes.
     *
     * @property totalMinutes Total duration in minutes.
     */
    data class Duration(val totalMinutes: Int) : SleepTimerMode

    /** Sleep timer is set to pause playback when current media ends. */
    data object EndOfMedia : SleepTimerMode
}

/**
 * Holds the active status and remaining time for the playback sleep timer.
 *
 * @property mode The active [SleepTimerMode].
 * @property remainingSeconds Time remaining in seconds before playback pauses.
 */
data class SleepTimerStatus(
    val mode: SleepTimerMode = SleepTimerMode.Off,
    val remainingSeconds: Long = 0L,
) {
    /** Returns true if the sleep timer is currently active (i.e. not [SleepTimerMode.Off]). */
    val isActive: Boolean
        get() = mode !is SleepTimerMode.Off
}
