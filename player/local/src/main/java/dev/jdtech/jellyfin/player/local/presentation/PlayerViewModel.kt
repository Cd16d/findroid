package dev.jdtech.jellyfin.player.local.presentation

import android.app.Application
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.di.ApplicationScope
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.player.core.R as CoreR
import dev.jdtech.jellyfin.player.core.domain.PlayMethodHelper
import dev.jdtech.jellyfin.player.core.domain.PlaybackManager
import dev.jdtech.jellyfin.player.core.domain.PlaylistManager
import dev.jdtech.jellyfin.player.core.domain.models.ExternalSubtitle
import dev.jdtech.jellyfin.player.core.domain.models.PlaybackInfoData
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerImages
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerMode
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerStatus
import dev.jdtech.jellyfin.player.core.domain.models.Track
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.core.domain.models.VideoQualityOption
import dev.jdtech.jellyfin.player.core.domain.utils.NetworkSpeedUtils
import dev.jdtech.jellyfin.player.core.domain.utils.SegmentUtils
import dev.jdtech.jellyfin.player.core.domain.utils.SegmentUtils.getSegments
import dev.jdtech.jellyfin.player.core.domain.utils.SegmentUtils.getSkipButtonTextStringId
import dev.jdtech.jellyfin.player.core.domain.utils.StreamUtils
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.player.core.domain.utils.fetchTrickplay
import dev.jdtech.jellyfin.player.local.mpv.MPVPlayer
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerBackend
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerMediaSegmentsAutoSkip
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerMpvAo
import dev.jdtech.jellyfin.utils.DeviceCodecCapabilities
import dev.jdtech.jellyfin.utils.getTranslatablePartName
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PlayMethod
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    private val application: Application,
    private val playlistManager: PlaylistManager,
    private val playbackManager: PlaybackManager,
    private val repository: JellyfinRepository,
    private val jellyfinApi: JellyfinApi,
    private val appPreferences: AppPreferences,
    private val savedStateHandle: SavedStateHandle,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : ViewModel(), Player.Listener {
    val player: Player

    private val _uiState =
        MutableStateFlow(
            UiState(
                currentItemTitle = "",
                currentSegment = null,
                currentSkipButtonStringRes = CoreR.string.player_controls_skip_intro,
                currentTrickplay = null,
                currentChapters = emptyList(),
                fileLoaded = false,
                isScreensaverEnabled = appPreferences.getValue(appPreferences.tvScreensaver),
                hideEpisodeSpoilers = appPreferences.getValue(appPreferences.hideEpisodeSpoilers),
                showChapterMarkers = appPreferences.getValue(appPreferences.playerChapterMarkers),
                maxTranscodeBitrateBps =
                    (appPreferences.getValue(appPreferences.castMaxBitrateKbps) * 1_000L)
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                        .toInt(),
            )
        )
    val uiState = _uiState.asStateFlow()

    private val eventsChannel = Channel<PlayerEvents>(Channel.BUFFERED)
    val eventsChannelFlow = eventsChannel.receiveAsFlow()

    data class UiState(
        val currentItemTitle: String,
        val currentItemSubtitle: String? = null,
        val currentSegment: FindroidSegment?,
        val currentSkipButtonStringRes: Int,
        val currentTrickplay: Trickplay?,
        val currentChapters: List<PlayerChapter>,
        val fileLoaded: Boolean,
        val isFavorite: Boolean = false,
        val seasonEpisodes: List<FindroidEpisode> = emptyList(),
        val currentEpisodeId: UUID? = null,
        val seriesId: UUID? = null,
        val segments: List<FindroidSegment> = emptyList(),
        val currentImages: PlayerImages? = null,
        val mediaStreams: List<FindroidMediaStream> = emptyList(),
        val suggestions: List<FindroidItem> = emptyList(),
        val isScreensaverEnabled: Boolean = true,
        val hideEpisodeSpoilers: Boolean = false,
        val playbackSpeed: Float = 1f,
        val showChapterMarkers: Boolean = true,
        val sleepTimerStatus: SleepTimerStatus = SleepTimerStatus(),
        val selectedQuality: VideoQualityOption = VideoQualityOption.ORIGINAL,
        val isSpeedTesting: Boolean = false,
        val autoResolvedQualityLabel: String? = null,
        val selectedAudioStreamIndex: Int? = null,
        val selectedExternalAudioStreamIndex: Int? = null,
        val availableAudioTracks: List<Track> = emptyList(),
        val playbackInfoData: PlaybackInfoData = PlaybackInfoData(),
        val maxTranscodeBitrateBps: Int = 120_000_000,
    )

    private var items: MutableList<PlayerItem> = mutableListOf()
    private var sleepTimerJob: Job? = null
    var selectedAudioStreamIndex: Int? = null
        private set

    val selectedExternalAudioStreamIndex: Int?
        get() = selectedAudioStreamIndex

    private var currentSource: FindroidSource? = null
    private var playSessionId: String? = null
    private var currentMediaSourceId: String? = null
    private var currentPlayMethod: PlayMethod = PlayMethod.DIRECT_PLAY

    private val trackSelector = DefaultTrackSelector(application)
    var playWhenReady = true
    private var currentMediaItemIndex = savedStateHandle["mediaItemIndex"] ?: 0
    private var playbackPosition: Long = savedStateHandle["position"] ?: 0
    private var currentMediaItemSegments: List<FindroidSegment> = emptyList()

    // Segments preferences
    var segmentsSkipButton: Boolean = false
    private var segmentsSkipButtonTypes: Set<String> = emptySet()
    var segmentsSkipButtonDuration: Long = 0L
    var segmentsAutoSkip: Boolean = false
    private var segmentsAutoSkipTypes: Set<String> = emptySet()
    private var segmentsAutoSkipMode: String = PlayerMediaSegmentsAutoSkip.ALWAYS.name.lowercase()

    private var activeSegment: FindroidSegment? = null

    val playbackSpeed: Float
        get() = _uiState.value.playbackSpeed

    var isInPictureInPictureMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                activeSegment = null
                updateCurrentSegment()
            }
        }

    val displayExtraInfo: Boolean
        get() = appPreferences.getValue(appPreferences.displayExtraInfo)

    private var hasReportedStart = false
    private val currentItem = MutableStateFlow<PlayerItem?>(null)

    init {
        viewModelScope.launch {
            appPreferences.observe(appPreferences.castMaxBitrateKbps).collect { kbps ->
                val bps = (kbps * 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                _uiState.update { it.copy(maxTranscodeBitrateBps = bps) }
            }
        }
        viewModelScope.launch {
            appPreferences.observe(appPreferences.tvScreensaver).collect { enabled ->
                _uiState.update { it.copy(isScreensaverEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appPreferences.observe(appPreferences.hideEpisodeSpoilers).collect { hideSpoilers ->
                _uiState.update { it.copy(hideEpisodeSpoilers = hideSpoilers) }
            }
        }
        viewModelScope.launch {
            appPreferences.observe(appPreferences.playerMediaSegmentsSkipButtonDuration).collect {
                duration ->
                segmentsSkipButtonDuration = duration
            }
        }
        viewModelScope.launch {
            appPreferences.observe(appPreferences.playerMediaSegmentsSkipButton).collect { enabled
                ->
                segmentsSkipButton = enabled
                updateCurrentSegment()
            }
        }
        viewModelScope.launch {
            appPreferences.observe(appPreferences.playerMediaSegmentsSkipButtonType).collect { types
                ->
                segmentsSkipButtonTypes = types
                updateCurrentSegment()
            }
        }
        viewModelScope.launch {
            appPreferences.observe(appPreferences.playerMediaSegmentsAutoSkip).collect { enabled ->
                segmentsAutoSkip = enabled
                updateCurrentSegment()
            }
        }
        viewModelScope.launch {
            appPreferences.observe(appPreferences.playerMediaSegmentsAutoSkipType).collect { types
                ->
                segmentsAutoSkipTypes = types
                updateCurrentSegment()
            }
        }
        viewModelScope.launch {
            appPreferences.observe(appPreferences.playerMediaSegmentsAutoSkipMode).collect { mode ->
                segmentsAutoSkipMode = mode
                updateCurrentSegment()
            }
        }

        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        trackSelector.setParameters(
            trackSelector
                .buildUponParameters()
                .setPreferredAudioLanguage(
                    appPreferences.getValue(appPreferences.preferredAudioLanguage)
                )
                .setPreferredTextLanguage(
                    appPreferences.getValue(appPreferences.preferredSubtitleLanguage)
                )
        )

        combine(
                currentItem,
                appPreferences.observe(appPreferences.playerTrickplay),
            ) { item, isTrickplayEnabled ->
                if (isTrickplayEnabled && (item != null)) item else null
            }
            .distinctUntilChanged()
            .flatMapLatest { item ->
                flow {
                    if (item != null) {
                        val trickplay = fetchTrickplay(repository, item)
                        emit(trickplay)
                    } else {
                        emit(null)
                    }
                }
            }
            .onEach { trickplay ->
                val oldTrickplay = _uiState.value.currentTrickplay
                if (oldTrickplay != trickplay) {
                    oldTrickplay?.images?.forEach { it.recycle() }
                    _uiState.update { it.copy(currentTrickplay = trickplay) }
                }
            }
            .launchIn(viewModelScope)

        val playerBackend = appPreferences.getPlayerBackend()
        player =
            when (playerBackend) {
                PlayerBackend.EXOPLAYER -> {
                    val renderersFactory =
                        DefaultRenderersFactory(application)
                            .setExtensionRendererMode(
                                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            )
                    val loadControl =
                        DefaultLoadControl.Builder()
                            .setBufferDurationsMs(
                                20_000,
                                60_000,
                                2_500,
                                5_000,
                            )
                            .setPrioritizeTimeOverSizeThresholds(true)
                            .build()
                    ExoPlayer.Builder(application, renderersFactory)
                        .setLoadControl(loadControl)
                        .setAudioAttributes(audioAttributes, true)
                        .setTrackSelector(trackSelector)
                        .setSeekBackIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekBackInc)
                        )
                        .setSeekForwardIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekForwardInc)
                        )
                        .setPauseAtEndOfMediaItems(true)
                        .build()
                }
                PlayerBackend.MPV -> {
                    val audioOutput = appPreferences.getPlayerMpvAo().value
                    val safeAudioOutput =
                        if (audioOutput == PlayerMpvAo.AAUDIO.value) {
                            PlayerMpvAo.AUDIOTRACK.value
                        } else {
                            audioOutput
                        }
                    if (safeAudioOutput != audioOutput) {
                        appPreferences.setValue(appPreferences.playerMpvAo, safeAudioOutput)
                    }
                    MPVPlayer.Builder(application)
                        .setAudioAttributes(audioAttributes, handleAudioFocus = true)
                        .setTrackSelectionParameters(trackSelector.parameters)
                        .setSeekBackIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekBackInc)
                        )
                        .setSeekForwardIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekForwardInc)
                        )
                        .setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems = true)
                        .setVideoOutput(appPreferences.getPlayerMpvVo().value)
                        .setAudioOutput(safeAudioOutput)
                        .setHwDec(appPreferences.getPlayerMpvHwdec().value)
                        .build()
                }
            }
    }

    val isMpv: Boolean
        get() = player is MPVPlayer

    fun initializePlayer(itemId: UUID, itemKind: String, startFromBeginning: Boolean) {
        player.addListener(this)

        if (appPreferences.getValue(appPreferences.playerPlaybackSpeedRemember)) {
            val savedSpeed = appPreferences.getValue(appPreferences.playerPlaybackSpeed)
            player.setPlaybackSpeed(savedSpeed)
            _uiState.update { it.copy(playbackSpeed = savedSpeed) }
        }

        val rememberedQuality =
            if (appPreferences.getValue(appPreferences.playerRememberQuality)) {
                val savedKey = appPreferences.getValue(appPreferences.playerDefaultQuality)
                when {
                    savedKey.equals("original", ignoreCase = true) -> VideoQualityOption.ORIGINAL
                    savedKey.equals("auto", ignoreCase = true) -> VideoQualityOption.AUTO
                    savedKey.contains("_") -> {
                        val parts = savedKey.split("_")
                        val resName = parts.getOrNull(0) ?: "1080p"
                        val bps = parts.getOrNull(1)?.toIntOrNull() ?: 15_000_000
                        VideoQualityOption.createFixedOption(resName, bps)
                    }
                    else -> {
                        val tier =
                            VideoQualityOption.RESOLUTION_TIERS.firstOrNull { (name) ->
                                name.equals(savedKey, ignoreCase = true)
                            }
                        if (tier != null) {
                            val (tierName, _, _, _, defaultBitrateBps) = tier
                            VideoQualityOption.createFixedOption(tierName, defaultBitrateBps)
                        } else {
                            VideoQualityOption.ORIGINAL
                        }
                    }
                }
            } else {
                val defaultKey = appPreferences.getValue(appPreferences.playerDefaultQuality)
                when {
                    defaultKey.equals("auto", ignoreCase = true) -> VideoQualityOption.AUTO
                    defaultKey.equals("original", ignoreCase = true) -> VideoQualityOption.ORIGINAL
                    else -> {
                        val tier =
                            VideoQualityOption.RESOLUTION_TIERS.firstOrNull { (name) ->
                                name.equals(defaultKey, ignoreCase = true)
                            }
                        if (tier != null) {
                            val (tierName, _, _, _, defaultBitrateBps) = tier
                            VideoQualityOption.createFixedOption(tierName, defaultBitrateBps)
                        } else {
                            VideoQualityOption.ORIGINAL
                        }
                    }
                }
            }
        _uiState.update { it.copy(selectedQuality = rememberedQuality) }

        viewModelScope.launch {
            val startItem =
                try {
                    val resolvedKind =
                        BaseItemKind.fromNameOrNull(itemKind)
                            ?: BaseItemKind.entries.firstOrNull {
                                it.name.equals(itemKind, ignoreCase = true) ||
                                    it.serialName.equals(itemKind, ignoreCase = true)
                            }
                            ?: repository.getItem(itemId)?.let { item ->
                                when (item) {
                                    is FindroidMovie -> BaseItemKind.MOVIE
                                    is FindroidEpisode -> BaseItemKind.EPISODE
                                    is FindroidShow -> BaseItemKind.SERIES
                                    is FindroidSeason -> BaseItemKind.SEASON
                                    else -> null
                                }
                            }
                            ?: BaseItemKind.fromName(itemKind)

                    playlistManager.getInitialItem(
                        itemId = itemId,
                        itemKind = resolvedKind,
                        mediaSourceIndex = null,
                        startFromBeginning = startFromBeginning,
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                    Toast.makeText(application, e.localizedMessage, Toast.LENGTH_LONG).show()
                    null
                }

            if (startItem == null) {
                Timber.e("No start item, stopping player initialization")
                return@launch
            }

            val selectedOption = _uiState.value.selectedQuality

            // If Auto, measure network speed
            val autoMeasuredBitrate =
                if (selectedOption.isAuto) {
                    _uiState.update { it.copy(isSpeedTesting = true) }
                    try {
                        NetworkSpeedUtils.measureNetworkSpeed(jellyfinApi)
                    } finally {
                        _uiState.update { it.copy(isSpeedTesting = false) }
                    }
                } else null

            val maxTranscodeBitrateKbps = appPreferences.getValue(appPreferences.castMaxBitrateKbps)
            val maxTranscodeBitrateBps =
                (maxTranscodeBitrateKbps * 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            val speedBps = autoMeasuredBitrate ?: 15_000_000
            val effectiveSpeedBps =
                if (maxTranscodeBitrateBps > 0) minOf(speedBps, maxTranscodeBitrateBps)
                else speedBps

            val requestedBitrate: Int? =
                when {
                    selectedOption.isOriginal -> null
                    selectedOption.isAuto -> effectiveSpeedBps
                    selectedOption.isFixed -> selectedOption.bitrateBps
                    else -> null
                }

            val effectiveBitrate =
                StreamUtils.computeEffectiveBitrate(
                    settingMaxBitrateKbps = maxTranscodeBitrateKbps,
                    requestedBitrate = requestedBitrate,
                    isOriginal = selectedOption.isOriginal,
                    isAuto = selectedOption.isAuto,
                )

            val isExternalAudio =
                selectedAudioStreamIndex?.let { idx ->
                    currentSource?.mediaStreams?.firstOrNull { it.index == idx }?.isExternal == true
                } ?: false

            val enableDirectPlay =
                (selectedOption.isOriginal || selectedOption.isAuto) && !isExternalAudio

            val deviceProfile =
                DeviceCodecCapabilities.createDeviceProfile(
                    maxBitrate = effectiveBitrate ?: 120_000_000,
                    maxWidth = selectedOption.maxWidth,
                    maxHeight = selectedOption.maxHeight,
                )

            val sources =
                try {
                    repository.getMediaSources(
                        itemId = itemId,
                        includePath = true,
                        maxStreamingBitrate = effectiveBitrate,
                        audioStreamIndex = selectedAudioStreamIndex,
                        deviceProfile = deviceProfile,
                        enableDirectPlay = enableDirectPlay,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error fetching media sources in initializePlayer")
                    emptyList()
                }

            val firstSource = sources.firstOrNull()
            currentSource = firstSource
            playSessionId = firstSource?.playSessionId
            currentMediaSourceId = firstSource?.id

            val isDirectPlay = (firstSource?.supportsDirectPlay == true) && enableDirectPlay
            currentPlayMethod =
                PlayMethodHelper.determinePlayMethod(
                    supportsDirectPlay = firstSource?.supportsDirectPlay == true,
                    enableDirectPlay = enableDirectPlay,
                    transcodingUrl = firstSource?.transcodingUrl,
                )

            val effectiveAutoLabel =
                if (selectedOption.isAuto) {
                    resolveAutoLabel(
                        source = firstSource,
                        effectiveSpeedBps = effectiveSpeedBps,
                        isDirectPlay = isDirectPlay,
                    )
                } else null

            val targetUri =
                firstSource?.let { source ->
                    if (
                        currentPlayMethod != PlayMethod.DIRECT_PLAY ||
                            !source.transcodingUrl.isNullOrBlank()
                    ) {
                        StreamUtils.buildStreamUrl(
                            baseUrl = repository.getBaseUrl(),
                            itemId = itemId,
                            mediaSourceId = source.id,
                            transcodingUrl = source.transcodingUrl,
                            audioStreamIndex = selectedAudioStreamIndex,
                        )
                    } else {
                        source.path.takeIf { it.isNotBlank() }
                    }
                } ?: startItem.mediaSourceUri
            val initialSubtitles =
                if ((currentPlayMethod != PlayMethod.DIRECT_PLAY) && (firstSource != null)) {
                    firstSource.mediaStreams
                        .asSequence()
                        .filter {
                            (it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE) &&
                                (!it.path.isNullOrBlank())
                        }
                        .map { mediaStream ->
                            ExternalSubtitle(
                                title = mediaStream.title,
                                language = mediaStream.language,
                                uri = mediaStream.path!!.toUri(),
                                mimeType =
                                    when (mediaStream.codec.lowercase()) {
                                        "subrip",
                                        "srt" -> MimeTypes.APPLICATION_SUBRIP
                                        "webvtt",
                                        "vtt" -> MimeTypes.TEXT_VTT
                                        "ass",
                                        "ssa" -> MimeTypes.TEXT_SSA
                                        else -> MimeTypes.TEXT_VTT
                                    },
                                isForced = mediaStream.isForced ?: false,
                                isHearingImpaired = mediaStream.isHearingImpaired ?: false,
                                isDefault = mediaStream.isDefault ?: false,
                                codec = mediaStream.codec,
                                displayTitle = mediaStream.displayTitle,
                                index = mediaStream.index,
                            )
                        }
                        .toList()
                } else {
                    startItem.externalSubtitles
                }
            val updatedStartItem =
                startItem.copy(mediaSourceUri = targetUri, externalSubtitles = initialSubtitles)

            val streams = firstSource?.mediaStreams ?: emptyList()
            val infoData =
                firstSource?.let { buildPlaybackInfoData(it, streams) } ?: PlaybackInfoData()

            _uiState.update {
                it.copy(
                    autoResolvedQualityLabel = effectiveAutoLabel,
                    mediaStreams = streams,
                    playbackInfoData = infoData,
                )
            }
            updateAvailableAudioTracks()

            items = listOf(updatedStartItem).toMutableList()
            currentMediaItemIndex = 0

            val startPosition =
                if (playbackPosition == 0L) {
                    updatedStartItem.playbackPosition
                } else {
                    playbackPosition
                }

            player.setMediaItems(listOf(updatedStartItem.toMediaItem()), 0, startPosition)
            player.prepare()
            player.play()
        }
        loadItemMetadata(itemId)
    }

    fun loadItemMetadata(itemId: UUID) {
        viewModelScope.launch {
            if (_uiState.value.selectedQuality.isAuto) {
                _uiState.update { it.copy(isSpeedTesting = true) }
            }
            try {
                val item = repository.getItem(itemId)
                if (item != null) {
                    val isFav = item.favorite
                    val episodes: List<FindroidEpisode> =
                        if (item is FindroidEpisode) {
                            repository.getEpisodes(
                                seriesId = item.seriesId,
                                seasonId = item.seasonId,
                            )
                        } else {
                            emptyList()
                        }
                    val selectedOption = _uiState.value.selectedQuality
                    val optionBitrate = selectedOption.bitrateBps
                    val requestedBitrate: Int? =
                        when {
                            selectedOption.isOriginal -> null
                            selectedOption.isAuto -> null
                            (optionBitrate != null) && (optionBitrate > 0) -> optionBitrate
                            else -> null
                        }
                    val effectiveBitrate =
                        StreamUtils.computeEffectiveBitrate(
                            settingMaxBitrateKbps =
                                appPreferences.getValue(appPreferences.castMaxBitrateKbps),
                            requestedBitrate = requestedBitrate,
                            isOriginal = selectedOption.isOriginal,
                            isAuto = selectedOption.isAuto,
                        )
                    val isExternalAudio =
                        selectedAudioStreamIndex?.let { idx ->
                            currentSource
                                ?.mediaStreams
                                ?.firstOrNull { it.index == idx }
                                ?.isExternal == true
                        } ?: false
                    val enableDirectPlay =
                        (selectedOption.isOriginal || selectedOption.isAuto) && !isExternalAudio
                    val activeSource =
                        currentSource
                            ?: run {
                                val deviceProfile =
                                    DeviceCodecCapabilities.createDeviceProfile(
                                        maxBitrate = effectiveBitrate ?: 120_000_000,
                                        maxWidth = selectedOption.maxWidth,
                                        maxHeight = selectedOption.maxHeight,
                                    )
                                val mediaSources =
                                    try {
                                        repository.getMediaSources(
                                            itemId = itemId,
                                            includePath = true,
                                            maxStreamingBitrate = effectiveBitrate,
                                            audioStreamIndex = selectedAudioStreamIndex,
                                            deviceProfile = deviceProfile,
                                            enableDirectPlay = enableDirectPlay,
                                        )
                                    } catch (_: Exception) {
                                        emptyList()
                                    }
                                val firstSource = mediaSources.firstOrNull()
                                currentSource = firstSource
                                playSessionId = firstSource?.playSessionId
                                currentMediaSourceId = firstSource?.id
                                currentPlayMethod =
                                    PlayMethodHelper.determinePlayMethod(
                                        supportsDirectPlay =
                                            firstSource?.supportsDirectPlay == true,
                                        enableDirectPlay = enableDirectPlay,
                                        transcodingUrl = firstSource?.transcodingUrl,
                                    )
                                firstSource
                            }

                    val suggestions =
                        try {
                            repository.getSuggestions(forceRefresh = false)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    val streams = activeSource?.mediaStreams ?: emptyList()
                    val infoData =
                        activeSource?.let { buildPlaybackInfoData(it, streams) }
                            ?: _uiState.value.playbackInfoData
                    _uiState.update {
                        it.copy(
                            isFavorite = isFav,
                            seasonEpisodes = episodes,
                            currentEpisodeId = (item as? FindroidEpisode)?.id,
                            seriesId = (item as? FindroidEpisode)?.seriesId,
                            mediaStreams = streams,
                            playbackInfoData = infoData,
                            suggestions = suggestions,
                        )
                    }
                    updateAvailableAudioTracks()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading item metadata for player")
            }
        }
    }

    /**
     * Updates the requested video quality preset and reloads the active playback stream.
     *
     * @param quality Selected [VideoQualityOption].
     */
    fun selectVideoQuality(quality: VideoQualityOption) {
        _uiState.update { it.copy(selectedQuality = quality) }
        reloadMediaStream()
    }

    /**
     * Refreshes auto quality by re-running the network speed test and adapting playback bitrate.
     */
    fun refreshAutoQuality() {
        if (_uiState.value.isSpeedTesting) return
        reloadMediaStream()
    }

    /**
     * Selects an audio track by Jellyfin stream index. If in Direct Play and the track is internal,
     * switches tracks at player level. Otherwise, requests a new transcoded or direct stream from
     * Jellyfin.
     *
     * @param streamIndex The audio stream index from Jellyfin media streams.
     */
    fun selectAudioTrack(streamIndex: Int) {
        val streams = currentSource?.mediaStreams?.ifEmpty { null } ?: _uiState.value.mediaStreams
        val targetStream = streams.firstOrNull {
            it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO && it.index == streamIndex
        }

        val isDirectPlay = currentPlayMethod == PlayMethod.DIRECT_PLAY
        val internalStreams = streams.filter {
            it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO && !it.isExternal
        }
        val targetInternalIndex = internalStreams.indexOfFirst { it.index == streamIndex }

        if (isDirectPlay && targetStream?.isExternal != true && targetInternalIndex >= 0) {
            selectedAudioStreamIndex = streamIndex
            switchToTrack(C.TRACK_TYPE_AUDIO, targetInternalIndex)
            updateAvailableAudioTracks()
        } else {
            selectedAudioStreamIndex = streamIndex
            _uiState.update {
                it.copy(
                    selectedAudioStreamIndex = streamIndex,
                    selectedExternalAudioStreamIndex = streamIndex,
                )
            }
            reloadMediaStream()
        }
    }

    fun updateAvailableAudioTracks() {
        val streams = currentSource?.mediaStreams?.ifEmpty { null } ?: _uiState.value.mediaStreams
        val audioStreams = streams.filter {
            it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO
        }

        if (audioStreams.isNotEmpty()) {
            val isDirect = currentPlayMethod == PlayMethod.DIRECT_PLAY
            val currentActiveIndex: Int? =
                selectedAudioStreamIndex
                    ?: if (!isDirect) {
                        currentSource?.transcodingUrl?.let { url ->
                            Regex("(?i)AudioStreamIndex=(\\d+)")
                                .find(url)
                                ?.groupValues
                                ?.get(1)
                                ?.toIntOrNull()
                        }
                    } else {
                        val internalSelectedIdx =
                            player.currentTracks.groups
                                .asSequence()
                                .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                                .indexOfFirst { it.isSelected }
                        if (internalSelectedIdx >= 0) {
                            val internalStreams = audioStreams.filter { !it.isExternal }
                            internalStreams.getOrNull(internalSelectedIdx)?.index
                        } else null
                    }
                    ?: audioStreams.firstOrNull { it.isDefault == true }?.index
                    ?: audioStreams.firstOrNull()?.index

            if (selectedAudioStreamIndex == null && currentActiveIndex != null) {
                selectedAudioStreamIndex = currentActiveIndex
            }

            val tracks = audioStreams.mapIndexed { idx, stream ->
                val streamIdx = stream.index ?: idx
                val isSelected = streamIdx == currentActiveIndex
                val isDefault =
                    stream.isDefault == true ||
                        stream.title.contains("default", ignoreCase = true) ||
                        stream.displayTitle?.contains("default", ignoreCase = true) == true
                Track(
                    id = streamIdx,
                    label = stream.title.takeIf { it.isNotBlank() } ?: stream.displayTitle,
                    language = stream.language,
                    codec = stream.codec,
                    selected = isSelected,
                    supported = true,
                    isExternal = stream.isExternal,
                    isForced = stream.isForced,
                    isHearingImpaired = stream.isHearingImpaired,
                    isDefault = isDefault,
                    channelLayout = stream.channelLayout,
                    channelCount = stream.channels,
                    type = C.TRACK_TYPE_AUDIO,
                )
            }
            _uiState.update {
                it.copy(
                    availableAudioTracks = tracks,
                    selectedAudioStreamIndex = currentActiveIndex,
                    selectedExternalAudioStreamIndex = currentActiveIndex,
                )
            }
        } else {
            val exoplayerAudioTracks =
                player.currentTracks.groups
                    .asSequence()
                    .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                    .mapIndexed { idx, group -> TrackFormatter.toTrack(group, idx + 1) }
                    .toList()
            _uiState.update { it.copy(availableAudioTracks = exoplayerAudioTracks) }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        updateAvailableAudioTracks()
    }

    private fun reloadMediaStream(targetInternalTrackIndex: Int? = null) {
        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val itemId = UUID.fromString(currentMediaId)
        val currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        val durationMs = player.duration

        viewModelScope.launch {
            if (_uiState.value.selectedQuality.isAuto) {
                _uiState.update { it.copy(isSpeedTesting = true) }
            }
            try {
                val selectedOption = _uiState.value.selectedQuality

                // Save quality preference if "remember quality" is enabled
                if (appPreferences.getValue(appPreferences.playerRememberQuality)) {
                    val prefKey =
                        when {
                            selectedOption.isOriginal -> "original"
                            selectedOption.isAuto -> "auto"
                            selectedOption.isFixed ->
                                "${selectedOption.resolutionName}_${selectedOption.bitrateBps}"
                            else -> "original"
                        }
                    appPreferences.setValue(appPreferences.playerDefaultQuality, prefKey)
                }

                // If AUTO, run speedtest to measure network bitrate
                val autoMeasuredBitrate =
                    if (selectedOption.isAuto) {
                        NetworkSpeedUtils.measureNetworkSpeed(jellyfinApi)
                    } else null

                val speedBps = autoMeasuredBitrate ?: 15_000_000
                val requestedBitrate: Int? =
                    when {
                        selectedOption.isOriginal -> null
                        selectedOption.isAuto -> speedBps
                        selectedOption.isFixed -> selectedOption.bitrateBps
                        else -> null
                    }

                val settingMaxBitrateKbps =
                    appPreferences.getValue(appPreferences.castMaxBitrateKbps)
                val effectiveBitrate =
                    StreamUtils.computeEffectiveBitrate(
                        settingMaxBitrateKbps = settingMaxBitrateKbps,
                        requestedBitrate = requestedBitrate,
                        isOriginal = selectedOption.isOriginal,
                        isAuto = selectedOption.isAuto,
                    )
                val maxTranscodeBitrateBps =
                    (settingMaxBitrateKbps * 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val effectiveSpeedBps =
                    if (maxTranscodeBitrateBps > 0) minOf(speedBps, maxTranscodeBitrateBps)
                    else speedBps

                val isExternalAudio =
                    selectedAudioStreamIndex?.let { idx ->
                        currentSource?.mediaStreams?.firstOrNull { it.index == idx }?.isExternal ==
                            true
                    } ?: false

                val enableDirectPlay =
                    (selectedOption.isOriginal || selectedOption.isAuto) && !isExternalAudio

                val deviceProfile =
                    DeviceCodecCapabilities.createDeviceProfile(
                        maxBitrate = effectiveBitrate ?: 120_000_000,
                        maxWidth = selectedOption.maxWidth,
                        maxHeight = selectedOption.maxHeight,
                    )

                val sources =
                    repository.getMediaSources(
                        itemId = itemId,
                        includePath = true,
                        maxStreamingBitrate = effectiveBitrate,
                        audioStreamIndex = selectedAudioStreamIndex,
                        deviceProfile = deviceProfile,
                        enableDirectPlay = enableDirectPlay,
                    )
                val newSource = sources.firstOrNull() ?: return@launch
                currentSource = newSource
                playSessionId = newSource.playSessionId
                currentMediaSourceId = newSource.id

                resetPlaybackStatsHistory()
                val isDirectPlay = newSource.supportsDirectPlay && enableDirectPlay
                currentPlayMethod =
                    PlayMethodHelper.determinePlayMethod(
                        supportsDirectPlay = newSource.supportsDirectPlay,
                        enableDirectPlay = enableDirectPlay,
                        transcodingUrl = newSource.transcodingUrl,
                    )

                val newStreamUrl =
                    if (
                        currentPlayMethod != PlayMethod.DIRECT_PLAY ||
                            !newSource.transcodingUrl.isNullOrBlank()
                    ) {
                        StreamUtils.buildStreamUrl(
                            baseUrl = repository.getBaseUrl(),
                            itemId = itemId,
                            mediaSourceId = newSource.id,
                            transcodingUrl = newSource.transcodingUrl,
                            audioStreamIndex = selectedAudioStreamIndex,
                        )
                    } else {
                        newSource.path
                    }
                if (newStreamUrl.isBlank()) return@launch

                val effectiveAutoLabel =
                    if (selectedOption.isAuto) {
                        resolveAutoLabel(
                            source = newSource,
                            effectiveSpeedBps = effectiveSpeedBps,
                            isDirectPlay = isDirectPlay,
                        )
                    } else null

                val streams = newSource.mediaStreams
                val infoData = buildPlaybackInfoData(newSource, streams)

                _uiState.update {
                    it.copy(
                        autoResolvedQualityLabel = effectiveAutoLabel,
                        mediaStreams = streams,
                        playbackInfoData = infoData,
                    )
                }
                updateAvailableAudioTracks()

                val currentItemObj = items.firstOrNull { it.itemId == itemId } ?: return@launch
                val updatedSubtitles =
                    if (currentPlayMethod != PlayMethod.DIRECT_PLAY) {
                        newSource.mediaStreams
                            .asSequence()
                            .filter {
                                it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE &&
                                    !it.path.isNullOrBlank()
                            }
                            .map { mediaStream ->
                                ExternalSubtitle(
                                    title = mediaStream.title,
                                    language = mediaStream.language,
                                    uri = mediaStream.path!!.toUri(),
                                    mimeType =
                                        when (mediaStream.codec.lowercase()) {
                                            "subrip",
                                            "srt" -> MimeTypes.APPLICATION_SUBRIP
                                            "webvtt",
                                            "vtt" -> MimeTypes.TEXT_VTT
                                            "ass",
                                            "ssa" -> MimeTypes.TEXT_SSA
                                            else -> MimeTypes.TEXT_VTT
                                        },
                                    isForced = mediaStream.isForced ?: false,
                                    isHearingImpaired = mediaStream.isHearingImpaired ?: false,
                                    isDefault = mediaStream.isDefault ?: false,
                                    codec = mediaStream.codec,
                                    displayTitle = mediaStream.displayTitle,
                                    index = mediaStream.index,
                                )
                            }
                            .toList()
                    } else {
                        currentItemObj.externalSubtitles
                    }
                val updatedItem =
                    currentItemObj.copy(
                        mediaSourceUri = newStreamUrl,
                        externalSubtitles = updatedSubtitles,
                    )

                val currentItemIndex = items.indexOf(currentItemObj)
                if (currentItemIndex >= 0) {
                    items[currentItemIndex] = updatedItem
                }
                currentItem.value = updatedItem

                player.setMediaItem(updatedItem.toMediaItem(), currentPositionMs)
                player.prepare()
                if (playWhenReady) player.play()

                targetInternalTrackIndex?.let { switchToTrack(C.TRACK_TYPE_AUDIO, it) }

                playbackManager.startSession(
                    itemId = itemId,
                    positionMs = currentPositionMs,
                    playMethod = currentPlayMethod,
                    mediaSourceId = currentMediaSourceId,
                    playSessionId = playSessionId,
                    previousDurationMs = durationMs,
                )
                hasReportedStart = true
            } catch (e: Exception) {
                Timber.e(e, "Error reloading media stream")
            } finally {
                _uiState.update { it.copy(isSpeedTesting = false) }
            }
        }
    }

    private fun resolveAutoLabel(
        source: FindroidSource?,
        effectiveSpeedBps: Int,
        isDirectPlay: Boolean,
    ): String {
        if (isDirectPlay) return "Original • Direct Play"
        val videoStream =
            source?.mediaStreams?.firstOrNull {
                it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO
            }
        val availableTiers =
            VideoQualityOption.getAvailableResolutions(
                videoStream?.width ?: 1920,
                videoStream?.height ?: 1080,
            )
        val chosenTier =
            availableTiers.firstOrNull { tier ->
                effectiveSpeedBps >= (tier.bitratesBps.minOrNull() ?: tier.defaultBitrateBps)
            } ?: availableTiers.lastOrNull() ?: VideoQualityOption.RESOLUTION_TIERS.last()
        val chosenBitrate =
            chosenTier.bitratesBps.asSequence().filter { it <= effectiveSpeedBps }.maxOrNull()
                ?: chosenTier.bitratesBps.minOrNull()
                ?: chosenTier.defaultBitrateBps
        return "${chosenTier.name} • ${VideoQualityOption.formatBitrate(chosenBitrate)}"
    }

    private fun buildPlaybackInfoData(
        source: FindroidSource,
        streams: List<FindroidMediaStream>,
    ): PlaybackInfoData {
        val backendName = if (isMpv) "MPV" else "ExoPlayer"
        val videoStream = streams.firstOrNull {
            it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO
        }
        val audioStream =
            streams.firstOrNull {
                it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO &&
                    (it.index == selectedExternalAudioStreamIndex || it.isDefault == true)
            } ?: streams.firstOrNull { it.type == org.jellyfin.sdk.model.api.MediaStreamType.AUDIO }

        val playMethodStr =
            when (currentPlayMethod) {
                PlayMethod.DIRECT_PLAY -> "Direct Play"
                PlayMethod.DIRECT_STREAM -> "Direct Stream"
                PlayMethod.TRANSCODE -> "Transcode"
            }
        val isTranscoding = currentPlayMethod == PlayMethod.TRANSCODE
        val streamType =
            when (currentPlayMethod) {
                PlayMethod.DIRECT_PLAY -> "Direct (http)"
                PlayMethod.DIRECT_STREAM -> "Direct Stream (remux)"
                PlayMethod.TRANSCODE -> "HLS (https)"
            }

        val containerExt =
            source.container?.uppercase() ?: source.name.substringAfterLast('.', "MKV").uppercase()
        val sizeText = formatFileSize(source.size)

        val durationSecs = (player.duration.takeIf { it > 0 } ?: 1000L) / 1000.0
        val actualBitrateBps =
            source.bitrate
                ?: videoStream?.bitRate
                ?: if (source.size > 0 && durationSecs > 10)
                    ((source.size * 8.0) / durationSecs).toInt()
                else null
        val bitrateText = actualBitrateBps?.let { VideoQualityOption.formatBitrate(it) } ?: ""

        val videoRes =
            if (videoStream?.width != null && videoStream.height != null) {
                "${videoStream.width}x${videoStream.height}"
            } else ""

        val frameRateText = videoStream?.frameRate?.let { String.format(Locale.US, "%.2f fps", it) }

        val rangeType = videoStream?.videoRangeType
        val dynamicRange =
            when {
                !videoStream?.videoDoViTitle.isNullOrBlank() -> videoStream.videoDoViTitle
                rangeType != null -> rangeType.serialName
                else -> "SDR"
            }

        val audioChannels =
            when {
                !audioStream?.channelLayout.isNullOrBlank() -> audioStream.channelLayout
                audioStream?.channels != null -> "${audioStream.channels} Ch"
                else -> null
            }

        val audioSampleRateText = audioStream?.sampleRate?.let { "$it Hz" }

        val videoProfile =
            listOfNotNull(
                    videoStream?.profile,
                    videoStream?.level?.let { "Lv $it" },
                )
                .asSequence()
                .filter { it.isNotBlank() }
                .joinToString(", ")
                .ifBlank { null }

        val transcodeReasons =
            if (currentPlayMethod == PlayMethod.DIRECT_PLAY) emptyList()
            else parseTranscodeReasons(source.transcodingUrl)

        val uri = runCatching { source.transcodingUrl?.toUri() }.getOrNull()

        val transcodeVideoCodec =
            when (currentPlayMethod) {
                PlayMethod.TRANSCODE -> uri?.getQueryParameter("VideoCodec")?.uppercase() ?: "H264"
                PlayMethod.DIRECT_STREAM -> "Copy"
                PlayMethod.DIRECT_PLAY -> null
            }

        val transcodeAudioCodec =
            when (currentPlayMethod) {
                PlayMethod.TRANSCODE -> {
                    val aCodec = uri?.getQueryParameter("AudioCodec")?.uppercase() ?: "AAC"
                    val aChannels = uri?.getQueryParameter("AudioChannels")
                    if (aChannels != null) "$aCodec ($aChannels Ch)" else aCodec
                }
                PlayMethod.DIRECT_STREAM -> {
                    val aCodec = uri?.getQueryParameter("AudioCodec")
                    if (aCodec != null && !aCodec.equals("copy", ignoreCase = true)) {
                        aCodec.uppercase()
                    } else "Copy"
                }
                PlayMethod.DIRECT_PLAY -> null
            }

        val transcodeResolution =
            if (currentPlayMethod == PlayMethod.TRANSCODE) {
                val maxHeight = uri?.getQueryParameter("MaxHeight")?.toIntOrNull()
                val maxWidth = uri?.getQueryParameter("MaxWidth")?.toIntOrNull()
                val origW = videoStream?.width
                val origH = videoStream?.height
                if (maxHeight != null && maxWidth != null) {
                    "${maxWidth}x$maxHeight"
                } else if (maxHeight != null && origW != null && origH != null && origH > 0) {
                    val scaledW = (origW * maxHeight.toDouble() / origH).toInt()
                    "${scaledW}x$maxHeight"
                } else {
                    videoRes.ifBlank { null }
                }
            } else null

        val transcodeBitrate =
            if (currentPlayMethod == PlayMethod.TRANSCODE) {
                val vBitrate =
                    uri?.getQueryParameter("VideoBitRate")?.toIntOrNull()
                        ?: uri?.getQueryParameter("videoBitRate")?.toIntOrNull()
                val aBitrate =
                    uri?.getQueryParameter("AudioBitRate")?.toIntOrNull()
                        ?: uri?.getQueryParameter("audioBitRate")?.toIntOrNull()
                val total = (vBitrate ?: 0) + (aBitrate ?: 0)
                if (total > 0) VideoQualityOption.formatBitrate(total) else null
            } else null

        return PlaybackInfoData(
            playerBackend = "$backendName ($playMethodStr)",
            playMethod = playMethodStr,
            streamType = streamType,
            playerResolution = videoRes,
            videoResolution = videoRes,
            isTranscoding = isTranscoding || currentPlayMethod == PlayMethod.DIRECT_STREAM,
            transcodeVideoCodec = transcodeVideoCodec,
            transcodeAudioCodec = transcodeAudioCodec,
            transcodeResolution = transcodeResolution,
            transcodeProgressRatio = null,
            transcodeProgressText = null,
            transcodeSpeed = null,
            transcodeBitrate = transcodeBitrate,
            transcodeReasons = transcodeReasons,
            container = containerExt,
            sizeText = sizeText.takeIf { it.isNotBlank() },
            bitrateText = bitrateText.takeIf { it.isNotBlank() },
            videoCodec = videoStream?.codec?.uppercase(),
            videoProfile = videoProfile,
            videoFrameRate = frameRateText,
            videoBitDepth = "${videoStream?.bitDepth ?: 8} Bit",
            dynamicRange = dynamicRange,
            audioCodec = audioStream?.codec?.uppercase(),
            audioChannels = audioChannels,
            audioSampleRate = audioSampleRateText,
        )
    }

    private fun parseTranscodeReasons(transcodingUrl: String?): List<String> {
        if (transcodingUrl.isNullOrBlank()) return emptyList()
        val uri =
            try {
                transcodingUrl.toUri()
            } catch (_: Exception) {
                return emptyList()
            }
        val reasonsParam = uri.getQueryParameter("TranscodeReasons") ?: return emptyList()
        return parseTranscodeReasonKeys(reasonsParam.split(",").map { it.trim() })
    }

    private fun parseTranscodeReasonKeys(reasons: List<String>): List<String> {
        return reasons.mapNotNull { reason ->
            val resId =
                when (reason) {
                    "ContainerNotSupported" -> CoreR.string.transcode_reason_container_not_supported
                    "VideoCodecNotSupported" ->
                        CoreR.string.transcode_reason_video_codec_not_supported
                    "AudioCodecNotSupported" ->
                        CoreR.string.transcode_reason_audio_codec_not_supported
                    "SubtitleCodecNotSupported" ->
                        CoreR.string.transcode_reason_subtitle_codec_not_supported
                    "AudioProfileNotSupported" ->
                        CoreR.string.transcode_reason_audio_profile_not_supported
                    "AudioChannelsNotSupported" ->
                        CoreR.string.transcode_reason_audio_channels_not_supported
                    "AudioBitrateExceedsLimit" ->
                        CoreR.string.transcode_reason_audio_bitrate_exceeds_limit
                    "AudioSampleRateNotSupported" ->
                        CoreR.string.transcode_reason_audio_sample_rate_not_supported
                    "AudioBitDepthNotSupported" ->
                        CoreR.string.transcode_reason_audio_bit_depth_not_supported
                    "VideoProfileNotSupported" ->
                        CoreR.string.transcode_reason_video_profile_not_supported
                    "VideoLevelNotSupported" ->
                        CoreR.string.transcode_reason_video_level_not_supported
                    "VideoResolutionNotSupported" ->
                        CoreR.string.transcode_reason_video_resolution_not_supported
                    "VideoBitrateExceedsLimit" ->
                        CoreR.string.transcode_reason_video_bitrate_exceeds_limit
                    "VideoFramerateExceedsLimit" ->
                        CoreR.string.transcode_reason_video_framerate_exceeds_limit
                    "RefFramesNotSupported" ->
                        CoreR.string.transcode_reason_ref_frames_not_supported
                    "AnamorphicVideoNotSupported" ->
                        CoreR.string.transcode_reason_anamorphic_video_not_supported
                    "InterlacedVideoNotSupported" ->
                        CoreR.string.transcode_reason_interlaced_video_not_supported
                    "VideoRangeTypeNotSupported" ->
                        CoreR.string.transcode_reason_video_range_type_not_supported
                    "ContainerBitrateExceedsLimit" ->
                        CoreR.string.transcode_reason_container_bitrate_exceeds_limit
                    "DeviceProfileNotSatisfied" ->
                        CoreR.string.transcode_reason_device_profile_not_satisfied
                    "AudioIsExternal" -> CoreR.string.transcode_reason_audio_is_external
                    "SecondaryAudioNotSupported" ->
                        CoreR.string.transcode_reason_secondary_audio_not_supported
                    "DirectPlayError" -> CoreR.string.transcode_reason_direct_play_error
                    else -> null
                }
            if (resId != null) {
                application.getString(resId)
            } else if (reason.isNotBlank()) {
                reason
                    .replace("_", " ")
                    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
            } else null
        }
    }

    fun resetPlaybackStatsHistory() {
        _uiState.update { state ->
            state.copy(
                playbackInfoData =
                    state.playbackInfoData.copy(
                        transcodeSpeedHistory = emptyList(),
                        bufferHealthHistory = emptyList(),
                    )
            )
        }
    }

    fun onPlaybackInfoOpened() {
        resetPlaybackStatsHistory()
        currentSource?.let { src ->
            _uiState.update { state ->
                val updatedInfo = buildPlaybackInfoData(src, state.mediaStreams)
                state.copy(playbackInfoData = updatedInfo)
            }
        }
        refreshPlaybackStats()
    }

    fun refreshPlaybackStats() {
        val currentPlay = player.isPlaying
        val statusText =
            when (player.playbackState) {
                Player.STATE_BUFFERING ->
                    application.getString(CoreR.string.playback_status_buffering)
                Player.STATE_READY ->
                    if (currentPlay) application.getString(CoreR.string.playback_status_playing)
                    else application.getString(CoreR.string.playback_status_paused)
                Player.STATE_IDLE,
                Player.STATE_ENDED -> application.getString(CoreR.string.playback_status_idle)
                else -> application.getString(CoreR.string.playback_status_idle)
            }

        val droppedCount =
            when (player) {
                is MPVPlayer -> player.getDroppedFrames().toString()
                is ExoPlayer -> player.videoDecoderCounters?.droppedBufferCount?.toString() ?: "0"
                else -> "0"
            }

        val currentBufferSecs = (player.totalBufferedDuration / 1000f).coerceAtLeast(0f)
        val bufferHealthText = String.format(Locale.US, "%.1f s", currentBufferSecs)
        val audioOutputText = if (isMpv) "MPV AudioTrack" else "ExoPlayer AudioTrack"

        _uiState.update { state ->
            val cur = state.playbackInfoData
            val newBufHistory = (cur.bufferHealthHistory + currentBufferSecs).takeLast(60)
            state.copy(
                playbackInfoData =
                    cur.copy(
                        droppedFrames = droppedCount,
                        bufferHealth = bufferHealthText,
                        playbackStatus = statusText,
                        audioOutput = audioOutputText,
                        bufferHealthHistory = newBufHistory,
                    )
            )
        }

        if (currentPlayMethod != PlayMethod.DIRECT_PLAY) {
            viewModelScope.launch {
                val transInfo = repository.getActiveTranscodingInfo(playSessionId) ?: return@launch
                if (transInfo.isVideoDirect && currentPlayMethod == PlayMethod.TRANSCODE) {
                    currentPlayMethod = PlayMethod.DIRECT_STREAM
                } else if (
                    !transInfo.isVideoDirect && currentPlayMethod == PlayMethod.DIRECT_STREAM
                ) {
                    currentPlayMethod = PlayMethod.TRANSCODE
                }
                val playMethodStr =
                    when (currentPlayMethod) {
                        PlayMethod.DIRECT_PLAY -> "Direct Play"
                        PlayMethod.DIRECT_STREAM -> "Direct Stream"
                        PlayMethod.TRANSCODE -> "Transcode"
                    }
                val backendName = if (isMpv) "MPV" else "ExoPlayer"

                _uiState.update { state ->
                    val currentInfo = state.playbackInfoData
                    val completion = transInfo.completionPercentage
                    val fps = transInfo.framerate
                    val origFps =
                        state.mediaStreams
                            .firstOrNull {
                                it.type == org.jellyfin.sdk.model.api.MediaStreamType.VIDEO
                            }
                            ?.frameRate

                    val progressRatio =
                        completion?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
                            ?: currentInfo.transcodeProgressRatio
                    val progressText =
                        completion?.let { String.format(Locale.US, "%.1f%%", it) }
                            ?: currentInfo.transcodeProgressText

                    val speedText =
                        if (fps != null && fps > 0) {
                            if (origFps != null && origFps > 0) {
                                val ratio = fps / origFps
                                String.format(Locale.US, "%.2f×", ratio)
                            } else null
                        } else currentInfo.transcodeSpeed

                    val bitrateText =
                        transInfo.bitrate?.let { VideoQualityOption.formatBitrate(it) }
                            ?: currentInfo.transcodeBitrate

                    val resText =
                        if (currentPlayMethod == PlayMethod.DIRECT_STREAM) {
                            currentInfo.videoResolution
                        } else if (transInfo.width != null && transInfo.height != null) {
                            "${transInfo.width}x${transInfo.height}"
                        } else currentInfo.transcodeResolution

                    val vCodec =
                        if (
                            currentPlayMethod == PlayMethod.DIRECT_STREAM || transInfo.isVideoDirect
                        ) {
                            "Copy"
                        } else {
                            transInfo.videoCodec?.uppercase() ?: currentInfo.transcodeVideoCodec
                        }

                    val aCodec =
                        if (transInfo.isAudioDirect) {
                            "Copy"
                        } else {
                            transInfo.audioCodec?.uppercase()?.let {
                                if (transInfo.audioChannels != null)
                                    "$it (${transInfo.audioChannels} Ch)"
                                else it
                            } ?: currentInfo.transcodeAudioCodec
                        }

                    val reasons =
                        if (transInfo.transcodeReasons.isNotEmpty()) {
                            parseTranscodeReasonKeys(transInfo.transcodeReasons.map { it.name })
                        } else currentInfo.transcodeReasons

                    val newSpeedHistory =
                        if (fps != null && fps > 0) {
                            (currentInfo.transcodeSpeedHistory + fps).takeLast(60)
                        } else currentInfo.transcodeSpeedHistory

                    state.copy(
                        playbackInfoData =
                            currentInfo.copy(
                                playerBackend = "$backendName ($playMethodStr)",
                                playMethod = playMethodStr,
                                isTranscoding = true,
                                transcodeProgressRatio = progressRatio,
                                transcodeProgressText = progressText,
                                transcodeSpeed = speedText,
                                transcodeBitrate = bitrateText,
                                transcodeResolution = resText,
                                transcodeVideoCodec = vCodec,
                                transcodeAudioCodec = aCodec,
                                transcodeReasons = reasons,
                                transcodeSpeedHistory = newSpeedHistory,
                            )
                    )
                }
            }
        }
    }

    private fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return ""
        val gib = sizeBytes / (1024.0 * 1024.0 * 1024.0)
        if (gib >= 1.0) return String.format(Locale.US, "%.1f GiB", gib)
        val mib = sizeBytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f MiB", mib)
    }

    fun toggleFavorite() {
        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val itemId = UUID.fromString(currentMediaId)
        val currentlyFavorite = _uiState.value.isFavorite
        viewModelScope.launch {
            if (_uiState.value.selectedQuality.isAuto) {
                _uiState.update { it.copy(isSpeedTesting = true) }
            }
            try {
                if (currentlyFavorite) {
                    repository.unmarkAsFavorite(itemId)
                } else {
                    repository.markAsFavorite(itemId)
                }
                _uiState.update { it.copy(isFavorite = !currentlyFavorite) }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling favorite")
            }
        }
    }

    fun playEpisode(episode: FindroidEpisode) {
        initializePlayer(
            itemId = episode.id,
            itemKind = BaseItemKind.EPISODE.serialName,
            startFromBeginning = false,
        )
    }

    private fun PlayerItem.toMediaItem(): MediaItem {
        val streamUrl = mediaSourceUri
        val mediaSubtitles = externalSubtitles.mapIndexed { idx, externalSubtitle ->
            var selectionFlags = 0
            if (externalSubtitle.isForced) {
                selectionFlags = selectionFlags or C.SELECTION_FLAG_FORCED
            }
            if (externalSubtitle.isDefault) {
                selectionFlags = selectionFlags or C.SELECTION_FLAG_DEFAULT
            }
            var roleFlags = 0
            if (externalSubtitle.isHearingImpaired) {
                roleFlags = 0 or C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or C.ROLE_FLAG_CAPTION
            }
            MediaItem.SubtitleConfiguration.Builder(externalSubtitle.uri)
                .setId("external_${externalSubtitle.index ?: idx}")
                .setLabel(
                    externalSubtitle.title.ifBlank {
                        externalSubtitle.displayTitle?.ifBlank { null }
                            ?: application.getString(CoreR.string.external)
                    }
                )
                .setMimeType(externalSubtitle.mimeType)
                .setLanguage(externalSubtitle.language)
                .setSelectionFlags(selectionFlags)
                .setRoleFlags(roleFlags)
                .build()
        }

        Timber.d("Stream url: $streamUrl")
        val partName = this.partName
        val title =
            if (partName != null) {
                "$name - ${partName.getTranslatablePartName(application)}"
            } else {
                name
            }

        val mediaItem =
            MediaItem.Builder()
                .setMediaId(itemId.toString())
                .setUri(streamUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .setSubtitleConfigurations(mediaSubtitles)
                .build()

        return mediaItem
    }

    private fun releasePlayer() {
        if (player.currentMediaItem?.mediaId == null) return
        val position = player.currentPosition
        val duration = player.duration

        appScope.launch {
            playbackManager.stopSession(
                positionMs = position,
                durationMs = duration,
            )
        }

        _uiState.update { it.copy(currentTrickplay = null) }
        playWhenReady = false
        playbackPosition = 0L
        currentMediaItemIndex = 0
        player.removeListener(this)
        player.release()
    }

    fun updatePlaybackProgress() {
        val currentPos = player.currentPosition
        if (currentPos < 0L) {
            return
        }
        Timber.d("Updating playback progress")
        viewModelScope.launch(Dispatchers.Main) {
            savedStateHandle["position"] = currentPos
            if (player.currentMediaItem != null && player.currentMediaItem!!.mediaId.isNotEmpty()) {
                playbackManager.reportProgress(
                    positionMs = currentPos,
                    isPaused = !player.isPlaying,
                )
            }
        }
    }

    fun updateCurrentSegment() {
        Timber.d("Updating current segment")
        viewModelScope.launch(Dispatchers.Main) {
            if (currentMediaItemSegments.isEmpty()) {
                activeSegment = null
                return@launch
            }

            val milliSeconds = player.currentPosition

            // Get current segment, - 100 milliseconds to avoid showing button after segment ends
            val currentSegment = currentMediaItemSegments.find { segment ->
                milliSeconds in segment.startTicks..<(segment.endTicks - 100L)
            }

            if (currentSegment == null) {
                // Remove button if not pressed and there is no current segment
                if (_uiState.value.currentSegment != null) {
                    _uiState.update { it.copy(currentSegment = null) }
                }
                activeSegment = null
                return@launch
            }

            if (currentSegment == activeSegment) {
                return@launch
            }

            activeSegment = currentSegment

            Timber.tag("SegmentInfo").d("currentSegment: %s", currentSegment)

            val isAutoSkipConfigured =
                segmentsAutoSkip && segmentsAutoSkipTypes.contains(currentSegment.type.toString())
            val isSkipButtonConfigured =
                segmentsSkipButton &&
                    segmentsSkipButtonTypes.contains(currentSegment.type.toString())

            val autoSkipMode = PlayerMediaSegmentsAutoSkip.fromValue(segmentsAutoSkipMode)
            if (
                isAutoSkipConfigured &&
                    (autoSkipMode == PlayerMediaSegmentsAutoSkip.ALWAYS ||
                        (autoSkipMode == PlayerMediaSegmentsAutoSkip.PIP &&
                            isInPictureInPictureMode))
            ) {
                // Auto Skip segment
                skipSegment(currentSegment)
            } else if (
                isSkipButtonConfigured ||
                    (isAutoSkipConfigured &&
                        autoSkipMode == PlayerMediaSegmentsAutoSkip.PIP &&
                        !isInPictureInPictureMode)
            ) {
                // Skip Button segment
                _uiState.update {
                    it.copy(
                        currentSegment = currentSegment,
                        currentSkipButtonStringRes =
                            getSkipButtonTextStringId(
                                currentSegment,
                                shouldSkipToNextEpisode(currentSegment),
                            ),
                    )
                }
            } else {
                _uiState.update { it.copy(currentSegment = null) }
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val currentSleepMode = _uiState.value.sleepTimerStatus.mode
        if (currentSleepMode is SleepTimerMode.EndOfMedia) {
            player.pause()
            setSleepTimer(SleepTimerMode.Off)
            eventsChannel.trySend(PlayerEvents.NavigateBack)
            return
        }
        if (
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                !appPreferences.getValue(appPreferences.playerAutoplayNext)
        ) {
            player.pause()
            eventsChannel.trySend(PlayerEvents.NavigateBack)
            return
        }
        Timber.d("Playing MediaItem: ${mediaItem?.mediaId}")
        savedStateHandle["mediaItemIndex"] = player.currentMediaItemIndex
        viewModelScope.launch {
            if (_uiState.value.selectedQuality.isAuto) {
                _uiState.update { it.copy(isSpeedTesting = true) }
            }
            try {
                items
                    .first { it.itemId.toString() == player.currentMediaItem?.mediaId }
                    .let { item ->
                        val parentIndexNumber = item.parentIndexNumber
                        val indexNumber = item.indexNumber
                        val indexNumberEnd = item.indexNumberEnd
                        val (itemTitle, itemSubtitle) =
                            if (parentIndexNumber != null && indexNumber != null) {
                                val epNumberStr =
                                    if (indexNumberEnd == null || indexNumberEnd <= indexNumber) {
                                        indexNumber.toString()
                                    } else {
                                        "$indexNumber-$indexNumberEnd"
                                    }
                                val baseStr = "S$parentIndexNumber:E$epNumberStr"
                                val partName = item.partName
                                val partSuffix =
                                    if (partName != null) {
                                        " - ${partName.getTranslatablePartName(application)}"
                                    } else {
                                        ""
                                    }
                                val epTitle = "$baseStr$partSuffix • ${item.name}"
                                val seriesName = item.seriesName?.takeIf { it.isNotBlank() }
                                if (seriesName != null) {
                                    seriesName to epTitle
                                } else {
                                    item.name to "$baseStr$partSuffix"
                                }
                            } else {
                                val partName = item.partName
                                val title =
                                    if (partName != null) {
                                        "${item.name} - ${partName.getTranslatablePartName(application)}"
                                    } else {
                                        item.name
                                    }
                                title to null
                            }
                        val loadedSegments =
                            getSegments(item.itemId, repository) {
                                appPreferences.getSegmentColor(it.name)
                            }
                        activeSegment = null
                        currentMediaItemSegments = loadedSegments

                        _uiState.update {
                            it.copy(
                                currentItemTitle = itemTitle,
                                currentItemSubtitle = itemSubtitle,
                                currentSegment = null,
                                currentChapters = item.chapters,
                                segments = loadedSegments,
                                currentImages = item.images,
                                showChapterMarkers =
                                    appPreferences.getValue(appPreferences.playerChapterMarkers),
                                fileLoaded = false,
                            )
                        }

                        hasReportedStart = false

                        currentItem.value = item

                        loadItemMetadata(item.itemId)

                        playlistManager.setCurrentMediaItemIndex(item.itemId)

                        val previousItem = playlistManager.getPreviousPlayerItem()
                        if (previousItem != null && previousItem !in items) {
                            items.add(player.currentMediaItemIndex, previousItem)
                            player.addMediaItem(
                                player.currentMediaItemIndex,
                                previousItem.toMediaItem(),
                            )
                        }

                        val nextItem = playlistManager.getNextPlayerItem()
                        if (nextItem != null && nextItem !in items) {
                            items.add(player.currentMediaItemIndex + 1, nextItem)
                            player.addMediaItem(
                                player.currentMediaItemIndex + 1,
                                nextItem.toMediaItem(),
                            )
                        }

                        Timber.tag("PlayerItems").d(items.map { it.indexNumber }.toString())
                    }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // Report playback stopped for current item and transition to the next one
        if (
            !playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                player.playbackState == ExoPlayer.STATE_READY
        ) {
            viewModelScope.launch {
                val mediaId = player.currentMediaItem?.mediaId ?: return@launch
                val position = player.currentPosition
                val duration = player.duration
                playbackManager.reportStop(
                    itemId = UUID.fromString(mediaId),
                    positionMs = position,
                    durationMs = duration,
                    mediaSourceId = currentMediaSourceId,
                    playSessionId = playSessionId,
                )
                if (appPreferences.getValue(appPreferences.playerAutoplayNext)) {
                    player.seekToNextMediaItem()
                    player.play()
                } else {
                    eventsChannel.send(PlayerEvents.NavigateBack)
                }
            }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            ExoPlayer.STATE_READY -> _uiState.update { it.copy(fileLoaded = true) }
            Player.STATE_BUFFERING -> {}
            Player.STATE_IDLE -> {}
            ExoPlayer.STATE_ENDED -> {
                if (_uiState.value.sleepTimerStatus.mode is SleepTimerMode.EndOfMedia) {
                    setSleepTimer(SleepTimerMode.Off)
                }
                eventsChannel.trySend(PlayerEvents.NavigateBack)
            }
        }
        Timber.d("Changed player state to $state")
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            updateCurrentSegment()
        }
    }

    override fun onCleared() {
        Timber.d("Clearing Player ViewModel")
        releasePlayer()
    }

    fun switchToTrack(trackType: @C.TrackType Int, index: Int) {
        // Index -1 equals disable track
        if (index == -1) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(trackType)
                    .setTrackTypeDisabled(trackType, true)
                    .build()
        } else {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(trackType)
                    .setOverrideForType(
                        TrackSelectionOverride(
                            player.currentTracks.groups
                                .filter { it.type == trackType && it.isSupported }[index]
                                .mediaTrackGroup,
                            0,
                        )
                    )
                    .setTrackTypeDisabled(trackType, false)
                    .build()
        }
    }

    fun selectSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
        if (appPreferences.getValue(appPreferences.playerPlaybackSpeedRemember)) {
            appPreferences.setValue(appPreferences.playerPlaybackSpeed, speed)
        }
    }

    fun setSleepTimer(mode: SleepTimerMode) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        when (mode) {
            is SleepTimerMode.Off -> {
                _uiState.update {
                    it.copy(sleepTimerStatus = SleepTimerStatus(SleepTimerMode.Off, 0L))
                }
            }
            is SleepTimerMode.EndOfMedia -> {
                val initialDuration = if (player.duration > 0) player.duration else 0L
                val initialPosition = maxOf(0L, player.currentPosition)
                val initialRemaining = maxOf(0L, initialDuration - initialPosition) / 1000L
                _uiState.update {
                    it.copy(
                        sleepTimerStatus =
                            SleepTimerStatus(SleepTimerMode.EndOfMedia, initialRemaining)
                    )
                }
                sleepTimerJob = viewModelScope.launch {
                    while (true) {
                        delay(1000L.milliseconds)
                        val duration = if (player.duration > 0) player.duration else 0L
                        val position = maxOf(0L, player.currentPosition)
                        val remaining = maxOf(0L, duration - position) / 1000L
                        _uiState.update {
                            it.copy(
                                sleepTimerStatus =
                                    SleepTimerStatus(SleepTimerMode.EndOfMedia, remaining)
                            )
                        }
                    }
                }
            }
            is SleepTimerMode.Duration -> {
                val totalSeconds = mode.totalMinutes * 60L
                _uiState.update { it.copy(sleepTimerStatus = SleepTimerStatus(mode, totalSeconds)) }
                sleepTimerJob = viewModelScope.launch {
                    var remaining = totalSeconds
                    while (remaining > 0) {
                        delay(1000L.milliseconds)
                        remaining--
                        _uiState.update {
                            it.copy(sleepTimerStatus = SleepTimerStatus(mode, remaining))
                        }
                    }
                    _uiState.update {
                        it.copy(sleepTimerStatus = SleepTimerStatus(SleepTimerMode.Off, 0L))
                    }
                    player.pause()
                }
            }
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        _uiState.update { it.copy(playbackSpeed = playbackParameters.speed) }
        if (appPreferences.getValue(appPreferences.playerPlaybackSpeedRemember)) {
            appPreferences.setValue(appPreferences.playerPlaybackSpeed, playbackParameters.speed)
        }
    }

    fun skipSegment(segment: FindroidSegment) {
        if (shouldSkipToNextEpisode(segment)) {
            player.seekToNextMediaItem()
        } else {
            player.seekTo(segment.endTicks)
        }
        activeSegment = null
        _uiState.update { it.copy(currentSegment = null) }
    }

    private fun shouldSkipToNextEpisode(segment: FindroidSegment): Boolean {
        return appPreferences.getValue(appPreferences.playerAutoplayNext) &&
            SegmentUtils.shouldSkipToNextEpisode(
                segment = segment,
                hasNextMediaItem = player.hasNextMediaItem(),
                playerDurationMillis = player.duration,
                nextEpisodeThreshold =
                    appPreferences.getValue(appPreferences.playerMediaSegmentsNextEpisodeThreshold),
            )
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        eventsChannel.trySend(PlayerEvents.IsPlayingChanged(isPlaying))

        if (isPlaying && !playbackManager.isReporting) {
            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId != null) {
                viewModelScope.launch {
                    playbackManager.startSession(
                        itemId = UUID.fromString(mediaId),
                        positionMs = player.currentPosition,
                        playMethod = currentPlayMethod,
                        mediaSourceId = currentMediaSourceId,
                        playSessionId = playSessionId,
                    )
                }
            }
        } else if (playbackManager.isReporting) {
            updatePlaybackProgress()
        }
    }
}

sealed interface PlayerEvents {
    data object NavigateBack : PlayerEvents

    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvents
}
