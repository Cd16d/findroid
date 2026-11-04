package dev.jdtech.jellyfin.player.core.domain.utils

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Tracks
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.player.core.R
import dev.jdtech.jellyfin.player.core.domain.models.Track
import dev.jdtech.jellyfin.player.core.domain.models.TrackDisplayInfo
import java.util.Locale
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Utility object for formatting media track information such as codecs, channel layouts, languages,
 * and display labels.
 */
object TrackFormatter {

    /** Formats a raw codec string into a human-readable display label. */
    fun formatCodec(codec: String?): String? {
        if (codec.isNullOrBlank()) return null
        val c = codec.lowercase(Locale.US)
        return when {
            c.contains("subrip") || c.contains("srt") -> "SubRip"
            c.contains("ass") || c.contains("ssa") || c.contains("substationalpha") -> "ASS"
            c.contains("pgs") || c.contains("hdmv_pgs") -> "PGS"
            c.contains("vtt") || c.contains("webvtt") -> "VTT"
            c.contains("dts-hd ma") -> "DTS-HD MA"
            c.contains("dts-hd hra") -> "DTS-HD HRA"
            c.contains("dts-hd") || c.contains("dtshd") -> "DTS-HD"
            c.contains("dts:x") || c.contains("dtsx") -> "DTS:X"
            c.contains("dts") || c == "dca" -> "DTS"
            c.contains("truehd") || c.contains("mlp") -> "TrueHD"
            c.contains("eac3") || c.contains("ec-3") || c.contains("e-ac-3") -> "E-AC-3"
            c.contains("ac3") || c.contains("ac-3") -> "AC-3"
            c.contains("flac") -> "FLAC"
            c.contains("alac") -> "ALAC"
            c.contains("aac") -> "AAC"
            c.contains("opus") -> "Opus"
            c.contains("vorbis") -> "Vorbis"
            c.contains("mp3") -> "MP3"
            c.contains("pcm") -> "PCM"
            c.contains("hevc") || c.contains("h265") -> "HEVC"
            c.contains("h264") || c.contains("avc") -> "AVC"
            c.contains("av1") -> "AV1"
            c.contains("dvd_subtitle") || c.contains("dvdsub") -> "DVD Subtitle"
            else -> codec.uppercase(Locale.US)
        }
    }

