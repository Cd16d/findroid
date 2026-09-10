package dev.jdtech.jellyfin.presentation.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.DownloadQualityPreset
import dev.jdtech.jellyfin.models.DownloadQualityPresets
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DownloadPresetsAction {
    data class SavePreset(val preset: DownloadQualityPreset) : DownloadPresetsAction
    data class DeletePreset(val id: String) : DownloadPresetsAction
    data class ImportPresets(val json: String) : DownloadPresetsAction
    data object ResetToDefaults : DownloadPresetsAction
}

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

    /** Returns false if the action failed (e.g. invalid JSON for [DownloadPresetsAction.ImportPresets]). */
    fun onAction(action: DownloadPresetsAction): Boolean {
        when (action) {
            is DownloadPresetsAction.SavePreset -> {
                val current = _presets.value.toMutableList()
                val index = current.indexOfFirst { it.id == action.preset.id }
                if (index >= 0) current[index] = action.preset else current.add(action.preset)
                DownloadQualityPresets.savePresets(appPreferences, current)
                _presets.value = current
            }
            is DownloadPresetsAction.DeletePreset -> {
                val current = _presets.value.filter { it.id != action.id }
                DownloadQualityPresets.savePresets(appPreferences, current)
                _presets.value = current
            }
            is DownloadPresetsAction.ImportPresets -> {
                val imported = DownloadQualityPresets.importFromJson(action.json) ?: return false
                val combined = listOf(DownloadQualityPresets.ORIGINAL) + imported.filter { !it.isOriginal }
                DownloadQualityPresets.savePresets(appPreferences, combined)
                _presets.value = combined
            }
            is DownloadPresetsAction.ResetToDefaults -> {
                DownloadQualityPresets.resetToDefaults(appPreferences)
                _presets.value = DownloadQualityPresets.defaultPresets
            }
        }
        return true
    }
}
