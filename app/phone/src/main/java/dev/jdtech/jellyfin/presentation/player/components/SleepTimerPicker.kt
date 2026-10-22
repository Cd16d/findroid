package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.CentralShapes
import dev.jdtech.jellyfin.core.presentation.theme.PlayerCardBackground
import dev.jdtech.jellyfin.core.presentation.theme.PlayerOverlayBackground
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerMode
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerStatus
import dev.jdtech.jellyfin.presentation.components.SelectableTrackItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

private val CardShape = CentralShapes.LargeCard

private val Presets = listOf(10, 15, 30, 45, 60)

/**
 * Sleep timer configuration screen featuring a custom minutes stepper (- / +) and preset durations,
 * including "End of episode".
 *
 * @param sleepTimerStatus Current status of the sleep timer.
 * @param onSelectMode Callback when a timer mode is selected.
 * @param isMovie Whether currently playing media is a movie rather than a series episode.
 * @param modifier Optional [Modifier].
 */
@Composable
fun SleepTimerPicker(
    sleepTimerStatus: SleepTimerStatus,
    onSelectMode: (SleepTimerMode) -> Unit,
    modifier: Modifier = Modifier,
    isMovie: Boolean = false,
) {
    val mode = sleepTimerStatus.mode

    var customMinutes by remember {
        val initial =
            when (mode) {
                is SleepTimerMode.Duration -> mode.totalMinutes
                else -> 30
            }
        mutableIntStateOf(initial)
    }

    LaunchedEffect(mode) {
        if (mode is SleepTimerMode.Duration) {
            customMinutes = mode.totalMinutes
        }
    }

    val currentMins =
        if (mode is SleepTimerMode.Duration || mode is SleepTimerMode.EndOfMedia) {
            maxOf(0, ((sleepTimerStatus.remainingSeconds + 59L) / 60L).toInt())
        } else {
            customMinutes
        }

    val isActive = sleepTimerStatus.isActive

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Custom Stepper Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                color = PlayerCardBackground,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Minus Button
                        val canDecrease = mode !is SleepTimerMode.EndOfMedia && currentMins > 1
                        Box(
                            modifier =
                                Modifier.size(46.dp)
                                    .clip(CircleShape)
                                    .background(PlayerOverlayBackground)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true, radius = 23.dp),
                                        enabled = canDecrease,
                                        onClick = {
                                            if (mode is SleepTimerMode.Duration) {
                                                val newMins = currentMins - 1
                                                customMinutes = newMins
                                                onSelectMode(SleepTimerMode.Duration(newMins))
                                            } else if (customMinutes > 1) {
                                                customMinutes--
                                            }
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = CoreR.drawable.ic_minus),
                                contentDescription = "-",
                                modifier = Modifier.size(20.dp),
                                tint =
                                    if (canDecrease) Color.White
                                    else Color.White.copy(alpha = 0.3f),
                            )
                        }

                        // Center: Big number and "min" in small
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "$currentMins",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text(
                                    text =
                                        stringResource(id = PlayerR.string.sleep_timer_min_short),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                            if (mode is SleepTimerMode.EndOfMedia) {
                                Text(
                                    text =
                                        if (isMovie) {
                                            stringResource(
                                                id = PlayerR.string.sleep_timer_end_of_video
                                            )
                                        } else {
                                            stringResource(
                                                id = PlayerR.string.sleep_timer_end_of_episode
                                            )
                                        },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // Plus Button
                        val canIncrease = mode !is SleepTimerMode.EndOfMedia && currentMins < 300
                        Box(
                            modifier =
                                Modifier.size(46.dp)
                                    .clip(CircleShape)
                                    .background(PlayerOverlayBackground)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = true, radius = 23.dp),
                                        enabled = canIncrease,
                                        onClick = {
                                            if (mode is SleepTimerMode.Duration) {
                                                val newMins = currentMins + 1
                                                customMinutes = newMins
                                                onSelectMode(SleepTimerMode.Duration(newMins))
                                            } else if (customMinutes < 300) {
                                                customMinutes++
                                            }
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = CoreR.drawable.ic_plus),
                                contentDescription = "+",
                                modifier = Modifier.size(20.dp),
                                tint =
                                    if (canIncrease) Color.White
                                    else Color.White.copy(alpha = 0.3f),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    ElevatedButton(
                        onClick = {
                            if (isActive) {
                                onSelectMode(SleepTimerMode.Off)
                            } else {
                                if (mode is SleepTimerMode.EndOfMedia) {
                                    onSelectMode(SleepTimerMode.EndOfMedia)
                                } else {
                                    onSelectMode(SleepTimerMode.Duration(customMinutes))
                                }
                            }
                        },
                        colors =
                            ButtonDefaults.elevatedButtonColors(
                                containerColor =
                                    if (isActive) MaterialTheme.colorScheme.primary
                                    else PlayerOverlayBackground,
                                contentColor =
                                    if (isActive) MaterialTheme.colorScheme.onPrimary
                                    else Color.White,
                            ),
                        shape = CircleShape,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                    ) {
                        Text(
                            text =
                                if (isActive) {
                                    stringResource(id = PlayerR.string.sleep_timer_turn_off)
                                } else {
                                    stringResource(id = PlayerR.string.sleep_timer_start)
                                },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Preset: End of Episode / Video
        item {
            val isEndOfMediaSelected = mode is SleepTimerMode.EndOfMedia
            val endOfMediaTitle =
                if (isMovie) {
                    stringResource(id = PlayerR.string.sleep_timer_end_of_video)
                } else {
                    stringResource(id = PlayerR.string.sleep_timer_end_of_episode)
                }
            SelectableTrackItem(
                title = endOfMediaTitle,
                chips = emptyList(),
                isSelected = isEndOfMediaSelected,
                shape = MenuSingleShape,
                onClick = { onSelectMode(SleepTimerMode.EndOfMedia) },
            )
        }

        // Presets: 10, 15, 30, 45, 60 minutes
        items(Presets) { minutes ->
            val currentMode = sleepTimerStatus.mode
            val isSelected =
                currentMode is SleepTimerMode.Duration && currentMode.totalMinutes == minutes
            val title = stringResource(id = PlayerR.string.sleep_timer_minutes, minutes)

            SelectableTrackItem(
                title = title,
                chips = emptyList(),
                isSelected = isSelected,
                shape = MenuSingleShape,
                onClick = { onSelectMode(SleepTimerMode.Duration(minutes)) },
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141416)
@Composable
private fun SleepTimerPickerOffPreview() {
    FindroidTheme {
        Surface(color = PlayerCardBackground) {
            SleepTimerPicker(
                sleepTimerStatus = SleepTimerStatus(mode = SleepTimerMode.Off),
                onSelectMode = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF141416)
@Composable
private fun SleepTimerPickerActivePreview() {
    FindroidTheme {
        Surface(color = PlayerCardBackground) {
            SleepTimerPicker(
                sleepTimerStatus =
                    SleepTimerStatus(
                        mode = SleepTimerMode.Duration(30),
                        remainingSeconds = 1140L,
                    ),
                onSelectMode = {},
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
