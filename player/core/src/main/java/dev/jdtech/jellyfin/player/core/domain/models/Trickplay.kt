package dev.jdtech.jellyfin.player.core.domain.models

import android.graphics.Bitmap

data class Trickplay(val interval: Int, val images: List<Bitmap>)

val Trickplay.intervalMs: Long
    get() = if (interval > 1000) interval.toLong() else interval.toLong() * 1000L
