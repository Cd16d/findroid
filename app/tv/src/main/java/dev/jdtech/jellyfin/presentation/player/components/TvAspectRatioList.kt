package dev.jdtech.jellyfin.presentation.player.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerResizeMode

/**
 * Playback speed selection list for TV player side panel.
 *
 * @param viewModel Active [PlayerViewModel].
 * @param initialFocusRequester [FocusRequester] for setting initial focus on the list.
 * @param onBack Callback to navigate back to the settings menu.
 */
@Composable
internal fun SpeedList(
    viewModel: PlayerViewModel,
    initialFocusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val speedOptions = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f) }
    val currentSpeed = viewModel.playbackSpeed

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(
            items = speedOptions,
            key = { _, speed -> speed },
        ) { index, speed ->
            val isSelected = currentSpeed == speed
            val label =
                if (speed == 1.0f) stringResource(id = PlayerR.string.normal_speed) else "${speed}×"
            val isFirst = index == 0

            SelectableItem(
                title = label,
                isSelected = isSelected,
                onClick = {
                    viewModel.selectSpeed(speed)
                    onBack()
                },
                modifier =
                    if (isFirst) Modifier.focusRequester(initialFocusRequester) else Modifier,
            )
        }
    }
}

/**
 * Aspect ratio / zoom mode selection list for TV player side panel.
 *
 * @param currentResizeMode Currently active [PlayerResizeMode].
 * @param isMpv Whether mpv player backend is active.
 * @param onResizeModeChange Callback when a new resize mode is selected.
 * @param initialFocusRequester [FocusRequester] for setting initial focus on the list.
 * @param onBack Callback to navigate back to the settings menu.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun AspectRatioList(
    currentResizeMode: PlayerResizeMode,
    isMpv: Boolean = false,
    onResizeModeChange: (PlayerResizeMode) -> Unit,
    initialFocusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val resizeModes = remember {
        listOf(
                PlayerResizeMode.FIT to PlayerR.string.aspect_ratio_fit,
                PlayerResizeMode.ZOOM to PlayerR.string.aspect_ratio_zoom,
                PlayerResizeMode.FILL to PlayerR.string.aspect_ratio_fill,
            )
            .let { modes -> if (isMpv) modes.dropLast(1) else modes }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(
            items = resizeModes,
            key = { _, (mode, _) -> mode },
        ) { index, (mode, labelRes) ->
            val isSelected = currentResizeMode == mode
            val isFirst = index == 0

            SelectableItem(
                title = stringResource(id = labelRes),
                isSelected = isSelected,
                onClick = {
                    onResizeModeChange(mode)
                    onBack()
                },
                modifier =
                    if (isFirst) Modifier.focusRequester(initialFocusRequester) else Modifier,
            )
        }
    }
}

@Preview
@Composable
private fun SpeedListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableItem(
                title = stringResource(id = PlayerR.string.normal_speed),
                isSelected = true,
                onClick = {},
            )
            SelectableItem(
                title = "1.25×",
                isSelected = false,
                onClick = {},
            )
            SelectableItem(
                title = "1.5×",
                isSelected = false,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun AspectRatioListPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.width(320.dp).background(Color.Black).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectableItem(
                title = stringResource(id = PlayerR.string.aspect_ratio_fit),
                isSelected = true,
                onClick = {},
            )
            SelectableItem(
                title = stringResource(id = PlayerR.string.aspect_ratio_zoom),
                isSelected = false,
                onClick = {},
            )
            SelectableItem(
                title = stringResource(id = PlayerR.string.aspect_ratio_fill),
                isSelected = false,
                onClick = {},
            )
        }
    }
}
