package dev.jdtech.jellyfin.player.cast

import android.content.Context
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.player.cast.devices.Chromecast
import dev.jdtech.jellyfin.player.cast.devices.ChromecastH265
import dev.jdtech.jellyfin.player.cast.models.CastConnectionState
import dev.jdtech.jellyfin.player.cast.models.CastMediaItem
import dev.jdtech.jellyfin.player.cast.models.CastPlaybackStatus
import dev.jdtech.jellyfin.player.cast.models.CastPlayerState
import dev.jdtech.jellyfin.player.core.domain.PlaybackManager
import dev.jdtech.jellyfin.player.core.domain.PlaylistManager
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.PlayerMediaType
import dev.jdtech.jellyfin.player.core.domain.models.Track
import dev.jdtech.jellyfin.player.core.domain.utils.NetworkSpeedUtils.measureNetworkSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.serializer.toUUID
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class CastPlayerControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val sessionManager: CastSessionManager,
    private val playbackManager: PlaybackManager,
    private val playlistManager: PlaylistManager
) : CastPlayerController {

    private val _currentItem = MutableStateFlow<CastMediaItem?>(null)
    override val currentItem: StateFlow<CastMediaItem?> = _currentItem.asStateFlow()

    private val _playerState = MutableStateFlow(CastPlayerState())
    override val playerState: StateFlow<CastPlayerState> = _playerState.asStateFlow()

    private val castContext: CastContext by lazy { CastContext.getSharedInstance(context) }

    private var remoteMediaClient: RemoteMediaClient? = null
    private var castSession: CastSession? = null

    private val queueMutex = Mutex()

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var reportingJob: Job? = null

    private var lastActiveItemId: Int = MediaQueueItem.INVALID_ITEM_ID
    private val itemDuration = ConcurrentHashMap<UUID, Long>()

    private var maxBitrate: Int? = null
    private var hasStarted = false
    private var audioStreamIndex: Int? = null
    private var subtitleStreamIndex: Int? = null

    private data class BuildMediaResult(
        val mediaInfo: MediaInfo,
        val playbackInfo: PlaybackInfoResponse,
        val subtitleTracks: List<Track>,
        val audioTracks: List<Track>
    )

    private val itemCache = ConcurrentHashMap<UUID, CastMediaItem>()

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val itemId = remoteMediaClient?.currentItem?.customData?.optString("itemId")
                ?.takeIf { it.isNotEmpty() }?.toUUID()

            updatePlaybackStatus(itemId)
        }

        override fun onMetadataUpdated() {
            super.onMetadataUpdated()
            val mediaStatus = remoteMediaClient?.mediaStatus ?: return
            val currentActiveItemId = mediaStatus.currentItemId
            val itemIdStr =
                mediaStatus.getQueueItemById(currentActiveItemId)?.media?.customData?.optString("itemId")
                    ?: return

            _currentItem.update { itemCache[itemIdStr.toUUID()] }

            manageQueue(itemIdStr.toUUID())
        }

        override fun onQueueStatusUpdated() {
            val mediaStatus = remoteMediaClient?.mediaStatus ?: return
            val currentActiveItemId = mediaStatus.currentItemId

            if (currentActiveItemId != lastActiveItemId) {

                if (lastActiveItemId != MediaQueueItem.INVALID_ITEM_ID) {

                    val previousItem = mediaStatus.getQueueItemById(lastActiveItemId)
                    val itemIdStr = previousItem?.media?.customData?.optString("itemId") ?: return

                    val durationMs = itemDuration[itemIdStr.toUUID()] ?: return

                    if (hasStarted) {
                        scope.launch {
                            reportPlaybackStop(
                                itemId = itemIdStr.toUUID(),
                                durationMs = durationMs,
                                positionMs = null
                            )
                            hasStarted = false
                        }
                    }
                }

                lastActiveItemId = currentActiveItemId

                val itemIdStr =
                    mediaStatus.getQueueItemById(currentActiveItemId)?.media?.customData?.optString(
                        "itemId"
                    ) ?: return
                itemDuration[itemIdStr.toUUID()] = remoteMediaClient?.streamDuration ?: 0L
            }
        }

        override fun onMediaError(p0: MediaError) {
            super.onMediaError(p0)
            Timber.e("Media Error: $p0")
        }
    }

    private val remoteMediaClientProgressListener =
        RemoteMediaClient.ProgressListener { progressMs, durationMs ->
            _playerState.update {
                it.copy(
                    currentPosition = progressMs,
                    duration = durationMs
                )
            }
        }

    private val castSessionListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            val session = castSession ?: return
            _playerState.update {
                it.copy(
                    volume = session.volume.toFloat(),
                    isMuted = session.isMute
                )
            }
        }

        override fun onStandbyStateChanged(standbyState: Int) {
            if (standbyState == Cast.STANDBY_STATE_YES) {
                stop()
            }
        }
    }

    init {
        scope.launch {
            sessionManager.connectionState.collectLatest { state ->
                val session = castContext.sessionManager.currentCastSession
                if (session != castSession) {
                    castSession?.removeCastListener(castSessionListener)
                    castSession = session
                    castSession?.addCastListener(castSessionListener)

                    _playerState.update {
                        it.copy(
                            volume = session?.volume?.toFloat() ?: 1f,
                            isMuted = session?.isMute ?: false
                        )
                    }

                    if (session != null && maxBitrate == null) {
                        scope.launch {
                            maxBitrate = measureNetworkSpeed(jellyfinApi)
                        }
                    }
                }

                val client = session?.remoteMediaClient
                if (client != remoteMediaClient) {
                    remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
                    remoteMediaClient = client
                    client?.let {
                        it.registerCallback(remoteMediaClientCallback)
                        restoreSession()
                        startReporting()
                    }
                }

                if (state == CastConnectionState.DISCONNECTED || session == null) {
                    clearSession()
                }
            }
        }
    }

    private fun clearSession() {
        val currentItem = _currentItem.value?.item
        val currentState = _playerState.value

        remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
        remoteMediaClient?.removeProgressListener(remoteMediaClientProgressListener)
        remoteMediaClient = null

        castSession?.removeCastListener(castSessionListener)
        castSession = null

        reportingJob?.cancel()

        scope.launch {
            if (currentItem != null && hasStarted) {
                reportPlaybackStop(
                    itemId = currentItem.itemId,
                    durationMs = currentState.duration,
                    positionMs = currentState.currentPosition
                )
            }
            _currentItem.value = null
            hasStarted = false
            itemCache.clear()
        }
    }

    private fun startReporting() {
        reportingJob?.cancel()
        val status = _playerState.value.status
        if (status == CastPlaybackStatus.IDLE || status == CastPlaybackStatus.ENDED) return

        reportingJob = scope.launch {
            while (isActive) {
                val client = remoteMediaClient
                val itemId = _currentItem.value?.item?.itemId ?: return@launch

                if (client != null && hasStarted) {
                    handleReporting(
                        itemId = itemId,
                        status = mapPlaybackStatus(client),
                        positionMs = client.approximateStreamPosition,
                    )
                }
                delay(10000.milliseconds)
            }
        }
    }

    private fun mapPlaybackStatus(client: RemoteMediaClient): CastPlaybackStatus {
        return when (client.playerState) {
            MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> CastPlaybackStatus.BUFFERING
            MediaStatus.PLAYER_STATE_PLAYING -> CastPlaybackStatus.PLAYING
            MediaStatus.PLAYER_STATE_PAUSED -> CastPlaybackStatus.PAUSED
            MediaStatus.PLAYER_STATE_IDLE -> {
                when (client.idleReason) {
                    MediaStatus.IDLE_REASON_FINISHED -> CastPlaybackStatus.ENDED
                    MediaStatus.IDLE_REASON_ERROR -> CastPlaybackStatus.ERROR
                    else -> CastPlaybackStatus.IDLE
                }
            }

            else -> CastPlaybackStatus.IDLE
        }
    }

    private fun updatePlaybackStatus(itemId: UUID?) {
        val client = remoteMediaClient ?: return
        val playbackStatus = mapPlaybackStatus(client)
        val currentPosition = client.approximateStreamPosition

        // Capture current values for reporting before we update anything
        val currentState = _playerState.value

        _playerState.update { it.copy(status = playbackStatus) }

        if (itemId == null) return

        when (playbackStatus) {
            CastPlaybackStatus.ENDED, CastPlaybackStatus.IDLE -> {
                if (hasStarted) {
                    scope.launch {
                        reportPlaybackStop(
                            itemId = itemId,
                            durationMs = currentState.duration,
                            positionMs = null
                        )
                    }
                }

                remoteMediaClient?.removeProgressListener(remoteMediaClientProgressListener)
                reportingJob?.cancel()

                _currentItem.value = null
                hasStarted = false

                stop()
            }

            else -> {
                handleReporting(
                    itemId = itemId,
                    status = playbackStatus,
                    positionMs = currentPosition
                )
            }
        }
    }

    private fun handleReporting(
        itemId: UUID,
        status: CastPlaybackStatus,
        positionMs: Long,
    ) {
        if (status == CastPlaybackStatus.ENDED || status == CastPlaybackStatus.IDLE) return
        val currentItem = itemCache[itemId] ?: return
        val mediaSource = currentItem.playbackInfo?.mediaSources?.firstOrNull()

        val playMethod = when {
            mediaSource?.supportsDirectPlay ?: false -> PlayMethod.DIRECT_PLAY
            mediaSource?.supportsDirectStream ?: false -> PlayMethod.DIRECT_STREAM
            else -> PlayMethod.TRANSCODE
        }

        // Progress Reporting
        if (!hasStarted && status == CastPlaybackStatus.PLAYING) {
            hasStarted = true
            scope.launch {
                playbackManager.reportStart(
                    itemId = itemId,
                    playMethod = playMethod,
                    mediaSourceId = mediaSource?.id,
                    playSessionId = currentItem.playbackInfo?.playSessionId
                )
            }
        } else {
            scope.launch {
                playbackManager.reportProgress(
                    itemId = itemId,
                    positionMs = positionMs,
                    isPaused = status != CastPlaybackStatus.PLAYING,
                    playMethod = playMethod,
                    mediaSourceId = mediaSource?.id,
                    playSessionId = currentItem.playbackInfo?.playSessionId
                )
            }
        }
    }

    private suspend fun reportPlaybackStop(
        itemId: UUID,
        durationMs: Long,
        positionMs: Long?
    ) {
        val currentItem = itemCache[itemId] ?: return
        val playbackInfo = currentItem.playbackInfo

        playbackManager.reportStop(
            itemId = itemId,
            positionMs = positionMs ?: durationMs, // Assume is finished
            durationMs = durationMs,
            mediaSourceId = playbackInfo?.mediaSources?.firstOrNull()?.id,
            playSessionId = playbackInfo?.playSessionId
        )
    }

    private fun restoreSession() {
        val client = remoteMediaClient ?: return
        val status = client.mediaStatus ?: return
        val currentItemId = status.currentItemId

        status.queueItems.forEach { queueItem ->
            val itemIdStr = queueItem.media?.customData?.optString("itemId") ?: return@forEach
            if (!itemCache.containsKey(itemIdStr.toUUID())) {
                restoreRemoteItem(itemIdStr, isCurrent = (queueItem.itemId == currentItemId))
            }
        }
    }

    private fun restoreRemoteItem(itemIdStr: String, isCurrent: Boolean = false) {
        scope.launch {
            try {
                val itemId = UUID.fromString(itemIdStr)
                val userId = jellyfinApi.userId
                val findroidItem = jellyfinApi.userLibraryApi.getItem(itemId, userId).content
                val itemKind = when (findroidItem.type) {
                    BaseItemKind.MOVIE -> BaseItemKind.MOVIE
                    BaseItemKind.EPISODE -> BaseItemKind.EPISODE
                    else -> return@launch
                }
                val playerItem = playlistManager.getInitialItem(
                    itemId = itemId,
                    itemKind = itemKind,
                    mediaSourceIndex = null,
                    startFromBeginning = false
                )
                if (playerItem != null) {
                    if (isCurrent) {
                        val playbackInfo = getPlaybackInfo(playerItem, null)
                        itemCache[playerItem.itemId] = CastMediaItem(
                            item = playerItem,
                            playbackInfo = playbackInfo
                        )
                        restoreItem(playerItem)
                    } else {
                        itemCache[playerItem.itemId] = CastMediaItem(playerItem)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore item $itemIdStr")
            }
        }
    }

    private suspend fun getPlaybackInfo(
        item: PlayerItem,
        audioStreamIndex: Int?
    ): PlaybackInfoResponse? {
        val userId = jellyfinApi.userId
        val connectedDevice = sessionManager.connectedDevice.value
        val profile = if (connectedDevice?.supportsH265 == true) {
            ChromecastH265.deviceProfile
        } else {
            Chromecast.deviceProfile
        }

        return try {
            jellyfinApi.mediaInfoApi.getPostedPlaybackInfo(
                item.itemId,
                PlaybackInfoDto(
                    userId = userId,
                    deviceProfile = profile,
                    maxStreamingBitrate = maxBitrate,
                    audioStreamIndex = audioStreamIndex,
                    enableDirectPlay = audioStreamIndex == null,
                    enableDirectStream = true,
                    enableTranscoding = true,
                    allowAudioStreamCopy = true,
                    allowVideoStreamCopy = true
                )
            ).content
        } catch (e: Exception) {
            Timber.e(e, "Failed to get playback info")
            null
        }
    }

    private suspend fun buildMediaInfo(item: PlayerItem): BuildMediaResult? {
        val baseUrl = jellyfinApi.api.baseUrl

        val mediaType =
            if (item.mediaType == PlayerMediaType.EPISODE) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE
        val mediaMetadata = MediaMetadata(mediaType).apply {
            putString(MediaMetadata.KEY_TITLE, item.name)
            item.seriesName?.let { putString(MediaMetadata.KEY_SERIES_TITLE, it) }

            item.indexNumber?.let { putInt(MediaMetadata.KEY_EPISODE_NUMBER, it) }
            item.parentIndexNumber?.let { putInt(MediaMetadata.KEY_SEASON_NUMBER, it) }

            item.images.showPrimary?.let { addImage(WebImage(it)) }
            item.images.showBackdrop?.let { addImage(WebImage(it)) }
            item.images.primary?.let { addImage(WebImage(it)) }
            item.images.backdrop?.let { addImage(WebImage(it)) }
        }

        val customData = JSONObject().apply {
            put("itemId", item.itemId.toString())
        }

        val playbackInfo = getPlaybackInfo(item, audioStreamIndex) ?: return null
        val mediaSource = playbackInfo.mediaSources.firstOrNull() ?: return null

        val (streamUrlOriginal, contentType) = if (mediaSource.supportsDirectPlay) {
            val url =
                baseUrl + "/Videos/${item.itemId}/stream?static=true&MediaSourceId=${mediaSource.id}"
            val mimeType = mediaSource.container?.let { if (it.contains("/")) it else "video/$it" }
                ?: "video/mp4"
            url to mimeType
        } else {
            val url = baseUrl + (mediaSource.transcodingUrl
                ?: "/Videos/${item.itemId}/stream?MediaSourceId=${mediaSource.id}")
            url to "application/x-mpegurl"
        }

        val streamUrl = if (audioStreamIndex != null) {
            if (streamUrlOriginal.contains("AudioStreamIndex=")) {
                streamUrlOriginal.replace(
                    Regex("AudioStreamIndex=\\d+"),
                    "AudioStreamIndex=$audioStreamIndex"
                )
            } else {
                "$streamUrlOriginal&AudioStreamIndex=$audioStreamIndex"
            }
        } else {
            streamUrlOriginal
        }

        Timber.d("Video url: $streamUrl")

        val mediaInfoBuilder = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(mediaMetadata)
            .setCustomData(customData)

        val castTracks = mutableListOf<MediaTrack>()
        val subtitles = mutableListOf<Track>()
        val audio = mutableListOf<Track>()

        mediaSource.mediaStreams?.forEach { stream ->

            // Subtitle
            if (stream.type == MediaStreamType.SUBTITLE) {
                val trackId = (stream.index + 100).toLong()
                val trackUrl = baseUrl + stream.deliveryUrl

                val builder = MediaTrack.Builder(trackId, MediaTrack.TYPE_TEXT)
                    .setContentId(trackUrl)
                    .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                    .setContentType("text/vtt")
                    .setLanguage(stream.language)
                    .setName(stream.title ?: stream.displayTitle ?: "Track ${stream.index}")

                castTracks.add(builder.build())

                val track = Track(
                    id = trackId.toInt(),
                    label = stream.title,
                    language = stream.language,
                    codec = stream.codec,
                    selected = stream.isDefault,
                    supported = true,
                    isExternal = stream.isExternal,
                    isForced = stream.isForced,
                    isHearingImpaired = stream.isHearingImpaired,
                )

                subtitles.add(track)

                Timber.d("Subtitle track: $stream")

            }

            // Audio
            else if (stream.type == MediaStreamType.AUDIO) {
                val track = Track(
                    id = stream.index,
                    label = stream.title,
                    language = stream.language,
                    codec = stream.codec,
                    selected = if (audioStreamIndex != null) stream.index == audioStreamIndex else stream.isDefault,
                    supported = true,
                    isExternal = stream.isExternal,
                    isForced = stream.isForced,
                    isHearingImpaired = stream.isHearingImpaired,
                )

                audio.add(track)

                Timber.d("Audio track: $stream")
            }
        }

        if (castTracks.isNotEmpty()) {
            mediaInfoBuilder.setMediaTracks(castTracks)
        }

        return BuildMediaResult(
            mediaInfo = mediaInfoBuilder.build(),
            playbackInfo = playbackInfo,
            subtitleTracks = subtitles,
            audioTracks = audio
        )
    }

    override fun playItem(itemId: UUID, itemKind: String, startFromBeginning: Boolean) {
        audioStreamIndex = null
        hasStarted = false
        itemCache.clear()

        scope.launch {
            val initialItem = playlistManager.getInitialItem(
                itemId = itemId,
                itemKind = BaseItemKind.fromName(itemKind),
                mediaSourceIndex = null,
                startFromBeginning = startFromBeginning
            )

            if (initialItem != null) {
                val client = remoteMediaClient ?: return@launch
                val result = buildMediaInfo(initialItem) ?: run {
                    _playerState.update { it.copy(status = CastPlaybackStatus.ERROR) }
                    return@launch
                }

                val castItem = CastMediaItem(
                    item = initialItem,
                    playbackInfo = result.playbackInfo,
                    subtitleTracks = result.subtitleTracks,
                    audioTracks = result.audioTracks
                )

                val loadRequest = MediaLoadRequestData.Builder()
                    .setMediaInfo(result.mediaInfo)
                    .setAutoplay(true)
                    .setCurrentTime(initialItem.playbackPosition)
                    .build()

                itemCache[initialItem.itemId] = castItem

                client.load(loadRequest)

                remoteMediaClient?.addProgressListener(remoteMediaClientProgressListener, 1000L)
            }
        }
    }

    private fun manageQueue(itemId: UUID) {
        scope.launch {
            val status = remoteMediaClient?.mediaStatus ?: return@launch
            val queueItems = status.queueItems

            playlistManager.setCurrentMediaItemIndex(itemId)

            val nextItem = playlistManager.getNextPlayerItem()
            val prevItem = playlistManager.getPreviousPlayerItem()

            val queuedItemIds = queueItems.mapNotNull {
                it.media?.customData?.optString("itemId")?.takeIf { id -> id.isNotEmpty() }
            }.toSet()

            nextItem?.let { next ->
                if (next.itemId.toString() !in queuedItemIds) {
                    queueNextItem(next)
                } else {
                    Timber.d("Item ${next.name} already in Cast queue.")
                }
            }

            prevItem?.let { prev ->
                if (prev.itemId.toString() !in queuedItemIds) {
                    queuePreviousItem(prev)
                } else {
                    Timber.d("Item ${prev.name} already in Cast queue.")
                }
            }

            _playerState.update {
                it.copy(
                    hasNextItem = nextItem != null,
                    hasPreviousItem = prevItem != null
                )
            }
        }
    }

    suspend fun queueNextItem(item: PlayerItem) {
        val result = buildMediaInfo(item) ?: return
        itemCache[item.itemId] = CastMediaItem(
            item = item,
            playbackInfo = result.playbackInfo,
            subtitleTracks = result.subtitleTracks,
            audioTracks = result.audioTracks
        )

        queueMutex.withLock {
            val client = remoteMediaClient ?: return@withLock
            val status = client.mediaStatus ?: return@withLock

            val queueItem = MediaQueueItem.Builder(result.mediaInfo)
                .setAutoplay(true)
                .setPreloadTime(20.0)
                .build()

            val currentItemId = status.currentItemId
            val queueItems = status.queueItems
            val currentIndex = queueItems.indexOfFirst { it.itemId == currentItemId }

            val nextItemId = if (currentIndex != -1 && currentIndex < queueItems.size - 1) {
                queueItems[currentIndex + 1].itemId
            } else {
                MediaQueueItem.INVALID_ITEM_ID
            }

            client.queueInsertItems(arrayOf(queueItem), nextItemId, null)
        }
    }

    suspend fun queuePreviousItem(item: PlayerItem) {
        val result = buildMediaInfo(item) ?: return
        itemCache[item.itemId] = CastMediaItem(
            item = item,
            playbackInfo = result.playbackInfo,
            subtitleTracks = result.subtitleTracks,
            audioTracks = result.audioTracks
        )

        queueMutex.withLock {
            val client = remoteMediaClient ?: return@withLock
            val status = client.mediaStatus ?: return@withLock

            val queueItem = MediaQueueItem.Builder(result.mediaInfo)
                .setAutoplay(true)
                .setPreloadTime(20.0)
                .build()

            val currentItemId = status.currentItemId

            client.queueInsertItems(arrayOf(queueItem), currentItemId, null)
        }
    }

    private fun restoreItem(item: PlayerItem) {
        val castItem = CastMediaItem(item)

        itemCache[item.itemId] = castItem
    }

    override fun play() {
        remoteMediaClient?.play()
    }

    override fun pause() {
        remoteMediaClient?.pause()
    }

    override fun seekTo(position: Long) {
        val options = MediaSeekOptions.Builder()
            .setPosition(position)
            .setResumeState(MediaSeekOptions.RESUME_STATE_UNCHANGED)
            .build()
        remoteMediaClient?.seek(options)
    }

    override fun seekToNext() {
        remoteMediaClient?.queueNext(null)
    }

    override fun seekToPrevious() {
        remoteMediaClient?.queuePrev(null)
    }

    override fun setVolume(volume: Float) {
        castSession?.volume = volume.toDouble()
    }

    override fun setSubtitleTrack(track: Track?) {
        val client = remoteMediaClient ?: return
        val activeIds = client.mediaStatus?.activeTrackIds?.toMutableList() ?: mutableListOf()

        activeIds.remove(subtitleStreamIndex?.toLong())

        if (track != null) activeIds.add(track.id.toLong())
        subtitleStreamIndex = track?.id

        client.setActiveMediaTracks(activeIds.toLongArray()).setResultCallback { result ->
            if (result.status.isSuccess) {
                val activeTrackIds = client.mediaStatus?.activeTrackIds?.toList() ?: emptyList()

                _currentItem.update { item ->
                    val subtitleTracks = item?.subtitleTracks?.map {
                        it.copy(selected = activeTrackIds.contains(it.id.toLong()))
                    } ?: emptyList()

                    item?.copy(
                        subtitleTracks = subtitleTracks,
                    )
                }

                Timber.d("Selected subtitle track: $track")
            }
        }
    }

    override fun setAudioTrack(track: Track?, itemId: UUID?) {
        if (itemId == null || track == null) return
        audioStreamIndex = track.id

        scope.launch {
            val client = remoteMediaClient ?: return@launch
            val cachedMedia = itemCache[itemId] ?: return@launch
            val currentPositionMs = client.approximateStreamPosition

            // Request new playback info from Jellyfin with the selected audio track index
            val result = buildMediaInfo(cachedMedia.item) ?: return@launch

            // Update the current item in the Cast queue
            val loadRequest = MediaLoadRequestData.Builder()
                .setMediaInfo(result.mediaInfo)
                .setCurrentTime(currentPositionMs)
                .setAutoplay(true)
                .build()

            client.load(loadRequest).setResultCallback { callbackResult ->
                if (callbackResult.status.isSuccess) {
                    itemCache.clear()

                    val updatedMedia = cachedMedia.copy(
                        playbackInfo = result.playbackInfo,
                        subtitleTracks = result.subtitleTracks,
                        audioTracks = result.audioTracks
                    )

                    itemCache[itemId] = updatedMedia
                    _currentItem.update { updatedMedia }

                    Timber.d("Selected audio track: $track")
                }
            }
        }
    }

    override fun stop() {
        remoteMediaClient?.stop()
    }
}
