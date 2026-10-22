package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.jdtech.jellyfin.settings.presentation.models.Preference

/**
 * Base card surface wrapper for settings preferences.
 *
 * @param preference Preference model determining enabled state.
 * @param onClick Callback invoked when card surface is tapped.
 * @param modifier Optional modifier applied to surface layout. Defaults to [Modifier].
 * @param content Preference item content inside card surface.
 */
@Composable
fun SettingsBaseCard(
    preference: Preference,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val contentColor =
        contentColorFor(MaterialTheme.colorScheme.surface)
            .copy(alpha = if (preference.enabled) 1.0f else 0.38f)
    Surface(
        onClick = onClick,
        modifier = modifier,
        enabled = preference.enabled,
        color = Color.Transparent,
        contentColor = contentColor,
    ) {
        content()
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun SettingsBaseCardPreview() {
    dev.jdtech.jellyfin.presentation.theme.FindroidTheme {
        SettingsBaseCard(
            preference =
                dev.jdtech.jellyfin.settings.presentation.models.PreferenceAppLanguage(
                    nameStringResource = dev.jdtech.jellyfin.core.R.string.app_name
                ),
            onClick = {},
        ) {
            androidx.compose.material3.Text("Settings Card Content")
        }
    }
}
