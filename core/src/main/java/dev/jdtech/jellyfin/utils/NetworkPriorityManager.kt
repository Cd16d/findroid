package dev.jdtech.jellyfin.utils

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks active UI network requests (Jellyfin REST API calls and Coil image downloads)
 * so that background media downloads can dynamically yield bandwidth, socket time, and CPU priority.
 */
@Singleton
class NetworkPriorityManager @Inject constructor() {

    private val activeUiRequests = AtomicInteger(0)
    @Volatile
    private var lastUiActivityTimestamp = 0L

    /**
     * Called when a UI network request begins.
     */
    fun onUiRequestStarted() {
        activeUiRequests.incrementAndGet()
        lastUiActivityTimestamp = SystemClock.elapsedRealtime()
    }

    /**
     * Called when a UI network request completes (success, failure, or cancellation).
     */
    fun onUiRequestFinished() {
        activeUiRequests.decrementAndGet()
        lastUiActivityTimestamp = SystemClock.elapsedRealtime()
    }

    /**
     * Returns true if there are currently in-flight UI network requests, or if a UI network
     * request completed within the last [GRACE_PERIOD_MS] (to prevent high-frequency oscillation
     * during multi-image or paginated list loads).
     */
    val isUiActive: Boolean
        get() {
            if (activeUiRequests.get() > 0) return true
            val elapsed = SystemClock.elapsedRealtime() - lastUiActivityTimestamp
            return elapsed in 0L..GRACE_PERIOD_MS
        }

    companion object {
        /** Grace period in milliseconds after the last UI request ends before downloads resume full-speed. */
        private const val GRACE_PERIOD_MS = 350L
    }
}
