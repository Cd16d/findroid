package dev.jdtech.jellyfin.core.presentation.components

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val INNER_WIDTH = 16
private const val INNER_HEIGHT = 12
private const val BORDER_PX = 3
private const val PAUSE_BETWEEN_CAPTURES_MS = 1000L
private const val PLAYBACK_MORPH_DURATION_MS = 800
private const val BORDER_TOGGLE_MORPH_DURATION_MS = 150
private const val FRAME_TIMEOUT_MS = 300L

/**
 * YouTube-style ambient background component that captures tiny frames from the video's
 * [SurfaceView] via [PixelCopy] and renders a soft, zoomed ambient glow around the video surface.
 *
 * @param playerView Active [View] containing the rendering [SurfaceView].
 * @param isPlaying Whether media is currently actively playing.
 * @param enabled Whether ambient mode is enabled.
 * @param modifier [Modifier] applied to the ambient background container.
 * @param ambientAlpha Alpha multiplier for the ambient glow overlay.
 * @param currentPositionMs Current playback position to trigger immediate capture on seek.
 * @param isPanelOpen Whether the side panel is currently open.
 * @param applyBlackBorder Whether to append a dark border padding around captured frames to ensure
 *   edge falloff when shrunk.
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
) {
    val enabledAlpha by
        animateFloatAsState(
            targetValue = if (enabled) ambientAlpha else 0f,
            animationSpec = tween(durationMillis = 500, easing = LinearEasing),
            label = "ambientEnabledAlpha",
        )

    if (playerView == null || (enabledAlpha <= 0f && !enabled)) return

    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentApplyBlackBorder by rememberUpdatedState(applyBlackBorder)
    var lastBorderState by remember { mutableStateOf(applyBlackBorder) }
    var previousImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var currentImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val transitionProgress = remember { Animatable(1f) }
    val captureMutex = remember { Mutex() }

    var copyHandler by remember { mutableStateOf<Handler?>(null) }

    DisposableEffect(Unit) {
        val copyThread = HandlerThread("FindroidAmbientPixelCopy").apply { start() }
        copyHandler = Handler(copyThread.looper)
        onDispose {
            copyThread.quitSafely()
            copyHandler = null
        }
    }

    suspend fun captureAndUpdateFrame() {
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
                val captured = requestTinyFrame(surfaceView, handler)
                if (captured != null) {
                    val isBorderToggle = currentApplyBlackBorder != lastBorderState
                    lastBorderState = currentApplyBlackBorder

                    val animDuration =
                        if (isBorderToggle) BORDER_TOGGLE_MORPH_DURATION_MS
                        else PLAYBACK_MORPH_DURATION_MS

                    val newImage =
                        withContext(Dispatchers.Default) {
                            val framed =
                                if (currentApplyBlackBorder) addBlackBorder(captured) else captured
                            val processedBitmap = softBlurTinyBitmap(framed)
                            if (framed != processedBitmap) framed.recycle()
                            processedBitmap.asImageBitmap()
                        }

                    val oldImage = currentImageBitmap
                    previousImageBitmap = oldImage
                    currentImageBitmap = newImage

                    if (oldImage == null) {
                        transitionProgress.snapTo(1f)
                        previousImageBitmap = null
                    } else {
                        transitionProgress.snapTo(0f)
                        try {
                            transitionProgress.animateTo(
                                targetValue = 1f,
                                animationSpec =
                                    tween(
                                        durationMillis = animDuration,
                                        easing = LinearEasing,
                                    ),
                            )
                        } catch (_: CancellationException) {
                            transitionProgress.snapTo(1f)
                        } finally {
                            previousImageBitmap = null
                        }
                    }
                }
            }
        } finally {
            captureMutex.unlock()
        }
    }

    // Unified LaunchedEffect handling startup, border changes, seek polling, and periodic captures
    LaunchedEffect(playerView, enabled, isPlaying, applyBlackBorder) {
        if (!enabled) return@LaunchedEffect

        // Immediate frame capture on enable or border state change (e.g. side panel toggle), even
        // if paused

        // if paused
        captureAndUpdateFrame()

        coroutineScope {
            // Observe position seeks (>1500ms jump)
            launch {
                var lastPos = currentPositionMs
                snapshotFlow { currentPositionMs }
                    .collect { pos ->
                        if (abs(pos - lastPos) > 1500L) {
                            lastPos = pos
                            // Give ExoPlayer / mpv decoder 120ms to render the seeked keyframe onto
                            //                            captureAndUpdateFrame()
                        } else {
                            lastPos = pos
                        }
                    }
            }

            // Periodic sampling ticker loop
            while (isActive) {
                delay(PAUSE_BETWEEN_CAPTURES_MS.milliseconds)
                if (currentIsPlaying) {
                    captureAndUpdateFrame()
                }
            }
        }
    }

    Box(modifier = modifier) {
        val prev = previousImageBitmap
        val curr = currentImageBitmap
        val progress = transitionProgress.value

        if (curr != null || prev != null) {
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
                if (prev != null) {
                    drawImage(
                        image = prev,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(prev.width, prev.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }

                if (curr != null) {
                    drawImage(
                        image = curr,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(curr.width, curr.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                        alpha = if (prev != null) progress else 1f,
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
        }
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
    handler: Handler,
): Bitmap? =
    withTimeoutOrNull(FRAME_TIMEOUT_MS.milliseconds) {
        suspendCancellableCoroutine { cont ->
            val bitmap = createBitmap(INNER_WIDTH, INNER_HEIGHT)
            cont.invokeOnCancellation {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            try {
                PixelCopy.request(
                    surfaceView,
                    bitmap,
                    { result ->
                        if (cont.isActive) {
                            if (result == PixelCopy.SUCCESS) {
                                cont.resume(bitmap)
                            } else {
                                bitmap.recycle()
                                cont.resume(null)
                            }
                        } else {
                            bitmap.recycle()
                        }
                    },
                    handler,
                )
            } catch (_: Exception) {
                bitmap.recycle()
                if (cont.isActive) {
                    cont.resume(null)
                }
            }
        }
    }

/**
 * Adds a black border around the source bitmap so that subsequent blur operations naturally blend
 * the image edges outwards towards dark background without losing original edge colors.
 */
private fun addBlackBorder(src: Bitmap): Bitmap {
    val paddedWidth = src.width + BORDER_PX * 2
    val paddedHeight = src.height + BORDER_PX * 2
    val padded = createBitmap(paddedWidth, paddedHeight)
    val canvas = android.graphics.Canvas(padded)
    canvas.drawColor(android.graphics.Color.BLACK)
    canvas.drawBitmap(src, BORDER_PX.toFloat(), BORDER_PX.toFloat(), null)
    src.recycle()
    return padded
}

/**
 * Fast multi-pass box-blur on tiny padded bitmap to provide soft color blending and transparent
 * edge falloff even on Android < 12 (API < 31).
 */
private fun softBlurTinyBitmap(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    var pixels = IntArray(w * h)
    var out = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)

    val passes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 1 else 3
    val radius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 2 else 3

    repeat(passes) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    for (dx in -radius..radius) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val c = pixels[ny * w + nx]
                        a += (c ushr 24) and 0xFF
                        r += (c ushr 16) and 0xFF
                        g += (c ushr 8) and 0xFF
                        b += c and 0xFF
                        count++
                    }
                }
                out[y * w + x] =
                    ((a / count) shl 24) or
                        ((r / count) shl 16) or
                        ((g / count) shl 8) or
                        (b / count)
            }
        }
        val temp = pixels
        pixels = out
        out = temp
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}
