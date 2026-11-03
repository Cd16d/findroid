package dev.jdtech.jellyfin.core.presentation.components

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.exp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val GRID_WIDTH = 16
private const val GRID_HEIGHT = 9
private const val PIXEL_COUNT = GRID_WIDTH * GRID_HEIGHT
private const val SAMPLE_INTERVAL_MS = 250L
private const val FRAME_TIMEOUT_MS = 1000L
private const val MORPH_TIME_CONSTANT_SECONDS = 1f

/**
 * Ambient background component that renders a diffused edge glow around the video surface using
 * 9-slice edge extrusion and continuous pixel-level color morphing.
 *
 * @param playerView Active [View] containing the rendering [SurfaceView].
 * @param isPlaying Whether media is currently actively playing.
 * @param enabled Whether ambient mode is enabled.
 * @param modifier [Modifier] applied to the ambient background container.
 * @param ambientAlpha Alpha multiplier for the ambient glow overlay.
 * @param currentPositionMs Current playback position to trigger immediate capture on seek.
 * @param isPanelOpen Whether the side panel is currently open.
 * @param applyBlackBorder Whether to apply edge falloff towards dark borders when shrunk.
 * @param videoMargin Margin surrounding the active video rect to extrude edges into.
 */
@Composable
fun AmbientPlayerBackground(
    playerView: View?,
    isPlaying: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    ambientAlpha: Float = 0.65f,
    currentPositionMs: Long = 0L,
    isPanelOpen: Boolean = false,
    applyBlackBorder: Boolean = false,
    videoMargin: Dp = 300.dp,
) {
    val enabledAlpha by
        animateFloatAsState(
            targetValue = if (enabled) ambientAlpha else 0f,
            animationSpec = tween(durationMillis = 400, easing = LinearEasing),
            label = "ambientEnabledAlpha",
        )

    if (playerView == null || (enabledAlpha <= 0f && !enabled)) return

    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentApplyBlackBorder by rememberUpdatedState(applyBlackBorder)
    val captureMutex = remember { Mutex() }

    // Persistent bitmaps for capture and display
    val captureBitmap = remember { createBitmap(GRID_WIDTH, GRID_HEIGHT) }
    val displayBitmap = remember { createBitmap(GRID_WIDTH, GRID_HEIGHT) }
    val displayImageBitmap = remember(displayBitmap) { displayBitmap.asImageBitmap() }

    // Working pixel buffers
    val capturePixels = remember { IntArray(PIXEL_COUNT) }
    val blurredTargetPixels = remember { IntArray(PIXEL_COUNT) }
    val tempPixels = remember { IntArray(PIXEL_COUNT) }
    val displayPixels = remember { IntArray(PIXEL_COUNT) }

    // Target color channel values set by periodic sampling
    val targetR = remember { FloatArray(PIXEL_COUNT) }
    val targetG = remember { FloatArray(PIXEL_COUNT) }
    val targetB = remember { FloatArray(PIXEL_COUNT) }

    // Current displayed color channel values interpolated on each display frame
    val currentR = remember { FloatArray(PIXEL_COUNT) }
    val currentG = remember { FloatArray(PIXEL_COUNT) }
    val currentB = remember { FloatArray(PIXEL_COUNT) }

    var hasDrawnFirstFrame by remember { mutableStateOf(false) }
    var frameRevision by remember { mutableLongStateOf(0L) }

    var copyHandler by remember { mutableStateOf<Handler?>(null) }

    DisposableEffect(Unit) {
        val copyThread = HandlerThread("FindroidAmbientPixelCopy").apply { start() }
        copyHandler = Handler(copyThread.looper)
        onDispose {
            copyThread.quitSafely()
            copyHandler = null
            if (!captureBitmap.isRecycled) captureBitmap.recycle()
            if (!displayBitmap.isRecycled) displayBitmap.recycle()
        }
    }

    suspend fun captureAndSetTarget(immediate: Boolean = false) {
        if (!captureMutex.tryLock()) return
        try {
            val handler = copyHandler ?: return
            val surfaceView = findSurfaceView(playerView)
            if (
                surfaceView != null &&
                    surfaceView.holder.surface.isValid &&
                    surfaceView.width > 0 &&
                    surfaceView.height > 0
            ) {
                val success = requestTinyFrame(surfaceView, captureBitmap, handler)
                if (success && !captureBitmap.isRecycled) {
                    withContext(Dispatchers.Default) {
                        captureBitmap.getPixels(
                            capturePixels,
                            0,
                            GRID_WIDTH,
                            0,
                            0,
                            GRID_WIDTH,
                            GRID_HEIGHT,
                        )

                        // Apply separable blur to the captured frame
                        applyFastSeparableBlur(
                            src = capturePixels,
                            dst = blurredTargetPixels,
                            temp = tempPixels,
                        )

                        // Update target color channels
                        for (i in 0 until PIXEL_COUNT) {
                            val c = blurredTargetPixels[i]
                            val r = (c ushr 16 and 0xFF).toFloat()
                            val g = (c ushr 8 and 0xFF).toFloat()
                            val b = (c and 0xFF).toFloat()

                            targetR[i] = r
                            targetG[i] = g
                            targetB[i] = b

                            if (immediate || !hasDrawnFirstFrame) {
                                currentR[i] = r
                                currentG[i] = g
                                currentB[i] = b
                                displayPixels[i] =
                                    (0xFF shl 24) or
                                        (r.toInt().coerceIn(0, 255) shl 16) or
                                        (g.toInt().coerceIn(0, 255) shl 8) or
                                        b.toInt().coerceIn(0, 255)
                            }
                        }

                        if (immediate || !hasDrawnFirstFrame) {
                            withContext(Dispatchers.Main.immediate) {
                                if (!displayBitmap.isRecycled) {
                                    displayBitmap.setPixels(
                                        displayPixels,
                                        0,
                                        GRID_WIDTH,
                                        0,
                                        0,
                                        GRID_WIDTH,
                                        GRID_HEIGHT,
                                    )
                                    hasDrawnFirstFrame = true
                                    frameRevision++
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            captureMutex.unlock()
        }
    }

    // Continuous pixel interpolation loop running on display VSync
    LaunchedEffect(enabled, isPlaying) {
        if (!enabled) return@LaunchedEffect
        var lastFrameTimeNanos = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = now
                    return@withFrameNanos
                }
                val dt = ((now - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                lastFrameTimeNanos = now

                if (!hasDrawnFirstFrame) return@withFrameNanos

                val factor = 1f - exp(-dt / MORPH_TIME_CONSTANT_SECONDS)
                var hasChanges = false

                for (i in 0 until PIXEL_COUNT) {
                    val tR = targetR[i]
                    val tG = targetG[i]
                    val tB = targetB[i]

                    val cR = currentR[i]
                    val cG = currentG[i]
                    val cB = currentB[i]

                    val diffR = tR - cR
                    val diffG = tG - cG
                    val diffB = tB - cB

                    if (abs(diffR) > 0.25f || abs(diffG) > 0.25f || abs(diffB) > 0.25f) {
                        hasChanges = true
                        val nR = cR + diffR * factor
                        val nG = cG + diffG * factor
                        val nB = cB + diffB * factor

                        currentR[i] = nR
                        currentG[i] = nG
                        currentB[i] = nB

                        displayPixels[i] =
                            (0xFF shl 24) or
                                (nR.toInt().coerceIn(0, 255) shl 16) or
                                (nG.toInt().coerceIn(0, 255) shl 8) or
                                nB.toInt().coerceIn(0, 255)
                    }
                }

                if (hasChanges && !displayBitmap.isRecycled) {
                    displayBitmap.setPixels(
                        displayPixels,
                        0,
                        GRID_WIDTH,
                        0,
                        0,
                        GRID_WIDTH,
                        GRID_HEIGHT,
                    )
                    frameRevision++
                }
            }
        }
    }

    // Sampling loop capturing frames at regular intervals
    LaunchedEffect(playerView, enabled, isPlaying, applyBlackBorder) {
        if (!enabled) return@LaunchedEffect

        captureAndSetTarget(immediate = true)

        coroutineScope {
            // Re-capture immediately on position seeks
            launch {
                var lastPos = currentPositionMs
                snapshotFlow { currentPositionMs }
                    .collect { pos ->
                        if (abs(pos - lastPos) > 1500L) {
                            lastPos = pos
                            delay(100.milliseconds)
                            captureAndSetTarget(immediate = true)
                        } else {
                            lastPos = pos
                        }
                    }
            }

            // Periodic sampling loop
            while (isActive) {
                delay(SAMPLE_INTERVAL_MS.milliseconds)
                if (currentIsPlaying) {
                    captureAndSetTarget(immediate = false)
                }
            }
        }
    }

    Box(modifier = modifier) {
        if (hasDrawnFirstFrame) {
            Canvas(
                modifier =
                    Modifier.matchParentSize()
                        .alpha(enabledAlpha)
                        .then(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                Modifier.blur(64.dp)
                            } else {
                                Modifier
                            }
                        )
            ) {
                // Read revision state to trigger redraw when pixel buffer updates
                val rev = frameRevision
                if (rev >= 0L && !displayBitmap.isRecycled) {
                    val marginXPx = videoMargin.toPx().coerceAtMost(size.width * 0.4f)
                    val marginYPx = videoMargin.toPx().coerceAtMost(size.height * 0.4f)
                    val videoWidthPx = (size.width - 2 * marginXPx).coerceAtLeast(1f)
                    val videoHeightPx = (size.height - 2 * marginYPx).coerceAtLeast(1f)

                    drawExtrudedAmbient(
                        image = displayImageBitmap,
                        alpha = 1f,
                        marginX = marginXPx,
                        marginY = marginYPx,
                        videoWidth = videoWidthPx,
                        videoHeight = videoHeightPx,
                    )

                    // Apply edge falloff gradients when shrunk
                    if (currentApplyBlackBorder) {
                        if (marginYPx > 0f) {
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = 0f,
                                        endY = marginYPx,
                                    ),
                                topLeft = Offset.Zero,
                                size = Size(size.width, marginYPx),
                            )
                        }
                        val bottomY = marginYPx + videoHeightPx
                        val bottomH = size.height - bottomY
                        if (bottomH > 0f) {
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startY = bottomY,
                                        endY = size.height,
                                    ),
                                topLeft = Offset(0f, bottomY),
                                size = Size(size.width, bottomH),
                            )
                        }
                        if (marginXPx > 0f) {
                            drawRect(
                                brush =
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startX = 0f,
                                        endX = marginXPx,
                                    ),
                                topLeft = Offset.Zero,
                                size = Size(marginXPx, size.height),
                            )
                        }
                        val rightX = marginXPx + videoWidthPx
                        val rightW = size.width - rightX
                        if (rightW > 0f) {
                            drawRect(
                                brush =
                                    Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startX = rightX,
                                        endX = size.width,
                                    ),
                                topLeft = Offset(rightX, 0f),
                                size = Size(rightW, size.height),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders the ambient texture using 9-slice edge extrusion:
 * - Center is mapped 1:1 behind the video.
 * - Top, bottom, left, and right outer margins are filled by stretching only the respective outer
 *   edge pixel slices.
 */
private fun DrawScope.drawExtrudedAmbient(
    image: ImageBitmap,
    alpha: Float,
    marginX: Float,
    marginY: Float,
    videoWidth: Float,
    videoHeight: Float,
) {
    val srcW = image.width
    val srcH = image.height
    if (srcW <= 0 || srcH <= 0) return

    val mX = marginX.toInt()
    val mY = marginY.toInt()
    val vW = videoWidth.toInt()
    val vH = videoHeight.toInt()

    val totalW = size.width.toInt()
    val totalH = size.height.toInt()

    val rightEdgeX = mX + vW
    val bottomEdgeY = mY + vH

    val rightMarginW = (totalW - rightEdgeX).coerceAtLeast(0)
    val bottomMarginH = (totalH - bottomEdgeY).coerceAtLeast(0)

    // 1. Center
    drawImage(
        image = image,
        srcOffset = IntOffset(0, 0),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(mX, mY),
        dstSize = IntSize(vW, vH),
        alpha = alpha,
        filterQuality = FilterQuality.Medium,
    )

    // 2. Top Edge Extrusion
    if (mY > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(srcW, 1),
            dstOffset = IntOffset(mX, 0),
            dstSize = IntSize(vW, mY),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }

    // 3. Bottom Edge Extrusion
    if (bottomMarginH > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset(0, srcH - 1),
            srcSize = IntSize(srcW, 1),
            dstOffset = IntOffset(mX, bottomEdgeY),
            dstSize = IntSize(vW, bottomMarginH),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }

    // 4. Left Edge Extrusion
    if (mX > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(1, srcH),
            dstOffset = IntOffset(0, mY),
            dstSize = IntSize(mX, vH),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }

    // 5. Right Edge Extrusion
    if (rightMarginW > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset(srcW - 1, 0),
            srcSize = IntSize(1, srcH),
            dstOffset = IntOffset(rightEdgeX, mY),
            dstSize = IntSize(rightMarginW, vH),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }

    // 6. Top-Left Corner
    if (mX > 0 && mY > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset(0, 0),
            srcSize = IntSize(1, 1),
            dstOffset = IntOffset(0, 0),
            dstSize = IntSize(mX, mY),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }

    // 7. Top-Right Corner
    if (rightMarginW > 0 && mY > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset(srcW - 1, 0),
            srcSize = IntSize(1, 1),
            dstOffset = IntOffset(rightEdgeX, 0),
            dstSize = IntSize(rightMarginW, mY),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }

    // 8. Bottom-Left Corner
    if (mX > 0 && bottomMarginH > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset(0, srcH - 1),
            srcSize = IntSize(1, 1),
            dstOffset = IntOffset(0, bottomEdgeY),
            dstSize = IntSize(mX, bottomMarginH),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }

    // 9. Bottom-Right Corner
    if (rightMarginW > 0 && bottomMarginH > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset(srcW - 1, srcH - 1),
            srcSize = IntSize(1, 1),
            dstOffset = IntOffset(rightEdgeX, bottomEdgeY),
            dstSize = IntSize(rightMarginW, bottomMarginH),
            alpha = alpha,
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** Recursively inspects the view hierarchy to locate the rendering [SurfaceView]. */
private fun findSurfaceView(view: View): SurfaceView? {
    if (view is SurfaceView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val found = findSurfaceView(view.getChildAt(i))
            if (found != null) return found
        }
    }
    return null
}

private suspend fun requestTinyFrame(
    surfaceView: SurfaceView,
    destination: Bitmap,
    handler: Handler,
): Boolean =
    withTimeoutOrNull(FRAME_TIMEOUT_MS.milliseconds) {
        suspendCancellableCoroutine { cont ->
            if (destination.isRecycled) {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            try {
                PixelCopy.request(
                    surfaceView,
                    destination,
                    { result ->
                        if (cont.isActive) {
                            cont.resume(result == PixelCopy.SUCCESS)
                        }
                    },
                    handler,
                )
            } catch (_: Exception) {
                if (cont.isActive) {
                    cont.resume(false)
                }
            }
        }
    } ?: false

/** Two-pass separable box blur applied to the working pixel array. */
private fun applyFastSeparableBlur(
    src: IntArray,
    dst: IntArray,
    temp: IntArray,
) {
    val passes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 1 else 2
    var input = src
    val output = dst

    repeat(passes) { pass ->
        // Horizontal blur pass
        for (y in 0 until GRID_HEIGHT) {
            val rowOffset = y * GRID_WIDTH
            for (x in 0 until GRID_WIDTH) {
                val x0 = if (x > 0) x - 1 else 0
                val x1 = x
                val x2 = if (x < GRID_WIDTH - 1) x + 1 else GRID_WIDTH - 1

                val c0 = input[rowOffset + x0]
                val c1 = input[rowOffset + x1]
                val c2 = input[rowOffset + x2]

                val a =
                    (((c0 ushr 24) and 0xFF) + ((c1 ushr 24) and 0xFF) + ((c2 ushr 24) and 0xFF)) /
                        3
                val r =
                    (((c0 ushr 16) and 0xFF) + ((c1 ushr 16) and 0xFF) + ((c2 ushr 16) and 0xFF)) /
                        3
                val g =
                    (((c0 ushr 8) and 0xFF) + ((c1 ushr 8) and 0xFF) + ((c2 ushr 8) and 0xFF)) / 3
                val b = ((c0 and 0xFF) + (c1 and 0xFF) + (c2 and 0xFF)) / 3

                temp[rowOffset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // Vertical blur pass
        for (x in 0 until GRID_WIDTH) {
            for (y in 0 until GRID_HEIGHT) {
                val y0 = if (y > 0) y - 1 else 0
                val y1 = y
                val y2 = if (y < GRID_HEIGHT - 1) y + 1 else GRID_HEIGHT - 1

                val c0 = temp[y0 * GRID_WIDTH + x]
                val c1 = temp[y1 * GRID_WIDTH + x]
                val c2 = temp[y2 * GRID_WIDTH + x]

                val a =
                    (((c0 ushr 24) and 0xFF) + ((c1 ushr 24) and 0xFF) + ((c2 ushr 24) and 0xFF)) /
                        3
                val r =
                    (((c0 ushr 16) and 0xFF) + ((c1 ushr 16) and 0xFF) + ((c2 ushr 16) and 0xFF)) /
                        3
                val g =
                    (((c0 ushr 8) and 0xFF) + ((c1 ushr 8) and 0xFF) + ((c2 ushr 8) and 0xFF)) / 3
                val b = ((c0 and 0xFF) + (c1 and 0xFF) + (c2 and 0xFF)) / 3

                output[y * GRID_WIDTH + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        if (pass == 0 && passes > 1) {
            input = output
        }
    }
}
