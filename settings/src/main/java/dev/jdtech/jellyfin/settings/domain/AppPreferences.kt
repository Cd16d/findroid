package dev.jdtech.jellyfin.settings.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.jdtech.jellyfin.settings.domain.Constants.MediaSegmentType
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerBackend
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerMediaSegmentsAutoSkip
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerMpvAo
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerMpvHwdec
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerMpvVo
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerOrientation
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerResizeMode
import dev.jdtech.jellyfin.settings.domain.Constants.Theme
import dev.jdtech.jellyfin.settings.domain.models.Preference
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/**
 * Manages application preferences using [SharedPreferences]. Provides typed preference objects and
 * reactive state observation.
 */
class AppPreferences @Inject constructor(val sharedPreferences: SharedPreferences) {
    constructor(
        context: Context
    ) : this(
        context.getSharedPreferences(
            context.packageName + "_preferences",
            Context.MODE_PRIVATE,
        )
    )

    // Server
    val currentServer = Preference<String?>("pref_current_server", null)

    // Language
    val preferredAudioLanguage = Preference<String?>("pref_audio_language", null)
    val preferredSubtitleLanguage = Preference<String?>("pref_subtitle_language", null)

    // Interface
    val theme = Preference("pref_theme", Theme.SYSTEM.name.lowercase())
    val dynamicColors = Preference("pref_dynamic_colors", true)
    val homeSuggestions = Preference("home_suggestions", true)
    val homeContinueWatching = Preference("home_continue_watching", true)
    val homeNextUp = Preference("home_next_up", true)
    val homeLatest = Preference("home_latest", true)
    val displayExtraInfo = Preference("pref_display_extra_info", false)
    val tvShowLogos = Preference("pref_tv_show_logos", false)
    val tvScreensaver = Preference("pref_tv_screensaver", true)
    val hideEpisodeSpoilers = Preference("hide_episode_spoilers", false)

    // Player
    val playerBackend = Preference("pref_player_backend", PlayerBackend.EXOPLAYER.name.lowercase())
    val playerBrightness = Preference("pref_player_brightness", -1.0f)
    val playerOrientation =
        Preference("pref_player_orientation", PlayerOrientation.SENSOR.name.lowercase())
    val playerOrientationLast =
        Preference("pref_player_orientation_last", PlayerOrientation.SENSOR.name.lowercase())
    val playerAutoplayNext = Preference("pref_player_autoplay_next", true)
    val playerHaptics = Preference("pref_player_haptics", true)
    val playerControlsTimeout = Preference("pref_player_controls_timeout", 4L)
    val playerPlaybackSpeedRemember = Preference("pref_player_playback_speed_remember", false)
    val playerPlaybackSpeed = Preference("pref_player_playback_speed", 1.0f)

    // Chromecast
    val castEnabled = Preference("pref_cast_enabled", true)
    val castMaxBitrateKbps = Preference("pref_cast_max_bitrate_kbps", 120_000)

    // Player - mpv
    val playerMpv = Preference("pref_player_mpv", false)
    val playerMpvHwdec = Preference("pref_player_mpv_hwdec", PlayerMpvHwdec.MEDIACODEC.value)
    val playerMpvVo = Preference("pref_player_mpv_vo", PlayerMpvVo.GPU.value)
    val playerMpvAo = Preference("pref_player_mpv_ao", PlayerMpvAo.AUDIOTRACK.value)

    // Player - gestures
    val playerGestures = Preference("pref_player_gestures", true)
    val playerGesturesVB = Preference("pref_player_gestures_vb", true)
    val playerGesturesZoom = Preference("pref_player_gestures_zoom", true)
    val playerGesturesSeek = Preference("pref_player_gestures_seek", true)
    val playerGesturesSeekTrickplay = Preference("pref_player_gestures_seek_trickplay", true)
    val playerGesturesChapterSkip = Preference("pref_player_gestures_chapter_skip", true)
    val playerGesturesQuickSpeed = Preference("pref_player_gestures_quick_speed", true)
    val playerGesturesBrightnessRemember = Preference("pref_player_brightness_remember", false)
    val playerDefaultResizeMode =
        Preference("pref_player_default_resize_mode", PlayerResizeMode.FIT.name.lowercase())

    // Player - seeking
    val playerSeekBackInc = Preference("pref_player_seek_back_inc", 5_000L)
    val playerSeekForwardInc = Preference("pref_player_seek_forward_inc", 15_000L)
    val playerChapterMarkers = Preference("pref_player_chapter_markers", true)

    // Player - Media Segments
    val prefSegmentColorIntro = Preference("pref_segment_color_intro", 0xFF4CAF50L)

    val prefSegmentColorOutro = Preference("pref_segment_color_outro", 0xFFFF9800L)

    val prefSegmentColorRecap = Preference("pref_segment_color_recap", 0xFF2196F3L)

    val prefSegmentColorPreview = Preference("pref_segment_color_preview", 0xFF9C27B0L)

    val prefSegmentColorCommercial = Preference("pref_segment_color_commercial", 0xFF795548L)

