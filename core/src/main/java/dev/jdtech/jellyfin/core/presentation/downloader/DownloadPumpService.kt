package dev.jdtech.jellyfin.core.presentation.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.core.R as CoreR
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

import android.text.format.Formatter
import androidx.core.content.ContextCompat
import dev.jdtech.jellyfin.models.FindroidEpisode

/**
 * Foreground service that keeps the app process alive while the DownloadQueue has
 * work to do. The OkHttp download engine runs in-process, so closing the app would
 * stall any active or pending transfers until the user reopens the app. This service
 * keeps the process alive while work remains.
 */
@AndroidEntryPoint
class DownloadPumpService : Service() {

    @Inject lateinit var downloadQueue: DownloadQueue

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForegroundWithActive(downloadQueue.entries.value)
        observeQueue()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> {
                downloadQueue.entries.value.forEach { entry ->
                    if (entry.state is DownloadQueue.EntryState.Downloading ||
                        entry.state is DownloadQueue.EntryState.Pending
                    ) {
                        downloadQueue.pause(entry.id)
                    }
                }
            }
            ACTION_RESUME_ALL -> {
                downloadQueue.entries.value.forEach { entry ->
                    if (entry.state is DownloadQueue.EntryState.Paused) {
                        downloadQueue.resume(entry.id)
                    }
                }
            }
        }
        val active = downloadQueue.entries.value.filter {
            it.state is DownloadQueue.EntryState.Downloading ||
                it.state is DownloadQueue.EntryState.Pending ||
                it.state is DownloadQueue.EntryState.Paused
        }
        if (active.isNotEmpty()) {
            updateNotification(active)
        }
        return START_STICKY
    }

    private fun observeQueue() {
        observeJob?.cancel()
        observeJob =
            scope.launch {
                downloadQueue.entries
                    .collect { entries ->
                        val active = entries.filter {
                            it.state is DownloadQueue.EntryState.Downloading ||
                                it.state is DownloadQueue.EntryState.Pending ||
                                it.state is DownloadQueue.EntryState.Paused
                        }
                        if (active.isEmpty()) {
                            stopForegroundCompat()
                            stopSelf()
                        } else {
                            updateNotification(active)
                        }
                    }
            }
    }

    private fun startForegroundWithActive(entries: List<DownloadQueue.Entry>) {
        val active = entries.filter {
            it.state is DownloadQueue.EntryState.Downloading ||
                it.state is DownloadQueue.EntryState.Pending ||
                it.state is DownloadQueue.EntryState.Paused
        }
        val notification = buildNotification(active)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(active: List<DownloadQueue.Entry>) {
        val nm = getSystemService<NotificationManager>() ?: return
        nm.notify(NOTIFICATION_ID, buildNotification(active))
    }

    private fun buildNotification(active: List<DownloadQueue.Entry>): android.app.Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val downloading = active.firstOrNull { it.state is DownloadQueue.EntryState.Downloading }
            ?: active.firstOrNull()

        val compactTitle = downloading?.item?.name?.ifEmpty { getString(CoreR.string.title_download) }
            ?: if (active.isNotEmpty()) active.first().item.name else getString(CoreR.string.title_download)

        val fullTitle = if (downloading != null) {
            val item = downloading.item
            val baseName = if (item is FindroidEpisode) {
                val epNum = if (item.indexNumber > 0) "E${item.indexNumber} " else ""
                "${item.seriesName} - $epNum${item.name}"
            } else {
                item.name
            }
            if (active.size > 1) {
                "$baseName (+${active.size - 1} queued)"
            } else {
                baseName
            }
        } else {
            resources.getQuantityString(
                CoreR.plurals.downloads_in_progress_title,
                active.size.coerceAtLeast(1),
                active.size.coerceAtLeast(1),
            )
        }

        val progress = downloading?.progress ?: 0
        val speedStr = if (downloading != null && downloading.bytesPerSecond > 0) {
            Formatter.formatFileSize(this, downloading.bytesPerSecond) + "/s"
        } else ""

        val etaStr = if (downloading != null && downloading.bytesPerSecond > 0 && downloading.totalBytes > downloading.bytesDownloaded) {
            val remainingSec = (downloading.totalBytes - downloading.bytesDownloaded) / downloading.bytesPerSecond
            val formattedTime = formatStableEta(remainingSec)
            if (formattedTime.isNotEmpty()) getString(CoreR.string.notification_download_remaining, formattedTime) else ""
        } else ""

        val compactContentText = when {
            downloading?.state is DownloadQueue.EntryState.Downloading && speedStr.isNotEmpty() ->
                "$progress% • $speedStr"
            downloading?.state is DownloadQueue.EntryState.Downloading ->
                "$progress%"
            downloading?.state is DownloadQueue.EntryState.Pending ->
                getString(CoreR.string.pending_in_queue)
            downloading?.state is DownloadQueue.EntryState.Paused ->
                getString(CoreR.string.download_paused)
            else -> ""
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(CoreR.drawable.ic_download)
            .setContentTitle(compactTitle)
            .setContentText(compactContentText)
            .setSubText(etaStr.ifEmpty { null })
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (downloading?.state is DownloadQueue.EntryState.Downloading) {
            builder.setProgress(100, progress, false)
        } else if (downloading?.state is DownloadQueue.EntryState.Pending) {
            builder.setProgress(0, 0, true)
        }

        // Multi-line expanded view following Android notification guidelines
        val downloadedFormatted = if (downloading != null && downloading.bytesDownloaded > 0) {
            Formatter.formatFileSize(this, downloading.bytesDownloaded)
        } else "0 B"
        val totalFormatted = if (downloading != null && downloading.totalBytes > 0) {
            Formatter.formatFileSize(this, downloading.totalBytes)
        } else ""

        val bigTextBuilder = StringBuilder()
        if (downloading != null && downloading.state is DownloadQueue.EntryState.Downloading) {
            bigTextBuilder.append("$progress%")
            if (speedStr.isNotEmpty()) bigTextBuilder.append(" • $speedStr")
            if (etaStr.isNotEmpty()) bigTextBuilder.append(" • $etaStr")
            if (totalFormatted.isNotEmpty()) {
                bigTextBuilder.append(getString(CoreR.string.notification_download_progress_size, downloadedFormatted, totalFormatted))
            }
        } else if (downloading != null && downloading.state is DownloadQueue.EntryState.Pending) {
            bigTextBuilder.append(getString(CoreR.string.pending_in_queue))
            if (totalFormatted.isNotEmpty()) {
                bigTextBuilder.append(getString(CoreR.string.notification_download_size, totalFormatted))
            }
        }

        val queuedItems = active.filter { it.id != downloading?.id }
        if (queuedItems.isNotEmpty()) {
            bigTextBuilder.append(getString(CoreR.string.notification_download_in_queue, queuedItems.size))
            queuedItems.take(4).forEach { queued ->
                val epTitle = if (queued.item is FindroidEpisode) {
                    val epNum = if (queued.item.indexNumber > 0) "E${queued.item.indexNumber} " else ""
                    "${queued.item.seriesName} - $epNum${queued.item.name}"
                } else {
                    queued.item.name
                }
                bigTextBuilder.append("\n• $epTitle")
            }
            if (queuedItems.size > 4) {
                bigTextBuilder.append(getString(CoreR.string.notification_download_more_items, queuedItems.size - 4))
            }
        }

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(fullTitle)
            .setSummaryText(if (active.size > 1) resources.getQuantityString(CoreR.plurals.downloads_count, active.size, active.size) else null)
            .bigText(bigTextBuilder.toString())

        builder.setStyle(bigTextStyle)

        // Notification Action: Pause / Resume
        val hasDownloadingOrPending = active.any {
            it.state is DownloadQueue.EntryState.Downloading || it.state is DownloadQueue.EntryState.Pending
        }
        val actionIntent = Intent(this, DownloadPumpService::class.java).apply {
            action = if (hasDownloadingOrPending) ACTION_PAUSE_ALL else ACTION_RESUME_ALL
        }
        val actionPendingIntent = PendingIntent.getService(
            this,
            1,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val actionTitle = if (hasDownloadingOrPending) {
            getString(CoreR.string.pause)
        } else {
            getString(CoreR.string.resume)
        }
        val actionIcon = if (hasDownloadingOrPending) {
            CoreR.drawable.ic_pause
        } else {
            CoreR.drawable.ic_play
        }
        builder.addAction(actionIcon, actionTitle, actionPendingIntent)

        return builder.build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService<NotificationManager>() ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(CoreR.string.downloads_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        channel.description = getString(CoreR.string.downloads_channel_description)
        channel.setShowBadge(false)
        nm.createNotificationChannel(channel)
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ caps dataSync FGS at ~6h/day. Stop cleanly instead of letting
        // the system crash the process. In-flight OkHttp transfers will be interrupted;
        // partial files are kept and resumed next time the app opens (restoreAll + ensurePump).
        Timber.w("DownloadPumpService hit the dataSync time limit; stopping")
        stopForegroundCompat()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val ACTION_PAUSE_ALL = "dev.jdtech.jellyfin.core.action.PAUSE_ALL"
        const val ACTION_RESUME_ALL = "dev.jdtech.jellyfin.core.action.RESUME_ALL"
        private const val CHANNEL_ID = "download_pump"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, DownloadPumpService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start DownloadPumpService")
            }
        }
    }
}
