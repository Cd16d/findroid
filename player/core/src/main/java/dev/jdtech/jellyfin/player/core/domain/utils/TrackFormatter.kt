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
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * Utility object for formatting media track information such as codecs, channel layouts, languages,
 * and display labels.
 */
object TrackFormatter {
    private val WHITESPACE_REGEX = Regex("\\s+")

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

    /** Strips codec information from a track label string to avoid duplicate text. */
    private fun stripCodec(label: String?, codec: String?): String? {
        if (label.isNullOrBlank()) return null
        val formattedCodec = formatCodec(codec)
        val codecsToRemove =
            listOfNotNull(formattedCodec, codec).filter { it.isNotBlank() }.distinct()

        if (codecsToRemove.isEmpty()) return label.trim()

        val pattern = codecsToRemove.joinToString("|") { Regex.escape(it) }
        val regex = Regex("(?i)[(\\[\\-/\\s]*\\b($pattern)\\b[)\\]]*")

        return label
            .replace(regex, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim('-', '(', ')', '[', ']', '/', ' ')
            .takeIf { it.isNotBlank() }
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
        val cleanLabel = stripCodec(track.label, track.codec)
        val baseName =
            localizedLanguage
                ?: track.language?.takeIf { it.isNotBlank() && it != "und" }
                ?: cleanLabel?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.track_index, fallbackIndex)

        val isSubtitle =
            trackType == C.TRACK_TYPE_TEXT ||
                (trackType == C.TRACK_TYPE_UNKNOWN &&
                    track.channelCount == null &&
                    track.channelLayout == null)

        if (isSubtitle) {
            val tags = mutableListOf<String>()

            val isForced =
                track.isForced == true ||
                    track.label?.contains("forced", ignoreCase = true) == true ||
                    track.label?.contains("forzat", ignoreCase = true) == true ||
                    track.label?.contains("forcé", ignoreCase = true) == true
            if (isForced) {
                tags.add(context.getString(R.string.forced))
            }

            val isHearingImpaired =
                track.isHearingImpaired == true ||
                    track.label?.contains("sdh", ignoreCase = true) == true ||
                    track.label?.contains("cc", ignoreCase = true) == true ||
                    track.label?.contains("non udenti", ignoreCase = true) == true ||
                    track.label?.contains("sordi", ignoreCase = true) == true
            if (isHearingImpaired) {
                tags.add(context.getString(R.string.hearing_impaired))
            }

            val isOriginal =
                track.isOriginal == true ||
                    track.label?.contains("original", ignoreCase = true) == true ||
                    track.label?.contains("orig", ignoreCase = true) == true
            if (isOriginal) {
                tags.add("Original")
            }

            val formattedName =
                if (tags.isNotEmpty()) {
                    "$baseName (${tags.joinToString(", ")})"
                } else {
                    baseName
                }

            val chips = mutableListOf<String>()
            val formattedCodec = formatCodec(track.codec)
            if (!formattedCodec.isNullOrBlank()) {
                chips.add(formattedCodec)
            }

            if (displayExtraInfo) {
                val isExternal =
                    track.isExternal == true ||
                        track.label?.contains("external", ignoreCase = true) == true ||
                        track.label?.contains("estern", ignoreCase = true) == true
                if (isExternal) {
                    chips.add(context.getString(R.string.external))
                }
            }

            return TrackDisplayInfo(name = formattedName, chips = chips)
        }

        val cleanCandidate = cleanLabel?.split('-', '(', '[', '/')?.firstOrNull()?.trim()
        val candidateLanguage = formatLanguageName(cleanCandidate, currentLocale)
        val audioName =
            localizedLanguage
                ?: candidateLanguage
                ?: track.language?.takeIf { it.isNotBlank() && it != "und" }
                ?: cleanCandidate?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.track_index, fallbackIndex)

        val chips = mutableListOf<String>()
        if (track.isDefault == true) {
            chips.add(context.getString(CoreR.string.track_default))
        }

        val channelLayout = formatChannelLayout(track.channelLayout, track.channelCount)
        if (channelLayout != null) {
            chips.add(channelLayout)
        }

