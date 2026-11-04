package dev.jdtech.jellyfin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.player.cast.CastSessionManager
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.viewmodels.MainViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject lateinit var castSessionManager: CastSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        castSessionManager.init()

        enableEdgeToEdge()

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()

            FindroidTheme(dynamicColor = state.isDynamicColors) {
                if (!state.isLoading) {
                    CompositionLocalProvider(
                        LocalOfflineMode provides state.isOfflineMode,
                        LocalVolumeKeyEvents provides viewModel.volumeKeyEvents,
                    ) {
                        NavigationRoot(
                            hasServers = state.hasServers,
                            hasCurrentServer = state.hasCurrentServer,
                            hasCurrentUser = state.hasCurrentUser,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        castSessionManager.release()
    }

    private var lastVolumeDownTime = 0L

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (viewModel.isCastPlayerOpen.value) {
            val action = event.action
            val keyCode = event.keyCode
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (action == android.view.KeyEvent.ACTION_DOWN) {
                        val isRepeat = event.repeatCount > 0
                        val now = android.os.SystemClock.uptimeMillis()
                        if (!isRepeat || now - lastVolumeDownTime >= 50L) {
                            lastVolumeDownTime = now
                            viewModel.emitVolumeKeyEvent(isUp = true, isRepeat = isRepeat)
                        }
                    }
                    return true
                }
                android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (action == android.view.KeyEvent.ACTION_DOWN) {
                        val isRepeat = event.repeatCount > 0
                        val now = android.os.SystemClock.uptimeMillis()
                        if (!isRepeat || now - lastVolumeDownTime >= 50L) {
                            lastVolumeDownTime = now
                            viewModel.emitVolumeKeyEvent(isUp = false, isRepeat = isRepeat)
                        }
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
