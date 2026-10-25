package dev.jdtech.jellyfin.player.core.domain

import androidx.core.net.toUri
import org.jellyfin.sdk.model.api.PlayMethod

/**
 * Utility for determining the accurate [PlayMethod] (Direct Play, Direct Stream, or Transcode)
 * based on the server media source, requested settings, and transcoding parameters.
 */
object PlayMethodHelper {
    private val VIDEO_TRANSCODE_REASONS =
        setOf(
            "VideoCodecNotSupported",
            "VideoProfileNotSupported",
            "VideoLevelNotSupported",
            "VideoResolutionNotSupported",
            "VideoBitrateExceedsLimit",
            "VideoFramerateExceedsLimit",
            "RefFramesNotSupported",
            "AnamorphicVideoNotSupported",
            "InterlacedVideoNotSupported",
            "VideoRangeTypeNotSupported",
            "ContainerBitrateExceedsLimit",
        )

    /**
     * Determines the active [PlayMethod].
     *
     * @param supportsDirectPlay Whether the media source supports direct play.
     * @param enableDirectPlay Whether direct play was allowed in the request.
     * @param transcodingUrl The transcoding URL returned by the server, if any.
     * @return [PlayMethod.DIRECT_PLAY], [PlayMethod.DIRECT_STREAM], or [PlayMethod.TRANSCODE].
     */
    fun determinePlayMethod(
        supportsDirectPlay: Boolean,
        enableDirectPlay: Boolean,
        transcodingUrl: String?,
    ): PlayMethod {
        if (transcodingUrl.isNullOrBlank()) {
            return if (supportsDirectPlay && enableDirectPlay) {
                PlayMethod.DIRECT_PLAY
            } else {
                PlayMethod.TRANSCODE
            }
        }

        if (supportsDirectPlay && enableDirectPlay && transcodingUrl.contains("static=true")) {
            return PlayMethod.DIRECT_PLAY
        }

        val uri = runCatching { transcodingUrl.toUri() }.getOrNull()
        val reasons =
            uri?.getQueryParameter("TranscodeReasons")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() } ?: emptyList()

        if (reasons.isEmpty()) {
            return if (supportsDirectPlay && enableDirectPlay) PlayMethod.DIRECT_PLAY
            else PlayMethod.DIRECT_STREAM
        }

        val hasVideoTranscodeReason = reasons.any { it in VIDEO_TRANSCODE_REASONS }
        return if (hasVideoTranscodeReason) {
            PlayMethod.TRANSCODE
        } else {
            PlayMethod.DIRECT_STREAM
        }
    }
}
