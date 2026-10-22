package dev.jdtech.jellyfin.film.presentation.search

import androidx.annotation.StringRes
import dev.jdtech.jellyfin.core.R as CoreR

/**
 * Enum representing item feature filtering options (subtitles, trailers, special features, etc.).
 */
enum class FeatureFlag(@StringRes val labelRes: Int) {
    HAS_SUBTITLES(CoreR.string.feature_has_subtitles),
    HAS_TRAILER(CoreR.string.feature_has_trailer),
    HAS_EXTRA(CoreR.string.feature_has_special_features),
    HAS_THEME_SONG(CoreR.string.feature_has_theme_song),
    HAS_THEME_VIDEO(CoreR.string.feature_has_theme_video),
}