    val playerMediaSegmentsSkipButton = Preference("pref_player_media_segments_skip_button", true)

    val playerMediaSegmentsSkipButtonType =
        Preference(
            "pref_player_media_segments_skip_button_type",
            setOf(
                MediaSegmentType.INTRO.name,
                MediaSegmentType.OUTRO.name,
            ),
        )

    val playerMediaSegmentsSkipButtonDuration =
        Preference("pref_player_media_segments_skip_button_duration", 5L)

    val playerMediaSegmentsAutoSkip = Preference("pref_player_media_segments_auto_skip", false)

    val playerMediaSegmentsAutoSkipMode =
        Preference(
            "pref_player_media_segments_auto_skip_mode",
            PlayerMediaSegmentsAutoSkip.ALWAYS.name.lowercase(),
        )

    val playerMediaSegmentsAutoSkipType =
        Preference(
            "pref_player_media_segments_auto_skip_type",
            setOf(
                MediaSegmentType.INTRO.name,
                MediaSegmentType.OUTRO.name,
            ),
        )

    val playerMediaSegmentsNextEpisodeThreshold =
        Preference("pref_player_media_segments_next_episode_threshold", 5_000L)

    enum class SegmentAction {
        NONE,
        SHOW_BUTTON,
        AUTO_SKIP,
    }

    fun getSegmentColor(type: String): Long {
        return when (MediaSegmentType.fromValue(type)) {
            MediaSegmentType.INTRO -> getValue(prefSegmentColorIntro)
            MediaSegmentType.OUTRO -> getValue(prefSegmentColorOutro)
            MediaSegmentType.RECAP -> getValue(prefSegmentColorRecap)
            MediaSegmentType.PREVIEW -> getValue(prefSegmentColorPreview)
            MediaSegmentType.COMMERCIAL -> getValue(prefSegmentColorCommercial)
            MediaSegmentType.UNKNOWN -> 0xFF4CAF50L
        }
    }

    fun setSegmentColor(type: String, color: Long) {
        when (MediaSegmentType.fromValue(type)) {
            MediaSegmentType.INTRO -> setValue(prefSegmentColorIntro, color)
            MediaSegmentType.OUTRO -> setValue(prefSegmentColorOutro, color)
            MediaSegmentType.RECAP -> setValue(prefSegmentColorRecap, color)
            MediaSegmentType.PREVIEW -> setValue(prefSegmentColorPreview, color)
            MediaSegmentType.COMMERCIAL -> setValue(prefSegmentColorCommercial, color)
            MediaSegmentType.UNKNOWN -> Unit
        }
    }

    fun getSegmentAction(type: String): SegmentAction {
        val typeName = type.uppercase()
        val autoSkipTypes = getValue(playerMediaSegmentsAutoSkipType)
        if (autoSkipTypes.contains(typeName)) {
            return SegmentAction.AUTO_SKIP
        }
        val skipButtonTypes = getValue(playerMediaSegmentsSkipButtonType)
        if (skipButtonTypes.contains(typeName)) {
            return SegmentAction.SHOW_BUTTON
        }
        return SegmentAction.NONE
    }

    fun setSegmentAction(type: String, action: SegmentAction) {
        val typeName = type.uppercase()
        val currentAutoSkip = getValue(playerMediaSegmentsAutoSkipType).toMutableSet()
        val currentSkipButton = getValue(playerMediaSegmentsSkipButtonType).toMutableSet()

        when (action) {
            SegmentAction.AUTO_SKIP -> {
                currentAutoSkip.add(typeName)
                currentSkipButton.remove(typeName)
                setValue(playerMediaSegmentsAutoSkip, true)
            }
            SegmentAction.SHOW_BUTTON -> {
                currentSkipButton.add(typeName)
                currentAutoSkip.remove(typeName)
                setValue(playerMediaSegmentsSkipButton, true)
            }
            SegmentAction.NONE -> {
                currentAutoSkip.remove(typeName)
                currentSkipButton.remove(typeName)
            }
        }

        setValue(playerMediaSegmentsAutoSkipType, currentAutoSkip)
        setValue(playerMediaSegmentsSkipButtonType, currentSkipButton)
    }

    // Player - trickplay
    val playerTrickplay = Preference("pref_player_trickplay", true)

    // Player - PiP
    val playerPipGesture = Preference("pref_player_picture_in_picture_gesture", true)

    // Downloads
    val downloadOverMobileData = Preference("pref_downloads_mobile_data", false)
    val downloadWhenRoaming = Preference("pref_downloads_roaming", false)
    val defaultDownloadStorageIndex = Preference("pref_downloads_default_storage_index", "0")
    val smartDownloadNextEpisode = Preference("pref_downloads_smart_download_next_episode", true)
    val smartDownloadNextEpisodesCount =
        Preference("pref_downloads_smart_download_next_episodes_count", 3)
    val smartDownloadStorageLimitGb =
        Preference("pref_downloads_smart_download_storage_limit_gb", 20)
    val autoDeleteWatched = Preference("pref_downloads_auto_delete_watched", false)
    val askPresetBeforeDownload = Preference("ask_preset_before_download", true)
    val userCanTranscode = Preference("pref_user_can_transcode", true)
    val downloadExternalAudio = Preference("pref_downloads_external_audio", false)
    val defaultTranscodePresetId = Preference("default_transcode_preset_id", "1080p_balanced")
    val customTranscodePresetsJson = Preference("custom_transcode_presets_json", "")
    val maxConcurrentDownloads = Preference("pref_downloads_max_concurrent", 1)

