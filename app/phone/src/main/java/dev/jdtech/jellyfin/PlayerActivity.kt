package dev.jdtech.jellyfin

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Rational
import android.view.KeyEvent
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.models.VolumeKeyEvent
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.PlayerScreen
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

var isControlsLocked: Boolean = false

@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlayerActivity : AppCompatActivity() {

    @Inject lateinit var appPreferences: AppPreferences

    val viewModel: PlayerViewModel by viewModels()

    private lateinit var mediaSession: MediaSession
    private var wasPip: Boolean = false

    private val volumeKeyEvents = MutableSharedFlow<VolumeKeyEvent>(extraBufferCapacity = 8)
    private var lastVolumeKeyTime = 0L

    private var isInPipMode by mutableStateOf(false)
    private var currentResizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var isOrientationLockedState by mutableStateOf(false)
    private var currentVideoBounds: Rect? = null

    private val playerListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                updatePipParams(isPlaying)
            }
        }

    private fun toggleOrientationLock() {
        if (isOrientationLockedState) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            isOrientationLockedState = false
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            isOrientationLockedState = true
        }
        val preferredOrientation =
            Constants.PlayerOrientation.fromValue(
                appPreferences.getValue(appPreferences.playerOrientation)
            )
        if (preferredOrientation == Constants.PlayerOrientation.REMEMBER) {
            val state =
                if (isOrientationLockedState) {
                    if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        Constants.PlayerOrientation.SENSOR_PORTRAIT.value
                    } else {
                        Constants.PlayerOrientation.SENSOR_LANDSCAPE.value
                    }
                } else {
                    Constants.PlayerOrientation.SENSOR.value
                }
            appPreferences.setValue(appPreferences.playerOrientationLast, state)
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

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_MUSIC

        val defaultResizeMode =
            Constants.PlayerResizeMode.fromValue(
                appPreferences.getValue(appPreferences.playerDefaultResizeMode)
            )
        currentResizeMode =
            when (defaultResizeMode) {
                Constants.PlayerResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                Constants.PlayerResizeMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                Constants.PlayerResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }

        if (appPreferences.getValue(appPreferences.playerGesturesBrightnessRemember)) {
            val savedBrightness = appPreferences.getValue(appPreferences.playerBrightness)
            if (savedBrightness >= 0f) {
                val lp = window.attributes
                lp.screenBrightness = savedBrightness.coerceIn(0.01f, 1f)
                window.attributes = lp
            }
        }

        val playerOrientation =
            Constants.PlayerOrientation.fromValue(
                appPreferences.getValue(appPreferences.playerOrientation)
            )
        when (playerOrientation) {
            Constants.PlayerOrientation.SENSOR_LANDSCAPE -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                isOrientationLockedState = true
            }
            Constants.PlayerOrientation.SENSOR_PORTRAIT -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                isOrientationLockedState = true
            }
            Constants.PlayerOrientation.SENSOR -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                isOrientationLockedState = false
            }
            Constants.PlayerOrientation.REMEMBER -> {
                val playerOrientationLast =
                    Constants.PlayerOrientation.fromValue(
                        appPreferences.getValue(appPreferences.playerOrientationLast)
                    )
                when (playerOrientationLast) {
                    Constants.PlayerOrientation.SENSOR_LANDSCAPE -> {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        isOrientationLockedState = true
                    }
                    Constants.PlayerOrientation.SENSOR_PORTRAIT -> {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        isOrientationLockedState = true
                    }
                    Constants.PlayerOrientation.LOCKED -> {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                        isOrientationLockedState = true
                    }
                    Constants.PlayerOrientation.SENSOR,
                    Constants.PlayerOrientation.REMEMBER -> {
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        isOrientationLockedState = false
                    }
                }
            }
            Constants.PlayerOrientation.LOCKED -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
                isOrientationLockedState = true
            }
        }

        isControlsLocked = false
        viewModel.player.addListener(playerListener)

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
                                updatePipParams(event.isPlaying)
                            }
                        }
                    }
                }
            }
        }

        setContent {
            FindroidTheme(darkTheme = true) {
                PlayerScreen(
                    itemId = itemId,
                    itemKind = itemKind,
                    startFromBeginning = startFromBeginning,
                    onBack = { finishPlayback() },
                    onEnterPip = { pictureInPicture() },
                    onVideoBoundsChanged = { rect ->
                        currentVideoBounds = rect
                        updatePipParams()
                    },
                    isPipSupported = isPipSupported,
                    isInPictureInPictureMode = isInPipMode,
                    resizeMode = currentResizeMode,
                    onResizeModeChange = { currentResizeMode = it },
                    onToggleOrientation = { toggleOrientationLock() },
                    isOrientationLocked = isOrientationLockedState,
                    volumeKeyEvents = volumeKeyEvents,
                    appPreferences = appPreferences,
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
        updatePipParams()
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
        updatePipParams(false)
    }

    override fun onStop() {
        super.onStop()

        mediaSession.release()

        if (wasPip) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.player.removeListener(playerListener)
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

    private fun finishPlayback() {
        viewModel.player.pause()
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isInPipMode) {
            val action = event.action
            val keyCode = event.keyCode
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        val isRepeat = event.repeatCount > 0
                        val now = SystemClock.uptimeMillis()
                        if (!isRepeat || now - lastVolumeKeyTime >= 50L) {
                            lastVolumeKeyTime = now
                            volumeKeyEvents.tryEmit(
                                VolumeKeyEvent(
                                    isUp = keyCode == KeyEvent.KEYCODE_VOLUME_UP,
                                    isRepeat = isRepeat,
                                )
                            )
                        }
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
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

        val videoBounds = currentVideoBounds
        val sourceRect =
            if (videoBounds != null && videoBounds.width() > 0 && videoBounds.height() > 0) {
                val boundsWidth = videoBounds.width()
                val boundsHeight = videoBounds.height()
                val boundsAspectRatio = boundsWidth.toFloat() / boundsHeight.toFloat()
                if (boundsAspectRatio > clampedRatio) {
                    val contentWidth = (boundsHeight * clampedRatio).toInt()
                    val left = videoBounds.left + (boundsWidth - contentWidth) / 2
                    Rect(left, videoBounds.top, left + contentWidth, videoBounds.bottom)
                } else {
                    val contentHeight = (boundsWidth / clampedRatio).toInt()
                    val top = videoBounds.top + (boundsHeight - contentHeight) / 2
                    Rect(videoBounds.left, top, videoBounds.right, top + contentHeight)
                }
            } else {
                val viewAspectRatio = viewWidth.toFloat() / viewHeight.toFloat()
                if (viewAspectRatio > clampedRatio) {
                    val contentWidth = (viewHeight * clampedRatio).toInt()
                    val left = (viewWidth - contentWidth) / 2
                    Rect(left, 0, left + contentWidth, viewHeight)
                } else {
                    val contentHeight = (viewWidth / clampedRatio).toInt()
                    val top = (viewHeight - contentHeight) / 2
                    Rect(0, top, viewWidth, top + contentHeight)
                }
            }
        builder.setSourceRectHint(sourceRect)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enableAutoEnter)
        }

        return builder.build()
    }

    fun updatePipParams(isPlaying: Boolean = viewModel.player.isPlaying) {
        if (!isPipSupported) return
        val shouldAutoEnter =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                appPreferences.getValue(appPreferences.playerPipGesture) &&
                isPlaying &&
                !isControlsLocked
        try {
            setPictureInPictureParams(pipParams(enableAutoEnter = shouldAutoEnter))
        } catch (_: IllegalArgumentException) {}
    }

    private fun pictureInPicture() {
        if (!isPipSupported) return
        try {
            enterPictureInPictureMode(pipParams(enableAutoEnter = false))
        } catch (_: IllegalArgumentException) {}
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.isInPictureInPictureMode = isInPictureInPictureMode
        isInPipMode = isInPictureInPictureMode
    }
}
