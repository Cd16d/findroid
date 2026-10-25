package dev.jdtech.jellyfin.player.core.domain.models

import android.os.Parcelable
import androidx.media3.common.C
import kotlinx.parcelize.Parcelize

/**
 * Domain model representing an audio, subtitle, or video track.
 *
 * @property id Unique track identifier.
 * @property label Human-readable track label or title.
 * @property language ISO language code.
 * @property codec Codec identifier string.
 * @property selected Whether this track is currently selected for playback.
 * @property supported Whether this track format is supported by the player.
 * @property isExternal Whether the track originates from an external file.
 * @property isForced Whether the track is forced (e.g. forced subtitles).
 * @property isHearingImpaired Whether the track is tailored for hearing impaired viewers (SDH/CC).
 * @property channelLayout Text description of audio channel layout.
 * @property channelCount Number of audio channels.
 * @property type Track type integer constant from [androidx.media3.common.C].
 */
@Parcelize
data class Track(
    val id: Int,
    val label: String?,
    val language: String?,
    val codec: String?,
    val selected: Boolean,
    val supported: Boolean,
    val isExternal: Boolean? = false,
    val isForced: Boolean? = false,
    val isHearingImpaired: Boolean? = false,
    val isDefault: Boolean? = false,
    val isOriginal: Boolean? = false,
    val channelLayout: String? = null,
    val channelCount: Int? = null,
    val type: Int = C.TRACK_TYPE_UNKNOWN,
) : Parcelable
