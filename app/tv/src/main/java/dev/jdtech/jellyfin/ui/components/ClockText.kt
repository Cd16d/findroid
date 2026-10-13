package dev.jdtech.jellyfin.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun ClockText(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    color: Color = Color.White.copy(alpha = 0.9f),
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val formatter =
        remember(is24Hour) { DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm") }
    val currentTime by
        produceState(initialValue = LocalTime.now().format(formatter)) {
            while (true) {
                value = LocalTime.now().format(formatter)
                val now = LocalTime.now()
                val millisUntilNextMinute = ((60 - now.second) * 1000L) - (now.nano / 1_000_000L)
                delay(maxOf(1000L, millisUntilNextMinute))
            }
        }

    Text(
        text = currentTime,
        style = style,
        color = color,
        modifier = modifier,
    )
}
