package dev.jdtech.jellyfin.player.core.domain.models

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PlayerImage(
    val uri: Uri?,
    val blurHash: String?
) : Parcelable

@Parcelize
data class PlayerImages (
    val primary: PlayerImage? = null,
    val backdrop: PlayerImage? = null,
    val logo: PlayerImage? = null,
    val showPrimary: PlayerImage? = null,
    val showBackdrop: PlayerImage? = null,
    val showLogo: PlayerImage? = null
) : Parcelable
