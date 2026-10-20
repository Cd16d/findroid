package dev.jdtech.jellyfin.player.core.domain.models

sealed interface SleepTimerMode {
    data object Off : SleepTimerMode

    data class Duration(val totalMinutes: Int) : SleepTimerMode

    data object EndOfMedia : SleepTimerMode
}

data class SleepTimerStatus(
    val mode: SleepTimerMode = SleepTimerMode.Off,
    val remainingSeconds: Long = 0L,
) {
    val isActive: Boolean
        get() = mode !is SleepTimerMode.Off
}
