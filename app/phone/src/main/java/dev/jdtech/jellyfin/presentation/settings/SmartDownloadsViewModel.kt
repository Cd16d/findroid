package dev.jdtech.jellyfin.presentation.settings

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class SmartDownloadsState(
    val smartDownloadNextEpisode: Boolean = true,
    val nextEpisodesCount: Int = 3,
    val storageLimitGb: Int = 20,
    val autoDeleteWatched: Boolean = false,
    val usedStorageFormatted: String = "",
    val freeStorageFormatted: String = "",
    val deviceUsedFormatted: String = "",
    val deviceTotalFormatted: String = "",
    val downloadedFraction: Float = 0f,
    val otherUsedFraction: Float = 0f,
    val freeFraction: Float = 0f,
)

@HiltViewModel
class SmartDownloadsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(SmartDownloadsState())
    val state = _state.asStateFlow()

    init {
        loadSettings()
        loadStorage()
    }

    private fun loadSettings() {
        val nextEp = appPreferences.getValue(appPreferences.smartDownloadNextEpisode)
        val count = appPreferences.getValue(appPreferences.smartDownloadNextEpisodesCount)
        val limit = appPreferences.getValue(appPreferences.smartDownloadStorageLimitGb)
        val autoDelete = appPreferences.getValue(appPreferences.autoDeleteWatched)

        _state.update {
            it.copy(
                smartDownloadNextEpisode = nextEp,
                nextEpisodesCount = count,
                storageLimitGb = limit,
                autoDeleteWatched = autoDelete,
            )
        }
    }

    private fun loadStorage() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val dirs = context.getExternalFilesDirs(null)
                    val defaultIndex = appPreferences.getValue(appPreferences.defaultDownloadStorageIndex).toIntOrNull() ?: 0
                    val storageLocation = dirs.getOrNull(defaultIndex) ?: dirs.firstOrNull() ?: context.filesDir
                    val downloadsDir = File(storageLocation, "downloads")
                    val downloadedBytes = if (downloadsDir.exists()) {
                        downloadsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    } else {
                        0L
                    }
                    val statFs = StatFs(storageLocation.path)
                    val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
                    val totalDeviceBytes = StorageUtils.getTotalStorageBytes(context, storageLocation.path)
                    val deviceUsedBytes = (totalDeviceBytes - availableBytes).coerceAtLeast(0L)

                    val downloadedFrac = if (totalDeviceBytes > 0) (downloadedBytes.toFloat() / totalDeviceBytes).coerceIn(0f, 1f) else 0f
                    val freeFrac = if (totalDeviceBytes > 0) (availableBytes.toFloat() / totalDeviceBytes).coerceIn(0f, 1f) else 0f
                    val otherFrac = (1f - downloadedFrac - freeFrac).coerceAtLeast(0f)

                    _state.update {
                        it.copy(
                            usedStorageFormatted = StorageUtils.formatDecimalFileSize(downloadedBytes),
                            freeStorageFormatted = StorageUtils.formatDecimalFileSize(availableBytes),
                            deviceUsedFormatted = StorageUtils.formatDecimalFileSize(deviceUsedBytes),
                            deviceTotalFormatted = StorageUtils.formatDecimalFileSize(totalDeviceBytes),
                            downloadedFraction = downloadedFrac,
                            otherUsedFraction = otherFrac,
                            freeFraction = freeFrac,
                        )
                    }
                } catch (e: Exception) {
                    // Ignore storage read error
                }
            }
        }
    }

    fun onAction(action: SmartDownloadsAction) {
        when (action) {
            is SmartDownloadsAction.SetSmartDownloadNextEpisode -> {
                appPreferences.setValue(appPreferences.smartDownloadNextEpisode, action.enabled)
                _state.update { it.copy(smartDownloadNextEpisode = action.enabled) }
            }
            is SmartDownloadsAction.SetNextEpisodesCount -> {
                appPreferences.setValue(appPreferences.smartDownloadNextEpisodesCount, action.count)
                _state.update { it.copy(nextEpisodesCount = action.count) }
            }
            is SmartDownloadsAction.SetStorageLimitGb -> {
                appPreferences.setValue(appPreferences.smartDownloadStorageLimitGb, action.limitGb)
                _state.update { it.copy(storageLimitGb = action.limitGb) }
            }
            is SmartDownloadsAction.SetAutoDeleteWatched -> {
                appPreferences.setValue(appPreferences.autoDeleteWatched, action.enabled)
                _state.update { it.copy(autoDeleteWatched = action.enabled) }
            }
        }
    }
}
