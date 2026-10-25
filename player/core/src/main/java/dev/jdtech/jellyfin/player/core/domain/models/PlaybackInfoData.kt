package dev.jdtech.jellyfin.player.core.domain.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Domain model holding detailed playback stats, transcoding information, media specs, and nerd
 * stats (including 60s telemetry histories) for the "Playback Info" overlay screen.
 */
@Parcelize
data class PlaybackInfoData(
    // 1. Playback Info
    val playerBackend: String = "ExoPlayer",
    val playMethod: String = "Direct Play",
    val streamType: String = "Direct",
    val playerResolution: String = "",
    val videoResolution: String = "",

    // 2. Transcode Info
    val isTranscoding: Boolean = false,
    val transcodeVideoCodec: String? = null,
    val transcodeAudioCodec: String? = null,
    val transcodeResolution: String? = null,
    val transcodeProgressRatio: Float? = null,
    val transcodeProgressText: String? = null,
    val transcodeSpeed: String? = null,
    val transcodeBitrate: String? = null,
    val transcodeReasons: List<String> = emptyList(),
    val transcodeSpeedHistory: List<Float> = emptyList(),

    // 3. Media Content Info
    val container: String? = null,
    val sizeText: String? = null,
    val bitrateText: String? = null,
    val videoCodec: String? = null,
    val videoProfile: String? = null,
    val videoFrameRate: String? = null,
    val videoBitDepth: String? = null,
    val dynamicRange: String? = null,
    val audioCodec: String? = null,
    val audioChannels: String? = null,
    val audioSampleRate: String? = null,

    // 4. Nerd Stats
    val droppedFrames: String = "0",
    val bufferHealth: String? = null,
    val playbackStatus: String? = null,
    val audioOutput: String? = null,
    val bufferHealthHistory: List<Float> = emptyList(),
) : Parcelable {
    /** Whether any video metadata properties are present. */
    val hasVideoInfo: Boolean
        get() =
            videoCodec != null ||
                videoProfile != null ||
                videoFrameRate != null ||
                videoBitDepth != null ||
                dynamicRange != null

    /** Whether any audio metadata properties are present. */
    val hasAudioInfo: Boolean
        get() = audioCodec != null || audioChannels != null || audioSampleRate != null
}
