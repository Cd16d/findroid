package dev.jdtech.jellyfin.settings.presentation.models

import dev.jdtech.jellyfin.settings.utils.StorageUtils

data class StorageDevice(
    val index: Int,
    val name: String,
    val isPrimary: Boolean = true,
    val isRemovable: Boolean = false,
    val isDefault: Boolean = false,
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
) {
    val usedBytes: Long
        get() = (totalBytes - availableBytes).coerceAtLeast(0L)

    val otherUsedBytes: Long
        get() = (usedBytes - downloadedBytes).coerceAtLeast(0L)

    val usedPercent: Int
        get() = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100) else 0

    val downloadedFraction: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val otherUsedFraction: Float
        get() = if (totalBytes > 0) (otherUsedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val freeFraction: Float
        get() = if (totalBytes > 0) (availableBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val totalFormatted: String
        get() = StorageUtils.formatDecimalFileSize(totalBytes)

    val usedFormatted: String
        get() = StorageUtils.formatDecimalFileSize(usedBytes)

    val freeFormatted: String
        get() = StorageUtils.formatDecimalFileSize(availableBytes)

    val downloadedFormatted: String
        get() = StorageUtils.formatDecimalFileSize(downloadedBytes)

    val otherUsedFormatted: String
        get() = StorageUtils.formatDecimalFileSize(otherUsedBytes)
}
