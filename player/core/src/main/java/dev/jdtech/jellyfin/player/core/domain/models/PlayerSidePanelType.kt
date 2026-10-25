package dev.jdtech.jellyfin.player.core.domain.models

/** Identifies the active sub-panel or bottom sheet displayed in the player. */
enum class PlayerSidePanelType {
    /** No panel or bottom sheet is open. */
    NONE,

    /** Audio track selection panel. */
    AUDIO,

    /** Subtitle track selection panel. */
    SUBTITLES,

    /** Main quick-settings navigation menu. */
    SETTINGS,

    /** Playback speed selection panel. */
    SPEED,

    /** Aspect ratio / video zoom mode selection panel. */
    ASPECT_RATIO,

    /** Chapters list panel. */
    CHAPTERS,

    /** Sleep timer configuration panel. */
    SLEEP_TIMER,

    /** Video quality selection panel. */
    QUALITY,

    /** Playback info / nerd stats panel. */
    PLAYBACK_INFO,
}
