package dev.jdtech.jellyfin.models

import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType

fun FindroidMovie.diskSize(): Long = sources.sumOf { it.size }

fun FindroidEpisode.diskSize(): Long = sources.sumOf { it.size }

fun FindroidShow.totalDiskSize(episodes: List<FindroidEpisode>): Long =
    episodes.sumOf { it.diskSize() }

fun formatSeasonRanges(seasonNumbers: List<Int>): String {
    val sorted = seasonNumbers.filter { it > 0 }.distinct().sorted()
    if (sorted.isEmpty()) return ""
    if (sorted.size == 1) return "Season ${sorted[0]}"
    if (sorted.size == 2 && sorted[1] == sorted[0] + 1) {
        return "Seasons ${sorted[0]} & ${sorted[1]}"
    }
    val ranges = mutableListOf<String>()
    var start = sorted[0]
    var prev = sorted[0]
    for (i in 1 until sorted.size) {
        val curr = sorted[i]
        if (curr == prev + 1) {
            prev = curr
        } else {
            if (prev == start) {
                ranges.add("$start")
            } else if (prev == start + 1) {
                ranges.add("$start, $prev")
            } else {
                ranges.add("$start–$prev")
            }
            start = curr
            prev = curr
        }
    }
    if (prev == start) {
        ranges.add("$start")
    } else if (prev == start + 1) {
        ranges.add("$start, $prev")
    } else {
        ranges.add("$start–$prev")
    }
    return "Seasons ${ranges.joinToString(", ")}"
}

fun formatSeasonsString(seasonNumbers: List<Int>, episodeCount: Int): String {
    val seasonsPart = formatSeasonRanges(seasonNumbers)
    val epString = if (episodeCount == 1) "1 episode" else "$episodeCount episodes"
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
