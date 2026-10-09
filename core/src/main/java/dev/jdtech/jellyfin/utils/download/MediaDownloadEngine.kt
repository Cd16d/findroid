package dev.jdtech.jellyfin.utils.download

import android.os.Process
import android.os.StatFs
import android.os.SystemClock
import dev.jdtech.jellyfin.di.DownloadHttpClient
import dev.jdtech.jellyfin.utils.NetworkConnectivity
import dev.jdtech.jellyfin.utils.NetworkPriorityManager
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import timber.log.Timber

// Pure helpers (file-top-level so they are unit-testable) ----------------------

/**
 * Decides whether a transfer should be paused given the current network state and the user's
 * metered/roaming allowances. Offline always pauses; metered/roaming pause when the corresponding
 * allowance flag is false.
 */
internal fun shouldPauseTransfer(
    isOnline: Boolean,
    isMetered: Boolean,
    isRoaming: Boolean,
    allowMetered: Boolean,
    allowRoaming: Boolean,
): Boolean =
    when {
        !isOnline -> true
        isMetered && !allowMetered -> true
        isRoaming && !allowRoaming -> true
        else -> false
    }

/** Plan returned by [resolveResumePlan] describing where to seek and what the total size is. */
internal data class ResumePlan(
    val startByte: Long,
    val totalBytes: Long,
    /** True when the file is already complete; the caller should treat it as SUCCESSFUL. */
    val complete: Boolean,
)

/**
 * Maps an HTTP response code to a resume plan.
 *
 * - 206 Partial Content: resume at [existingLength]; total derived from Content-Range header or as
 *   existingLength+contentLength.
 * - 200 OK: server ignored the Range header (e.g. transcode/DV case) - truncate and restart.
 * - 416 Range Not Satisfiable: file is already complete.
 * - Other codes: caller is expected to throw before calling; this is a defensive path.
 */
internal fun resolveResumePlan(
    code: Int,
    existingLength: Long,
    contentLength: Long,
    contentRangeTotal: Long,
): ResumePlan =
    when (code) {
        206 -> {
            val total =
                if (contentRangeTotal > 0) {
                    contentRangeTotal
                } else if (contentLength >= 0) {
                    existingLength + contentLength
                } else {
                    -1L
                }
            val complete = total > 0 && existingLength >= total
            ResumePlan(startByte = existingLength, totalBytes = total, complete = complete)
        }
        200 -> ResumePlan(startByte = 0L, totalBytes = contentLength, complete = false)
        416 -> ResumePlan(startByte = existingLength, totalBytes = existingLength, complete = true)
        else -> ResumePlan(startByte = 0L, totalBytes = contentLength, complete = false)
    }

/**
 * Parses the numeric total from a Content-Range response header.
 *
 * - "bytes 200-1023/1024" returns 1024
 * - "bytes 200-1024/1024" returns 1024
 * - wildcard total ("bytes 0-0/&#42;") or null or malformed returns -1
 */
internal fun parseContentRangeTotal(header: String?): Long {
    if (header == null) return -1L
    // Format: bytes <start>-<end>/<total>  or  bytes <start>-<end>/asterisk
    val slashIndex = header.lastIndexOf('/')
    if (slashIndex < 0) return -1L
    val totalStr = header.substring(slashIndex + 1).trim()
    if (totalStr == "*") return -1L
    return totalStr.toLongOrNull() ?: -1L
}

/** Returns the temporary .part file corresponding to [destFile]. */
internal fun getPartFile(destFile: File): File =
    if (destFile.name.endsWith(".part")) {
        destFile
    } else {
        File(destFile.parentFile ?: File("."), "${destFile.name}.part")
    }

/**
 * Atomically moves [source] to [dest], falling back to rename or stream copy-delete if atomic move
 * is unsupported on the underlying filesystem.
 */
