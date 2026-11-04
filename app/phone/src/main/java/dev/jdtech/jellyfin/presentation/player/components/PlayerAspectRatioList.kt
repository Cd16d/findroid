package dev.jdtech.jellyfin.presentation.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.player.core.R as PlayerR
import dev.jdtech.jellyfin.presentation.components.SelectableTrackItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.Constants.PlayerResizeMode

/**
 * Aspect ratio / zoom mode picker list for phone bottom sheet.
 *
 * @param currentResizeMode Currently selected [PlayerResizeMode].
 * @param isMpv Whether mpv player backend is active.
 * @param onResizeModeSelected Callback when a resize mode is selected.
 */
@Composable
internal fun PhoneAspectRatioList(
    currentResizeMode: PlayerResizeMode,
    isMpv: Boolean = false,
    onResizeModeSelected: (PlayerResizeMode) -> Unit,
) {
    val modes =
        listOf(
            PlayerResizeMode.FIT to stringResource(id = PlayerR.string.aspect_ratio_fit),
            PlayerResizeMode.ZOOM to stringResource(id = PlayerR.string.aspect_ratio_zoom),
            PlayerResizeMode.FILL to stringResource(id = PlayerR.string.aspect_ratio_fill),
        )
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(modes) { _, (mode, label) ->
            val isSelected = currentResizeMode == mode
            SelectableTrackItem(
                title = label,
                chips = emptyList(),
                isSelected = isSelected,
                onClick = { onResizeModeSelected(mode) },
            )
        }
    }
}

/**
 * Aspect ratio / zoom mode picker list for tablet side panel.
 *
 * @param currentResizeMode Currently selected [AspectRatioFrameLayout] resize mode.
 * @param isMpv Whether mpv player backend is active.
 * @param onResizeModeSelected Callback when a resize mode is picked.
 */
@Composable
internal fun TabletAspectRatioList(
    currentResizeMode: PlayerResizeMode,
    isMpv: Boolean = false,
    onResizeModeSelected: (PlayerResizeMode) -> Unit,
) {
    PhoneAspectRatioList(
        currentResizeMode = currentResizeMode,
        isMpv = isMpv,
        onResizeModeSelected = onResizeModeSelected,
    )
}

@Preview(
    name = "Phone Aspect Ratio List",
    showBackground = true,
    backgroundColor = 0xFF141416,
    widthDp = 360,
)
@Composable
private fun PhoneAspectRatioListPreview() {
    FindroidTheme(darkTheme = true) {
        PhoneAspectRatioList(
            currentResizeMode = PlayerResizeMode.FIT,
            onResizeModeSelected = {},
        )
    }
}

@Preview(
    name = "Tablet Aspect Ratio List",
    showBackground = true,
    backgroundColor = 0xFF141416,
    widthDp = 432,
)
@Composable
private fun TabletAspectRatioListPreview() {
    FindroidTheme(darkTheme = true) {
        TabletAspectRatioList(
            currentResizeMode = PlayerResizeMode.ZOOM,
            onResizeModeSelected = {},
        )
    }
}
