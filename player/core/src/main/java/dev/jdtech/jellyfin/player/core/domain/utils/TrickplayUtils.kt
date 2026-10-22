package dev.jdtech.jellyfin.player.core.domain.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.concurrent.CancellationException
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Downloads trickplay (BIF/Thumbnail) data for scrubbing previews, slices the sprite sheets into
 * individual frame [Bitmap]s, and returns a [Trickplay] object.
 *
 * @param repository Jellyfin repository to fetch raw byte data.
 * @param item Player item containing trickplay metadata.
 * @param dispatcher Coroutine dispatcher for image decoding (defaults to [Dispatchers.Default]).
 * @return Parsed [Trickplay] containing decoded frame bitmaps, or null if loading fails or is
 *   canceled.
 */
suspend fun fetchTrickplay(
    repository: JellyfinRepository,
    item: PlayerItem,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
): Trickplay? {
    val trickplayInfo = item.trickplayInfo ?: return null
    return withContext(dispatcher) {
        val tilesPerSheet = trickplayInfo.tileWidth * trickplayInfo.tileHeight
        if (tilesPerSheet <= 0) return@withContext null
        val sheetCount = ceil(trickplayInfo.thumbnailCount.toDouble().div(tilesPerSheet)).toInt()
        val bitmaps = mutableListOf<Bitmap>()
        var success = false

        try {
            for (i in 0 until sheetCount) {
                if (bitmaps.size >= trickplayInfo.thumbnailCount) break
                coroutineContext.ensureActive()
                val byteArray =
                    repository.getTrickplayData(item.itemId, trickplayInfo.width, i) ?: continue
                coroutineContext.ensureActive()
                val fullBitmap =
                    BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size) ?: continue
                try {
                    for (offsetY in
                        0..<trickplayInfo.height * trickplayInfo.tileHeight step
                            trickplayInfo.height) {
                        for (offsetX in
                            0..<trickplayInfo.width * trickplayInfo.tileWidth step
                                trickplayInfo.width) {
                            coroutineContext.ensureActive()
                            if (bitmaps.size < trickplayInfo.thumbnailCount) {
                                val bitmap =
                                    Bitmap.createBitmap(
                                        fullBitmap,
                                        offsetX,
                                        offsetY,
                                        trickplayInfo.width,
                                        trickplayInfo.height,
                                    )
                                bitmaps.add(bitmap)
                            } else {
                                break
                            }
                        }
                        if (bitmaps.size >= trickplayInfo.thumbnailCount) break
                    }
                } finally {
                    fullBitmap.recycle()
                }
            }

            coroutineContext.ensureActive()
            if (bitmaps.isNotEmpty()) {
                success = true
                Trickplay(trickplayInfo.interval, bitmaps)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "Failed to decode trickplay thumbnails for item: %s", item.itemId)
            null
        } finally {
            if (!success) {
                bitmaps.forEach { it.recycle() }
                bitmaps.clear()
            }
        }
    }
}