    // Device
    val customDeviceName = Preference("pref_custom_device_name", "")

    // Network
    val requestTimeout =
        Preference("pref_network_request_timeout", Constants.NETWORK_DEFAULT_REQUEST_TIMEOUT)
    val connectTimeout =
        Preference("pref_network_connect_timeout", Constants.NETWORK_DEFAULT_CONNECT_TIMEOUT)
    val socketTimeout =
        Preference("pref_network_socket_timeout", Constants.NETWORK_DEFAULT_SOCKET_TIMEOUT)

    // Library filter
    val filterWatched = Preference("pref_filter_watched", false)

    // Cache
    val imageCache = Preference("pref_image_cache", true)
    val imageCacheSize = Preference("pref_image_cache_size", 20)

    // Sorting
    val sortBy = Preference("pref_sort_by", "SortName")
    val sortOrder = Preference("pref_sort_order", "Ascending")

    // Offline mode
    val offlineMode = Preference("pref_offline_mode", false)

    // Migrations
    val mpvMigrated = Preference("mpv_migrated", false)

    fun getTheme(): Theme = Theme.fromValue(getValue(theme))

    fun getPlayerBackend(): PlayerBackend = PlayerBackend.fromValue(getValue(playerBackend))

    fun getPlayerOrientation(): PlayerOrientation =
        PlayerOrientation.fromValue(getValue(playerOrientation))

    fun getLastPlayerOrientation(): PlayerOrientation =
        PlayerOrientation.fromValue(getValue(playerOrientationLast))

    fun getPlayerResizeMode(): PlayerResizeMode =
        PlayerResizeMode.fromValue(getValue(playerDefaultResizeMode))

    fun getPlayerMpvHwdec(): PlayerMpvHwdec =
        PlayerMpvHwdec.entries.firstOrNull { it.value == getValue(playerMpvHwdec) }
            ?: PlayerMpvHwdec.MEDIACODEC

    fun getPlayerMpvVo(): PlayerMpvVo =
        PlayerMpvVo.entries.firstOrNull { it.value == getValue(playerMpvVo) } ?: PlayerMpvVo.GPU

    fun getPlayerMpvAo(): PlayerMpvAo =
        PlayerMpvAo.entries.firstOrNull { it.value == getValue(playerMpvAo) }
            ?: PlayerMpvAo.AUDIOTRACK

    fun getPlayerMediaSegmentsAutoSkipMode(): PlayerMediaSegmentsAutoSkip =
        PlayerMediaSegmentsAutoSkip.fromValue(getValue(playerMediaSegmentsAutoSkipMode))

    inline fun <reified T> getValue(preference: Preference<T>): T {
        return try {
            @Suppress("UNCHECKED_CAST")
            when (preference.defaultValue) {
                is Boolean ->
                    sharedPreferences.getBoolean(preference.backendName, preference.defaultValue)
                        as T
                is Int ->
                    sharedPreferences.getInt(preference.backendName, preference.defaultValue) as T
                is Long ->
                    sharedPreferences.getLong(preference.backendName, preference.defaultValue) as T
                is Float ->
                    sharedPreferences.getFloat(preference.backendName, preference.defaultValue) as T
                is String? ->
                    sharedPreferences.getString(preference.backendName, preference.defaultValue)
                        as T
                is Set<*> ->
                    sharedPreferences.getStringSet(
                        preference.backendName,
                        preference.defaultValue as Set<String>,
                    ) as T
                else -> preference.defaultValue
            }
        } catch (_: Exception) {
            Timber.w(
                "Failed to load ${preference.backendName} preference. Resetting to default value..."
            )
            setValue(preference, preference.defaultValue)
            preference.defaultValue
        }
    }

    /** Emits the current value of [preference] and then emits a new value every time it changes. */
    inline fun <reified T> observe(preference: Preference<T>): Flow<T> = callbackFlow {
        trySend(getValue(preference))

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == preference.backendName) {
                trySend(getValue(preference))
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    inline fun <reified T> setValue(preference: Preference<T>, value: T) {
        sharedPreferences.edit {
            @Suppress("UNCHECKED_CAST")
            when (preference.defaultValue) {
                is Boolean -> putBoolean(preference.backendName, value as Boolean)
                is Int -> putInt(preference.backendName, value as Int)
                is Long -> putLong(preference.backendName, value as Long)
                is Float -> putFloat(preference.backendName, value as Float)
                is String? -> putString(preference.backendName, value as String?)
                is Set<*> -> putStringSet(preference.backendName, value as Set<String>)
                else -> throw Exception()
            }
        }
    }
}
