package dev.jdtech.jellyfin.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.ui.components.ScreensaverSlide
import dev.jdtech.jellyfin.ui.components.toScreensaverSlide
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class ScreensaverViewModel @Inject constructor(private val repository: JellyfinRepository) :
    ViewModel() {
    private val _slides = MutableStateFlow<List<ScreensaverSlide>>(emptyList())
    val slides = _slides.asStateFlow()

    fun loadSlides() {
        if (_slides.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val suggestions = repository.getSuggestions(forceRefresh = false)
                _slides.value = suggestions.map { it.toScreensaverSlide() }
            } catch (e: Exception) {
                Timber.e(e, "Error loading screensaver suggestions")
            }
        }
    }
}
