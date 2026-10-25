package dev.jdtech.jellyfin.player.core.presentation

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Helpers for formatting and rendering telemetry stat graphs. */
object PlaybackInfoFormatter {
    fun formatStatMax(maxVal: Float, unit: String): String =
        String.format(Locale.US, "max: %.1f %s", maxVal, unit).trim()

    fun formatStatLatest(latestVal: Float, unit: String): String =
        String.format(Locale.US, "%.1f %s", latestVal, unit).trim()
}

/** Draws a minimalist line and filled area chart of historical data within a [DrawScope]. */
fun DrawScope.drawStatGraph(
    data: List<Float>,
    maxVal: Float,
    lineColor: Color,
    strokeWidth: Dp = 2.dp,
    verticalPadding: Dp = 3.dp,
    historyCapacity: Int = 60,
) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f || data.isEmpty()) return

    val stepX = width / (historyCapacity - 1).toFloat()
    val strokePath = Path()
    val fillPath = Path()

    val vPadPx = verticalPadding.toPx()
    val usableHeight = height - (vPadPx * 2)

    var firstX = width
    data.forEachIndexed { i, value ->
        val secondsAgo = (data.size - 1) - i
        val x = (width - (secondsAgo * stepX)).coerceIn(0f, width)
        val normalizedY = if (maxVal > 0f) (value / maxVal).coerceIn(0f, 1f) else 0f
        val y = height - (normalizedY * usableHeight) - vPadPx

        if (i == 0) {
            firstX = x
            strokePath.moveTo(x, y)
            fillPath.moveTo(x, height)
            fillPath.lineTo(x, y)
        } else {
            strokePath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }

    fillPath.lineTo(width, height)
    fillPath.lineTo(firstX, height)
    fillPath.close()

    drawPath(
        path = fillPath,
        brush =
            Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent),
                startY = 0f,
                endY = height,
            ),
    )
    drawPath(
        path = strokePath,
        color = lineColor,
        style = Stroke(width = strokeWidth.toPx()),
    )
}