internal fun atomicMove(source: File, dest: File) {
    if (!source.exists()) {
        throw IOException("Source file ${source.absolutePath} does not exist for atomic move")
    }
    dest.parentFile?.mkdirs()
    try {
        Files.move(
            source.toPath(),
            dest.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (e: Exception) {
        Timber.d(e, "ATOMIC_MOVE failed or unsupported, falling back to rename/copy")
        if (dest.exists()) {
            dest.delete()
        }
        if (!source.renameTo(dest)) {
            source.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            source.delete()
        }
    }
    if (!dest.exists()) {
        throw IOException("Failed to move ${source.absolutePath} to ${dest.absolutePath}")
    }
}

/**
 * Exception thrown when available storage space on disk is less than the required download size.
 */
internal class InsufficientDiskSpaceException(val required: Long, val available: Long) :
    IOException("Insufficient disk space: required $required bytes, available $available bytes")

// Private exception used for mid-transfer pause ---------------------------------

private class PausedMidTransfer : Exception()

// Engine ------------------------------------------------------------------------

/**
 * Singleton OkHttp-backed download engine. Manages a registry of in-flight (and terminal) download
 * tasks keyed by Long id. Each task runs in the engine's own [CoroutineScope].
 *
 * Thread-safety: the registry is a [ConcurrentHashMap]. Individual task state fields are
 *
 * @Volatile and written only by the task's own coroutine. Reads from outside are
 *   eventually-consistent snapshots, acceptable for progress polling.
 */
@Singleton
class MediaDownloadEngine
@Inject
constructor(
    @DownloadHttpClient private val client: OkHttpClient,
    private val connectivity: NetworkConnectivity,
    private val priorityManager: NetworkPriorityManager,
) {
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // Secondary constructor for testing with custom dispatcher
    constructor(
        client: OkHttpClient,
        connectivity: NetworkConnectivity,
        priorityManager: NetworkPriorityManager,
        ioDispatcher: CoroutineDispatcher,
    ) : this(client, connectivity, priorityManager) {
        this.ioDispatcher = ioDispatcher
    }

    // Public API types ---------------------------------------------------------

    data class Snapshot(
        val status: DownloadStatus,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSecond: Long = 0L,
    )

    data class Request(
        val id: Long,
        val url: String,
        val destFile: File,
        val allowMetered: Boolean,
        val allowRoaming: Boolean,
        val estimatedTotalBytes: Long = -1L,
    )

    // Engine internals ---------------------------------------------------------

    private val scope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

    /** Per-task mutable state held in the registry. */
    private inner class TaskState(val request: Request) {
        @Volatile var status: DownloadStatus = DownloadStatus.PENDING
        @Volatile var bytesDownloaded: Long = 0L
        @Volatile var totalBytes: Long = request.estimatedTotalBytes
        @Volatile var speedBytesPerSecond: Long = 0L
        @Volatile var isUserPaused: Boolean = false
        @Volatile var etag: String? = null
        @Volatile var lastModified: String? = null
        var job: Job? = null

        /**
         * The in-flight OkHttp call. Held so [cancel] can interrupt the blocking `source.read()`
         * immediately — `job.cancel()` alone cannot, since the transfer loop has no suspension
         * points while reading. Without this a cancelled download keeps writing for up to the read
         * timeout, racing the file delete in deleteItem.
         */
        @Volatile var call: Call? = null
    }

    private val registry = ConcurrentHashMap<Long, TaskState>()

    // Public methods -----------------------------------------------------------

    /**
     * Start or resume the transfer for [request.id].
     *
     * Idempotent while the task is ACTIVE (PENDING / RUNNING / PAUSED). If the existing task is
     * TERMINAL (SUCCESSFUL / FAILED) or absent, (re)starts, picking up from the partial file on
     * disk.
     */
    @Synchronized
    fun start(request: Request) {
        val existing = registry[request.id]
        if (existing != null) {
            val status = existing.status
            if (
                status == DownloadStatus.PENDING ||
                    status == DownloadStatus.RUNNING ||
                    status == DownloadStatus.PAUSED
            ) {
                // If previously user-paused, unpause
                if (existing.isUserPaused) {
                    existing.isUserPaused = false
                }
                if (existing.job?.isActive != true) {
                    existing.job = scope.launch { runTask(existing) }
                }
                return
            }
            // Terminal: cancel/clear old call + job before replacing.
            existing.call?.cancel()
            existing.job?.cancel()
        }

        val state = TaskState(request)
        registry[request.id] = state
        state.job = scope.launch { runTask(state) }
    }

    /** Explicitly pause the in-flight transfer for [id]. */
    @Synchronized
    fun pause(id: Long) {
        val state = registry[id] ?: return
        state.isUserPaused = true
        state.status = DownloadStatus.PAUSED
        state.speedBytesPerSecond = 0L
        state.call?.cancel()
    }

    /** Resume a previously user-paused download for [id]. */
    @Synchronized
    fun resume(id: Long) {
        val state = registry[id] ?: return
        state.isUserPaused = false
        state.status = DownloadStatus.PENDING
        if (state.job?.isActive != true) {
            state.job = scope.launch { runTask(state) }
        }
    }

    /**
     * Abort the in-flight transfer for [id] and remove it from the registry. If [deletePartialFile]
     * is true, also deletes the temporary .part file from disk.
     */
    @Synchronized
    fun cancel(id: Long, deletePartialFile: Boolean = false) {
        val state = registry.remove(id)
        // Cancel the OkHttp call first so a blocking read unblocks immediately, then the job.
        state?.call?.cancel()
        state?.job?.cancel()
        if (deletePartialFile && state != null) {
            deletePartialFile(state.request.destFile)
        }
    }

    /** Deletes any partial .part file associated with [destFile]. */
    fun deletePartialFile(destFile: File) {
        val part = getPartFile(destFile)
        if (part.exists()) {
            part.delete()
        }
    }

    /** Returns a point-in-time [Snapshot] for [id], or null if the engine has no record. */
    fun snapshot(id: Long): Snapshot? {
        val state = registry[id] ?: return null
        return Snapshot(
            status = state.status,
            bytesDownloaded = state.bytesDownloaded,
            totalBytes = state.totalBytes,
            speedBytesPerSecond =
                if (state.status == DownloadStatus.RUNNING) state.speedBytesPerSecond else 0L,
        )
    }

    /** Returns snapshots for all [ids] that the engine has records for. Missing ids are absent. */
    fun snapshots(ids: List<Long>): Map<Long, Snapshot> {
        return ids.mapNotNull { id -> snapshot(id)?.let { id to it } }.toMap()
    }

    /** Returns the ids of all tasks currently in the registry (active and terminal). */
    fun liveTaskIds(): Set<Long> = registry.keys.toSet()

    /**
     * Returns the absolute paths of all destination and temporary .part files for tasks currently
     * in the registry. Used as a race-guard in orphan sweeps: any path in this set must not be
     * deleted even if it is not in the DB yet.
     */
    fun liveTaskPaths(): Set<String> =
        registry.values
            .flatMap {
                val dest = it.request.destFile
                listOf(dest.absolutePath, getPartFile(dest).absolutePath)
            }
            .toSet()

    /** Cancels all active tasks and clears the registry. Useful for testing and teardown. */
    @Synchronized
    fun shutdown() {
        for ((_, state) in registry) {
            state.call?.cancel()
            state.job?.cancel()
        }
        registry.clear()
    }

    // Transfer loop ------------------------------------------------------------

    /** True when the current network state means the transfer for [req] must wait. */
    private fun pausedForNetwork(req: Request): Boolean =
        shouldPauseTransfer(
            isOnline = connectivity.isOnline(),
            isMetered = connectivity.isMetered(),
            isRoaming = connectivity.isRoaming(),
            allowMetered = req.allowMetered,
            allowRoaming = req.allowRoaming,
        )

    private suspend fun runTask(state: TaskState) {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        } catch (_: Throwable) {}
        state.status = DownloadStatus.PENDING
        val req = state.request
        var consecutiveTimeouts = 0

        while (true) {
            if (state.isUserPaused) {
                state.status = DownloadStatus.PAUSED
                state.speedBytesPerSecond = 0L
                delay(1_000)
                continue
            }
            if (pausedForNetwork(req)) {
                state.status = DownloadStatus.PAUSED
                state.speedBytesPerSecond = 0L
                delay(2_000)
                continue
            }

            state.status = DownloadStatus.RUNNING
            val bytesBefore = state.bytesDownloaded
            try {
                httpTransfer(state)
                state.status = DownloadStatus.SUCCESSFUL
                state.speedBytesPerSecond = 0L
                return
            } catch (e: CancellationException) {
                state.speedBytesPerSecond = 0L
                if (state.isUserPaused) {
                    state.status = DownloadStatus.PAUSED
                    delay(1_000)
                    continue
                }
                throw e
            } catch (e: PausedMidTransfer) {
                state.speedBytesPerSecond = 0L
                // Network became metered/roaming mid-transfer or paused by user; loop back to top
                // to re-evaluate.
                continue
            } catch (e: InsufficientDiskSpaceException) {
                state.speedBytesPerSecond = 0L
                Timber.w(e, "Download failed for id=${req.id} due to insufficient storage")
                state.status = DownloadStatus.FAILED
                return
            } catch (e: SocketTimeoutException) {
                state.speedBytesPerSecond = 0L
                if (state.isUserPaused) {
                    state.status = DownloadStatus.PAUSED
                    delay(1_000)
                    continue
                }
                if (state.bytesDownloaded > bytesBefore) {
                    consecutiveTimeouts = 0
                }
                consecutiveTimeouts++
                if (consecutiveTimeouts <= 3) {
                    Timber.w(
                        e,
                        "Socket timeout for id=${req.id} (attempt $consecutiveTimeouts/3); retrying with backoff",
                    )
                    state.status = DownloadStatus.PAUSED
                    delay(1_000L * consecutiveTimeouts)
                    continue
                } else {
                    Timber.w(
                        e,
                        "Socket timeout for id=${req.id} exceeded max retries; failing transfer",
                    )
                    state.status = DownloadStatus.FAILED
                    return
                }
            } catch (e: IOException) {
                state.speedBytesPerSecond = 0L
                if (state.isUserPaused) {
                    Timber.i("Download id=${req.id} paused by user; holding in loop")
                    state.status = DownloadStatus.PAUSED
                    delay(1_000)
                    continue
                }
                // Connectivity dropping (or turning metered/roaming-disallowed) under an
                // active read surfaces here as an IOException before any mid-transfer
                // checkpoint is hit. That is NOT a real failure — DownloadManager used to
                // auto-pause on network loss. Pause and loop so we hold (alive) until the
                // network returns, then resume from the partial via Range, instead of dying
                // and falling to the queue's slow 30s/2m/10m backoff retry.
                if (pausedForNetwork(req)) {
                    Timber.i(
                        "Download id=${req.id} interrupted by connectivity loss; pausing to resume"
                    )
                    continue
                }
                // The socket error can race ahead of ConnectivityManager updating its state,
                // so isOnline() may still report true for a moment after Wi-Fi drops. Give it
                // a brief grace window and re-check before treating this as a real failure.
                state.status = DownloadStatus.PAUSED
                delay(1_500)
                if (pausedForNetwork(req)) {
                    Timber.i("Download id=${req.id} paused after connectivity dropped; will resume")
                    continue
                }
                Timber.w(e, "Download failed for id=${req.id}; partial file kept for resume")
                state.status = DownloadStatus.FAILED
                return
            } catch (e: Exception) {
                state.speedBytesPerSecond = 0L
                if (state.isUserPaused) {
                    state.status = DownloadStatus.PAUSED
                    delay(1_000)
                    continue
                }
                Timber.e(e, "Unexpected download error for id=${req.id}")
                state.status = DownloadStatus.FAILED
                return
            }
        }
    }

    private fun checkAvailableDiskSpace(file: File, requiredBytes: Long) {
        val parentDir = file.parentFile ?: File(".")
        parentDir.mkdirs()
        try {
            val stat = StatFs(parentDir.absolutePath)
            val available = stat.availableBytes
            if (available in 0 until requiredBytes) {
                throw InsufficientDiskSpaceException(
                    required = requiredBytes,
                    available = available,
                )
            }
        } catch (e: InsufficientDiskSpaceException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "StatFs check failed for path=${parentDir.absolutePath}")
        }
    }

    /**
     * Executes the actual HTTP byte transfer for [state]. Downloads into a temporary `.part` file,
     * verifies integrity upon stream completion, and atomically moves to `destFile`. Throws
     * [PausedMidTransfer] if the network becomes restricted mid-stream (caller loops back). Throws
     * [IOException] on HTTP error or I/O failure. On exception the partial file is left on disk for
     * future resume.
     */
    @Throws(IOException::class, PausedMidTransfer::class)
    private suspend fun httpTransfer(state: TaskState) {
        val req = state.request
        val partFile = getPartFile(req.destFile)
        req.destFile.parentFile?.mkdirs()

        // If destFile exists but partFile doesn't, migrate destFile to partFile to resume
        if (partFile != req.destFile && !partFile.exists() && req.destFile.exists()) {
            if (!req.destFile.renameTo(partFile)) {
                Timber.w("Failed to rename partial destFile to partFile for id=${req.id}")
            }
        }

        val existingLen = if (partFile.exists()) partFile.length() else 0L

        val httpRequest =
            okhttp3.Request.Builder()
                .url(req.url)
                .apply {
                    if (existingLen > 0L) {
                        header("Range", "bytes=$existingLen-")
                        val validator = state.etag ?: state.lastModified
                        if (!validator.isNullOrBlank()) {
                            header("If-Range", validator)
                        }
                    }
                }
                .build()

        val call = client.newCall(httpRequest)
        // Publish the call atomically against cancel() (same monitor). If cancel() already
        // removed this task, bail before the blocking execute() — otherwise execute() would
        // run an uninterruptible network transfer to completion as an orphan. If we publish
        // first, a later cancel() sees the call and cancels it.
        synchronized(this) {
            if (!registry.containsKey(req.id)) {
                throw CancellationException("Download ${req.id} was cancelled before start")
            }
            state.call = call
        }

        // Ensure that any coroutine cancellation interrupts the blocking OkHttp socket immediately
        val cancellable = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }

        try {
            val response = call.execute()
            response.use { resp ->
                currentCoroutineContext().ensureActive()
                val code = resp.code
                val contentRangeTotal = parseContentRangeTotal(resp.header("Content-Range"))
                val body = resp.body
                val contentLength = body.contentLength()
                val etag = resp.header("ETag")
                val lastModified = resp.header("Last-Modified")
                if (!etag.isNullOrBlank()) {
                    state.etag = etag
                }
                if (!lastModified.isNullOrBlank()) {
                    state.lastModified = lastModified
                }

                when (code) {
                    200,
                    206,
                    416 -> Unit // handled below
                    else -> throw IOException("HTTP $code for download id=${req.id}")
                }

                val plan = resolveResumePlan(code, existingLen, contentLength, contentRangeTotal)

                if (plan.complete) {
                    // 416 or existingLength reached total: file is already complete.
                    if (partFile != req.destFile && partFile.exists()) {
                        atomicMove(partFile, req.destFile)
                    }
                    state.bytesDownloaded = plan.startByte
                    state.totalBytes = plan.totalBytes
                    return
                }

                // Pre-flight disk check against remaining download bytes
                val bytesNeeded =
                    if (contentLength > 0L) {
                        contentLength
                    } else if (plan.totalBytes > plan.startByte) {
                        plan.totalBytes - plan.startByte
                    } else {
                        -1L
                    }
                if (bytesNeeded > 0L) {
                    checkAvailableDiskSpace(partFile, bytesNeeded)
                }

                // For a 200 response (server ignored Range / restart), truncate the file before
                // writing.
                if (plan.startByte == 0L && partFile.exists()) {
                    RandomAccessFile(partFile, "rw").use { it.setLength(0L) }
                }

                state.totalBytes =
                    if (plan.totalBytes > 0L) plan.totalBytes else req.estimatedTotalBytes

                Timber.i(
                    "Starting HTTP transfer for id=${req.id}: startByte=${plan.startByte}, totalBytes=${state.totalBytes}, url=${req.url.take(80)}..."
                )

                body.use { responseBody ->
                    val source = responseBody.source()
                    RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(plan.startByte)
                        state.bytesDownloaded = plan.startByte

                        val buf = ByteArray(128 * 1024) // 128 KiB chunks optimal for Android I/O
                        var bytesSinceLastMeteredCheck = 0L
                        val meteredCheckInterval = 2L * 1024 * 1024 // 2 MiB

                        var lastSpeedTimestamp = SystemClock.elapsedRealtime()
                        var bytesSinceLastSpeedSample = 0L
                        var smoothedSpeed = 0.0
                        val speedEmaAlpha = 0.3

                        while (currentCoroutineContext().isActive) {
                            if (state.isUserPaused) {
                                throw PausedMidTransfer()
                            }
                            if (priorityManager.isUiActive) {
                                // Yield bandwidth and CPU to UI requests (metadata JSON, Coil
                                // images)
                                delay(40)
                            }
                            currentCoroutineContext().ensureActive()
                            val n = source.read(buf)
                            if (n == -1) break
                            currentCoroutineContext().ensureActive()

                            raf.write(buf, 0, n)
                            state.bytesDownloaded += n
                            bytesSinceLastMeteredCheck += n
                            bytesSinceLastSpeedSample += n

                            // Calculate speed with Exponential Moving Average (EMA)
                            val now = SystemClock.elapsedRealtime()
                            val elapsedMs = now - lastSpeedTimestamp
                            if (elapsedMs >= 500L) {
                                val instantaneousSpeed =
                                    (bytesSinceLastSpeedSample * 1000.0) / elapsedMs
                                smoothedSpeed =
                                    if (smoothedSpeed <= 0.0) {
                                        instantaneousSpeed
                                    } else {
                                        speedEmaAlpha * instantaneousSpeed +
                                            (1.0 - speedEmaAlpha) * smoothedSpeed
                                    }
                                state.speedBytesPerSecond = smoothedSpeed.toLong().coerceAtLeast(0L)
                                bytesSinceLastSpeedSample = 0L
                                lastSpeedTimestamp = now
                            }

                            if (bytesSinceLastMeteredCheck >= meteredCheckInterval) {
                                bytesSinceLastMeteredCheck = 0L
                                if (pausedForNetwork(req)) {
                                    // Keep partial; outer loop will re-evaluate and set PAUSED.
                                    throw PausedMidTransfer()
                                }
                            }
                        }
                        currentCoroutineContext().ensureActive()
                    }
                }

                // Verify completed file integrity before atomic promotion
                if (plan.totalBytes > 0L && partFile.length() != plan.totalBytes) {
                    throw IOException(
                        "Downloaded size mismatch for id=${req.id}: expected ${plan.totalBytes} bytes, got ${partFile.length()} bytes"
                    )
                }
                if (partFile.length() <= 0L && plan.totalBytes != 0L) {
                    throw IOException("Downloaded file is empty for id=${req.id}")
                }

                // Promotion: atomic rename from temp .part to final destination file
                if (partFile != req.destFile) {
                    atomicMove(partFile, req.destFile)
                }

                Timber.i(
                    "HTTP transfer finished successfully for id=${req.id}, size=${req.destFile.length()} bytes"
                )
            }
        } finally {
            cancellable.dispose()
            synchronized(this) {
                if (state.call === call) {
                    state.call = null
                }
            }
            state.speedBytesPerSecond = 0L
        }
    }
}