    /**
     * Formats audio channel layout or channel count into a standardized string (e.g. "Stereo",
     * "5.1").
     */
    fun formatChannelLayout(channelLayout: String? = null, channelCount: Int? = null): String? {
        if (!channelLayout.isNullOrBlank()) {
            val layout = channelLayout.trim().lowercase(Locale.US)
            return when {
                layout == "stereo" || layout == "2.0" -> "Stereo"
                layout == "mono" || layout == "1.0" -> "Mono"
                layout.startsWith("5.1") -> "5.1"
                layout.startsWith("7.1") -> "7.1"
                layout.startsWith("2.1") -> "2.1"
                layout.startsWith("6.1") -> "6.1"
                layout == "atmos" -> "Atmos"
                else ->
                    channelLayout.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                    }
            }
        }
        return when (channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            3 -> "2.1"
            6 -> "5.1"
            7 -> "6.1"
            8 -> "7.1"
            else -> if (channelCount != null && channelCount > 0) "$channelCount ch" else null
        }
    }

    /** Formats an ISO language code into a localized display language name. */
    fun formatLanguageName(
        language: String?,
        currentLocale: Locale = Locale.getDefault(),
    ): String? {
        val langTag = language?.takeIf { it.isNotBlank() && it != "und" } ?: return null
        val cleanLang = langTag.split("-").firstOrNull() ?: langTag
        val localeFromTag = Locale.forLanguageTag(cleanLang.replace("_", "-"))
        var display = localeFromTag.getDisplayLanguage(currentLocale)
        if (display.isBlank() || display.equals(cleanLang, ignoreCase = true)) {
            val fallbackLocale = @Suppress("DEPRECATION") Locale(cleanLang)
            val fallbackDisplay = fallbackLocale.getDisplayLanguage(currentLocale)
            if (fallbackDisplay.isNotBlank()) {
                display = fallbackDisplay
            }
        }
        return display
            .takeIf { it.isNotBlank() }
            ?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(currentLocale) else it.toString()
            }
    }

    /**
     * Creates formatted track display information including localized names and status tags/chips
     * for UI display.
     */
    fun getTrackDisplayInfo(
        track: Track,
        context: Context,
        displayExtraInfo: Boolean = false,
        currentLocale: Locale = Locale.getDefault(),
        fallbackIndex: Int = track.id,
        trackType: Int = track.type,
    ): TrackDisplayInfo {
        val localizedLanguage = formatLanguageName(track.language, currentLocale)
        var name =
            localizedLanguage
                ?: track.label?.takeIf { it.isNotBlank() }
                ?: track.language?.takeIf { it.isNotBlank() && it != "und" }
                ?: context.getString(R.string.track_index, fallbackIndex)

        val isSubtitle =
            trackType == C.TRACK_TYPE_TEXT ||
                (trackType == C.TRACK_TYPE_UNKNOWN &&
                    track.channelCount == null &&
                    track.channelLayout == null)

        val chips = mutableListOf<String>()

        if (track.isDefault == true) {
            chips.add(context.getString(CoreR.string.track_default))
        }

        if (isSubtitle) {
            val subtitleAttributes = mutableListOf<String>()
            if (track.isForced == true) {
                subtitleAttributes.add(context.getString(R.string.forced))
            }
            if (track.isHearingImpaired == true) {
                subtitleAttributes.add(context.getString(R.string.hearing_impaired))
            }
            if (subtitleAttributes.isNotEmpty()) {
                name = "$name (${subtitleAttributes.joinToString(", ")})"
            }
            if (track.isOriginal == true) {
                chips.add(context.getString(R.string.track_original))
            }
            val formattedCodec = formatCodec(track.codec)
            if (!formattedCodec.isNullOrBlank()) {
                chips.add(formattedCodec)
            }
            if (displayExtraInfo && track.isExternal == true) {
                chips.add(context.getString(R.string.external))
            }
        } else {
            val channelLayout = formatChannelLayout(track.channelLayout, track.channelCount)
            if (channelLayout != null) {
                chips.add(channelLayout)
            }
            val formattedCodec = formatCodec(track.codec)
            if (!formattedCodec.isNullOrBlank()) {
                chips.add(formattedCodec)
            }
            if (track.isOriginal == true) {
                chips.add(context.getString(R.string.track_original))
            }
            if (displayExtraInfo && track.isExternal == true) {
                chips.add(context.getString(R.string.external))
            }
        }

        return TrackDisplayInfo(name = name, chips = chips)
    }

    /** Converts an ExoPlayer [Tracks.Group] into a domain [Track] model. */
    fun toTrack(group: Tracks.Group, fallbackIndex: Int = 0): Track {
        val format = group.mediaTrackGroup.getFormat(0)
        val isForced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
        val isHearingImpaired =
            (format.roleFlags and
                (C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or
                    C.ROLE_FLAG_EASY_TO_READ or
                    C.ROLE_FLAG_TRANSCRIBES_DIALOG or
                    C.ROLE_FLAG_CAPTION)) != 0
        val isExternal = format.id?.startsWith("external", ignoreCase = true) == true
        val isDefault = (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0
        val rawCodec = format.codecs ?: format.sampleMimeType?.substringAfterLast("/")

        return Track(
            id = fallbackIndex,
            label = format.label,
            language = format.language,
            codec = rawCodec,
            selected = group.isSelected,
            supported = group.isSupported,
            isExternal = isExternal,
            isForced = isForced,
            isHearingImpaired = isHearingImpaired,
            isDefault = isDefault,
            channelCount = if (group.type == C.TRACK_TYPE_AUDIO) format.channelCount else null,
            type = group.type,
        )
    }

    /** Creates formatted track display information directly from an ExoPlayer [Tracks.Group]. */
    fun getTrackDisplayInfo(
        group: Tracks.Group,
        context: Context,
        displayExtraInfo: Boolean = false,
        currentLocale: Locale = Locale.getDefault(),
        fallbackIndex: Int = 1,
    ): TrackDisplayInfo {
        return getTrackDisplayInfo(
            track = toTrack(group, fallbackIndex),
            context = context,
            displayExtraInfo = displayExtraInfo,
            currentLocale = currentLocale,
            fallbackIndex = fallbackIndex,
            trackType = group.type,
        )
    }

    /** Converts a [FindroidMediaStream] into a domain [Track] model. */
    fun toTrack(stream: FindroidMediaStream): Track {
        return Track(
            id = stream.index ?: 0,
            label = stream.title.takeIf { it.isNotBlank() } ?: stream.displayTitle,
            language = stream.language.takeIf { it.isNotBlank() },
            codec = stream.codec.takeIf { it.isNotBlank() },
            selected = false,
            supported = true,
            isExternal = stream.isExternal,
            isForced = stream.isForced == true,
            isHearingImpaired = stream.isHearingImpaired == true,
            isDefault = stream.isDefault == true,
            isOriginal = stream.isOriginal == true,
            channelLayout = stream.channelLayout,
            channelCount = stream.channels,
            type =
                when (stream.type) {
                    MediaStreamType.AUDIO -> C.TRACK_TYPE_AUDIO
                    MediaStreamType.SUBTITLE -> C.TRACK_TYPE_TEXT
                    else -> C.TRACK_TYPE_UNKNOWN
                },
        )
    }

    /**
     * Merges information from a Jellyfin [FindroidMediaStream] into a domain [Track] model,
     * preferring API metadata when available.
     */
    fun mergeWithStream(track: Track, stream: FindroidMediaStream?): Track {
        if (stream == null) return track
        val streamTrack = toTrack(stream)
        return track.copy(
            label = track.label?.takeIf { it.isNotBlank() } ?: streamTrack.label,
            language =
                track.language?.takeIf { it.isNotBlank() && it != "und" } ?: streamTrack.language,
            codec = streamTrack.codec?.takeIf { it.isNotBlank() } ?: track.codec,
            isExternal = streamTrack.isExternal == true || track.isExternal == true,
            isForced = streamTrack.isForced == true || track.isForced == true,
            isHearingImpaired =
                streamTrack.isHearingImpaired == true || track.isHearingImpaired == true,
            isDefault = streamTrack.isDefault == true || track.isDefault == true,
            isOriginal = streamTrack.isOriginal == true || track.isOriginal == true,
            channelLayout = streamTrack.channelLayout ?: track.channelLayout,
            channelCount = streamTrack.channelCount ?: track.channelCount,
        )
    }
}
