package dev.jdtech.jellyfin.utils

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

/**
 * Tracks active UI network requests (Jellyfin REST API calls and Coil image downloads) so that
 * background media downloads can dynamically yield bandwidth, socket time, and CPU priority.
 */
@Singleton
class NetworkPriorityManager @Inject constructor() {

    private val activeUiRequests = AtomicInteger(0)
    private val lastUiActivityTimestamp = AtomicLong(0L)

    /** Number of UI network requests currently in-flight. */
    val activeUiRequestsCount: Int
        get() = activeUiRequests.get()

    /** Called when a UI network request begins. */
    fun onUiRequestStarted() {
        activeUiRequests.incrementAndGet()
        val now = SystemClock.elapsedRealtime()
        lastUiActivityTimestamp.updateAndGet { current -> maxOf(current, now) }
    }

    /** Called when a UI network request completes (success, failure, or cancellation). */
    fun onUiRequestFinished() {
        // Record timestamp before decrementing request count to avoid a race condition
        // where a concurrent reader observes zero active requests with a stale timestamp.
        val now = SystemClock.elapsedRealtime()
        lastUiActivityTimestamp.updateAndGet { current -> maxOf(current, now) }
        activeUiRequests.updateAndGet { maxOf(0, it - 1) }
    }

    /**
     * Returns true if there are currently in-flight UI network requests, or if a UI network request
     * completed within the last [GRACE_PERIOD_MS] (to prevent high-frequency oscillation during
     * multi-image or paginated list loads).
     */
    val isUiActive: Boolean
        get() {
            if (activeUiRequests.get() > 0) return true
            val lastTimestamp = lastUiActivityTimestamp.get()
            if (lastTimestamp <= 0L) return false
            val elapsed = SystemClock.elapsedRealtime() - lastTimestamp
            return elapsed in 0L..GRACE_PERIOD_MS
        }

    /**
     * Cooperatively suspends execution for [delayMs] if UI network activity is ongoing or within
     * [GRACE_PERIOD_MS], yielding network bandwidth and CPU cycles to foreground tasks.
     *
     * @param delayMs Duration to suspend in milliseconds. Defaults to [DEFAULT_PACING_DELAY_MS].
     */
    suspend fun paceDownloadIfNeeded(delayMs: Long = DEFAULT_PACING_DELAY_MS) {
        if (isUiActive) {
            delay(delayMs)
        }
    }

    /** Resets the tracker state to idle. Useful for testing or account/session switches. */
    fun reset() {
        activeUiRequests.set(0)
        lastUiActivityTimestamp.set(0L)
    }

    companion object {
        /**
         * Grace period in milliseconds after the last UI request ends before downloads resume
         * full-speed.
         */
        const val GRACE_PERIOD_MS = 350L

        /**
         * Default pacing delay in milliseconds to suspend download threads while UI activity is
         * detected.
         */
        const val DEFAULT_PACING_DELAY_MS = 40L
    }
}
