package dev.jdtech.jellyfin.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.DeviceCodecCapabilities
import dev.jdtech.jellyfin.utils.DeviceCodecsOverview
import dev.jdtech.jellyfin.utils.DeviceUtils
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class DeviceViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val repository: JellyfinRepository,
) : ViewModel() {

    val codecsOverview: DeviceCodecsOverview = DeviceCodecCapabilities.getDeviceCodecs()

    private val _deviceName =
        MutableStateFlow(
            appPreferences.getValue(appPreferences.customDeviceName).ifBlank {
                repository.getDeviceName().ifBlank { DeviceUtils.getDeviceName(context) }
            }
        )
    val deviceName = _deviceName.asStateFlow()

    fun updateDeviceName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            _deviceName.value = trimmed
            appPreferences.setValue(appPreferences.customDeviceName, trimmed)
            viewModelScope.launch {
                try {
                    repository.updateDeviceName(trimmed)
                    Timber.d("Device name updated on Jellyfin server to: %s", trimmed)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update device name on Jellyfin server")
                }
            }
        }
    }
}
