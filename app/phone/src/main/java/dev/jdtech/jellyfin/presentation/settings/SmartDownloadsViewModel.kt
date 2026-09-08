package dev.jdtech.jellyfin.presentation.settings

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.utils.StorageUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
        val count = appPreferences.getValue(appPreferences.smartDownloadNextEpisodesCount).toIntOrNull() ?: 3
        val limit = appPreferences.getValue(appPreferences.smartDownloadStorageLimitGb).toIntOrNull() ?: 20
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

    fun setSmartDownloadNextEpisode(enabled: Boolean) {
        appPreferences.setValue(appPreferences.smartDownloadNextEpisode, enabled)
        _state.update { it.copy(smartDownloadNextEpisode = enabled) }
    }

    fun setNextEpisodesCount(count: Int) {
        appPreferences.setValue(appPreferences.smartDownloadNextEpisodesCount, count.toString())
        _state.update { it.copy(nextEpisodesCount = count) }
    }

    fun setStorageLimitGb(limitGb: Int) {
        appPreferences.setValue(appPreferences.smartDownloadStorageLimitGb, limitGb.toString())
        _state.update { it.copy(storageLimitGb = limitGb) }
    }

    fun setAutoDeleteWatched(enabled: Boolean) {
        appPreferences.setValue(appPreferences.autoDeleteWatched, enabled)
        _state.update { it.copy(autoDeleteWatched = enabled) }
    }
}
