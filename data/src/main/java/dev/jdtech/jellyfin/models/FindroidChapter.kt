package dev.jdtech.jellyfin.models

import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

@Serializable
data class FindroidChapter(
    /** The start position. */
    val startPosition: Long,
    /** The name. */
    val name: String? = null,
    /** The optional chapter image URI. */
    val imageUri: String? = null,
)

fun BaseItemDto.toFindroidChapters(): List<FindroidChapter> {
    val rawChapters = chapters ?: return emptyList()
    return rawChapters.mapIndexed { index, chapter ->
        val imageUri =
            chapter.imageTag?.let { tag ->
                if (tag.isNotBlank()) {
                    "/Items/$id/Images/Chapter/$index?tag=$tag"
                } else null
            }
        FindroidChapter(
            startPosition = chapter.startPositionTicks / 10000,
            name = chapter.name,
            imageUri = imageUri,
        )
    }
}
