package dev.jdtech.jellyfin.core.presentation.downloader

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
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloaderViewModel
@Inject
constructor(
    private val downloader: Downloader,
    private val downloadQueue: DownloadQueue,
    val appPreferences: AppPreferences,
    private val jellyfinRepository: JellyfinRepository,
) : ViewModel() {
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

    val presets: List<DownloadQualityPreset>
        get() = DownloadQualityPresets.loadPresets(appPreferences)

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

    private val eventsChannel = Channel<DownloaderEvent>()
    val events = eventsChannel.receiveAsFlow()

    val queueEntries: StateFlow<List<DownloadQueue.Entry>> = downloadQueue.entries

    var downloadId: Long? = null
    var downloadItem: FindroidItem? = null

    init {
        viewModelScope.launch {
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
                val current = downloadItem
                if (current != null) {
                    val entry = entries.firstOrNull { it.id == current.id }
                    if (entry != null) {
                        when (val s = entry.state) {
                            is DownloadQueue.EntryState.Downloading,
                            is DownloadQueue.EntryState.Converting -> {
                                _state.update {
                                    it.copy(
                                        status = DownloadStatus.RUNNING,
                                        progress = entry.progress / 100f,
                                        errorText = null,
                                    )
                                }
                            }
                            is DownloadQueue.EntryState.Pending -> {
                                _state.update {
                                    it.copy(
                                        status = DownloadStatus.PENDING,
                                        progress = 0f,
                                        errorText = null,
                                    )
                                }
                            }
                            is DownloadQueue.EntryState.Paused -> {
                                _state.update {
                                    it.copy(
                                        status = DownloadStatus.PAUSED,
                                        progress = entry.progress / 100f,
                                        errorText = null,
                                    )
                                }
                            }
                            is DownloadQueue.EntryState.Completed -> {
                                _state.update {
                                    it.copy(
                                        status = DownloadStatus.SUCCESSFUL,
                                        progress = 1f,
                                        errorText = null,
                                    )
                                }
                                eventsChannel.trySend(DownloaderEvent.Successful)
                            }
                            is DownloadQueue.EntryState.Failed -> {
                                _state.update {
                                    it.copy(
                                        status = DownloadStatus.FAILED,
                                        errorText = s.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun update(item: FindroidItem) {
        this.downloadItem = item
        viewModelScope.launch {
            val entry = downloadQueue.entries.value.firstOrNull { it.id == item.id }
            if (entry != null) {
                when (entry.state) {
                    is DownloadQueue.EntryState.Downloading,
                    is DownloadQueue.EntryState.Converting ->
                        _state.update {
                            it.copy(
                                status = DownloadStatus.RUNNING,
                                progress = entry.progress / 100f,
                                errorText = null,
                            )
                        }
                    is DownloadQueue.EntryState.Pending ->
                        _state.update {
                            it.copy(
                                status = DownloadStatus.PENDING,
                                progress = 0f,
                                errorText = null,
                            )
                        }
                    is DownloadQueue.EntryState.Paused ->
                        _state.update {
                            it.copy(
                                status = DownloadStatus.PAUSED,
                                progress = entry.progress / 100f,
                                errorText = null,
                            )
                        }
                    is DownloadQueue.EntryState.Completed ->
                        _state.update {
                            it.copy(
                                status = DownloadStatus.SUCCESSFUL,
                                progress = 1f,
                                errorText = null,
                            )
                        }
                    is DownloadQueue.EntryState.Failed ->
                        _state.update { it.copy(status = DownloadStatus.FAILED) }
                }
            } else if (item.isDownloading()) {
                val source =
                    item.sources.firstOrNull { it.type == FindroidSourceType.LOCAL }
                        ?: return@launch
                this@DownloaderViewModel.downloadId = source.downloadId
                val progressObj = downloader.getProgress(source.downloadId)
                _state.update {
                    it.copy(
                        status = progressObj.status,
                        progress = progressObj.progress.coerceAtLeast(0) / 100f,
                        errorText = null,
                    )
                }
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
        viewModelScope.launch {
            _state.update {
                it.copy(status = DownloadStatus.PENDING, progress = 0f, errorText = null)
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
        viewModelScope.launch {
            _state.update {
                it.copy(status = DownloadStatus.PENDING, progress = 0f, errorText = null)
            }
            val toDownload = items.filter {
                !it.sources.any { src -> src.type == FindroidSourceType.LOCAL }
            }
            for (item in toDownload) {
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
        viewModelScope.launch {
            downloadQueue.cancel(item.id)
            downloadId?.let { downloader.cancelDownload(item = item, downloadId = it) }
            _state.update { DownloaderState() }
        }
    }

    private fun cancelDownloadMany(items: List<FindroidItem> = emptyList()) {
        viewModelScope.launch {
            if (items.isNotEmpty()) {
                items.forEach { downloadQueue.cancel(it.id) }
            } else {
                downloadQueue.cancelAll()
            }
            _state.update { DownloaderState() }
            eventsChannel.send(DownloaderEvent.Deleted)
        }
    }

    private fun deleteDownload(item: FindroidItem) {
        viewModelScope.launch {
            downloader.deleteItem(
                item = item,
                source = item.sources.first { it.type == FindroidSourceType.LOCAL },
            )
            eventsChannel.send(DownloaderEvent.Deleted)
        }
    }

    private fun deleteDownloadedMany(items: List<FindroidItem>) {
        // Only if a source has type local can it be deleted
        items
            .filter { ep -> ep.sources.any { src -> src.type == FindroidSourceType.LOCAL } }
            .forEach(::deleteDownload)
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

    override fun onCleared() {
        super.onCleared()
    }
}
