package dev.jdtech.jellyfin.settings.domain

object Constants {
    // Interface
    enum class Theme {
        SYSTEM,
        LIGHT,
        DARK;

        companion object {
            fun fromValue(value: String?): Theme =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
        }
    }

    // Player
    enum class PlayerBackend {
        EXOPLAYER,
        MPV;

        companion object {
            fun fromValue(value: String?): PlayerBackend =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EXOPLAYER
        }
    }

    enum class PlayerOrientation {
        SENSOR,
        SENSOR_LANDSCAPE,
        SENSOR_PORTRAIT,
        REMEMBER,
        LOCKED;

        companion object {
            fun fromValue(value: String?): PlayerOrientation =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SENSOR
        }
    }

    enum class PlayerResizeMode {
        FIT,
        ZOOM,
        STRETCH;

        companion object {
            fun fromValue(value: String?): PlayerResizeMode =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FIT
        }
    }

    // Player - mpv
    enum class PlayerMpvHwdec(val value: String) {
        NO("no"),
        MEDIACODEC("mediacodec"),
        MEDIACODEC_COPY("mediacodec-copy"),
    }

    enum class PlayerMpvVo(val value: String) {
        GPU("gpu"),
        GPU_NEXT("gpu-next"),
    }

    enum class PlayerMpvAo(val value: String) {
        AUDIOTRACK("audiotrack"),
        OPENSLES("opensles"),
        AAUDIO("aaudio"),
    }

    // Player - Media Segments
    enum class MediaSegmentType {
        INTRO,
        OUTRO,
        RECAP,
        PREVIEW,
        COMMERCIAL,
        UNKNOWN;

        companion object {
            fun fromValue(value: String?): MediaSegmentType =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }

    enum class PlayerMediaSegmentsAutoSkip {
        ALWAYS,
        PIP;

        companion object {
            fun fromValue(value: String?): PlayerMediaSegmentsAutoSkip =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ALWAYS
        }
    }

    // Network
    const val NETWORK_DEFAULT_REQUEST_TIMEOUT = 30_000L
    const val NETWORK_DEFAULT_CONNECT_TIMEOUT = 6_000L
    const val NETWORK_DEFAULT_SOCKET_TIMEOUT = 10_000L
}
