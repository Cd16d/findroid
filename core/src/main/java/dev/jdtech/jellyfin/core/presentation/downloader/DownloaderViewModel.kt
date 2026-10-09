package dev.jdtech.jellyfin.core.presentation.downloader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.DownloadQualityPreset
import dev.jdtech.jellyfin.models.DownloadQualityPresets
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.isDownloading
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import dev.jdtech.jellyfin.utils.download.DownloadStatus
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class DownloaderViewModel
@Inject
constructor(
    private val downloader: Downloader,
    private val downloadQueue: DownloadQueue,
    private val appPreferences: AppPreferences,
    private val jellyfinRepository: JellyfinRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(
        downloader: Downloader,
        downloadQueue: DownloadQueue,
        appPreferences: AppPreferences,
        jellyfinRepository: JellyfinRepository,
        savedStateHandle: SavedStateHandle,
        ioDispatcher: CoroutineDispatcher,
    ) : this(downloader, downloadQueue, appPreferences, jellyfinRepository, savedStateHandle) {
        this.ioDispatcher = ioDispatcher
    }

    val askPresetBeforeDownload: Boolean
        get() = appPreferences.getValue(appPreferences.askPresetBeforeDownload)

    val userCanTranscode: Boolean
        get() = appPreferences.getValue(appPreferences.userCanTranscode)

    val downloadExternalAudio: Boolean
        get() = appPreferences.getValue(appPreferences.downloadExternalAudio)

    val defaultTranscodePresetId: String
        get() = appPreferences.getValue(appPreferences.defaultTranscodePresetId)

    val defaultDownloadStorageIndex: Int
        get() =
            appPreferences.getValue(appPreferences.defaultDownloadStorageIndex).toIntOrNull() ?: -1

    private var cachedPresets: List<DownloadQualityPreset>? = null

    val presets: List<DownloadQualityPreset>
        get() {
            var current = cachedPresets
            if (current == null) {
                current = DownloadQualityPresets.loadPresets(appPreferences)
                cachedPresets = current
            }
            return current
        }

    fun refreshPresets() {
        cachedPresets = DownloadQualityPresets.loadPresets(appPreferences)
    }

    fun saveDownloadSettings(
        presetId: String,
        downloadExternalAudio: Boolean,
        rememberSettings: Boolean,
    ) {
        if (rememberSettings) {
            appPreferences.setValue(appPreferences.askPresetBeforeDownload, false)
            appPreferences.setValue(appPreferences.defaultTranscodePresetId, presetId)
            appPreferences.setValue(appPreferences.downloadExternalAudio, downloadExternalAudio)
        }
    }

    private val _state = MutableStateFlow(DownloaderState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<DownloaderEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    val queueEntries: StateFlow<List<DownloadQueue.Entry>> = downloadQueue.entries

    private var downloadId: Long? = savedStateHandle[KEY_DOWNLOAD_ID]
    private var downloadItemId: UUID? =
        savedStateHandle.get<String>(KEY_DOWNLOAD_ITEM_ID)?.let { idStr ->
            runCatching { UUID.fromString(idStr) }.getOrNull()
        }
    private var downloadItem: FindroidItem? = null

    init {
        viewModelScope.launch(ioDispatcher) {
            try {
                appPreferences.setValue(
                    appPreferences.userCanTranscode,
                    jellyfinRepository.canTranscode(),
                )
            } catch (_: Exception) {
                // Keep default
            }
        }
        viewModelScope.launch {
            downloadQueue.entries.collect { entries ->
                val currentId = downloadItem?.id ?: downloadItemId
                if (currentId != null) {
                    val entry = entries.firstOrNull { it.id == currentId }
                    if (entry != null) {
                        val wasAlreadyCompleted = _state.value.status == DownloadStatus.SUCCESSFUL
                        val newState = mapEntryToState(entry)
                        _state.value = newState
                        if (
                            entry.state is DownloadQueue.EntryState.Completed &&
                                !wasAlreadyCompleted
                        ) {
                            eventsChannel.send(DownloaderEvent.Successful)
                        }
                    }
                }
            }
        }
    }

    fun update(item: FindroidItem) {
        this.downloadItem = item
        this.downloadItemId = item.id
        savedStateHandle[KEY_DOWNLOAD_ITEM_ID] = item.id.toString()
        viewModelScope.launch(ioDispatcher) {
            val entry = downloadQueue.entries.value.firstOrNull { it.id == item.id }
            if (entry != null) {
                _state.value = mapEntryToState(entry)
            } else if (item.isDownloading()) {
                val source =
                    item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                        ?: return@launch
                this@DownloaderViewModel.downloadId = source.downloadId
                savedStateHandle[KEY_DOWNLOAD_ID] = source.downloadId
                val progressObj = downloader.getProgress(source.downloadId)
                _state.update {
                    it.copy(
                        status = progressObj.status,
                        progress = progressObj.progress.coerceAtLeast(0) / 100f,
                        errorText = null,
                        extraInfo = null,
                    )
                }
            } else {
                _state.value = DownloaderState()
            }
        }
    }

    private fun download(
        item: FindroidItem,
        storageIndex: Int = -1,
        presetId: String? = null,
        downloadExternalAudio: Boolean = false,
        audioStreamIndex: Int? = null,
    ) {
        this.downloadItem = item
        this.downloadItemId = item.id
        savedStateHandle[KEY_DOWNLOAD_ITEM_ID] = item.id.toString()
        viewModelScope.launch(ioDispatcher) {
            _state.update {
                it.copy(
                    status = DownloadStatus.PENDING,
                    progress = 0f,
                    errorText = null,
                    extraInfo = null,
                )
            }
            downloadQueue.enqueue(
                item = item,
                presetId = presetId,
                downloadExternalAudio = downloadExternalAudio,
                storageIndex = storageIndex,
                audioStreamIndex = audioStreamIndex,
            )
        }
    }

    private fun downloadMany(
        items: List<FindroidItem>,
        storageIndex: Int = -1,
        presetId: String? = null,
        downloadExternalAudio: Boolean = false,
        audioStreamIndex: Int? = null,
    ) {
        viewModelScope.launch(ioDispatcher) {
            _state.update {
                it.copy(
                    status = DownloadStatus.PENDING,
                    progress = 0f,
                    errorText = null,
                    extraInfo = null,
                )
            }
            val toDownload = items.filter {
                !it.sources.any { src -> src.type == FindroidSourceType.LOCAL }
            }
            for (item in toDownload) {
                if (!isActive) break
                downloadQueue.enqueue(
                    item = item,
                    presetId = presetId,
                    downloadExternalAudio = downloadExternalAudio,
                    storageIndex = storageIndex,
                    audioStreamIndex = audioStreamIndex,
                )
            }
        }
    }

    private fun cancelDownload(item: FindroidItem) {
        viewModelScope.launch(ioDispatcher) {
            downloadQueue.cancel(item.id)
            val currentDlId = downloadId ?: savedStateHandle.get<Long>(KEY_DOWNLOAD_ID)
            currentDlId?.let { downloader.cancelDownload(item = item, downloadId = it) }
            val currentId = downloadItem?.id ?: downloadItemId
            if (currentId == item.id) {
                clearSavedItem()
                _state.update { DownloaderState() }
            }
        }
    }

    private fun cancelDownloadMany(items: List<FindroidItem> = emptyList()) {
        viewModelScope.launch(ioDispatcher) {
            if (items.isNotEmpty()) {
                items.forEach {
                    if (isActive) {
                        downloadQueue.cancel(it.id)
                    }
                }
            } else {
                downloadQueue.cancelAll()
            }
            clearSavedItem()
            _state.update { DownloaderState() }
            eventsChannel.send(DownloaderEvent.Deleted)
        }
    }

    private fun deleteDownload(item: FindroidItem) {
        viewModelScope.launch(ioDispatcher) {
            val source =
                item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                    ?: item.sources.firstOrNull()
                    ?: return@launch
            downloader.deleteItem(
                item = item,
                source = source,
            )
            eventsChannel.send(DownloaderEvent.Deleted)
        }
    }

    private fun deleteDownloadedMany(items: List<FindroidItem>) {
        viewModelScope.launch(ioDispatcher) {
            val localItems = items.filter { ep ->
                ep.sources.any { src -> src.type == FindroidSourceType.LOCAL }
            }
            if (localItems.isEmpty()) return@launch

            for (item in localItems) {
                if (!isActive) break
                val source =
                    item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                        ?: item.sources.firstOrNull()
                        ?: continue
                downloader.deleteItem(item = item, source = source)
            }
            eventsChannel.send(DownloaderEvent.Deleted)
        }
    }

    private fun clearSavedItem() {
        downloadItem = null
        downloadItemId = null
        downloadId = null
        savedStateHandle.remove<String>(KEY_DOWNLOAD_ITEM_ID)
        savedStateHandle.remove<Long>(KEY_DOWNLOAD_ID)
    }

    private fun mapEntryToState(entry: DownloadQueue.Entry): DownloaderState {
        val extraInfo = buildExtraInfo(entry)
        return when (val state = entry.state) {
            is DownloadQueue.EntryState.Downloading,
            is DownloadQueue.EntryState.Converting ->
                DownloaderState(
                    status = DownloadStatus.RUNNING,
                    progress = entry.progress / 100f,
                    errorText = null,
                    extraInfo = extraInfo,
                )
            is DownloadQueue.EntryState.Pending ->
                DownloaderState(
                    status = DownloadStatus.PENDING,
                    progress = 0f,
                    errorText = null,
                    extraInfo = null,
                )
            is DownloadQueue.EntryState.Paused ->
                DownloaderState(
                    status = DownloadStatus.PAUSED,
                    progress = entry.progress / 100f,
                    errorText = null,
                    extraInfo = null,
                )
            is DownloadQueue.EntryState.Completed ->
                DownloaderState(
                    status = DownloadStatus.SUCCESSFUL,
                    progress = 1f,
                    errorText = null,
                    extraInfo = null,
                )
            is DownloadQueue.EntryState.Failed ->
                DownloaderState(
                    status = DownloadStatus.FAILED,
                    progress = entry.progress / 100f,
                    errorText = state.error,
                    extraInfo = null,
                )
        }
    }

    private fun buildExtraInfo(entry: DownloadQueue.Entry): String? {
        val speedStr = if (entry.bytesPerSecond > 0L) formatSpeed(entry.bytesPerSecond) else ""
        val etaStr = if (entry.etaSeconds > 0L) formatStableEta(entry.etaSeconds) else ""
        return when {
            speedStr.isNotEmpty() && etaStr.isNotEmpty() -> "$speedStr • $etaStr"
            speedStr.isNotEmpty() -> speedStr
            etaStr.isNotEmpty() -> etaStr
            else -> null
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.ROOT, "%.1f GB/s", gb)
            mb >= 1.0 -> String.format(Locale.ROOT, "%.1f MB/s", mb)
            kb >= 1.0 -> String.format(Locale.ROOT, "%.1f KB/s", kb)
            else -> "$bytesPerSec B/s"
        }
    }

    fun onAction(action: DownloaderAction) {
        when (action) {
            is DownloaderAction.Download ->
                download(
                    item = action.item,
                    storageIndex = action.storageIndex,
                    presetId = action.presetId,
                    downloadExternalAudio = action.downloadExternalAudio,
                    audioStreamIndex = action.audioStreamIndex,
                )
            is DownloaderAction.DownloadMany ->
                downloadMany(
                    items = action.items,
                    storageIndex = action.storageIndex,
                    presetId = action.presetId,
                    downloadExternalAudio = action.downloadExternalAudio,
                    audioStreamIndex = action.audioStreamIndex,
                )
            is DownloaderAction.DeleteDownload -> deleteDownload(action.item)
            is DownloaderAction.DeleteDownloadMany -> deleteDownloadedMany(action.items)
            is DownloaderAction.CancelDownload -> cancelDownload(action.item)
            is DownloaderAction.CancelDownloadMany -> cancelDownloadMany(action.items)
        }
    }

    companion object {
        private const val KEY_DOWNLOAD_ID = "downloader_download_id"
        private const val KEY_DOWNLOAD_ITEM_ID = "downloader_download_item_id"
    }
}
