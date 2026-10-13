package dev.jdtech.jellyfin.player.core.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class TrackDisplayInfo(
    val name: String,
    val chips: List<String> = emptyList(),
) : Parcelable {
    /**
     * Formatted string combining the name and chips, e.g. "English (Forced • SRT)" Useful for
     * dialogs or simple text views that do not render chips individually.
     */
    val fullTitle: String
        get() =
            if (chips.isEmpty()) {
                name
            } else {
                "$name (${chips.joinToString(" • ")})"
            }
}
