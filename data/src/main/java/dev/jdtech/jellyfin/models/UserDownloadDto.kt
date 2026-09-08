package dev.jdtech.jellyfin.models

import androidx.room.Entity
import java.util.UUID

@Entity(tableName = "user_downloads", primaryKeys = ["userId", "itemId"])
data class UserDownloadDto(
    val userId: UUID,
    val itemId: UUID,
    val downloadedAt: Long = System.currentTimeMillis(),
)
