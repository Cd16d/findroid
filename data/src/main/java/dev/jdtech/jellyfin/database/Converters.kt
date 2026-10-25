package dev.jdtech.jellyfin.database

import androidx.room3.ColumnTypeConverter
import dev.jdtech.jellyfin.models.FindroidChapter
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.DateTime

/**
 * Room database type converters for custom data types. Converts complex types such as [UUID],
 * [DateTime], and custom lists to/from database-supported types.
 */
class Converters {
    /** Converts a String representation to a [UUID]. */
    @ColumnTypeConverter
    fun fromStringToUUID(value: String?): UUID? {
        return value?.let { UUID.fromString(it) }
    }

    /** Converts a [UUID] to its String representation. */
    @ColumnTypeConverter
    fun fromUUIDToString(value: UUID?): String? {
        return value?.toString()
    }

    /** Converts a [DateTime] to epoch seconds in UTC. */
    @ColumnTypeConverter
    fun fromDateTimeToLong(value: DateTime?): Long? {
        return value?.toEpochSecond(ZoneOffset.UTC)
    }

    /** Converts epoch seconds in UTC to a [DateTime]. */
    @ColumnTypeConverter
    fun fromLongToDatetime(value: Long?): DateTime? {
        return value?.let { DateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }
    }

    /** Serializes a list of [FindroidChapter] objects to a JSON String. */
    @ColumnTypeConverter
    fun fromFindroidChaptersToString(value: List<FindroidChapter>?): String? {
        return value?.let { Json.encodeToString(value) }
    }

    /** Deserializes a JSON String to a list of [FindroidChapter] objects. */
    @ColumnTypeConverter
    fun fromStringToFindroidChapters(value: String?): List<FindroidChapter>? {
        return value?.let { Json.decodeFromString<List<FindroidChapter>>(value) }
    }

    /** Converts a list of [UUID] objects to a comma-separated String. */
    @ColumnTypeConverter
    fun fromUUIDListToString(value: List<UUID>?): String? {
        return value?.joinToString(",") { it.toString() }
    }

    /** Converts a comma-separated String to a list of [UUID] objects. */
    @ColumnTypeConverter
    fun fromStringToUUIDList(value: String?): List<UUID>? {
        return value?.split(",")?.filter { it.isNotEmpty() }?.map { UUID.fromString(it) }
    }
}
