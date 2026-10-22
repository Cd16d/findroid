package dev.jdtech.jellyfin.player.core.domain.models

import android.graphics.Bitmap

/**
 * Represents trickplay seek preview thumbnail data.
 *
 * @property interval Time interval between thumbnail frames.
 * @property images List of bitmap images representing scrubbing preview frames.
 */
data class Trickplay(val interval: Int, val images: List<Bitmap>)

/** Returns the trickplay frame interval normalized to milliseconds. */
val Trickplay.intervalMs: Long
    get() = if (interval > 1000) interval.toLong() else interval.toLong() * 1000L
