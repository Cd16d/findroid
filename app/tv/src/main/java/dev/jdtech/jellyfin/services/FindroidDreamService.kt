package dev.jdtech.jellyfin.services

import android.service.dreams.DreamService
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.BaseApplication
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.ui.components.AppScreensaver
import dev.jdtech.jellyfin.ui.components.ScreensaverSlide
import dev.jdtech.jellyfin.ui.components.toScreensaverSlide
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class FindroidDreamService : DreamService() {
    @Inject lateinit var repository: JellyfinRepository

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Only activate if the user is currently using the Findroid application
        if (!BaseApplication.isAppInForeground) {
            finish()
            return
        }

        isInteractive = false
        isFullscreen = true

        val composeView =
            ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    FindroidTheme {
                        var slides by remember {
                            mutableStateOf<List<ScreensaverSlide>>(emptyList())
                        }
                        LaunchedEffect(Unit) {
                            try {
                                val items = repository.getSuggestions(forceRefresh = false)
                                slides = items.map { it.toScreensaverSlide() }
                            } catch (e: Exception) {
                                Timber.e(
                                    e,
                                    "Error loading screensaver suggestions for DreamService",
                                )
                            }
                        }
                        if (slides.isNotEmpty()) {
                            AppScreensaver(slides = slides)
                        }
                    }
                }
            }
        setContentView(composeView)
    }
}
