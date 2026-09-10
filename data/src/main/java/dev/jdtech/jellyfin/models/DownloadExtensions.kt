package dev.jdtech.jellyfin.models

import android.content.Context
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType

fun FindroidSources.diskSize(): Long = sources.sumOf { it.size }

fun FindroidShow.totalDiskSize(episodes: List<FindroidEpisode>): Long =
    episodes.sumOf { it.diskSize() }

fun formatSeasonRanges(
    seasonNumbers: List<Int>,
    context: Context? = null,
    seasonSingleRes: Int? = null,
    seasonPairRes: Int? = null,
    seasonMultipleRes: Int? = null,
): String {
    val sorted = seasonNumbers.filter { it > 0 }.distinct().sorted()
    if (sorted.isEmpty()) return ""
    if (context != null && seasonSingleRes != null && seasonPairRes != null && seasonMultipleRes != null) {
        if (sorted.size == 1) return context.getString(seasonSingleRes, sorted[0])
        if (sorted.size == 2 && sorted[1] == sorted[0] + 1) {
            return context.getString(seasonPairRes, sorted[0], sorted[1])
        }
    } else {
        if (sorted.size == 1) return "Season ${sorted[0]}"
        if (sorted.size == 2 && sorted[1] == sorted[0] + 1) {
            return "Seasons ${sorted[0]} & ${sorted[1]}"
        }
    }
    val ranges = mutableListOf<String>()
    var start = sorted[0]
    var prev = sorted[0]

    fun addRange(s: Int, p: Int) {
        when {
            p == s -> ranges.add("$s")
            p == s + 1 -> ranges.add("$s, $p")
            else -> ranges.add("$s–$p")
        }
    }

    for (i in 1 until sorted.size) {
        val curr = sorted[i]
        if (curr == prev + 1) {
            prev = curr
        } else {
            addRange(start, prev)
            start = curr
            prev = curr
        }
    }
    addRange(start, prev)
    val joined = ranges.joinToString(", ")
    return if (context != null && seasonMultipleRes != null) {
        context.getString(seasonMultipleRes, joined)
    } else {
        "Seasons $joined"
    }
}

fun formatSeasonsString(
    seasonNumbers: List<Int>,
    episodeCount: Int,
    context: Context? = null,
    seasonSingleRes: Int? = null,
    seasonPairRes: Int? = null,
    seasonMultipleRes: Int? = null,
    episodesPluralRes: Int? = null,
): String {
    val seasonsPart = formatSeasonRanges(seasonNumbers, context, seasonSingleRes, seasonPairRes, seasonMultipleRes)
    val epString = if (context != null && episodesPluralRes != null) {
        context.resources.getQuantityString(episodesPluralRes, episodeCount, episodeCount)
    } else {
        if (episodeCount == 1) "1 episode" else "$episodeCount episodes"
    }
    return if (seasonsPart.isEmpty()) epString else "$seasonsPart • $epString"
}

fun FindroidItem.getQualityLabel(): String {
    val videoStream =
        sources
            .flatMap { it.mediaStreams }
            .firstOrNull { it.type == MediaStreamType.VIDEO }
    val height = videoStream?.height
    val isHdr =
        videoStream?.videoRangeType != null &&
            videoStream.videoRangeType != VideoRangeType.SDR
    return when {
        height != null && height >= 2160 -> if (isHdr) "4K HDR" else "4K"
        height != null && height >= 1080 -> "1080p Balanced"
        height != null && height >= 720 -> "720p Mobile"
        height != null && height >= 480 -> "480p SD"
        else -> "1080p Balanced"
    }
}

fun formatDuration(runtimeTicks: Long): String {
    val totalMinutes = runtimeTicks.div(600_000_000)
    if (totalMinutes <= 0) return ""
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
    } else {
        "${minutes}m"
    }
}
