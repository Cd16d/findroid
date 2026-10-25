package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.core.domain.models.SleepTimerMode
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * Sleep timer option selection list for TV player side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param initialFocusRequester [FocusRequester] for setting initial focus on the list.
 * @param onBack Callback to navigate back to the settings menu.
 */
@Composable
internal fun SleepTimerList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val isMovie = uiState.seriesId == null
    val sleepTimerStatus = uiState.sleepTimerStatus

    val options =
        remember(isMovie) {
            listOf(
                TvSleepTimerOption.EndOfMedia,
                TvSleepTimerOption.Minutes(10),
                TvSleepTimerOption.Minutes(15),
                TvSleepTimerOption.Minutes(30),
                TvSleepTimerOption.Minutes(45),
                TvSleepTimerOption.Minutes(60),
                TvSleepTimerOption.Minutes(90),
                TvSleepTimerOption.Minutes(120),
                TvSleepTimerOption.Off,
            )
        }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(items = options) { index, option ->
            val isFirst = index == 0
            val (title, isSelected, chips) =
                when (option) {
                    is TvSleepTimerOption.EndOfMedia -> {
                        val text =
                            if (isMovie)
                                stringResource(id = PlayerR.string.sleep_timer_end_of_video)
                            else stringResource(id = PlayerR.string.sleep_timer_end_of_episode)
                        val selected = sleepTimerStatus.mode is SleepTimerMode.EndOfMedia
                        Triple(text, selected, emptyList())
                    }

                    is TvSleepTimerOption.Minutes -> {
                        val text =
                            stringResource(id = PlayerR.string.sleep_timer_minutes, option.count)
                        val selected =
                            sleepTimerStatus.mode is SleepTimerMode.Duration &&
                                (sleepTimerStatus.mode as SleepTimerMode.Duration).totalMinutes ==
                                    option.count
                        val chipList =
                            if (selected) {
                                val remainingMins = (sleepTimerStatus.remainingSeconds + 59L) / 60L
                                listOf(
                                    stringResource(
                                        id = PlayerR.string.sleep_timer_remaining,
                                        remainingMins.toInt(),
                                    )
                                )
                            } else emptyList()
                        Triple(text, selected, chipList)
                    }

                    is TvSleepTimerOption.Off -> {
                        val text = stringResource(id = PlayerR.string.sleep_timer_off)
                        val selected = !sleepTimerStatus.isActive
                        Triple(text, selected, emptyList())
                    }
                }

            SelectableItem(
                title = title,
                isSelected = isSelected,
                chips = chips,
                onClick = {
                    when (option) {
                        is TvSleepTimerOption.EndOfMedia ->
                            viewModel.setSleepTimer(SleepTimerMode.EndOfMedia)

                        is TvSleepTimerOption.Minutes ->
                            viewModel.setSleepTimer(SleepTimerMode.Duration(option.count))

                        is TvSleepTimerOption.Off -> viewModel.setSleepTimer(SleepTimerMode.Off)
                    }
                    onBack()
                },
                modifier =
                    if (isFirst) Modifier.focusRequester(initialFocusRequester) else Modifier,
            )
        }
    }
}

private sealed interface TvSleepTimerOption {
    data object EndOfMedia : TvSleepTimerOption

    data class Minutes(val count: Int) : TvSleepTimerOption

    data object Off : TvSleepTimerOption
}

@Preview
@Composable
private fun SleepTimerListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableItem(
                title = stringResource(id = PlayerR.string.sleep_timer_end_of_video),
                isSelected = false,
                onClick = {},
            )
            SelectableItem(
                title = stringResource(id = PlayerR.string.sleep_timer_minutes, 30),
                isSelected = true,
                chips = listOf("28m remaining"),
                onClick = {},
            )
            SelectableItem(
                title = stringResource(id = PlayerR.string.sleep_timer_off),
                isSelected = false,
                onClick = {},
            )
        }
    }
}
