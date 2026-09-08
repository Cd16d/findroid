package dev.jdtech.jellyfin.core.presentation.downloader

/** Outcome of one download-speed sample. */
data class SpeedSample(
    /** Transfer rate in bytes/sec to display. */
    val bytesPerSecond: Long,
    /** True when real progress was observed and the sample clock should advance. */
    val advanced: Boolean,
)

/**
 * Computes a smoothed download speed using a multi-second sliding window and exponential moving average.
 *
 * Keeps the transfer rate and ETA stable over network jitter and bursty I/O.
 */
class DownloadSpeedTracker(
    private val windowDurationMs: Long = 15_000L,
    private val emaAlpha: Double = 0.15,
) {
    private data class Sample(val bytes: Long, val timeMs: Long)
    private val samples = ArrayDeque<Sample>()
    private var smoothedSpeed: Long = 0L
    private var smoothedEtaSeconds: Long = -1L

    fun record(currentBytes: Long, nowMs: Long): Long {
        if (currentBytes < 0) return smoothedSpeed

        // Add latest sample
        samples.addLast(Sample(currentBytes, nowMs))

        // Evict samples older than windowDurationMs, but retain at least 2 samples
        while (samples.size > 2 && (nowMs - samples.first().timeMs) > windowDurationMs) {
            samples.removeFirst()
        }

        val oldest = samples.first()
        val newest = samples.last()

        if (newest.bytes < oldest.bytes) {
            samples.clear()
            samples.addLast(newest)
            smoothedSpeed = 0L
            return 0L
        }

        val deltaBytes = newest.bytes - oldest.bytes
        val elapsedMs = newest.timeMs - oldest.timeMs

        if (elapsedMs > 0 && deltaBytes >= 0) {
            val windowSpeed = (deltaBytes * 1000L / elapsedMs)
            smoothedSpeed = if (smoothedSpeed <= 0L) {
                windowSpeed
            } else {
                (smoothedSpeed * (1.0 - emaAlpha) + windowSpeed * emaAlpha).toLong()
            }
        }

        return smoothedSpeed.coerceAtLeast(0L)
    }

    fun calculateEtaSeconds(remainingBytes: Long): Long {
        if (remainingBytes <= 0L || smoothedSpeed <= 0L) {
            smoothedEtaSeconds = -1L
            return -1L
        }
        val instantEta = remainingBytes / smoothedSpeed
        smoothedEtaSeconds = if (smoothedEtaSeconds <= 0L) {
            instantEta
        } else {
            (smoothedEtaSeconds * 0.82 + instantEta * 0.18).toLong()
        }
        return smoothedEtaSeconds
    }

    fun reset() {
        samples.clear()
        smoothedSpeed = 0L
        smoothedEtaSeconds = -1L
    }
}

/**
 * Formats ETA seconds into a stable, clean user-facing string (e.g. "< 1 min", "3 min", "1 h 10 min").
 */
fun formatStableEta(etaSeconds: Long): String {
    if (etaSeconds <= 0L) return ""
    return when {
        etaSeconds < 60 -> "${etaSeconds}s"
        etaSeconds < 3600 -> {
            val mins = etaSeconds / 60
            val secs = etaSeconds % 60
            if (secs > 0) "${mins}m ${secs}s" else "${mins}m"
        }
        else -> {
            val hours = etaSeconds / 3600
            val mins = (etaSeconds % 3600) / 60
            if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
        }
    }
}

/**
 * Computes the download speed for one poll tick.
 *
 * Keeps the last known speed across flat polls and only advances the sample clock on real progress,
 * so the speed + ETA don't flash 0 in the UI.
 */
fun nextDownloadSpeed(
    prevSample: Pair<Long, Long>?,
    currentBytes: Long,
    nowMs: Long,
    previousSpeed: Long,
): SpeedSample {
    if (currentBytes < 0) return SpeedSample(previousSpeed, advanced = false)
    if (prevSample == null) return SpeedSample(previousSpeed, advanced = true)
    val (prevBytes, prevAt) = prevSample
    val deltaBytes = currentBytes - prevBytes
    val elapsedMs = nowMs - prevAt
    return if (deltaBytes > 0 && elapsedMs > 0) {
        SpeedSample((deltaBytes * 1000L / elapsedMs).coerceAtLeast(0L), advanced = true)
    } else {
        SpeedSample(previousSpeed, advanced = false)
    }
}
