package dev.jdtech.jellyfin.player.core.domain.utils

import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Initializes trickplay thumbnail metadata for scrubbing previews and sets up the 3-sheet sliding
 * window cache without eagerly downloading or slicing sprite sheets.
 *
 * @param repository Jellyfin repository to fetch trickplay sheets.
 * @param item Player item containing trickplay metadata.
 * @param cacheDir Cache directory for temporary sheet disk caching.
 * @param coroutineScope Coroutine scope for background preloading and disk downloads.
 * @param dispatcher Coroutine dispatcher for image decoding and I/O (defaults to [Dispatchers.IO]).
 * @return Initialized [Trickplay] sliding cache manager, or null if trickplay metadata is
 *   unavailable or invalid.
 */
suspend fun fetchTrickplay(
    repository: JellyfinRepository,
    item: PlayerItem,
    cacheDir: File? = null,
    coroutineScope: CoroutineScope? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
): Trickplay? {
    val trickplayInfo = item.trickplayInfo ?: return null
    if (
        trickplayInfo.thumbnailCount <= 0 || trickplayInfo.width <= 0 || trickplayInfo.height <= 0
    ) {
        return null
    }
    if (trickplayInfo.tileWidth <= 0 || trickplayInfo.tileHeight <= 0) {
        return null
    }

    val tilesPerSheet = trickplayInfo.tileWidth * trickplayInfo.tileHeight
    if (tilesPerSheet <= 0) return null
    val sheetCount = ceil(trickplayInfo.thumbnailCount.toDouble() / tilesPerSheet).toInt()
    if (sheetCount <= 0 || sheetCount > 150) return null

    return Trickplay(
        itemId = item.itemId,
        mediaSourceId = item.mediaSourceId,
        info = trickplayInfo,
        repository = repository,
        cacheDir = cacheDir,
        coroutineScope = coroutineScope,
        dispatcher = dispatcher,
    )
}
