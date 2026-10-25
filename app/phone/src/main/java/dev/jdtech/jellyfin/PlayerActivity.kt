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
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
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
    private var currentResizeMode by mutableStateOf(Constants.PlayerResizeMode.FIT)
    private var isOrientationLockedState by mutableStateOf(false)
    private var activePlayerView: PlayerView? = null

    private var savedCustomBrightness: Float = BRIGHTNESS_OVERRIDE_NONE

    private var isPipGestureEnabled: Boolean = true
    private var isGesturesVBEnabled: Boolean = true
    private var isBrightnessRememberEnabled: Boolean = false
    private var savedPlayerBrightness: Float = -1f
    private var preferredOrientation: Constants.PlayerOrientation =
        Constants.PlayerOrientation.SENSOR
    private var preferredOrientationLast: Constants.PlayerOrientation =
        Constants.PlayerOrientation.SENSOR

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

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                updatePipParams()
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
        if (preferredOrientation == Constants.PlayerOrientation.REMEMBER) {
            val state =
                if (isOrientationLockedState) {
                    if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        Constants.PlayerOrientation.SENSOR_PORTRAIT.name.lowercase()
                    } else {
                        Constants.PlayerOrientation.SENSOR_LANDSCAPE.name.lowercase()
                    }
                } else {
                    Constants.PlayerOrientation.SENSOR.name.lowercase()
                }
            appPreferences.setValue(appPreferences.playerOrientationLast, state)
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun applyOrientationPreference() {
        when (preferredOrientation) {
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
                when (preferredOrientationLast) {
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

        isControlsLocked = false
        viewModel.player.addListener(playerListener)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind") ?: ""
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning", false)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    appPreferences.observe(appPreferences.playerPipGesture).collect {
                        isPipGestureEnabled = it
                        updatePipParams()
                    }
                }
                launch {
                    appPreferences.observe(appPreferences.playerGesturesVB).collect {
                        isGesturesVBEnabled = it
                    }
                }
                launch {
                    appPreferences
                        .observe(appPreferences.playerGesturesBrightnessRemember)
                        .collect { isBrightnessRememberEnabled = it }
                }
                launch {
                    appPreferences.observe(appPreferences.playerBrightness).collect {
                        savedPlayerBrightness = it
                        if (
                            isBrightnessRememberEnabled &&
                                savedPlayerBrightness >= 0f &&
                                !isInPipMode
                        ) {
                            val lp = window.attributes
                            lp.screenBrightness = savedPlayerBrightness.coerceIn(0.01f, 1f)
                            window.attributes = lp
                        }
                    }
                }
                launch {
                    appPreferences.observe(appPreferences.playerOrientation).collect {
                        preferredOrientation = Constants.PlayerOrientation.fromValue(it)
                        applyOrientationPreference()
                    }
                }
                launch {
                    appPreferences.observe(appPreferences.playerOrientationLast).collect {
                        preferredOrientationLast = Constants.PlayerOrientation.fromValue(it)
                    }
                }
                launch {
                    appPreferences.observe(appPreferences.playerDefaultResizeMode).collect {
                        currentResizeMode = Constants.PlayerResizeMode.fromValue(it)
                    }
                }
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
                    onPlayerViewAvailable = { playerView ->
                        activePlayerView = playerView
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
            updatePipParams(false)
        }
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
        activePlayerView = null
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
                isPipGestureEnabled &&
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
            when (val keyCode = event.keyCode) {
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
        enableAutoEnter: Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                isPipGestureEnabled &&
                viewModel.player.isPlaying &&
                !isControlsLocked
    ): PictureInPictureParams {
        val videoSize = viewModel.player.videoSize
        val aspectRatio =
            if (videoSize.width > 0 && videoSize.height > 0) {
                Rational(
                    videoSize.width.coerceAtMost((videoSize.height * 2.39f).toInt()),
                    videoSize.height.coerceAtMost((videoSize.width * 2.39f).toInt()),
                )
            } else {
                Rational(16, 9)
            }

        val sourceRectHint = Rect()
        val surfaceView = activePlayerView?.videoSurfaceView
        val targetView =
            if (surfaceView != null && surfaceView.width > 0 && surfaceView.height > 0) {
                surfaceView
            } else {
                activePlayerView ?: window.decorView
            }
        targetView.getGlobalVisibleRect(sourceRectHint)

        val builder = PictureInPictureParams.Builder().setAspectRatio(aspectRatio)

        if (!sourceRectHint.isEmpty) {
            builder.setSourceRectHint(sourceRectHint)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enableAutoEnter)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    fun updatePipParams(isPlaying: Boolean = viewModel.player.isPlaying) {
        if (!isPipSupported) return
        val shouldAutoEnter =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                isPipGestureEnabled &&
                isPlaying &&
                !isControlsLocked
        try {
            setPictureInPictureParams(pipParams(enableAutoEnter = shouldAutoEnter))
        } catch (_: IllegalArgumentException) {}
    }

    private fun pictureInPicture() {
        if (!isPipSupported) return
        try {
            enterPictureInPictureMode(pipParams())
        } catch (_: IllegalArgumentException) {}
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.isInPictureInPictureMode = isInPictureInPictureMode
        isInPipMode = isInPictureInPictureMode

        val lp = window.attributes
        if (isInPictureInPictureMode) {
            if (lp.screenBrightness >= 0f) {
                savedCustomBrightness = lp.screenBrightness
                lp.screenBrightness = BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
            }
        } else {
            if (isGesturesVBEnabled && isBrightnessRememberEnabled) {
                if (savedPlayerBrightness >= 0f) {
                    lp.screenBrightness = savedPlayerBrightness.coerceIn(0.01f, 1f)
                    window.attributes = lp
                }
            } else if (savedCustomBrightness >= 0f) {
                lp.screenBrightness = savedCustomBrightness
                window.attributes = lp
                savedCustomBrightness = BRIGHTNESS_OVERRIDE_NONE
            }
        }
    }
}
