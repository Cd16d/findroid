package dev.jdtech.jellyfin.player.core.domain.models

import android.os.Parcelable
import androidx.media3.common.C
import kotlinx.parcelize.Parcelize

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
    val channelLayout: String? = null,
    val channelCount: Int? = null,
    val type: Int = C.TRACK_TYPE_UNKNOWN,
) : Parcelable
