package dev.jdtech.jellyfin.player.core.domain.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Represents frame offset and size within a trickplay sprite sheet. */
data class TrickplayFrame(val sheetIndex: Int, val srcOffset: IntOffset, val srcSize: IntSize)

/**
 * Manages trickplay preview thumbnails with an asynchronous 3-sheet sliding window cache.
 *
 * Rather than eagerly downloading and slicing hundreds of individual Bitmaps into RAM (~150-200
 * MB), this manager maintains at most 3 whole sprite sheets in memory (~6-8 MB in RGB_565) and
 * coordinates sub-rect drawing directly via Canvas clipping.
 */
class Trickplay(
    val itemId: UUID,
    val interval: Int,
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val bandwidth: Int = 0,
    private val repository: JellyfinRepository? = null,
    private val cacheDir: File? = null,
    private val coroutineScope: CoroutineScope? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val intervalMs: Long =
        when {
            interval > 1000 -> interval.toLong()
            interval > 0 -> interval.toLong() * 1000L
            else -> 10_000L
        }

    val tileColumns: Int = tileWidth.coerceAtLeast(1)
    val tileRows: Int = tileHeight.coerceAtLeast(1)
    val tilesPerSheet: Int = (tileColumns * tileRows).coerceAtLeast(1)
    val sheetCount: Int =
        if (thumbnailCount > 0) ceil(thumbnailCount.toDouble() / tilesPerSheet).toInt() else 0
    val hasThumbnails: Boolean = thumbnailCount > 0 && sheetCount > 0 && width > 0 && height > 0

    val images: List<Bitmap>
        get() = emptyList()

    private val trickplayDir: File? = cacheDir?.let { File(it, "trickplay/$itemId") }

    // 3-sheet LRU cache: keeps max 3 Bitmaps in memory (~6-8 MB in RGB_565)
    private val sheetCache = LruCache<Int, Bitmap>(3)

    private val activeLoads = ConcurrentHashMap<Int, Deferred<Bitmap?>>()

    @Volatile private var lastCenterSheet: Int = -1

    init {
        if (
            coroutineScope != null && repository != null && trickplayDir != null && sheetCount > 0
        ) {
            trickplayDir.mkdirs()
            preloadWindow(0)
            coroutineScope.launch(dispatcher) {
                try {
                    for (i in 0 until sheetCount) {
                        if (!isActive) break
                        val sheetFile = File(trickplayDir, "$i.jpg")
                        if (!sheetFile.exists() || sheetFile.length() == 0L) {
                            val data = repository.getTrickplayData(itemId, width, i)
                            if (data != null && isActive) {
                                try {
                                    sheetFile.writeBytes(data)
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: CancellationException) {
                    // Scope cancelled
                } catch (e: Exception) {
                    Timber.w(e, "Error downloading trickplay sheets to disk cache for $itemId")
                }
            }
        }
    }

    constructor(
        itemId: UUID,
        info: TrickplayInfo,
        repository: JellyfinRepository? = null,
        cacheDir: File? = null,
        coroutineScope: CoroutineScope? = null,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        itemId = itemId,
        interval = info.interval,
        width = info.width,
        height = info.height,
        tileWidth = info.tileWidth,
        tileHeight = info.tileHeight,
        thumbnailCount = info.thumbnailCount,
        bandwidth = info.bandwidth,
        repository = repository,
        cacheDir = cacheDir,
        coroutineScope = coroutineScope,
        dispatcher = dispatcher,
    )

    constructor(
        interval: Int,
        images: List<Bitmap> = emptyList(),
    ) : this(
        itemId = UUID.randomUUID(),
        interval = interval,
        width = images.firstOrNull()?.width ?: 320,
        height = images.firstOrNull()?.height ?: 180,
        tileWidth = 1,
        tileHeight = 1,
        thumbnailCount = images.size,
    )

    /**
     * Updates playback position and slides the 3-sheet cache window ([center-1, center, center+1]).
     */
    fun updatePosition(positionMs: Long) {
        if (!hasThumbnails || sheetCount <= 0) return
        val safePos = positionMs.coerceAtLeast(0L)
        val frameIndex = (safePos / intervalMs).toInt().coerceIn(0, thumbnailCount - 1)
        val centerSheet = frameIndex / tilesPerSheet
        if (centerSheet != lastCenterSheet) {
            lastCenterSheet = centerSheet
            preloadWindow(centerSheet)
        }
    }

    /** Preloads the adjacent sheets around [centerSheet] into the cache window. */
    fun preloadWindow(centerSheet: Int) {
        if (coroutineScope == null || sheetCount <= 0) return
        val minSheet = (centerSheet - 1).coerceAtLeast(0)
        val maxSheet = (centerSheet + 1).coerceAtMost(sheetCount - 1)
        for (sheet in minSheet..maxSheet) {
            if (getCachedSheet(sheet) == null) {
                coroutineScope.launch(dispatcher) { getSheet(sheet) }
            }
        }
    }

    /** Returns the cached sheet bitmap if currently resident in the LRU cache. */
    fun getCachedSheet(sheetIndex: Int): Bitmap? {
        return synchronized(sheetCache) { sheetCache.get(sheetIndex) }
    }

    /**
     * Asynchronously loads and decodes the sheet bitmap at [sheetIndex], checking disk cache first.
     */
    suspend fun getSheet(sheetIndex: Int): Bitmap? =
        withContext(dispatcher) {
            if (sheetIndex !in 0 until sheetCount) return@withContext null
            synchronized(sheetCache) {
                sheetCache.get(sheetIndex)?.let {
                    return@withContext it
                }
            }

            val scope = coroutineScope ?: this
            val deferred =
                activeLoads.computeIfAbsent(sheetIndex) {
                    scope.async(dispatcher) { decodeSheet(sheetIndex) }
                }

            try {
                val bitmap = deferred.await()
                if (bitmap != null) {
                    synchronized(sheetCache) { sheetCache.put(sheetIndex, bitmap) }
                }
                bitmap
            } finally {
                activeLoads.remove(sheetIndex)
            }
        }

    private suspend fun decodeSheet(sheetIndex: Int): Bitmap? {
        val sheetFile = trickplayDir?.let { File(it, "$sheetIndex.jpg") }
        if (sheetFile != null && sheetFile.exists() && sheetFile.length() > 0L) {
            return decodeBitmapFromFile(sheetFile)
        }

        if (repository != null) {
            val bytes = repository.getTrickplayData(itemId, width, sheetIndex)
            if (bytes != null) {
                if (sheetFile != null) {
                    try {
                        sheetFile.writeBytes(bytes)
                    } catch (_: Exception) {}
                }
                return decodeBitmapFromBytes(bytes)
            }
        }
        return null
    }

    private fun decodeBitmapFromFile(file: File): Bitmap? {
        return try {
            val options =
                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Throwable) {
            Timber.w(e, "Failed to decode trickplay sheet from file: %s", file.name)
            null
        }
    }

    private fun decodeBitmapFromBytes(bytes: ByteArray): Bitmap? {
        return try {
            val options =
                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: Throwable) {
            Timber.w(e, "Failed to decode trickplay sheet from bytes")
            null
        }
    }

    /**
     * Calculates the sheet index and source sub-rectangle for rendering the frame at [positionMs].
     */
    fun getFrameInfo(positionMs: Long): TrickplayFrame? {
        if (!hasThumbnails || intervalMs <= 0L || width <= 0 || height <= 0) return null
        val safePos = positionMs.coerceAtLeast(0L)
        val frameIndex = (safePos / intervalMs).toInt().coerceIn(0, thumbnailCount - 1)
        val sheetIndex = frameIndex / tilesPerSheet
        val tileIndex = frameIndex % tilesPerSheet
        val col = tileIndex % tileColumns
        val row = tileIndex / tileColumns
        val srcX = col * width
        val srcY = row * height
        return TrickplayFrame(
            sheetIndex = sheetIndex,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(width, height),
        )
    }

    /** Releases cached bitmaps, cancels in-flight loads, and deletes temporary disk files. */
    fun release() {
        synchronized(sheetCache) {
            val snapshot = sheetCache.snapshot()
            sheetCache.evictAll()
            snapshot.values.forEach { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
        activeLoads.values.forEach { it.cancel() }
        activeLoads.clear()
        if (trickplayDir != null && trickplayDir.exists()) {
            try {
                trickplayDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Trickplay) return false
        return itemId == other.itemId &&
            interval == other.interval &&
            thumbnailCount == other.thumbnailCount
    }

    override fun hashCode(): Int {
        var result = itemId.hashCode()
        result = 31 * result + interval
        result = 31 * result + thumbnailCount
        return result
    }
}
