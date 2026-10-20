package dev.jdtech.jellyfin.settings.domain

object Constants {
    // Interface
    enum class Theme(val value: String) {
        SYSTEM("system"),
        LIGHT("light"),
        DARK("dark");

        companion object {
            fun fromValue(value: String?): Theme =
                entries.firstOrNull { it.value == value } ?: SYSTEM
        }
    }

    // Player
    enum class PlayerBackend(val value: String) {
        EXOPLAYER("exoplayer"),
        MPV("mpv");

        companion object {
            fun fromValue(value: String?): PlayerBackend =
                entries.firstOrNull { it.value == value } ?: EXOPLAYER
        }
    }

    enum class PlayerOrientation(val value: String) {
        SENSOR("sensor"),
        SENSOR_LANDSCAPE("sensor_landscape"),
        SENSOR_PORTRAIT("sensor_portrait"),
        REMEMBER("remember"),
        LOCKED("locked");

        companion object {
            fun fromValue(value: String?): PlayerOrientation =
                entries.firstOrNull { it.value == value } ?: SENSOR
        }
    }

    enum class PlayerResizeMode(val value: String) {
        FIT("fit"),
        ZOOM("zoom"),
        STRETCH("stretch");

        companion object {
            fun fromValue(value: String?): PlayerResizeMode =
                entries.firstOrNull { it.value == value } ?: FIT
        }
    }

    // Player - mpv
    enum class PlayerMpvHwdec(val value: String) {
        NO("no"),
        MEDIACODEC("mediacodec"),
        MEDIACODEC_COPY("mediacodec-copy");

        companion object {
            fun fromValue(value: String?): PlayerMpvHwdec =
                entries.firstOrNull { it.value == value } ?: MEDIACODEC
        }
    }

    enum class PlayerMpvVo(val value: String) {
        GPU("gpu"),
        GPU_NEXT("gpu-next");

        companion object {
            fun fromValue(value: String?): PlayerMpvVo =
                entries.firstOrNull { it.value == value } ?: GPU_NEXT
        }
    }

    enum class PlayerMpvAo(val value: String) {
        AUDIOTRACK("audiotrack"),
        OPENSLES("opensles"),
        AAUDIO("aaudio");

        companion object {
            fun fromValue(value: String?): PlayerMpvAo =
                entries.firstOrNull { it.value == value } ?: AAUDIO
        }
    }

    // Player - Media Segments
    enum class MediaSegmentType(val value: String) {
        INTRO("INTRO"),
        OUTRO("OUTRO"),
        RECAP("RECAP"),
        PREVIEW("PREVIEW"),
        COMMERCIAL("COMMERCIAL"),
        UNKNOWN("UNKNOWN");

        companion object {
            fun fromValue(value: String?): MediaSegmentType =
                entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }

    enum class PlayerMediaSegmentsAutoSkip(val value: String) {
        ALWAYS("always"),
        PIP("pip");

        companion object {
            fun fromValue(value: String?): PlayerMediaSegmentsAutoSkip =
                entries.firstOrNull { it.value == value } ?: ALWAYS
        }
    }

    // Network
    const val NETWORK_DEFAULT_REQUEST_TIMEOUT = 30_000L
    const val NETWORK_DEFAULT_CONNECT_TIMEOUT = 6_000L
    const val NETWORK_DEFAULT_SOCKET_TIMEOUT = 10_000L
}
