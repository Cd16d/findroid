package dev.jdtech.jellyfin.settings.utils

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import dev.jdtech.jellyfin.settings.R
import dev.jdtech.jellyfin.settings.presentation.models.StorageDevice
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

object StorageUtils {

    fun getTotalStorageBytes(context: Context, storagePath: String): Long {
        val rawFsBytes =
            try {
                val statFs = StatFs(storagePath)
                statFs.blockCountLong * statFs.blockSizeLong
            } catch (_: Exception) {
                0L
            }

        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val volume =
            try {
                storageManager?.getStorageVolume(File(storagePath))
            } catch (_: Exception) {
                null
            }

        val isPrimary = volume?.isPrimary ?: true
        val statsManagerBytes =
            if (isPrimary) {
                try {
                    val storageStatsManager =
                        context.getSystemService(Context.STORAGE_STATS_SERVICE)
                            as? StorageStatsManager
                    storageStatsManager?.getTotalBytes(StorageManager.UUID_DEFAULT) ?: 0L
                } catch (_: Exception) {
                    0L
                }
            } else {
                0L
            }

        val bytesToTest = if (statsManagerBytes > 0L) statsManagerBytes else rawFsBytes
        if (bytesToTest <= 0L) return 0L

        // Commercial storage tiers in GB
        val standardTiersGb = longArrayOf(4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048)

        val bytesInGbDecimal = bytesToTest / 1_000_000_000.0
        val bytesInGiB = bytesToTest / (1024.0 * 1024.0 * 1024.0)

        val candidateGb =
            standardTiersGb.minByOrNull { tier ->
                val distDecimal = abs(tier - bytesInGbDecimal)
                val distGiB = abs(tier - bytesInGiB)
                min(distDecimal, distGiB)
            } ?: (bytesToTest / 1_000_000_000L)

        return (candidateGb * 1_000_000_000L).coerceAtLeast(rawFsBytes)
    }

    fun getStorageDevices(context: Context, defaultIndex: Int): List<StorageDevice> {
        val dirs = context.getExternalFilesDirs(null)
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

        return dirs.mapIndexedNotNull { index, dir ->
            if (
                dir != null && Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
            ) {
                val volume =
                    try {
                        storageManager?.getStorageVolume(dir)
                    } catch (_: Exception) {
                        null
                    }
                val isPrimary = volume?.isPrimary ?: (index == 0)
                val isRemovable = volume?.isRemovable ?: (index > 0)
                val description =
                    try {
                        volume?.getDescription(context)?.trim()
                    } catch (_: Exception) {
                        null
                    }
                val name =
                    if (!description.isNullOrBlank()) {
                        description
                    } else if (isPrimary) {
                        context.getString(R.string.downloads_storage_internal)
                    } else {
                        context.getString(R.string.downloads_storage_sdcard)
                    }

                val downloadsDir = File(dir, "downloads")
                val downloadedBytes =
                    if (downloadsDir.exists()) {
                        downloadsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    } else {
                        0L
                    }

                val statFs =
                    try {
                        StatFs(dir.path)
                    } catch (_: Exception) {
                        null
                    }
                val availableBytes = statFs?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
                val rawFsTotal = statFs?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
                val totalBytes = getTotalStorageBytes(context, dir.path).coerceAtLeast(rawFsTotal)

                StorageDevice(
                    index = index,
                    name = name,
                    isPrimary = isPrimary,
                    isRemovable = isRemovable,
                    isDefault = (index == defaultIndex),
                    totalBytes = totalBytes,
                    availableBytes = availableBytes,
                    downloadedBytes = downloadedBytes,
                )
            } else {
                null
            }
        }
    }

    fun formatDecimalFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups =
            (log10(bytes.toDouble()) / log10(1000.0)).toInt().coerceIn(0, units.lastIndex)
        if (digitGroups == 0) return "$bytes B"
        val value = bytes / 1000.0.pow(digitGroups.toDouble())
        return if (value >= 100 || value % 1.0 == 0.0) {
            String.format(Locale.getDefault(), "%.0f %s", value, units[digitGroups])
        } else {
            String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroups])
        }
    }
}
