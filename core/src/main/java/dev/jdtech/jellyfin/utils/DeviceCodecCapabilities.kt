package dev.jdtech.jellyfin.utils

import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.deviceprofile.buildDeviceProfile

data class CodecInfo(
    val id: String,
    val name: String,
    val mimeTypes: List<String>,
    val isSupported: Boolean,
)

data class DeviceCodecsOverview(
    val deviceName: String,
    val videoCodecs: List<CodecInfo>,
    val audioCodecs: List<CodecInfo>,
)

object DeviceCodecCapabilities {

    // Video codec specifications
    private val VIDEO_DEFINITIONS =
        listOf(
            "H.264" to listOf(MediaFormat.MIMETYPE_VIDEO_AVC, "video/avc"),
            "H.265" to listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, "video/hevc"),
            "H.266" to listOf("video/vvc", "video/h266"),
            "VP8" to listOf(MediaFormat.MIMETYPE_VIDEO_VP8, "video/x-vnd.on2.vp8"),
            "VP9" to listOf(MediaFormat.MIMETYPE_VIDEO_VP9, "video/x-vnd.on2.vp9"),
            "AV1" to listOf(MediaFormat.MIMETYPE_VIDEO_AV1, "video/av01"),
        )

    // Audio codec specifications
    private val AUDIO_DEFINITIONS =
        listOf(
            "AAC" to listOf(MediaFormat.MIMETYPE_AUDIO_AAC, "audio/mp4a-latm"),
            "MP3" to listOf(MediaFormat.MIMETYPE_AUDIO_MPEG, "audio/mpeg"),
            "Vorbis" to listOf(MediaFormat.MIMETYPE_AUDIO_VORBIS, "audio/vorbis"),
            "Opus" to listOf(MediaFormat.MIMETYPE_AUDIO_OPUS, "audio/opus"),
            "FLAC" to listOf(MediaFormat.MIMETYPE_AUDIO_FLAC, "audio/flac"),
            "ALAC" to listOf("audio/alac", "audio/x-alac"),
            "PCM" to listOf(MediaFormat.MIMETYPE_AUDIO_RAW, "audio/raw"),
            "AC-3" to listOf(MediaFormat.MIMETYPE_AUDIO_AC3, "audio/ac3"),
            "E-AC-3" to listOf(MediaFormat.MIMETYPE_AUDIO_EAC3, "audio/eac3"),
            "DTS" to listOf("audio/vnd.dts", "audio/dts"),
            "DTS-HD" to listOf("audio/vnd.dts.hd", "audio/dts-hd"),
            "TrueHD" to listOf("audio/true-hd", "audio/vnd.dolby.mlp"),
        )

    private val cachedDeviceCodecs by lazy { computeDeviceCodecs() }

    fun getDeviceCodecs(): DeviceCodecsOverview = cachedDeviceCodecs

    private fun computeDeviceCodecs(): DeviceCodecsOverview {
        val supportedMimeTypes = mutableSetOf<String>()

        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            for (info in codecList.codecInfos) {
                if (info.isEncoder) continue
                for (type in info.supportedTypes) {
                    supportedMimeTypes.add(type.lowercase())
                }
            }
        } catch (e: Exception) {
            // Fallback for security restrictions
            supportedMimeTypes.addAll(
                listOf(
                    MediaFormat.MIMETYPE_VIDEO_AVC.lowercase(),
                    MediaFormat.MIMETYPE_VIDEO_VP8.lowercase(),
                    MediaFormat.MIMETYPE_AUDIO_AAC.lowercase(),
                    MediaFormat.MIMETYPE_AUDIO_MPEG.lowercase(),
                    MediaFormat.MIMETYPE_AUDIO_RAW.lowercase(),
                )
            )
        }

        // PCM is inherently supported by Android AudioTrack
        supportedMimeTypes.add(MediaFormat.MIMETYPE_AUDIO_RAW.lowercase())
        supportedMimeTypes.add("audio/raw")

        val videoList = VIDEO_DEFINITIONS.map { (name, mimes) ->
            val isSupported = mimes.any { supportedMimeTypes.contains(it.lowercase()) }
            CodecInfo(
                id = name.lowercase().replace(".", ""),
                name = name,
                mimeTypes = mimes,
                isSupported = isSupported,
            )
        }

        val audioList = AUDIO_DEFINITIONS.map { (name, mimes) ->
            val isSupported =
                if (name == "PCM") true
                else mimes.any { supportedMimeTypes.contains(it.lowercase()) }
            CodecInfo(
                id = name.lowercase().replace("-", "").replace(".", ""),
                name = name,
                mimeTypes = mimes,
                isSupported = isSupported,
            )
        }

        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

        return DeviceCodecsOverview(
            deviceName = deviceName,
            videoCodecs = videoList,
            audioCodecs = audioList,
        )
    }

    /** Comma-separated video codecs for Jellyfin transcode/stream request */
    fun getSupportedVideoCodecsForJellyfin(): String {
        val supported = mutableListOf("h264")
        val overview = getDeviceCodecs()
        for (codec in overview.videoCodecs) {
            if (codec.isSupported) {
                when (codec.name) {
                    "H.265" -> supported.add("hevc")
                    "VP9" -> supported.add("vp9")
                    "AV1" -> supported.add("av1")
                }
            }
        }
        return supported.distinct().joinToString(",")
    }

    /** Comma-separated audio codecs for Jellyfin transcode/stream request */
    fun getSupportedAudioCodecsForJellyfin(): String {
        val supported =
            mutableListOf(
                "aac",
                "mp3",
                "opus",
                "flac",
                "vorbis",
                "ac3",
                "eac3",
                "alac",
                "pcm",
                "wav",
                "dts",
                "dtshd",
                "truehd",
            )
        return supported.distinct().joinToString(",")
    }

    /**
     * Checks if a video codec name (e.g. "hevc", "h264", "av1", "vp9") is supported by device
     * decoders
     */
    fun isVideoCodecSupported(codec: String?): Boolean {
        if (codec.isNullOrBlank()) return true
        val c = codec.lowercase()
        val overview = getDeviceCodecs()
        val targetName =
            when {
                c.contains("h264") || c.contains("avc") -> "H.264"
                c.contains("hevc") || c.contains("h265") -> "H.265"
                c.contains("vp9") -> "VP9"
                c.contains("vp8") -> "VP8"
                c.contains("av1") || c.contains("av01") -> "AV1"
                c.contains("vvc") || c.contains("h266") -> "H.266"
                else -> return false
            }
        return overview.videoCodecs.firstOrNull { it.name == targetName }?.isSupported ?: false
    }

    /** Checks if an audio codec name is supported by device decoders */
    fun isAudioCodecSupported(codec: String?): Boolean {
        if (codec.isNullOrBlank()) return true
        val c = codec.lowercase()
        val overview = getDeviceCodecs()
        val targetName =
            when {
                c.contains("aac") -> "AAC"
                c.contains("mp3") || c.contains("mpeg") -> "MP3"
                c.contains("opus") -> "Opus"
                c.contains("flac") -> "FLAC"
                c.contains("eac3") || c.contains("e-ac-3") -> "E-AC-3"
                c.contains("ac3") || c.contains("ac-3") -> "AC-3"
                c.contains("vorbis") -> "Vorbis"
                c.contains("alac") -> "ALAC"
                c.contains("pcm") || c.contains("raw") -> "PCM"
                c.contains("dts-hd") -> "DTS-HD"
                c.contains("dts") -> "DTS"
                c.contains("truehd") -> "TrueHD"
                else -> return false
            }
        return overview.audioCodecs.firstOrNull { it.name == targetName }?.isSupported ?: false
    }

    /**
     * Builds a [DeviceProfile] representing this Android device's hardware decoders and streaming
     * capabilities.
     *
     * @param maxBitrate Target maximum streaming bitrate in bits per second.
     * @param maxHeight Optional video height limit constraint (for resolution-capped transcoding).
     * @param maxWidth Optional video width limit constraint.
     */
    fun createDeviceProfile(
        maxBitrate: Int = 120_000_000,
        maxHeight: Int? = null,
        maxWidth: Int? = null,
    ): DeviceProfile {
        val supportedVideo = getSupportedVideoCodecsForJellyfin()
        val supportedAudio = getSupportedAudioCodecsForJellyfin()

        return buildDeviceProfile {
            name = "Findroid Android Device"
            maxStreamingBitrate = maxBitrate
            maxStaticBitrate = maxBitrate

            if (maxHeight != null || maxWidth != null) {
                codecProfile {
                    type = CodecType.VIDEO
                    conditions {
                        if (maxHeight != null && maxHeight > 0) {
                            lowerThanOrEquals(ProfileConditionValue.HEIGHT, maxHeight)
                        }
                        if (maxWidth != null && maxWidth > 0) {
                            lowerThanOrEquals(ProfileConditionValue.WIDTH, maxWidth)
                        }
                    }
                }
            }

            directPlayProfile {
                container("mp4", "m4v", "mkv", "webm", "ts")
                type = DlnaProfileType.VIDEO
                videoCodec(supportedVideo)
                audioCodec(supportedAudio)
            }

            transcodingProfile {
                container = "ts"
                type = DlnaProfileType.VIDEO
                if (isVideoCodecSupported("hevc")) {
                    videoCodec("hevc", "h264")
                } else {
                    videoCodec("h264")
                }
                audioCodec("aac", "mp3", "ac3", "eac3", "opus")
                protocol = MediaStreamProtocol.HLS
                context = EncodingContext.STREAMING
                minSegments = 2
                breakOnNonKeyFrames = true
                maxAudioChannels = "6"
            }

            subtitleProfile("srt", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("ass", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("vtt", SubtitleDeliveryMethod.EXTERNAL)
            subtitleProfile("vtt", SubtitleDeliveryMethod.EMBED)
        }
    }
}
