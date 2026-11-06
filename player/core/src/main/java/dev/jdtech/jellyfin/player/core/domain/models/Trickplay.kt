package dev.jdtech.jellyfin.player.core.domain.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Coordinates for clipping a frame from a trickplay sprite sheet. */
data class TrickplayFrame(
    val sheetIndex: Int,
    val srcX: Int,
    val srcY: Int,
    val srcWidth: Int,
    val srcHeight: Int,
)

/**
 * Sliding window cache (3 sheets: [N-1, N, N+1]) for trickplay scrubbing previews.
 *
 * Maintains at most 3 whole sheets in memory (~6-8 MB in RGB_565) and coordinates frame rendering
 * directly via Canvas GPU clipping.
 */
class Trickplay(
    val itemId: UUID,
    val info: TrickplayInfo,
    val mediaSourceId: String? = null,
    private val repository: JellyfinRepository? = null,
    private val cacheDir: File? = null,
    private val coroutineScope: CoroutineScope? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val width: Int = info.width
    val height: Int = info.height
    val interval: Int = info.interval
    val intervalMs: Long =
        if (info.interval > 1000) info.interval.toLong() else info.interval * 1000L
    val tileColumns: Int = info.tileWidth.coerceAtLeast(1)
    val tileRows: Int = info.tileHeight.coerceAtLeast(1)
    val tilesPerSheet: Int = (tileColumns * tileRows).coerceAtLeast(1)
    val sheetCount: Int = ceil(info.thumbnailCount.toDouble() / tilesPerSheet).toInt()
    val hasThumbnails: Boolean = sheetCount > 0 && width > 0 && height > 0

    private val trickplayDir: File? = cacheDir?.let {
        val dirName = if (mediaSourceId.isNullOrEmpty()) "$itemId" else "${itemId}_$mediaSourceId"
        File(it, "trickplay/$dirName")
    }

    private val sheetCache = LruCache<Int, Bitmap>(3)
    private val activeLoads = ConcurrentHashMap<Int, Deferred<Bitmap?>>()
    private var downloadJob: Job? = null

    @Volatile private var lastCenterSheet: Int = -1

    init {
        if (
            coroutineScope != null && repository != null && trickplayDir != null && sheetCount > 0
        ) {
            trickplayDir.mkdirs()
            preloadWindow(0)
            downloadJob =
                coroutineScope.launch(dispatcher) {
                    try {
                        for (i in 0 until sheetCount) {
                            if (!isActive) break
                            val file = File(trickplayDir, "$i.jpg")
                            if (!file.exists() || file.length() == 0L) {
                                val data = repository.getTrickplayData(itemId, width, i)
                                if (data != null && isActive) writeAtomically(file, data)
                            }
                        }
                    } catch (_: CancellationException) {
                        // Scope cancelled
                    } catch (e: Exception) {
                        Timber.w(e, "Error downloading trickplay sheets for $itemId")
                    }
                }
        }
    }

    /** Updates playback position and slides the 3-sheet cache window. */
    fun updatePosition(positionMs: Long) {
        if (sheetCount <= 0 || intervalMs <= 0L) return
        val frameIndex =
            (positionMs.coerceAtLeast(0L) / intervalMs).toInt().coerceIn(0, info.thumbnailCount - 1)
        val centerSheet = frameIndex / tilesPerSheet
        if (centerSheet != lastCenterSheet) {
            lastCenterSheet = centerSheet
            preloadWindow(centerSheet)
        }
    }

    /** Preloads adjacent sheets around [centerSheet] into the cache window. */
    fun preloadWindow(centerSheet: Int) {
        if (coroutineScope == null || sheetCount <= 0) return
        val minSheet = (centerSheet - 1).coerceAtLeast(0)
        val maxSheet = (centerSheet + 1).coerceAtMost(sheetCount - 1)
        activeLoads.forEach { (index, deferred) ->
            if (index !in minSheet..maxSheet) deferred.cancel()
        }
        for (sheet in minSheet..maxSheet) {
            if (getCachedSheet(sheet) == null) {
                coroutineScope.launch(dispatcher) { getSheet(sheet) }
            }
        }
    }

    /** Returns the cached sheet bitmap if currently resident in the LRU cache. */
    fun getCachedSheet(sheetIndex: Int): Bitmap? =
        synchronized(sheetCache) { sheetCache.get(sheetIndex) }

    /**
     * Asynchronously loads and decodes the sheet bitmap at [sheetIndex], checking disk cache first.
     */
    suspend fun getSheet(sheetIndex: Int): Bitmap? =
        withContext(dispatcher) {
            if (sheetIndex !in 0 until sheetCount) return@withContext null
            synchronized(sheetCache) { sheetCache.get(sheetIndex) }
                ?.let {
                    return@withContext it
                }

            val scope = coroutineScope ?: this
            val deferred =
                activeLoads.computeIfAbsent(sheetIndex) {
                    scope.async(dispatcher) { decodeSheet(sheetIndex) }
                }
            try {
                val bitmap = deferred.await()
                if (bitmap != null) synchronized(sheetCache) { sheetCache.put(sheetIndex, bitmap) }
                bitmap
            } finally {
                activeLoads.remove(sheetIndex)
            }
        }

    private suspend fun decodeSheet(sheetIndex: Int): Bitmap? {
        val sheetFile = trickplayDir?.let { File(it, "$sheetIndex.jpg") }
        if (sheetFile != null && sheetFile.exists() && sheetFile.length() > 0L) {
            val bitmap = decodeBitmap(sheetFile)
            if (bitmap != null) return bitmap
            sheetFile.delete()
        }
        if (repository != null) {
            val bytes = repository.getTrickplayData(itemId, width, sheetIndex)
            if (bytes != null) {
                if (sheetFile != null) writeAtomically(sheetFile, bytes)
                return decodeBitmap(bytes)
            }
        }
        return null
    }

    private fun writeAtomically(targetFile: File, data: ByteArray) {
        try {
            val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tmpFile.writeBytes(data)
            tmpFile.renameTo(targetFile)
        } catch (_: Exception) {}
    }

    private fun decodeBitmap(source: Any): Bitmap? =
        try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            when (source) {
                is File -> BitmapFactory.decodeFile(source.absolutePath, opts)
                is ByteArray -> BitmapFactory.decodeByteArray(source, 0, source.size, opts)
                else -> null
            }
        } catch (e: Throwable) {
            Timber.w(e, "Failed to decode trickplay sheet")
            null
        }

    /**
     * Calculates the sheet index and source sub-rectangle for rendering the frame at [positionMs].
     */
    fun getFrameInfo(positionMs: Long): TrickplayFrame? {
        if (intervalMs <= 0L || width <= 0 || height <= 0 || info.thumbnailCount <= 0) return null
        val frameIndex =
            (positionMs.coerceAtLeast(0L) / intervalMs).toInt().coerceIn(0, info.thumbnailCount - 1)
        val sheetIndex = frameIndex / tilesPerSheet
        val tileIndex = frameIndex % tilesPerSheet
        val col = tileIndex % tileColumns
        val row = tileIndex / tileColumns
        return TrickplayFrame(
            sheetIndex = sheetIndex,
            srcX = col * width,
            srcY = row * height,
            srcWidth = width,
            srcHeight = height,
        )
    }

    /** Releases cached bitmaps, cancels in-flight loads, and deletes temporary disk files. */
    fun release() {
        downloadJob?.cancel()
        downloadJob = null
        synchronized(sheetCache) {
            val snapshot = sheetCache.snapshot()
            sheetCache.evictAll()
            snapshot.values.forEach { if (!it.isRecycled) it.recycle() }
        }
        activeLoads.values.forEach { it.cancel() }
        activeLoads.clear()
        trickplayDir?.deleteRecursively()
    }
}
