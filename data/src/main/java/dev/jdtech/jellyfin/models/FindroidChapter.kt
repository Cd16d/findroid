package dev.jdtech.jellyfin.models

import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

@Serializable
data class FindroidChapter(
    /** The start position. */
    val startPosition: Long,
    /** The name. */
    val name: String? = null,
)

fun BaseItemDto.toFindroidChapters(): List<FindroidChapter> {
    val rawChapters = chapters ?: return emptyList()
    val sorted = rawChapters.sortedBy { it.startPositionTicks }
    val result = mutableListOf<FindroidChapter>()
    var lastTicks: Long? = null

    for (chapter in sorted) {
        val ticks = chapter.startPositionTicks
        val prev = result.lastOrNull()
        if (prev != null && lastTicks == ticks) {
            val prevName = prev.name?.trim().orEmpty()
            val currName = chapter.name?.trim().orEmpty()
            val prevIsGeneric = prevName.isBlank() || prevName.matches(Regex("(?i)chapter\\s*\\d+"))
            val currIsGeneric = currName.isBlank() || currName.matches(Regex("(?i)chapter\\s*\\d+"))

            if (prevIsGeneric && !currIsGeneric) {
                result[result.size - 1] =
                    FindroidChapter(startPosition = ticks / 10000, name = chapter.name)
            }
        } else {
            result.add(FindroidChapter(startPosition = ticks / 10000, name = chapter.name))
            lastTicks = ticks
        }
    }

    return result
}

/** Deduplicates chapters by removing redundant entries with exactly identical start positions. */
fun List<FindroidChapter>.deduplicate(): List<FindroidChapter> {
    if (size <= 1) return this

    val sorted = sortedBy { it.startPosition }
    val result = mutableListOf<FindroidChapter>()

    for (chapter in sorted) {
        val prev = result.lastOrNull()
        if (prev != null && prev.startPosition == chapter.startPosition) {
            val prevName = prev.name?.trim().orEmpty()
            val currName = chapter.name?.trim().orEmpty()
            val prevIsGeneric = prevName.isBlank() || prevName.matches(Regex("(?i)chapter\\s*\\d+"))
            val currIsGeneric = currName.isBlank() || currName.matches(Regex("(?i)chapter\\s*\\d+"))

            if (prevIsGeneric && !currIsGeneric) {
                result[result.size - 1] = chapter
            }
        } else {
            result.add(chapter)
        }
    }

    return result
}
