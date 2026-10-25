package dev.jdtech.jellyfin.player.core.domain.utils

import java.util.UUID

/**
 * Common utilities for calculating streaming bitrates and constructing stream URLs for both local
 * and remote (Cast) players.
 */
object StreamUtils {

    /**
     * Computes the effective streaming bitrate in bits per second (bps) by combining user settings
     * (e.g., maximum transcode bitrate limit) and the requested bitrate / measured network speed.
     *
     * @param settingMaxBitrateKbps The configured maximum bitrate in Kbps from preferences (0 or
     *   negative means unlimited).
     * @param requestedBitrate The target bitrate in bps (e.g. from selected quality preset or auto
     *   speed test).
     * @param isOriginal Whether original/direct quality was chosen.
     * @return The effective bitrate in bps, or null if original/unrestricted.
     */
    fun computeEffectiveBitrate(
        settingMaxBitrateKbps: Int,
        requestedBitrate: Int? = null,
        isOriginal: Boolean = false,
        isAuto: Boolean = false,
    ): Int? {
        val settingBitrateBps =
            (settingMaxBitrateKbps * 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return when {
            isOriginal -> null
            isAuto && settingBitrateBps > 0 && requestedBitrate != null ->
                minOf(requestedBitrate, settingBitrateBps)
            isAuto && settingBitrateBps > 0 -> settingBitrateBps
            requestedBitrate != null -> requestedBitrate
            else -> null
        }
    }

    /**
     * Builds a full playback URL, handling both direct play endpoints and transcoding/remuxing
     * endpoints. Ensures consistent handling of leading slashes, base URL trimming, and external
     * audio stream index injection.
     *
     * @param baseUrl Server base URL.
     * @param itemId Item UUID.
     * @param mediaSourceId Media source ID.
     * @param transcodingUrl Relative or absolute transcoding URL from server playback info.
     * @param audioStreamIndex Optional external or selected audio stream index.
     * @return Full stream URL formatted for playback.
     */
    fun buildStreamUrl(
        baseUrl: String,
        itemId: UUID,
        mediaSourceId: String?,
        transcodingUrl: String?,
        audioStreamIndex: Int? = null,
    ): String {
        val cleanBase = baseUrl.trimEnd('/')
        val url =
            if (!transcodingUrl.isNullOrBlank()) {
                val cleanPath =
                    if (transcodingUrl.startsWith("/")) transcodingUrl else "/$transcodingUrl"
                "$cleanBase$cleanPath"
            } else {
                val sourceParam =
                    if (!mediaSourceId.isNullOrBlank()) "&MediaSourceId=$mediaSourceId" else ""
                "$cleanBase/Videos/$itemId/stream?static=true$sourceParam"
            }

        return if (audioStreamIndex != null) {
            if (url.contains(Regex("(?i)AudioStreamIndex="))) {
                url.replace(
                    Regex("(?i)AudioStreamIndex=\\d+"),
                    "AudioStreamIndex=$audioStreamIndex",
                )
            } else {
                val sep = if (url.contains("?")) "&" else "?"
                "$url${sep}AudioStreamIndex=$audioStreamIndex"
            }
        } else {
            url
        }
    }
}
