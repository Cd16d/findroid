package dev.jdtech.jellyfin.player.cast

import android.content.Context
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.google.android.gms.cast.framework.SessionManagerListener
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.player.cast.models.CastConnectionState
import dev.jdtech.jellyfin.player.cast.models.Device
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class ChromeCastDevice(val route: MediaRouter.RouteInfo) :
    Device(
        id = route.id,
        name = route.name,
        enabled = route.isEnabled,
        supportsH265 =
            CastDevice.getFromBundle(route.extras)?.modelName?.let { modelName ->
                modelName.contains("Ultra", ignoreCase = true) ||
                    modelName.contains("Google TV", ignoreCase = true)
            } ?: false,
    ) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Device) return false
        return super.equals(other)
    }

    override fun hashCode(): Int = super.hashCode()

    override fun toString(): String =
        "ChromeCastDevice(id=$id, name=$name, enabled=$enabled, supportsH265=$supportsH265)"
}

@Singleton
class CastSessionManagerImpl
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val appPreferences: AppPreferences,
) : CastSessionManager {

    private var _castContext: CastContext? = null
    private val castContext: CastContext?
        get() {
            if (_castContext != null) return _castContext
            return try {
                CastContext.getSharedInstance(context).also { _castContext = it }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get CastContext")
                null
            }
        }

    override val isSupported: Boolean
        get() =
            castContext != null &&
                appPreferences.getValue(appPreferences.castEnabled) &&
                (jellyfinApi.api.baseUrl?.startsWith("https://", ignoreCase = true) ?: false)

    private val _connectionState = MutableStateFlow(CastConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<CastConnectionState> = _connectionState.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<Device>>(emptyList())
    override val availableDevices: StateFlow<List<Device>> = _availableDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<Device?>(null)
    override val connectedDevice: StateFlow<Device?> = _connectedDevice.asStateFlow()

    private val mediaRouter by lazy { MediaRouter.getInstance(context) }
    private val routeSelector by lazy {
        MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
                )
            )
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_VIDEO_PLAYBACK)
            .build()
    }

    private var isInitialized = false

    private val routeCallback =
        object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
                updateRoutes()
            }

            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
                updateRoutes()
                if (_connectedDevice.value?.id == route.id) {
                    updateConnectedDevice()
                }
            }

            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
                updateRoutes()
                if (_connectedDevice.value?.id == route.id) {
                    updateConnectedDevice()
                }
            }

            override fun onRouteSelected(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
                reason: Int,
            ) {
                updateConnectedDevice()
            }

            override fun onRouteUnselected(
                router: MediaRouter,
                route: MediaRouter.RouteInfo,
                reason: Int,
            ) {
                updateConnectedDevice()
            }
        }

    private val sessionManagerListener =
        object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) {
                Timber.d("Starting session")
                _connectionState.value = CastConnectionState.CONNECTING
                updateConnectedDevice()
            }

            override fun onSessionStarted(session: CastSession, sessionId: String) {
                Timber.d("Session started (sessionId: $sessionId)")
                _connectionState.value = CastConnectionState.CONNECTED
                updateConnectedDevice()
                updateDiscovery(0)
            }

            override fun onSessionResuming(session: CastSession, sessionId: String) {
                Timber.d("Resuming session (sessionId: $sessionId)")
                _connectionState.value = CastConnectionState.CONNECTING
                updateConnectedDevice()
                updateDiscovery(MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                Timber.d("Session resumed (wasSuspended: $wasSuspended)")
                _connectionState.value = CastConnectionState.CONNECTED
                updateConnectedDevice()
                updateDiscovery(0)
            }

            override fun onSessionEnding(session: CastSession) {
                Timber.d("Session ending")
                _connectionState.value = CastConnectionState.DISCONNECTED
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                Timber.d("Session ended (error: $error)")
                _connectedDevice.value = null
                _connectionState.value = CastConnectionState.DISCONNECTED
                updateDiscovery()
            }

            override fun onSessionSuspended(session: CastSession, reason: Int) {
                val reasonMessage = CastStatusCodes.getStatusCodeString(reason)
                Timber.d("Session suspended. Reason: $reasonMessage ($reason)")
                _connectionState.value = CastConnectionState.CONNECTING
                updateDiscovery()
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                val errorMessage = CastStatusCodes.getStatusCodeString(error)
                Timber.e("Session start failed: $errorMessage ($error)")
                _connectedDevice.value = null
                _connectionState.value = CastConnectionState.DISCONNECTED
                updateDiscovery()
            }

            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                val errorMessage = CastStatusCodes.getStatusCodeString(error)
                Timber.e("Session resume failed: $errorMessage ($error)")
                _connectedDevice.value = null
                _connectionState.value = CastConnectionState.DISCONNECTED
                updateDiscovery()
            }
        }

    private val castStateListener = CastStateListener { state ->
        _connectionState.value =
            when (state) {
                CastState.CONNECTED -> CastConnectionState.CONNECTED
                CastState.CONNECTING -> CastConnectionState.CONNECTING
                else -> CastConnectionState.DISCONNECTED
            }
        if (state == CastState.CONNECTED || state == CastState.CONNECTING) {
            updateConnectedDevice()
        } else {
            _connectedDevice.value = null
        }
    }

    override fun init() {
        if (isInitialized) return
        val context = castContext ?: return
        try {
            context.addCastStateListener(castStateListener)
            context.sessionManager.addSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java,
            )
            isInitialized = true

            val currentSession = context.sessionManager.currentCastSession
            if (currentSession != null && currentSession.isConnected) {
                _connectionState.value = CastConnectionState.CONNECTED
                updateConnectedDevice()
            } else {
                _connectionState.value =
                    when (context.castState) {
                        CastState.CONNECTED -> CastConnectionState.CONNECTED
                        CastState.CONNECTING -> CastConnectionState.CONNECTING
                        else -> CastConnectionState.DISCONNECTED
                    }
                if (_connectionState.value == CastConnectionState.CONNECTED) {
                    updateConnectedDevice()
                }
            }

            updateDiscovery()
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize CastSessionManager")
        }
    }

    private fun updateConnectedDevice() {
        val route = mediaRouter.selectedRoute
        _connectedDevice.value =
            if (!route.isDefault && route.matchesSelector(routeSelector)) {
                ChromeCastDevice(route)
            } else {
                null
            }
    }

    private fun updateRoutes() {
        val routes =
            mediaRouter.routes.filter { route ->
                // 1. Must match the selector (Video Playback) and not be the default route
                if (route.isDefault || !route.matchesSelector(routeSelector)) return@filter false

                // 2. Filter by device type (excludes single speakers)
                if (route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER) {
                    return@filter false
                }

                // 3. Specific check on Cast capabilities (excludes speaker groups and audio-only
                // devices)
                val castDevice = CastDevice.getFromBundle(route.extras)
                castDevice?.hasCapability(CastDevice.CAPABILITY_VIDEO_OUT) ?: true
            }

        _availableDevices.value = routes.map { route -> ChromeCastDevice(route) }
    }

    override fun updateDiscovery(flags: Int) {
        if (!appPreferences.getValue(appPreferences.castEnabled)) {
            mediaRouter.removeCallback(routeCallback)
            _availableDevices.value = emptyList()
            return
        }

        mediaRouter.removeCallback(routeCallback)
        mediaRouter.addCallback(
            routeSelector,
            routeCallback,
            flags,
        )
        updateRoutes()
    }

    override fun connect(device: Device) {
        val route =
            (device as? ChromeCastDevice)?.route
                ?: mediaRouter.routes.firstOrNull { it.id == device.id }
                ?: return

        if (
            mediaRouter.selectedRoute.id == route.id &&
                castContext?.castState == CastState.CONNECTED
        ) {
            Timber.d("Device already connected: ${device.name}")
            return
        }

        if (castContext?.castState == CastState.CONNECTED) {
            disconnect()
        }

        mediaRouter.selectRoute(route)
    }

    override fun disconnect() {
        try {
            castContext?.sessionManager?.endCurrentSession(true)
        } catch (e: Exception) {
            Timber.e(e, "Error ending Cast session")
        }
        try {
            if (!mediaRouter.selectedRoute.isDefault) {
                mediaRouter.selectRoute(mediaRouter.defaultRoute)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error resetting media router route")
        }
    }

    override fun release() {
        try {
            mediaRouter.removeCallback(routeCallback)
            castContext?.let { context ->
                context.removeCastStateListener(castStateListener)
                context.sessionManager.removeSessionManagerListener(
                    sessionManagerListener,
                    CastSession::class.java,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error releasing CastSessionManager")
        } finally {
            isInitialized = false
        }
    }
}
