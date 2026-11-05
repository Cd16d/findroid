package dev.jdtech.jellyfin.player.core.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import kotlin.math.roundToInt

/**
 * Calculates the dynamic width of the trickplay preview card based on media/trickplay aspect ratio.
 *
 * @param trickplay Available [Trickplay] metadata containing thumbnail dimensions.
 * @param videoAspectRatio Native aspect ratio of the playing video.
 * @param baseHeight Fixed height of the preview card container.
 * @return Computed [Dp] width coerced within reasonable aspect ratio bounds.
 */
fun calculateTrickplayCardWidth(
    trickplay: Trickplay?,
    videoAspectRatio: Float?,
    baseHeight: Dp = 110.dp,
): Dp {
    val aspect =
        if (trickplay != null && trickplay.height > 0) {
            trickplay.width.toFloat() / trickplay.height.toFloat()
        } else if (videoAspectRatio != null && videoAspectRatio > 0f) {
            videoAspectRatio
        } else {
            16f / 9f
        }
    return (baseHeight.value * aspect.coerceIn(0.75f, 2.75f)).dp
}

/**
 * Renders a trickplay seek preview thumbnail for the specified [positionMs] by clipping coordinates
 * directly from the cached whole-sheet bitmap via Canvas GPU clipping.
 *
 * This completely avoids slicing sheets into hundreds of individual Bitmaps, keeping heap memory
 * capped to only the active 3-sheet sliding window (~6-8 MB).
 *
 * @param trickplay Trickplay manager instance.
 * @param positionMs Target playback or scrubbing timestamp in milliseconds.
 * @param modifier Layout modifier applied to the thumbnail canvas.
 */
@Composable
fun TrickplayThumbnail(
    trickplay: Trickplay,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val frame = remember(trickplay, positionMs) { trickplay.getFrameInfo(positionMs) } ?: return

    var sheetBitmap by
        remember(trickplay, frame.sheetIndex) {
            mutableStateOf(trickplay.getCachedSheet(frame.sheetIndex))
        }

    LaunchedEffect(trickplay, frame.sheetIndex, positionMs) {
        trickplay.updatePosition(positionMs)
        if (sheetBitmap == null || sheetBitmap?.isRecycled == true) {
            sheetBitmap = trickplay.getSheet(frame.sheetIndex)
        }
    }

    val currentSheet = sheetBitmap
    if (currentSheet != null && !currentSheet.isRecycled) {
        val imageBitmap = remember(currentSheet) { currentSheet.asImageBitmap() }
        Canvas(modifier = modifier) {
            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset(frame.srcX, frame.srcY),
                srcSize = IntSize(frame.srcWidth, frame.srcHeight),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            )
        }
    }
}
