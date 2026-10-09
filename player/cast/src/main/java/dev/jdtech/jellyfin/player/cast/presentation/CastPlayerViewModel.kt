package dev.jdtech.jellyfin.player.cast.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.cast.CastPlayerController
import dev.jdtech.jellyfin.player.cast.CastSessionManager
import dev.jdtech.jellyfin.player.cast.models.CastConnectionState
import dev.jdtech.jellyfin.player.cast.models.CastPlaybackStatus
import dev.jdtech.jellyfin.player.cast.models.CastPlayerState
import dev.jdtech.jellyfin.player.cast.models.Device
import dev.jdtech.jellyfin.player.core.R
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerImage
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.PlayerMediaType
import dev.jdtech.jellyfin.player.core.domain.models.Track
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.core.domain.utils.SegmentUtils
import dev.jdtech.jellyfin.player.core.domain.utils.SegmentUtils.getSegments
import dev.jdtech.jellyfin.player.core.domain.utils.SegmentUtils.getSkipButtonTextStringId
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import dev.jdtech.jellyfin.utils.getTranslatablePartName
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class CastPlayerViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val sessionManager: CastSessionManager,
    val playerController: CastPlayerController,
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Visible for testing / dispatcher overriding
    internal var defaultDispatcher: CoroutineDispatcher = Dispatchers.Default

    constructor(
        context: Context,
        sessionManager: CastSessionManager,
        playerController: CastPlayerController,
        repository: JellyfinRepository,
        appPreferences: AppPreferences,
        savedStateHandle: SavedStateHandle,
        defaultDispatcher: CoroutineDispatcher,
    ) : this(
        context = context,
        sessionManager = sessionManager,
        playerController = playerController,
        repository = repository,
        appPreferences = appPreferences,
        savedStateHandle = savedStateHandle,
    ) {
        this.defaultDispatcher = defaultDispatcher
    }

    data class CurrentItemTitle(
        val seriesName: String? = null,
        val episodeInfo: String? = null,
        val title: String,
    )

    data class UiState(
        val connectionState: CastConnectionState = CastConnectionState.DISCONNECTED,
        val playerState: CastPlayerState = CastPlayerState(),
        val availableDevices: List<Device> = emptyList(),
        val connectedDevice: Device? = null,
        val currentItemTitle: CurrentItemTitle = CurrentItemTitle(title = ""),
        val currentItemPoster: PlayerImage? = null,
        val isMovie: Boolean = false,
        val defaultAspectRatio: Float = 16f / 10f,
        val trickplayAspectRatio: Float? = null,
        val currentSegment: FindroidSegment? = null,
        val currentSkipButtonStringRes: Int = R.string.player_controls_skip_intro,
        val currentTrickplay: Trickplay? = null,
        val currentChapters: List<PlayerChapter> = emptyList(),
        val fileLoaded: Boolean = false,
        val audioTracks: List<Track> = emptyList(),
        val subtitleTracks: List<Track> = emptyList(),
        val displayExtraInfo: Boolean = false,
    )

    private val _uiState =
        MutableStateFlow(
            UiState(
                connectionState = sessionManager.connectionState.value,
                availableDevices = sessionManager.availableDevices.value,
                connectedDevice = sessionManager.connectedDevice.value,
                playerState = playerController.playerState.value,
                currentItemTitle = CurrentItemTitle(title = ""),
                currentItemPoster = null,
                isMovie = false,
                defaultAspectRatio = 16f / 10f,
                trickplayAspectRatio = null,
                currentSegment = null,
                currentSkipButtonStringRes = R.string.player_controls_skip_intro,
                currentTrickplay = null,
                currentChapters = emptyList(),
                fileLoaded = false,
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                displayExtraInfo = appPreferences.getValue(appPreferences.displayExtraInfo),
            )
        )

    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var currentMediaItemSegments: List<FindroidSegment> = emptyList()
    var currentItemId: UUID? =
        savedStateHandle.get<String>(KEY_CURRENT_ITEM_ID)?.let {
            try {
                UUID.fromString(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        private set

    private var trickplayJob: Job? = null

    // Segments preferences
    private val segmentsSkipButton: Boolean
        get() = appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton)

    private val segmentsAutoSkip: Boolean
        get() = appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)

    init {
        sessionManager.connectionState
            .onEach { value ->
                _uiState.update { it.copy(connectionState = value) }
                if (value == CastConnectionState.DISCONNECTED) {
                    onMediaItemCleared()
                }
            }
            .launchIn(viewModelScope)

        sessionManager.availableDevices
            .onEach { value -> _uiState.update { it.copy(availableDevices = value) } }
            .launchIn(viewModelScope)

        sessionManager.connectedDevice
            .onEach { value -> _uiState.update { it.copy(connectedDevice = value) } }
            .launchIn(viewModelScope)

        playerController.playerState
            .onEach { value ->
                _uiState.update { it.copy(playerState = value) }
                if (segmentsSkipButton || segmentsAutoSkip) {
                    updateCurrentSegment(value.currentPosition)
                }
            }
            .launchIn(viewModelScope)

        playerController.currentItem
            .onEach { item ->
                _uiState.update {
                    it.copy(
                        subtitleTracks = item?.subtitleTracks ?: emptyList(),
                        audioTracks = item?.audioTracks ?: emptyList(),
                    )
                }
                if (item != null) {
                    onMediaItemTransition(item.item)
                } else {
                    onMediaItemCleared()
                }
                Timber.d("CurrentItem: $item")
            }
            .launchIn(viewModelScope)
    }

    /** Handles the transition when a new media item starts playing. */
    private suspend fun onMediaItemTransition(item: PlayerItem) {
        Timber.d("Cast MediaItem transition: ${item.itemId}")
        currentItemId = item.itemId
        savedStateHandle[KEY_CURRENT_ITEM_ID] = item.itemId.toString()
        val isMovie = item.mediaType == PlayerMediaType.MOVIE

        val defaultRatio =
            when (item.mediaType) {
                PlayerMediaType.EPISODE -> 16f / 9f
                PlayerMediaType.MOVIE -> 2f / 3f
                else -> 16f / 10f
            }

        val trickplayRatio =
            item.trickplayInfo?.let {
                if (it.width > 0 && it.height > 0) it.width.toFloat() / it.height.toFloat()
                else null
            }

        val itemTitle =
            if (item.parentIndexNumber != null && item.indexNumber != null) {
                val parentIndex = item.parentIndexNumber.toString().padStart(2, '0')
                val index = item.indexNumber.toString().padStart(2, '0')
                val episodeInfoBaseStr =
                    if (item.indexNumberEnd == null) {
                        "S$parentIndex - E$index"
                    } else {
                        val indexEnd = item.indexNumberEnd.toString().padStart(2, '0')
                        "S$parentIndex - E$index:$indexEnd"
                    }

                val partName = item.partName
                val episodeInfo =
                    if (partName != null) {
                        "$episodeInfoBaseStr - ${partName.getTranslatablePartName(context)}"
                    } else {
                        episodeInfoBaseStr
                    }

                CurrentItemTitle(
                    seriesName = item.seriesName,
                    episodeInfo = episodeInfo,
                    title = item.name,
                )
            } else {
                val partName = item.partName
                CurrentItemTitle(
                    title = item.name,
                    episodeInfo = partName?.getTranslatablePartName(context),
                )
            }

        val oldTrickplay = _uiState.value.currentTrickplay
        oldTrickplay?.images?.forEach { it.recycle() }

        _uiState.update {
            it.copy(
                currentItemTitle = itemTitle,
                currentItemPoster = item.images.primary,
                isMovie = isMovie,
                defaultAspectRatio = defaultRatio,
                trickplayAspectRatio = trickplayRatio,
                currentSegment = null,
                currentTrickplay = null,
                currentChapters = item.chapters,
                fileLoaded = true,
            )
        }

        currentMediaItemSegments = getSegments(item.itemId, repository)

        trickplayJob?.cancel()
        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
            trickplayJob = viewModelScope.launch(defaultDispatcher) { getTrickplay(item) }
        }
    }

    /**
     * Checks if the current playback position falls within a known "segment" (like intros or
     * credits). If auto-skip is enabled, it automatically skips the segment. Otherwise, it updates
     * the UI state to show a "Skip" button if the segment type matches the user's preferences.
     */
    private fun updateCurrentSegment(positionMs: Long) {
        if (currentMediaItemSegments.isEmpty()) return

        val currentSegment = currentMediaItemSegments.find { segment ->
            positionMs in segment.startTicks..<(segment.endTicks - 100L)
        }

        if (currentSegment == null) {
            if (_uiState.value.currentSegment != null) {
                _uiState.update { it.copy(currentSegment = null) }
            }
            return
        }

        if (_uiState.value.currentSegment == currentSegment) {
            return
        }

        Timber.tag("SegmentInfo").d("currentSegment: %s", currentSegment)

        val segmentsAutoSkip = appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)
        val segmentsAutoSkipTypes =
            appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipType)
        val segmentsAutoSkipMode =
            appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipMode)
        val segmentsSkipButtonTypes =
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonType)

        if (
            segmentsAutoSkip &&
                segmentsAutoSkipTypes.contains(currentSegment.type.toString()) &&
                segmentsAutoSkipMode == Constants.PlayerMediaSegmentsAutoSkip.ALWAYS
        ) {
            skipSegment(currentSegment)
        } else if (segmentsSkipButtonTypes.contains(currentSegment.type.toString())) {
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

        Timber.d("Updated current segment: ${_uiState.value.currentSegment?.type}")
    }

    fun play() {
        playerController.play()
    }

    fun pause() {
        playerController.pause()
    }

    fun togglePlayPause() {
        if (_uiState.value.playerState.status == CastPlaybackStatus.PLAYING) {
            pause()
        } else {
            play()
        }
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun setVolume(volume: Float) {
        playerController.setVolume(volume)
    }

    fun rewindOrPlayPreviousItem() {
        val playerState = _uiState.value.playerState
        if (!playerState.hasPreviousItem || playerState.currentPosition > 5000) {
            seekTo(0)
        } else {
            playPreviousItem()
        }
    }

    /**
     * Executes the skip action for a given segment. If the segment is the end credits and the
     * threshold allows it, it skips directly to the next episode. Otherwise, it seeks past the
     * segment's end boundary.
     */
    fun skipSegment(segment: FindroidSegment) {
        if (shouldSkipToNextEpisode(segment)) {
            playNextItem()
        } else {
            playerController.seekTo(segment.endTicks)
        }
        _uiState.update { it.copy(currentSegment = null) }
    }

    /** Skips playback to the next item in the playlist queue. */
    fun playNextItem() {
        _uiState.update { it.copy(fileLoaded = false) }
        playerController.seekToNext()
    }

    /** Reverts playback to the previous item in the playlist queue. */
    fun playPreviousItem() {
        _uiState.update { it.copy(fileLoaded = false) }
        playerController.seekToPrevious()
    }

    /**
     * Handles the selection of a new audio track from the UI. Updates the local UI state and tells
     * the [playerController] to switch the track.
     */
    fun onAudioTrackSelected(track: Track?) {
        _uiState.update { state ->
            state.copy(
                audioTracks = state.audioTracks.map { it.copy(selected = it.id == track?.id) }
            )
        }
        playerController.setAudioTrack(track, currentItemId)
    }

    /**
     * Handles the selection of a new subtitle track from the UI. Updates the local UI state and
     * tells the [playerController] to switch the track.
     */
    fun onSubtitleTrackSelected(track: Track?) {
        _uiState.update { state ->
            state.copy(
                subtitleTracks = state.subtitleTracks.map { it.copy(selected = it.id == track?.id) }
            )
        }
        playerController.setSubtitleTrack(track)
    }

    /**
     * Resets the entire UI state and flushes cache when the remote receiver stops playing media or
     * disconnects.
     */
    private fun onMediaItemCleared() {
        trickplayJob?.cancel()
        trickplayJob = null
        currentItemId = null
        savedStateHandle.remove<String>(KEY_CURRENT_ITEM_ID)
        currentMediaItemSegments = emptyList()
        val oldTrickplay = _uiState.value.currentTrickplay
        oldTrickplay?.images?.forEach { it.recycle() }
        _uiState.update {
            it.copy(
                currentItemTitle = CurrentItemTitle(title = ""),
                currentItemPoster = null,
                isMovie = false,
                defaultAspectRatio = 16f / 10f,
                trickplayAspectRatio = null,
                currentSegment = null,
                currentSkipButtonStringRes = R.string.player_controls_skip_intro,
                currentTrickplay = null,
                currentChapters = emptyList(),
                fileLoaded = false,
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
            )
        }
    }

    /**
     * Determines whether skipping a specific segment (usually an outro or credits) should
     * automatically jump to the next episode instead of just seeking ahead, based on the user's
     * threshold preferences.
     */
    private fun shouldSkipToNextEpisode(segment: FindroidSegment): Boolean {
        return SegmentUtils.shouldSkipToNextEpisode(
            segment = segment,
            hasNextMediaItem = playerController.playerState.value.hasNextItem,
            playerDurationMillis = playerController.playerState.value.duration,
            nextEpisodeThreshold =
                appPreferences.getValue(appPreferences.playerMediaSegmentsNextEpisodeThreshold),
        )
    }

    /**
     * Downloads trickplay (BIF/Thumbnail) data for scrubbing previews, slices the sprite sheet into
     * individual bitmaps, and updates the UI state so the seek bar can show thumbnails.
     */
    private suspend fun getTrickplay(item: PlayerItem) {
        val trickplayInfo = item.trickplayInfo ?: return
        withContext(defaultDispatcher) {
            val maxIndex =
                ceil(
                        trickplayInfo.thumbnailCount
                            .toDouble()
                            .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                    )
                    .toInt()
            val bitmaps = mutableListOf<Bitmap>()
            var success = false

            try {
                for (i in 0..maxIndex) {
                    coroutineContext.ensureActive()
                    val byteArray =
                        repository.getTrickplayData(item.itemId, trickplayInfo.width, i) ?: continue
                    coroutineContext.ensureActive()
                    val fullBitmap =
                        BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size) ?: continue
                    try {
                        for (offsetY in
                            0..<trickplayInfo.height * trickplayInfo.tileHeight step
                                trickplayInfo.height) {
                            for (offsetX in
                                0..<trickplayInfo.width * trickplayInfo.tileWidth step
                                    trickplayInfo.width) {
                                coroutineContext.ensureActive()
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
                                }
                            }
                        }
                    } finally {
                        fullBitmap.recycle()
                    }
                }

                coroutineContext.ensureActive()
                if (currentItemId == item.itemId) {
                    val oldTrickplay = _uiState.value.currentTrickplay
                    oldTrickplay?.images?.forEach { it.recycle() }
                    _uiState.update {
                        it.copy(currentTrickplay = Trickplay(trickplayInfo.interval, bitmaps))
                    }
                    success = true
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.e(e, "Failed to decode trickplay thumbnails")
            } finally {
                if (!success) {
                    bitmaps.forEach { it.recycle() }
                    bitmaps.clear()
                }
            }
        }
    }

    override fun onCleared() {
        trickplayJob?.cancel()
        val oldTrickplay = _uiState.value.currentTrickplay
        oldTrickplay?.images?.forEach { it.recycle() }
        super.onCleared()
    }

    companion object {
        private const val KEY_CURRENT_ITEM_ID = "cast_current_item_id"
    }
}
