package dev.jdtech.jellyfin

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.PlayerScreen
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch

var isControlsLocked: Boolean = false

@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    val viewModel: PlayerViewModel by viewModels()

    private lateinit var mediaSession: MediaSession
    private var wasPip: Boolean = false

    private var isInPipMode by mutableStateOf(false)
    private var currentResizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var isOrientationLockedState by mutableStateOf(false)

    private fun toggleOrientationLock() {
        if (isOrientationLockedState) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            isOrientationLockedState = false
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            isOrientationLockedState = true
        }
    }

    private val isPipSupported by lazy {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return@lazy false
        }
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager?
        appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        isControlsLocked = false

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind") ?: ""
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning", false)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is PlayerEvents.NavigateBack -> finishPlayback()
                            is PlayerEvents.IsPlayingChanged -> {
                                if (event.isPlaying) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                    )
                                }
                                if (appPreferences.getValue(appPreferences.playerPipGesture)) {
                                    try {
                                        setPictureInPictureParams(pipParams(event.isPlaying))
                                    } catch (_: IllegalArgumentException) {}
                                }
                            }
                        }
                    }
                }
            }
        }

        setContent {
            FindroidTheme {
                PlayerScreen(
                    itemId = itemId,
                    itemKind = itemKind,
                    startFromBeginning = startFromBeginning,
                    onBack = { finishPlayback() },
                    onEnterPip = { pictureInPicture() },
                    isPipSupported = isPipSupported,
                    isInPictureInPictureMode = isInPipMode,
                    resizeMode = currentResizeMode,
                    onResizeModeChange = { currentResizeMode = it },
                    onToggleOrientation = { toggleOrientationLock() },
                    isOrientationLocked = isOrientationLockedState,
                    viewModel = viewModel,
                )
            }
        }
        hideSystemUI()
    }

    override fun onStart() {
        super.onStart()
        mediaSession = MediaSession.Builder(this, viewModel.player).build()
    }

    override fun onResume() {
        super.onResume()

        if (wasPip) {
            wasPip = false
        } else {
            viewModel.player.playWhenReady = viewModel.playWhenReady
        }
        hideSystemUI()
    }

    override fun onPause() {
        super.onPause()

        if (isInPictureInPictureMode) {
            wasPip = true
        } else {
            viewModel.playWhenReady = viewModel.player.playWhenReady
            viewModel.player.playWhenReady = false
            viewModel.updatePlaybackProgress()
        }
    }

    override fun onStop() {
        super.onStop()

        mediaSession.release()

        if (wasPip) {
            finish()
        }
    }

    private fun hideSystemUI() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning", false)

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                appPreferences.getValue(appPreferences.playerPipGesture) &&
                viewModel.player.isPlaying &&
                !isControlsLocked
        ) {
            pictureInPicture()
        }
    }

    private fun toggleOrientation() {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        requestedOrientation =
            if (isPortrait) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
    }

    private fun finishPlayback() {
        viewModel.player.pause()
        finish()
    }

    private fun pipParams(
        enableAutoEnter: Boolean = viewModel.player.isPlaying
    ): PictureInPictureParams {
        val videoSize = viewModel.player.videoSize
        val videoWidth = if (videoSize.width > 0) videoSize.width else 16
        val videoHeight = if (videoSize.height > 0) videoSize.height else 9

        val rawRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val clampedRatio = rawRatio.coerceIn(1f / 2.39f, 2.39f)

        val aspectRatio =
            if (rawRatio > 2.39f) {
                Rational((videoHeight * 2.39f).toInt(), videoHeight)
            } else if (rawRatio < 1f / 2.39f) {
                Rational(videoWidth, (videoWidth * 2.39f).toInt())
            } else {
                Rational(videoWidth, videoHeight)
            }

        val builder = PictureInPictureParams.Builder().setAspectRatio(aspectRatio)

        val decorView = window.decorView
        val viewWidth = decorView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val viewHeight = decorView.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        val viewAspectRatio = viewWidth.toFloat() / viewHeight.toFloat()
        val sourceRect =
            if (viewAspectRatio > clampedRatio) {
                val contentWidth = (viewHeight * clampedRatio).toInt()
                val left = (viewWidth - contentWidth) / 2
                Rect(left, 0, left + contentWidth, viewHeight)
            } else {
                val contentHeight = (viewWidth / clampedRatio).toInt()
                val top = (viewHeight - contentHeight) / 2
                Rect(0, top, viewWidth, top + contentHeight)
            }
        builder.setSourceRectHint(sourceRect)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enableAutoEnter)
        }

        return builder.build()
    }

    private fun pictureInPicture() {
        if (!isPipSupported) return
        currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        try {
            enterPictureInPictureMode(pipParams())
        } catch (_: IllegalArgumentException) {}
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        viewModel.isInPictureInPictureMode = isInPictureInPictureMode
        isInPipMode = isInPictureInPictureMode
    }
}
