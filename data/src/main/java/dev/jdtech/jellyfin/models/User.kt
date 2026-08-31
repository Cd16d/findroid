package dev.jdtech.jellyfin.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import android.content.Context
import java.io.File
import java.util.UUID

@Entity(
    tableName = "users",
    foreignKeys =
        [
            ForeignKey(
                entity = Server::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("serverId"),
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class User(
    @PrimaryKey val id: UUID,
    val name: String,
    @ColumnInfo(index = true) val serverId: String,
    val accessToken: String? = null,
    val primaryImageTag: String? = null,
)

fun User.getProfileImageModel(context: Context, baseUrl: String): Any? {
    val fileName = if (primaryImageTag != null) "primary_$primaryImageTag" else "primary"
    val localFile = File(context.filesDir, "images/users/$id/$fileName")
    return if (localFile.exists()) {
        localFile
    } else if (baseUrl.isNotEmpty()) {
        "$baseUrl/Users/$id/Images/Primary" + (if (primaryImageTag != null) "?tag=$primaryImageTag" else "")
    } else null
}
