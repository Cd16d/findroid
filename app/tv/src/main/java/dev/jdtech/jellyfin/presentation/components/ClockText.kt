package dev.jdtech.jellyfin.presentation.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * Dynamic clock text component for TV UI with automatic minute tick updates.
 *
 * @param modifier Optional modifier applied to clock text layout. Defaults to [Modifier].
 * @param style TextStyle for clock time text. Defaults to `MaterialTheme.typography.titleSmall`.
 * @param color Text color for clock time text. Defaults to `Color.White.copy(alpha = 0.9f)`.
 * @param showDate Whether to display the formatted date underneath time. Defaults to `false`.
 * @param dateStyle TextStyle for date text. Defaults to `MaterialTheme.typography.titleMedium`.
 * @param dateColor Text color for date text. Defaults to `Color.White.copy(alpha = 0.85f)`.
 */
@Composable
fun ClockText(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    color: Color = Color.White.copy(alpha = 0.9f),
    showDate: Boolean = false,
    dateStyle: TextStyle = MaterialTheme.typography.titleMedium,
    dateColor: Color = Color.White.copy(alpha = 0.85f),
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val timeFormatter =
        remember(is24Hour) { DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm") }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    }

    val currentDateTime by
        produceState(initialValue = LocalDateTime.now()) {
            while (true) {
                value = LocalDateTime.now()
                val now = LocalTime.now()
                val millisUntilNextMinute = ((60 - now.second) * 1000L) - (now.nano / 1_000_000L)
                delay(maxOf(1000L, millisUntilNextMinute).milliseconds)
            }
        }

    val timeString =
        remember(currentDateTime, timeFormatter) { currentDateTime.format(timeFormatter) }
    val dateString =
        remember(currentDateTime, dateFormatter) {
            currentDateTime.format(dateFormatter).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }

    if (showDate) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = timeString,
                style = style,
                color = color,
            )
            Text(
                text = dateString,
                style = dateStyle,
                color = dateColor,
            )
        }
    } else {
        Text(
            text = timeString,
            style = style,
            color = color,
            modifier = modifier,
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun ClockTextPreview() {
    FindroidTheme { ClockText(showDate = true) }
}
