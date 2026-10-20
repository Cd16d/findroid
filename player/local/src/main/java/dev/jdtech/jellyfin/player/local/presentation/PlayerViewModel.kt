package dev.jdtech.jellyfin.player.local.presentation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.di.ApplicationScope
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.player.core.R as CoreR
import dev.jdtech.jellyfin.player.core.domain.PlaybackManager
import dev.jdtech.jellyfin.player.core.domain.PlaylistManager
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerImages
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerMode
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerStatus
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.core.domain.utils.SegmentUtils
import dev.jdtech.jellyfin.player.core.domain.utils.SegmentUtils.getSegments
import dev.jdtech.jellyfin.player.core.domain.utils.SegmentUtils.getSkipButtonTextStringId
import dev.jdtech.jellyfin.player.local.mpv.MPVPlayer
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import dev.jdtech.jellyfin.utils.getTranslatablePartName
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.PlayMethod
import timber.log.Timber

@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    private val application: Application,
    private val playlistManager: PlaylistManager,
    private val playbackManager: PlaybackManager,
    private val repository: JellyfinRepository,
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
    )

    private var items: MutableList<PlayerItem> = mutableListOf()
    private var sleepTimerJob: Job? = null

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
    private var segmentsAutoSkipMode: String = Constants.PlayerMediaSegmentsAutoSkip.ALWAYS.value

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

    init {
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
        segmentsSkipButton = appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton)
        segmentsSkipButtonTypes =
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonType)
        segmentsSkipButtonDuration =
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonDuration)
        segmentsAutoSkip = appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)
        segmentsAutoSkipTypes =
            appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipType)
        segmentsAutoSkipMode =
            appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipMode)

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

        val playerBackend =
            Constants.PlayerBackend.fromValue(appPreferences.getValue(appPreferences.playerBackend))
        player =
            when (playerBackend) {
                Constants.PlayerBackend.EXOPLAYER -> {
                    val renderersFactory =
                        DefaultRenderersFactory(application)
                            .setExtensionRendererMode(
                                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            )
                    ExoPlayer.Builder(application, renderersFactory)
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
                Constants.PlayerBackend.MPV -> {
                    MPVPlayer.Builder(application)
                        .setAudioAttributes(audioAttributes, true)
                        .setTrackSelectionParameters(trackSelector.parameters)
                        .setSeekBackIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekBackInc)
                        )
                        .setSeekForwardIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekForwardInc)
                        )
                        .setPauseAtEndOfMediaItems(true)
                        .setVideoOutput(appPreferences.getValue(appPreferences.playerMpvVo))
                        .setAudioOutput(appPreferences.getValue(appPreferences.playerMpvAo))
                        .setHwDec(appPreferences.getValue(appPreferences.playerMpvHwdec))
                        .build()
                }
            }
    }

    fun initializePlayer(itemId: UUID, itemKind: String, startFromBeginning: Boolean) {
        player.addListener(this)

        if (appPreferences.getValue(appPreferences.playerPlaybackSpeedRemember)) {
            val savedSpeed = appPreferences.getValue(appPreferences.playerPlaybackSpeed)
            player.setPlaybackSpeed(savedSpeed)
            _uiState.update { it.copy(playbackSpeed = savedSpeed) }
        }

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

            items = listOfNotNull(startItem).toMutableList()
            currentMediaItemIndex = items.indexOf(startItem)

            val mediaItems = mutableListOf<MediaItem>()
            try {
                for (item in items) {
                    mediaItems.add(item.toMediaItem())
                }
            } catch (e: Exception) {
                Timber.e(e)
            }

            val startPosition =
                if (playbackPosition == 0L) {
                    items.getOrNull(currentMediaItemIndex)?.playbackPosition ?: C.TIME_UNSET
                } else {
                    playbackPosition
                }

            player.setMediaItems(mediaItems, 0, startPosition)
            player.prepare()
            player.play()
        }
        loadItemMetadata(itemId)
    }

    fun getBaseUrl(): String = repository.getBaseUrl()

    fun loadItemMetadata(itemId: UUID) {
        viewModelScope.launch {
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
                    val mediaSources =
                        try {
                            repository.getMediaSources(itemId, includePath = true)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    val streams = mediaSources.firstOrNull()?.mediaStreams ?: emptyList()
                    val suggestions =
                        try {
                            repository.getSuggestions(forceRefresh = false)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    _uiState.update {
                        it.copy(
                            isFavorite = isFav,
                            seasonEpisodes = episodes,
                            currentEpisodeId = if (item is FindroidEpisode) item.id else null,
                            seriesId = if (item is FindroidEpisode) item.seriesId else null,
                            mediaStreams = streams,
                            suggestions = suggestions,
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading item metadata for player")
            }
        }
    }

    fun toggleFavorite() {
        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val itemId = UUID.fromString(currentMediaId)
        val currentlyFavorite = _uiState.value.isFavorite
        viewModelScope.launch {
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
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val position = player.currentPosition
        val duration = player.duration

        appScope.launch {
            playbackManager.reportStop(
                itemId = UUID.fromString(mediaId),
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
        Timber.d("Updating playback progress")
        viewModelScope.launch(Dispatchers.Main) {
            savedStateHandle["position"] = player.currentPosition
            if (player.currentMediaItem != null && player.currentMediaItem!!.mediaId.isNotEmpty()) {
                val itemId = UUID.fromString(player.currentMediaItem!!.mediaId)
                playbackManager.reportProgress(
                    itemId = itemId,
                    positionMs = player.currentPosition,
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

            val autoSkipMode = Constants.PlayerMediaSegmentsAutoSkip.fromValue(segmentsAutoSkipMode)
            if (
                isAutoSkipConfigured &&
                    (autoSkipMode == Constants.PlayerMediaSegmentsAutoSkip.ALWAYS ||
                        (autoSkipMode == Constants.PlayerMediaSegmentsAutoSkip.PIP &&
                            isInPictureInPictureMode))
            ) {
                // Auto Skip segment
                skipSegment(currentSegment)
            } else if (
                isSkipButtonConfigured ||
                    (isAutoSkipConfigured &&
                        autoSkipMode == Constants.PlayerMediaSegmentsAutoSkip.PIP &&
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
                                        "$indexNumber"
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

                        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
                            getTrickplay(item)
                        }

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
        var stateString = "UNKNOWN_STATE             -"
        when (state) {
            ExoPlayer.STATE_IDLE -> {
                stateString = "ExoPlayer.STATE_IDLE      -"
            }

            ExoPlayer.STATE_BUFFERING -> {
                stateString = "ExoPlayer.STATE_BUFFERING -"
            }

            ExoPlayer.STATE_READY -> {
                stateString = "ExoPlayer.STATE_READY     -"
                _uiState.update { it.copy(fileLoaded = true) }
            }

            ExoPlayer.STATE_ENDED -> {
                stateString = "ExoPlayer.STATE_ENDED     -"
                val currentSleepMode = _uiState.value.sleepTimerStatus.mode
                if (currentSleepMode is SleepTimerMode.EndOfMedia) {
                    setSleepTimer(SleepTimerMode.Off)
                }
                eventsChannel.trySend(PlayerEvents.NavigateBack)
            }
        }
        Timber.d("Changed player state to $stateString")
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

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onPositionDiscontinuity(reason: Int) {
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

    private suspend fun getTrickplay(item: PlayerItem) {
        val trickplayInfo = item.trickplayInfo ?: return
        Timber.d("Trickplay Resolution: ${trickplayInfo.width}")

        withContext(Dispatchers.Default) {
            val tilesPerSheet = trickplayInfo.tileWidth * trickplayInfo.tileHeight
            if (tilesPerSheet <= 0) return@withContext
            val sheetCount =
                ceil(trickplayInfo.thumbnailCount.toDouble().div(tilesPerSheet)).toInt()
            val bitmaps = mutableListOf<Bitmap>()

            for (i in 0 until sheetCount) {
                if (bitmaps.size >= trickplayInfo.thumbnailCount) break
                repository.getTrickplayData(item.itemId, trickplayInfo.width, i)?.let { byteArray ->
                    val fullBitmap =
                        BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size) ?: return@let
                    try {
                        for (offsetY in
                            0..<trickplayInfo.height * trickplayInfo.tileHeight step
                                trickplayInfo.height) {
                            for (offsetX in
                                0..<trickplayInfo.width * trickplayInfo.tileWidth step
                                    trickplayInfo.width) {
                                if (bitmaps.size < trickplayInfo.thumbnailCount) {
                                    val bitmap =
                                        Bitmap.createBitmap(
                                            fullBitmap,
                                            offsetX,
                                            offsetY,
                                            trickplayInfo.width,
                                            trickplayInfo.height,
                                        )
                                    bitmaps.add(bitmap)
                                } else {
                                    break
                                }
                            }
                            if (bitmaps.size >= trickplayInfo.thumbnailCount) break
                        }
                    } finally {
                        fullBitmap.recycle()
                    }
                }
            }
            _uiState.update {
                it.copy(currentTrickplay = Trickplay(trickplayInfo.interval, bitmaps))
            }
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

        if (isPlaying && !hasReportedStart) {
            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId != null) {
                viewModelScope.launch {
                    playbackManager.reportStart(
                        itemId = UUID.fromString(mediaId),
                        playMethod = PlayMethod.DIRECT_PLAY,
                    )
                }
                hasReportedStart = true
            }
        } else if (hasReportedStart) {
            updatePlaybackProgress()
        }
    }
}

sealed interface PlayerEvents {
    data object NavigateBack : PlayerEvents

    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvents
}
