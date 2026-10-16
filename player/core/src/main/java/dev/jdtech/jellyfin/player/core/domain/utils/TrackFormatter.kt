package dev.jdtech.jellyfin.player.core.domain.utils

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Tracks
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.player.core.R
import dev.jdtech.jellyfin.player.core.domain.models.Track
import dev.jdtech.jellyfin.player.core.domain.models.TrackDisplayInfo
import java.util.Locale

object TrackFormatter {
    fun formatCodec(codec: String?): String? {
        if (codec.isNullOrBlank()) return null
        val c = codec.lowercase(Locale.US)
        return when {
            c.contains("subrip") || c.contains("srt") -> "SubRip"
            c.contains("ass") || c.contains("ssa") -> "ASS"
            c.contains("pgs") -> "PGS"
            c.contains("vtt") || c.contains("webvtt") -> "VTT"
            c.contains("dts-hd ma") -> "DTS-HD MA"
            c.contains("dts-hd hra") -> "DTS-HD HRA"
            c.contains("dts-hd") || c.contains("dtshd") -> "DTS-HD"
            c.contains("dts:x") || c.contains("dtsx") -> "DTS:X"
            c.contains("dts") -> "DTS"
            c.contains("truehd") -> "TrueHD"
            c.contains("eac3") || c.contains("ec-3") || c.contains("e-ac-3") -> "E-AC-3"
            c.contains("ac3") || c.contains("ac-3") -> "AC-3"
            c.contains("flac") -> "FLAC"
            c.contains("alac") -> "ALAC"
            c.contains("aac") -> "AAC"
            c.contains("opus") -> "Opus"
            c.contains("vorbis") -> "Vorbis"
            c.contains("mp3") -> "MP3"
            c.contains("pcm") -> "PCM"
            else -> codec.uppercase(Locale.US)
        }
    }

    fun formatChannelLayout(channelLayout: String? = null, channelCount: Int? = null): String? {
        if (!channelLayout.isNullOrBlank()) {
            val layout = channelLayout.trim().lowercase(Locale.US)
            return when (layout) {
                "stereo",
                "2.0" -> "Stereo"
                "mono",
                "1.0" -> "Mono"
                "5.1" -> "5.1"
                "7.1" -> "7.1"
                "2.1" -> "2.1"
                "6.1" -> "6.1"
                "atmos" -> "Atmos"
                else ->
                    channelLayout.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                    }
            }
        }
        return when (channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1"
            8 -> "7.1"
            else -> if (channelCount != null && channelCount > 0) "$channelCount ch" else null
        }
    }

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

    fun getTrackDisplayInfo(
        track: Track,
        context: Context,
        displayExtraInfo: Boolean = false,
        currentLocale: Locale = Locale.getDefault(),
        fallbackIndex: Int = track.id,
    ): TrackDisplayInfo {
        val localizedLanguage = formatLanguageName(track.language, currentLocale)
        val name =
            localizedLanguage
                ?: track.language?.takeIf { it.isNotBlank() && it != "und" }
                ?: track.label?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.track_index, fallbackIndex)

        val chips = mutableListOf<String>()

        val isForced =
            track.isForced == true ||
                track.label?.contains("forced", ignoreCase = true) == true ||
                track.label?.contains("forzat", ignoreCase = true) == true ||
                track.label?.contains("forcé", ignoreCase = true) == true
        if (isForced) {
            val forcedLabel =
                if (currentLocale.language == "it") "Forzati"
                else context.getString(R.string.forced)
            chips.add(forcedLabel)
        }

        val isHearingImpaired =
            track.isHearingImpaired == true ||
                track.label?.contains("sdh", ignoreCase = true) == true ||
                track.label?.contains("cc", ignoreCase = true) == true ||
                track.label?.contains("non udenti", ignoreCase = true) == true ||
                track.label?.contains("sordi", ignoreCase = true) == true
        if (isHearingImpaired) {
            val sdhLabel =
                if (currentLocale.language == "it") "Non udenti"
                else context.getString(R.string.hearing_impaired)
            chips.add(sdhLabel)
        }

        if (displayExtraInfo) {
            val isExternal =
                track.isExternal == true ||
                    track.label?.contains("external", ignoreCase = true) == true ||
                    track.label?.contains("estern", ignoreCase = true) == true
            if (isExternal) {
                val externalLabel =
                    if (currentLocale.language == "it") "Esterno"
                    else context.getString(R.string.external)
                chips.add(externalLabel)
            }

            val channelLayout = formatChannelLayout(track.channelLayout, track.channelCount)
            if (channelLayout != null) {
                chips.add(channelLayout)
            }

            val formattedCodec = formatCodec(track.codec)
            if (!formattedCodec.isNullOrBlank()) {
                chips.add(formattedCodec)
            }
        }

        return TrackDisplayInfo(name = name, chips = chips)
    }

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
            channelCount = if (group.type == C.TRACK_TYPE_AUDIO) format.channelCount else null,
        )
    }

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
        )
    }

    fun toTrack(stream: FindroidMediaStream): Track {
        return Track(
            id = stream.index ?: 0,
            label = stream.title.takeIf { it.isNotBlank() } ?: stream.displayTitle,
            language = stream.language,
            codec = stream.codec,
            selected = stream.isDefault ?: false,
            supported = true,
            isExternal = stream.isExternal,
            isForced = stream.isForced,
            isHearingImpaired = stream.isHearingImpaired,
            channelLayout = stream.channelLayout,
        )
    }
}

fun Tracks.Group.toTrack(fallbackIndex: Int = 0): Track =
    TrackFormatter.toTrack(this, fallbackIndex)

fun FindroidMediaStream.toTrack(): Track = TrackFormatter.toTrack(this)
