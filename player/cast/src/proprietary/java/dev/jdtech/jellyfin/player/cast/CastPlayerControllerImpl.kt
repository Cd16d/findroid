package dev.jdtech.jellyfin.player.cast

import android.content.Context
import android.media.AudioManager
import android.net.Uri
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
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.getTranslatablePartName
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.serializer.toUUID
import org.json.JSONObject
import timber.log.Timber

@Singleton
class CastPlayerControllerImpl
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val sessionManager: CastSessionManager,
    private val playbackManager: PlaybackManager,
    private val playlistManager: PlaylistManager,
    private val appPreferences: AppPreferences,
) : CastPlayerController {

    internal var mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        context: Context,
        jellyfinApi: JellyfinApi,
        sessionManager: CastSessionManager,
        playbackManager: PlaybackManager,
        playlistManager: PlaylistManager,
        appPreferences: AppPreferences,
        mainDispatcher: CoroutineDispatcher,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        context,
        jellyfinApi,
        sessionManager,
        playbackManager,
        playlistManager,
        appPreferences,
    ) {
        this.mainDispatcher = mainDispatcher
        this.ioDispatcher = ioDispatcher
    }

    private val _currentItem = MutableStateFlow<CastMediaItem?>(null)
    override val currentItem: StateFlow<CastMediaItem?> = _currentItem.asStateFlow()

    private val _playerState = MutableStateFlow(CastPlayerState())
    override val playerState: StateFlow<CastPlayerState> = _playerState.asStateFlow()

    private var _castContext: CastContext? = null
    private val castContext: CastContext?
        get() {
            if (_castContext != null) return _castContext
            return try {
                CastContext.getSharedInstance(context).also { _castContext = it }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get CastContext")
                null
            }
        }

    private var remoteMediaClient: RemoteMediaClient? = null
    private var castSession: CastSession? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val queueMutex = Mutex()

    private val scope = CoroutineScope(mainDispatcher + SupervisorJob())

    private var playJob: Job? = null
    private var audioTrackJob: Job? = null
    private var queueJob: Job? = null

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    @Volatile private var isSessionRestored = false
    @Volatile private var isReporting = false
    @Volatile private var lastActiveItemId: Int = MediaQueueItem.INVALID_ITEM_ID
    @Volatile private var lastActiveItemUuid: UUID? = null

    @Volatile private var maxBitrate: Int? = null
    @Volatile private var playMethod: PlayMethod = PlayMethod.DIRECT_PLAY
    @Volatile private var audioStreamIndex: Int? = null
    @Volatile private var subtitleStreamIndex: Int? = null

    private data class BuildMediaResult(
        val mediaInfo: MediaInfo,
        val playbackInfo: PlaybackInfoResponse,
        val subtitleTracks: List<Track>,
        val audioTracks: List<Track>,
    )

    private val itemCache = ConcurrentHashMap<UUID, CastMediaItem>()
    private val itemDuration = ConcurrentHashMap<UUID, Long>()

    private fun abandonAudioFocus() {
        try {
            @Suppress("DEPRECATION") audioManager?.abandonAudioFocus(null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to abandon audio focus")
        }
    }

    private val remoteMediaClientCallback =
        object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                updatePlaybackStatus()

                if (!isSessionRestored && remoteMediaClient?.mediaStatus != null) {
                    restoreSession()
                }
            }

            override fun onMetadataUpdated() {
                super.onMetadataUpdated()
                val client = remoteMediaClient ?: return
                val itemIdStr = client.currentItem?.media?.customData?.optString("itemId") ?: return
                val itemId =
                    try {
                        itemIdStr.toUUID()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse itemId in onMetadataUpdated: $itemIdStr")
                        return
                    }

                val cachedItem = itemCache[itemId]
                if (cachedItem != null) {
                    _currentItem.value = cachedItem
                    itemDuration[itemId] = client.streamDuration
                    manageQueue(itemId)
                } else {
                    val playbackInfoStr =
                        client.currentItem?.media?.customData?.optString("playbackInfo")
                    val playbackInfo =
                        if (!playbackInfoStr.isNullOrEmpty()) {
                            try {
                                json.decodeFromString<PlaybackInfoResponse>(playbackInfoStr)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to decode playbackInfo in onMetadataUpdated")
                                null
                            }
                        } else null
                    restoreRemoteItem(
                        itemIdStr = itemIdStr,
                        playbackInfo = playbackInfo,
                        isCurrent = true,
                    )
                }
            }

            override fun onQueueStatusUpdated() {
                val client = remoteMediaClient ?: return
                val currentActiveItemId = client.mediaStatus?.currentItemId ?: return

                if (currentActiveItemId != lastActiveItemId) {
                    if (lastActiveItemId != MediaQueueItem.INVALID_ITEM_ID) {
                        val previousItem = client.mediaStatus?.getQueueItemById(lastActiveItemId)
                        val previousItemIdStr = previousItem?.media?.customData?.optString("itemId")
                        val previousUuid =
                            try {
                                previousItemIdStr?.toUUID()
                            } catch (e: Exception) {
                                null
                            } ?: lastActiveItemUuid

                        if (previousUuid != null) {
                            stopReporting(previousUuid)
                        }
                        if (currentActiveItemId != MediaQueueItem.INVALID_ITEM_ID) {
                            startReporting()
                        }
                    }

                    lastActiveItemId = currentActiveItemId
                    val currentItem = client.mediaStatus?.getQueueItemById(currentActiveItemId)
                    val currentItemIdStr = currentItem?.media?.customData?.optString("itemId")
                    lastActiveItemUuid =
                        try {
                            currentItemIdStr?.toUUID()
                        } catch (e: Exception) {
                            null
                        }
                }
            }

            override fun onMediaError(p0: MediaError) {
                super.onMediaError(p0)
                Timber.e("Media Error: $p0")
                _playerState.update { it.copy(status = CastPlaybackStatus.ERROR) }
            }
        }

    private val playbackReportingCallback = RemoteMediaClient.ProgressListener { progressMs, _ ->
        reportPlaybackProgressAndState(progressMs)
    }

    private val remoteMediaClientProgressListener =
        RemoteMediaClient.ProgressListener { progressMs, durationMs ->
            val safeProgress = progressMs.coerceAtLeast(0L)
            val safeDuration = durationMs.coerceAtLeast(0L)
            _playerState.update {
                it.copy(
                    currentPosition = safeProgress,
                    duration = safeDuration,
                )
            }
            val currentItemId = _currentItem.value?.item?.itemId
            if (currentItemId != null && safeDuration > 0L) {
                itemDuration[currentItemId] = safeDuration
            }
        }

    private val castSessionListener =
        object : Cast.Listener() {
            override fun onVolumeChanged() {
                val session = castSession ?: return
                try {
                    val volume = session.volume.toFloat()
                    val isMuted = session.isMute
                    _playerState.update {
                        it.copy(
                            volume = volume,
                            isMuted = isMuted,
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update volume from CastSession")
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
            sessionManager.connectionState.collect { state ->
                val session =
                    if (state == CastConnectionState.CONNECTED) {
                        try {
                            castContext?.sessionManager?.currentCastSession
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to get currentCastSession")
                            null
                        }
                    } else {
                        null
                    }

                if (state == CastConnectionState.CONNECTED && session != null) {
                    if (session != castSession) {
                        abandonAudioFocus()

                        // Cleanup previous session
                        if (castSession != null) {
                            try {
                                castSession?.removeCastListener(castSessionListener)
                                remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
                                remoteMediaClient?.removeProgressListener(
                                    remoteMediaClientProgressListener
                                )
                                remoteMediaClient?.removeProgressListener(playbackReportingCallback)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to clean up previous CastSession")
                            }
                        }

                        // Setup new session
                        castSession = session
                        try {
                            castSession?.addCastListener(castSessionListener)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to add cast listener")
                        }

                        remoteMediaClient = session.remoteMediaClient
                        remoteMediaClient?.let { client ->
                            try {
                                client.registerCallback(remoteMediaClientCallback)
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to register callback on RemoteMediaClient")
                            }
                            isSessionRestored = false
                            restoreSession()
                        }

                        try {
                            _playerState.update {
                                it.copy(
                                    volume = session.volume.toFloat(),
                                    isMuted = session.isMute,
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to read initial volume/mute state")
                        }

                        if (maxBitrate == null) {
                            scope.launch(ioDispatcher) {
                                val speedTestBitrate = measureNetworkSpeed(jellyfinApi)
                                val settingBitrateKbps =
                                    appPreferences.getValue(appPreferences.castMaxBitrateKbps)
                                val settingBitrateBps = settingBitrateKbps * 1_000L

                                maxBitrate =
                                    if (settingBitrateBps > 0) {
                                        val settingBitrateBpsInt =
                                            settingBitrateBps
                                                .coerceAtMost(Int.MAX_VALUE.toLong())
                                                .toInt()
                                        if (speedTestBitrate != null) {
                                            minOf(settingBitrateBpsInt, speedTestBitrate)
                                        } else {
                                            settingBitrateBpsInt
                                        }
                                    } else {
                                        speedTestBitrate
                                    }
                            }
                        }
                    }
                } else if (state == CastConnectionState.DISCONNECTED) {
                    clearSession()
                }
            }
        }
    }

    private fun clearSession() {
        Timber.d("Clear session")

        stopReporting()

        try {
            remoteMediaClient?.unregisterCallback(remoteMediaClientCallback)
            remoteMediaClient?.removeProgressListener(remoteMediaClientProgressListener)
            remoteMediaClient?.removeProgressListener(playbackReportingCallback)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unregister remote media client listeners")
        }
        remoteMediaClient = null

        try {
            castSession?.removeCastListener(castSessionListener)
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove cast listener")
        }
        castSession = null

        _currentItem.value = null
        _playerState.update { CastPlayerState() }
        isSessionRestored = false
        lastActiveItemId = MediaQueueItem.INVALID_ITEM_ID
        lastActiveItemUuid = null
        itemCache.clear()
        itemDuration.clear()
    }

    private fun mapPlaybackStatus(client: RemoteMediaClient): CastPlaybackStatus {
        return when (client.playerState) {
            MediaStatus.PLAYER_STATE_BUFFERING,
            MediaStatus.PLAYER_STATE_LOADING -> CastPlaybackStatus.BUFFERING
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

    private fun updatePlaybackStatus() {
        val client = remoteMediaClient ?: return
        val playbackStatus = mapPlaybackStatus(client)

        _playerState.update { it.copy(status = playbackStatus) }

        when (playbackStatus) {
            CastPlaybackStatus.ENDED,
            CastPlaybackStatus.IDLE -> {
                val itemId = _currentItem.value?.item?.itemId
                stopReporting(itemId)
                _currentItem.value = null
                try {
                    remoteMediaClient?.removeProgressListener(remoteMediaClientProgressListener)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to remove progress listener")
                }
            }

            CastPlaybackStatus.PLAYING -> {
                abandonAudioFocus()
                startReporting()
                try {
                    remoteMediaClient?.removeProgressListener(remoteMediaClientProgressListener)
                    remoteMediaClient?.addProgressListener(remoteMediaClientProgressListener, 1000L)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to add progress listener")
                }
            }

            CastPlaybackStatus.PAUSED -> {
                if (isReporting) {
                    reportPlaybackProgressAndState()
                    try {
                        remoteMediaClient?.removeProgressListener(playbackReportingCallback)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to remove playback reporting listener")
                    }
                }
                try {
                    remoteMediaClient?.removeProgressListener(remoteMediaClientProgressListener)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to remove progress listener")
                }
            }

            else -> {
                if (isReporting) {
                    reportPlaybackProgressAndState()
                }
            }
        }
    }

    private fun reportPlaybackProgressAndState(positionMs: Long? = null) {
        val client = remoteMediaClient ?: return
        val currentItem = _currentItem.value ?: return
        val playbackInfo = currentItem.playbackInfo
        val safePosition = (positionMs ?: client.approximateStreamPosition).coerceAtLeast(0L)

        scope.launch {
            try {
                playbackManager.reportProgress(
                    itemId = currentItem.item.itemId,
                    positionMs = safePosition,
                    isPaused = !client.isPlaying,
                    playMethod = playMethod,
                    mediaSourceId = playbackInfo?.mediaSources?.firstOrNull()?.id,
                    playSessionId = playbackInfo?.playSessionId,
                )
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "Failed to report playback progress for item: ${currentItem.item.itemId}",
                )
            }
        }
    }

    private fun startReporting() {
        if (!isReporting) {
            val client = remoteMediaClient ?: return
            val currentItem = _currentItem.value ?: return
            val playbackInfo = currentItem.playbackInfo
            val mediaSource = playbackInfo?.mediaSources?.firstOrNull()
            val startPositionMs =
                if (client.approximateStreamPosition <= 0L) {
                    currentItem.item.playbackPosition.coerceAtLeast(0L)
                } else {
                    client.approximateStreamPosition
                }

            playMethod =
                when {
                    mediaSource?.supportsDirectPlay ?: false -> PlayMethod.DIRECT_PLAY
                    mediaSource?.supportsDirectStream ?: false -> PlayMethod.DIRECT_STREAM
                    else -> PlayMethod.TRANSCODE
                }

            scope.launch {
                try {
                    playbackManager.reportStart(
                        itemId = currentItem.item.itemId,
                        positionMs = startPositionMs,
                        playMethod = playMethod,
                        mediaSourceId = mediaSource?.id,
                        playSessionId = playbackInfo?.playSessionId,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to report start for item: ${currentItem.item.itemId}")
                }
            }

            isReporting = true
        } else {
            reportPlaybackProgressAndState()
        }

        try {
            remoteMediaClient?.removeProgressListener(playbackReportingCallback)
            remoteMediaClient?.addProgressListener(playbackReportingCallback, 10000L)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add playback reporting progress listener")
        }
    }

    private fun stopReporting(itemId: UUID? = null) {
        if (!isReporting) return

        val targetItemId = itemId ?: _currentItem.value?.item?.itemId
        if (targetItemId != null) {
            val currentItem =
                itemCache[targetItemId]
                    ?: _currentItem.value?.takeIf { it.item.itemId == targetItemId }
            val playbackInfo = currentItem?.playbackInfo
            val playerState = _playerState.value
            val client = remoteMediaClient
            val duration =
                itemDuration[targetItemId]?.takeIf { it > 0L }
                    ?: playerState.duration.takeIf { it > 0L }
                    ?: client?.streamDuration?.takeIf { it > 0L }
                    ?: 0L

            scope.launch {
                try {
                    playbackManager.reportStop(
                        itemId = targetItemId,
                        positionMs = playerState.currentPosition.coerceAtLeast(0L),
                        durationMs = duration.coerceAtLeast(1L),
                        mediaSourceId = playbackInfo?.mediaSources?.firstOrNull()?.id,
                        playSessionId = playbackInfo?.playSessionId,
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to report playback stop for item: $targetItemId")
                }
            }
        }

        if (itemId != null) {
            itemCache.remove(itemId)
            itemDuration.remove(itemId)
        } else {
            itemCache.clear()
            itemDuration.clear()
        }

        try {
            remoteMediaClient?.removeProgressListener(playbackReportingCallback)
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove playback reporting progress listener")
        }
        isReporting = false
    }

    private fun restoreSession() {
        val client = remoteMediaClient ?: return
        val status = client.mediaStatus ?: return

        if (status.playerState == MediaStatus.PLAYER_STATE_IDLE || isSessionRestored) return

        Timber.d("Restoring Session")
        isSessionRestored = true

        val currentItemId = status.currentItemId

        val currentQueueItem = status.getQueueItemById(currentItemId)
        val streamUrl = currentQueueItem?.media?.contentId
        Timber.d("Stream Url: $streamUrl")
        audioStreamIndex = streamUrl?.let { url ->
            Regex("AudioStreamIndex=(\\d+)").find(url)?.groupValues?.get(1)?.toIntOrNull()
        }
        subtitleStreamIndex = status.activeTrackIds?.firstOrNull()?.toInt()

        status.queueItems.forEach { queueItem ->
            val customData = queueItem.media?.customData ?: return@forEach
            val itemIdStr = customData.optString("itemId") ?: return@forEach
            val playbackInfoStr = customData.optString("playbackInfo")
            val playbackInfo =
                if (!playbackInfoStr.isNullOrEmpty()) {
                    try {
                        json.decodeFromString<PlaybackInfoResponse>(playbackInfoStr)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to decode playbackInfo")
                        null
                    }
                } else null

            val uuid =
                try {
                    itemIdStr.toUUID()
                } catch (e: Exception) {
                    null
                }
            if (uuid != null && !itemCache.containsKey(uuid)) {
                restoreRemoteItem(
                    itemIdStr = itemIdStr,
                    playbackInfo = playbackInfo,
                    isCurrent = (queueItem.itemId == currentItemId),
                )
            }
        }

        updatePlaybackStatus()

        currentQueueItem?.media?.customData?.optString("itemId")?.let { idStr ->
            try {
                val uuid = idStr.toUUID()
                Timber.d("Managing queue")
                manageQueue(uuid)
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse UUID for queue management: $idStr")
            }
        }

        Timber.d("Restored Session")
        Timber.d("Audio stream index: $audioStreamIndex")
        Timber.d("Subtitle stream index: $subtitleStreamIndex")
    }

    private fun restoreRemoteItem(
        itemIdStr: String,
        playbackInfo: PlaybackInfoResponse?,
        isCurrent: Boolean = false,
    ) {
        scope.launch {
            try {
                val itemId = UUID.fromString(itemIdStr)
                val userId = jellyfinApi.userId
                val findroidItem =
                    withContext(ioDispatcher) {
                        jellyfinApi.userLibraryApi.getItem(itemId, userId).content
                    }
                val itemKind =
                    when (findroidItem.type) {
                        BaseItemKind.MOVIE -> BaseItemKind.MOVIE
                        BaseItemKind.EPISODE -> BaseItemKind.EPISODE
                        else -> return@launch
                    }
                val playerItem =
                    playlistManager.getInitialItem(
                        itemId = itemId,
                        itemKind = itemKind,
                        mediaSourceIndex = null,
                        startFromBeginning = false,
                    )
                if (playerItem != null) {
                    val mediaSource = playbackInfo?.mediaSources?.firstOrNull()

                    val (_, subtitles, audio) =
                        if (mediaSource != null) {
                            getTracks(mediaSource)
                        } else {
                            Triple(emptyList(), emptyList(), emptyList())
                        }

                    val castItem =
                        CastMediaItem(
                            item = playerItem,
                            playbackInfo = playbackInfo,
                            subtitleTracks = subtitles,
                            audioTracks = audio,
                        )

                    itemCache[playerItem.itemId] = castItem

                    if (isCurrent) {
                        _currentItem.value = castItem
                        manageQueue(itemId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore item $itemIdStr")
            }
        }
    }

    private suspend fun getPlaybackInfo(
        item: PlayerItem,
        audioStreamIndex: Int?,
    ): PlaybackInfoResponse? =
        withContext(ioDispatcher) {
            val userId = jellyfinApi.userId
            val connectedDevice = sessionManager.connectedDevice.value
            val profile =
                if (connectedDevice?.supportsH265 == true) {
                    ChromecastH265.deviceProfile
                } else {
                    Chromecast.deviceProfile
                }

            val settingBitrateKbps = appPreferences.getValue(appPreferences.castMaxBitrateKbps)
            val settingBitrateBps =
                (settingBitrateKbps * 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val currentMaxBitrate = maxBitrate
            val maxStreamingBitrate =
                when {
                    settingBitrateBps > 0 && currentMaxBitrate != null ->
                        minOf(currentMaxBitrate, settingBitrateBps)
                    settingBitrateBps > 0 -> settingBitrateBps
                    else -> currentMaxBitrate
                }

            try {
                jellyfinApi.mediaInfoApi
                    .getPostedPlaybackInfo(
                        item.itemId,
                        PlaybackInfoDto(
                            userId = userId,
                            deviceProfile = profile,
                            maxStreamingBitrate = maxStreamingBitrate,
                            audioStreamIndex = audioStreamIndex,
                            enableDirectPlay = audioStreamIndex == null,
                            enableDirectStream = true,
                            enableTranscoding = true,
                            allowAudioStreamCopy = true,
                            allowVideoStreamCopy = true,
                        ),
                    )
                    .content
            } catch (e: Exception) {
                Timber.e(e, "Failed to get playback info")
                null
            }
        }

    private fun Uri.toCastOptimizeImageUri(
        mediaType: PlayerMediaType = PlayerMediaType.MOVIE,
        isBackdrop: Boolean = false,
    ): Uri {
        val quality = 70

        val (targetWidthPx, targetHeightPx) =
            if (mediaType == PlayerMediaType.EPISODE || isBackdrop) {
                1280 to 720
            } else {
                480 to 720
            }

        return this.buildUpon()
            .appendQueryParameter("fillWidth", targetWidthPx.toString())
            .appendQueryParameter("fillHeight", targetHeightPx.toString())
            .appendQueryParameter("quality", quality.toString())
            .build()
    }

    private suspend fun buildMediaInfo(item: PlayerItem): BuildMediaResult? =
        withContext(ioDispatcher) {
            val baseUrl = jellyfinApi.api.baseUrl?.removeSuffix("/").orEmpty()
            if (baseUrl.isEmpty()) {
                Timber.e("Base URL is empty, cannot build MediaInfo")
                return@withContext null
            }

            val mediaType =
                if (item.mediaType == PlayerMediaType.EPISODE) MediaMetadata.MEDIA_TYPE_TV_SHOW
                else MediaMetadata.MEDIA_TYPE_MOVIE
            val partName = item.partName
            val itemTitle =
                if (partName != null) {
                    "${item.name} - ${partName.getTranslatablePartName(context)}"
                } else {
                    item.name
                }

            val mediaMetadata =
                MediaMetadata(mediaType).apply {
                    putString(MediaMetadata.KEY_TITLE, itemTitle)
                    item.seriesName?.let { putString(MediaMetadata.KEY_SERIES_TITLE, it) }

                    item.indexNumber?.let { putInt(MediaMetadata.KEY_EPISODE_NUMBER, it) }
                    item.parentIndexNumber?.let { putInt(MediaMetadata.KEY_SEASON_NUMBER, it) }

                    item.images.showPrimary?.uri?.let {
                        addImage(WebImage(it.toCastOptimizeImageUri()))
                    }
                    item.images.showBackdrop?.uri?.let {
                        addImage(WebImage(it.toCastOptimizeImageUri(isBackdrop = true)))
                    }

                    item.images.primary?.uri?.let {
                        addImage(WebImage(it.toCastOptimizeImageUri(item.mediaType)))
                    }
                    item.images.backdrop?.uri?.let {
                        addImage(
                            WebImage(
                                it.toCastOptimizeImageUri(
                                    item.mediaType,
                                    isBackdrop = true,
                                )
                            )
                        )
                    }
                }

            val playbackInfo = getPlaybackInfo(item, audioStreamIndex) ?: return@withContext null

            val customData =
                JSONObject().apply {
                    put("itemId", item.itemId.toString())
                    put("playbackInfo", json.encodeToString(playbackInfo))
                }

            val mediaSource = playbackInfo.mediaSources.firstOrNull() ?: return@withContext null

            val (streamUrlOriginal, contentType) =
                if (mediaSource.supportsDirectPlay) {
                    val url =
                        "$baseUrl/Videos/${item.itemId}/stream?static=true&MediaSourceId=${mediaSource.id}"
                    val mimeType =
                        mediaSource.container?.let { if (it.contains("/")) it else "video/$it" }
                            ?: "video/mp4"
                    url to mimeType
                } else {
                    val transcodingUrl = mediaSource.transcodingUrl
                    val path =
                        if (transcodingUrl != null) {
                            if (transcodingUrl.startsWith("/")) transcodingUrl
                            else "/$transcodingUrl"
                        } else {
                            "/Videos/${item.itemId}/stream?MediaSourceId=${mediaSource.id}"
                        }
                    val url = "$baseUrl$path"
                    url to "application/x-mpegurl"
                }

            val streamUrl =
                if (audioStreamIndex != null) {
                    if (streamUrlOriginal.contains("AudioStreamIndex=")) {
                        streamUrlOriginal.replace(
                            Regex("AudioStreamIndex=\\d+"),
                            "AudioStreamIndex=$audioStreamIndex",
                        )
                    } else {
                        val separator = if (streamUrlOriginal.contains("?")) "&" else "?"
                        "$streamUrlOriginal${separator}AudioStreamIndex=$audioStreamIndex"
                    }
                } else {
                    streamUrlOriginal
                }

            Timber.d("Video url: $streamUrl")

            val mediaInfoBuilder =
                MediaInfo.Builder(streamUrl)
                    .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                    .setContentType(contentType)
                    .setMetadata(mediaMetadata)
                    .setCustomData(customData)

            val (castTracks, subtitles, audio) = getTracks(mediaSource)

            if (castTracks.isNotEmpty()) {
                mediaInfoBuilder.setMediaTracks(castTracks)
            }

            BuildMediaResult(
                mediaInfo = mediaInfoBuilder.build(),
                playbackInfo = playbackInfo,
                subtitleTracks = subtitles,
                audioTracks = audio,
            )
        }

    private fun getTracks(
        mediaSource: MediaSourceInfo
    ): Triple<List<MediaTrack>, List<Track>, List<Track>> {
        val baseUrl = jellyfinApi.api.baseUrl?.removeSuffix("/").orEmpty()

        val castTracks = mutableListOf<MediaTrack>()
        val subtitles = mutableListOf<Track>()
        val audio = mutableListOf<Track>()

        mediaSource.mediaStreams?.forEach { stream ->
            // Subtitle
            if (stream.type == MediaStreamType.SUBTITLE) {
                val trackId = (stream.index + 100)
                val deliveryUrl = stream.deliveryUrl

                if (subtitleStreamIndex == null && stream.isDefault) {
                    subtitleStreamIndex = trackId
                }

                if (!deliveryUrl.isNullOrEmpty()) {
                    val trackUrl =
                        if (
                            deliveryUrl.startsWith("http://") || deliveryUrl.startsWith("https://")
                        ) {
                            deliveryUrl
                        } else {
                            val path =
                                if (deliveryUrl.startsWith("/")) deliveryUrl else "/$deliveryUrl"
                            "$baseUrl$path"
                        }

                    val builder =
                        MediaTrack.Builder(trackId.toLong(), MediaTrack.TYPE_TEXT)
                            .setContentId(trackUrl)
                            .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                            .setContentType("text/vtt")
                            .setLanguage(stream.language)
                            .setName(stream.title ?: stream.displayTitle ?: "Track ${stream.index}")

                    castTracks.add(builder.build())
                }

                val track =
                    Track(
                        id = trackId,
                        label = stream.title ?: stream.displayTitle,
                        language = stream.language,
                        codec = stream.codec,
                        selected =
                            if (subtitleStreamIndex != null) trackId == subtitleStreamIndex
                            else stream.isDefault,
                        supported = !deliveryUrl.isNullOrEmpty(),
                        isExternal = stream.isExternal,
                        isForced = stream.isForced,
                        isHearingImpaired = stream.isHearingImpaired,
                    )

                subtitles.add(track)
                Timber.d("Subtitle track: $stream")
            }
            // Audio
            else if (stream.type == MediaStreamType.AUDIO) {
                if (audioStreamIndex == null && stream.isDefault) {
                    audioStreamIndex = stream.index
                }

                val track =
                    Track(
                        id = stream.index,
                        label = stream.title ?: stream.displayTitle,
                        language = stream.language,
                        codec = stream.codec,
                        selected =
                            if (audioStreamIndex != null) stream.index == audioStreamIndex
                            else false,
                        supported = true,
                        isExternal = stream.isExternal,
                        isForced = stream.isForced,
                        isHearingImpaired = stream.isHearingImpaired,
                    )

                audio.add(track)
                Timber.d("Audio track: $stream")
            }
        }

        if (audioStreamIndex == null && audio.isNotEmpty()) {
            audioStreamIndex = audio.first().id
            audio[0] = audio[0].copy(selected = true)
        }

        return Triple(castTracks, subtitles, audio)
    }

    override fun playItem(itemId: UUID, itemKind: String, startFromBeginning: Boolean) {
        stopReporting()

        audioStreamIndex = null
        subtitleStreamIndex = null
        isSessionRestored = true
        isReporting = false
        itemCache.clear()
        itemDuration.clear()

        abandonAudioFocus()

        playJob?.cancel()
        playJob = scope.launch {
            val initialItem =
                playlistManager.getInitialItem(
                    itemId = itemId,
                    itemKind = BaseItemKind.fromName(itemKind),
                    mediaSourceIndex = null,
                    startFromBeginning = startFromBeginning,
                )

            if (initialItem != null) {
                val client = remoteMediaClient ?: return@launch
                val result = buildMediaInfo(initialItem) ?: return@launch

                val castItem =
                    CastMediaItem(
                        item = initialItem,
                        playbackInfo = result.playbackInfo,
                        subtitleTracks = result.subtitleTracks,
                        audioTracks = result.audioTracks,
                    )

                val startPositionMs = initialItem.playbackPosition.coerceAtLeast(0L)
                val loadRequest =
                    MediaLoadRequestData.Builder()
                        .setMediaInfo(result.mediaInfo)
                        .setAutoplay(true)
                        .setCurrentTime(startPositionMs)
                        .build()

                itemCache[initialItem.itemId] = castItem
                _currentItem.value = castItem

                client.load(loadRequest).setResultCallback { callbackResult ->
                    if (callbackResult.status.isSuccess) {
                        subtitleStreamIndex?.let { subId ->
                            client.setActiveMediaTracks(longArrayOf(subId.toLong()))
                        }
                    } else {
                        Timber.e("Error loading media: ${callbackResult.status.statusMessage}")
                        _currentItem.value = null
                    }
                }
            }
        }
    }

    private fun manageQueue(itemId: UUID) {
        queueJob?.cancel()
        queueJob = scope.launch {
            val status = remoteMediaClient?.mediaStatus ?: return@launch
            val queueItems = status.queueItems

            playlistManager.setCurrentMediaItemIndex(itemId)

            val nextItem = playlistManager.getNextPlayerItem()
            val prevItem = playlistManager.getPreviousPlayerItem()

            val queuedItemIds =
                queueItems
                    .mapNotNull {
                        it.media?.customData?.optString("itemId")?.takeIf { id -> id.isNotEmpty() }
                    }
                    .toSet()

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
                    hasPreviousItem = prevItem != null,
                )
            }
        }
    }

    private suspend fun queueNextItem(item: PlayerItem) {
        val result = buildMediaInfo(item) ?: return
        itemCache[item.itemId] =
            CastMediaItem(
                item = item,
                playbackInfo = result.playbackInfo,
                subtitleTracks = result.subtitleTracks,
                audioTracks = result.audioTracks,
            )

        queueMutex.withLock {
            val client = remoteMediaClient ?: return@withLock
            val status = client.mediaStatus ?: return@withLock

            val queueItem =
                MediaQueueItem.Builder(result.mediaInfo)
                    .setAutoplay(true)
                    .setPreloadTime(20.0)
                    .build()

            val currentItemId = status.currentItemId
            val queueItems = status.queueItems
            val currentIndex = queueItems.indexOfFirst { it.itemId == currentItemId }

            val nextItemId =
                if (currentIndex != -1 && currentIndex < queueItems.size - 1) {
                    queueItems[currentIndex + 1].itemId
                } else {
                    MediaQueueItem.INVALID_ITEM_ID
                }

            try {
                client.queueInsertItems(arrayOf(queueItem), nextItemId, null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to insert next queue item")
            }
        }
    }

    private suspend fun queuePreviousItem(item: PlayerItem) {
        val result = buildMediaInfo(item) ?: return
        itemCache[item.itemId] =
            CastMediaItem(
                item = item,
                playbackInfo = result.playbackInfo,
                subtitleTracks = result.subtitleTracks,
                audioTracks = result.audioTracks,
            )

        queueMutex.withLock {
            val client = remoteMediaClient ?: return@withLock
            val status = client.mediaStatus ?: return@withLock

            val queueItem =
                MediaQueueItem.Builder(result.mediaInfo)
                    .setAutoplay(true)
                    .setPreloadTime(20.0)
                    .build()

            val currentItemId = status.currentItemId

            try {
                client.queueInsertItems(arrayOf(queueItem), currentItemId, null)
            } catch (e: Exception) {
                Timber.e(e, "Failed to insert previous queue item")
            }
        }
    }

    override fun play() {
        try {
            abandonAudioFocus()
            remoteMediaClient?.play()
        } catch (e: Exception) {
            Timber.e(e, "Failed to play")
        }
    }

    override fun pause() {
        try {
            remoteMediaClient?.pause()
        } catch (e: Exception) {
            Timber.e(e, "Failed to pause")
        }
    }

    override fun seekTo(position: Long) {
        val safePosition = position.coerceAtLeast(0L)
        _playerState.update { it.copy(currentPosition = safePosition) }
        try {
            val options =
                MediaSeekOptions.Builder()
                    .setPosition(safePosition)
                    .setResumeState(MediaSeekOptions.RESUME_STATE_UNCHANGED)
                    .build()
            remoteMediaClient?.seek(options)
        } catch (e: Exception) {
            Timber.e(e, "Failed to seek to $safePosition")
        }
    }

    override fun seekToNext() {
        try {
            remoteMediaClient?.queueNext(null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to seek to next item")
        }
    }

    override fun seekToPrevious() {
        try {
            remoteMediaClient?.queuePrev(null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to seek to previous item")
        }
    }

    override fun setVolume(volume: Float) {
        try {
            castSession?.volume = volume.toDouble().coerceIn(0.0, 1.0)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set volume on CastSession")
        }
    }

    override fun setSubtitleTrack(track: Track?) {
        val client = remoteMediaClient ?: return
        val activeIds = client.mediaStatus?.activeTrackIds?.toMutableList() ?: mutableListOf()

        activeIds.remove(subtitleStreamIndex?.toLong())

        if (track != null) activeIds.add(track.id.toLong())
        subtitleStreamIndex = track?.id

        _currentItem.update { item ->
            val subtitleTracks =
                item?.subtitleTracks?.map { it.copy(selected = track != null && it.id == track.id) }
                    ?: emptyList()

            item?.copy(subtitleTracks = subtitleTracks)
        }

        val targetIds = activeIds.distinct().toLongArray()
        client.setActiveMediaTracks(targetIds).setResultCallback { result ->
            if (result.status.isSuccess) {
                val activeTrackIds = client.mediaStatus?.activeTrackIds?.toList() ?: emptyList()

                _currentItem.update { item ->
                    val subtitleTracks =
                        item?.subtitleTracks?.map {
                            it.copy(selected = activeTrackIds.contains(it.id.toLong()))
                        } ?: emptyList()

                    item?.copy(subtitleTracks = subtitleTracks)
                }

                Timber.d("Selected subtitle track: $track")
            } else {
                Timber.e("Failed to set subtitle track: ${result.status.statusMessage}")
            }
        }
    }

    override fun setAudioTrack(track: Track?, itemId: UUID?) {
        if (itemId == null || track == null) return
        if (audioStreamIndex == track.id) return
        audioStreamIndex = track.id

        // Update to avoid UI glitches
        _currentItem.update { item ->
            val audioTracks =
                item?.audioTracks?.map { it.copy(selected = it.id == track.id) } ?: emptyList()

            item?.copy(audioTracks = audioTracks)
        }

        audioTrackJob?.cancel()
        audioTrackJob = scope.launch {
            val client = remoteMediaClient ?: return@launch
            val cachedMedia =
                itemCache[itemId]
                    ?: _currentItem.value?.takeIf { it.item.itemId == itemId }
                    ?: return@launch
            val currentPositionMs = client.approximateStreamPosition.coerceAtLeast(0L)

            // Request new playback info from Jellyfin with the selected audio track index
            val result = buildMediaInfo(cachedMedia.item) ?: return@launch

            // Update the current item
            val loadRequest =
                MediaLoadRequestData.Builder()
                    .setMediaInfo(result.mediaInfo)
                    .setCurrentTime(currentPositionMs)
                    .setAutoplay(true)
                    .build()

            client.load(loadRequest).setResultCallback { callbackResult ->
                if (callbackResult.status.isSuccess) {
                    itemCache.clear()

                    val updatedMedia =
                        cachedMedia.copy(
                            playbackInfo = result.playbackInfo,
                            audioTracks = result.audioTracks,
                            subtitleTracks = result.subtitleTracks,
                        )

                    itemCache[itemId] = updatedMedia
                    _currentItem.value = updatedMedia

                    subtitleStreamIndex?.let { subId ->
                        client.setActiveMediaTracks(longArrayOf(subId.toLong()))
                    }

                    Timber.d("Selected audio track: $track")
                } else {
                    Timber.e("Failed to set audio track: ${callbackResult.status.statusMessage}")
                }
            }
        }
    }

    override fun stop() {
        try {
            remoteMediaClient?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop remote media client")
        } finally {
            clearSession()
        }
    }
}
