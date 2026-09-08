package dev.jdtech.jellyfin.presentation.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.DownloadQualityPreset
import dev.jdtech.jellyfin.models.DownloadQualityPresets
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class DownloadPresetsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _presets = MutableStateFlow<List<DownloadQualityPreset>>(emptyList())
    val presets = _presets.asStateFlow()

    init {
        load()
    }

    fun load() {
        _presets.value = DownloadQualityPresets.loadPresets(appPreferences)
    }

    fun savePreset(preset: DownloadQualityPreset) {
        val current = _presets.value.toMutableList()
        val index = current.indexOfFirst { it.id == preset.id }
        if (index >= 0) {
            current[index] = preset
        } else {
            current.add(preset)
        }
        DownloadQualityPresets.savePresets(appPreferences, current)
        _presets.value = current
    }

    fun deletePreset(id: String) {
        val current = _presets.value.filter { it.id != id }
        DownloadQualityPresets.savePresets(appPreferences, current)
        _presets.value = current
    }

    fun importPresets(json: String): Boolean {
        val imported = DownloadQualityPresets.importFromJson(json) ?: return false
        val withoutOriginal = imported.filter { !it.isOriginal }
        val combined = listOf(DownloadQualityPresets.ORIGINAL) + withoutOriginal
        DownloadQualityPresets.savePresets(appPreferences, combined)
        _presets.value = combined
        return true
    }

    fun resetToDefaults() {
        DownloadQualityPresets.resetToDefaults(appPreferences)
        _presets.value = DownloadQualityPresets.defaultPresets
    }
}
