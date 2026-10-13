package dev.jdtech.jellyfin.player.local.domain

import android.content.Context
import androidx.media3.common.Tracks
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter

fun List<Tracks.Group>.getTrackNames(context: Context? = null): Array<String> {
    return this.mapIndexed { index, group ->
            if (context != null) {
                TrackFormatter.getTrackDisplayInfo(
                        group = group,
                        context = context,
                        displayExtraInfo = true,
                        fallbackIndex = index + 1,
                    )
                    .fullTitle
            } else {
                val track = TrackFormatter.toTrack(group, index + 1)
                val lang =
                    TrackFormatter.formatLanguageName(track.language)
                        ?: track.label
                        ?: "Track ${index + 1}"
                val codec = TrackFormatter.formatCodec(track.codec)
                val channel = TrackFormatter.formatChannelLayout(channelCount = track.channelCount)
                listOfNotNull(lang, channel, codec).joinToString(separator = " • ")
            }
        }
        .toTypedArray()
}