        val formattedCodec = formatCodec(track.codec)
        if (!formattedCodec.isNullOrBlank()) {
            chips.add(formattedCodec)
        }

        if (displayExtraInfo) {
            val isExternal =
                track.isExternal == true ||
                    track.label?.contains("external", ignoreCase = true) == true ||
                    track.label?.contains("estern", ignoreCase = true) == true
            if (isExternal) {
                chips.add(context.getString(R.string.external))
            }
        }

        return TrackDisplayInfo(name = audioName, chips = chips)
    }

    /** Converts an ExoPlayer [Tracks.Group] into a domain [Track] model. */
    fun toTrack(group: Tracks.Group, fallbackIndex: Int = 0): Track {
        val format = group.mediaTrackGroup.getFormat(0)
        val label = format.label
        val isForced =
            (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0 ||
                label?.contains("forced", ignoreCase = true) == true ||
                label?.contains("forzat", ignoreCase = true) == true ||
                label?.contains("forcé", ignoreCase = true) == true
        val isHearingImpaired =
            (format.roleFlags and
                (C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or
                    C.ROLE_FLAG_EASY_TO_READ or
                    C.ROLE_FLAG_TRANSCRIBES_DIALOG or
                    C.ROLE_FLAG_CAPTION)) != 0 ||
                label?.contains("sdh", ignoreCase = true) == true ||
                label?.contains("cc", ignoreCase = true) == true ||
                label?.contains("non udenti", ignoreCase = true) == true ||
                label?.contains("sordi", ignoreCase = true) == true
        val isExternal =
            format.id?.startsWith("external", ignoreCase = true) == true ||
                label?.contains("external", ignoreCase = true) == true ||
                label?.contains("estern", ignoreCase = true) == true
        val isDefault =
            (format.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0 ||
                label?.contains("default", ignoreCase = true) == true ||
                label?.contains("predefinit", ignoreCase = true) == true
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
        val isDefault =
            stream.isDefault == true ||
                stream.title.contains("default", ignoreCase = true) ||
                stream.displayTitle?.contains("default", ignoreCase = true) == true ||
                stream.displayTitle?.contains("predefinit", ignoreCase = true) == true
        return Track(
            id = stream.index ?: 0,
            label = stream.title.takeIf { it.isNotBlank() } ?: stream.displayTitle,
            language = stream.language,
            codec = stream.codec,
            selected = false,
            supported = true,
            isExternal = stream.isExternal,
            isForced = stream.isForced,
            isHearingImpaired = stream.isHearingImpaired,
            isDefault = isDefault,
            isOriginal = stream.isOriginal,
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

    /** Converts a Jellyfin SDK [MediaStream] into a domain [Track] model. */
    fun toTrack(stream: MediaStream): Track {
        val isDefault =
            stream.isDefault ||
                stream.title?.contains("default", ignoreCase = true) == true ||
                stream.displayTitle?.contains("default", ignoreCase = true) == true ||
                stream.displayTitle?.contains("predefinit", ignoreCase = true) == true
        return Track(
            id = stream.index,
            label = stream.title?.takeIf { it.isNotBlank() } ?: stream.displayTitle,
            language = stream.language,
            codec = stream.codec,
            selected = false,
            supported = true,
            isExternal = stream.isExternal,
            isForced = stream.isForced,
            isHearingImpaired = stream.isHearingImpaired,
            isDefault = isDefault,
            isOriginal = stream.isOriginal,
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

    /**
     * Merges information from a Jellyfin SDK [MediaStream] into a domain [Track] model, preferring
     * API metadata when available.
     */
    fun mergeWithStream(track: Track, stream: MediaStream?): Track {
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

/** Converts an ExoPlayer [Tracks.Group] into a domain [Track] model. */
fun Tracks.Group.toTrack(fallbackIndex: Int = 0): Track =
    TrackFormatter.toTrack(this, fallbackIndex)

/** Converts a [FindroidMediaStream] into a domain [Track] model. */
fun FindroidMediaStream.toTrack(): Track = TrackFormatter.toTrack(this)

/** Converts a Jellyfin SDK [MediaStream] into a domain [Track] model. */
fun MediaStream.toTrack(): Track = TrackFormatter.toTrack(this)
