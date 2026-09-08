package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.core.R as CoreR

data class PresetOption(
    val id: String,
    val title: String,
    val details: String,
)

@Composable
fun DownloadPresetDialog(
    initialPresetId: String = "1080p_balanced",
    hasExternalAudio: Boolean = false,
    onConfirm: (presetId: String, downloadExternalAudio: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedPreset by remember { mutableStateOf(initialPresetId) }
    var downloadExternalAudio by remember { mutableStateOf(false) }

    val presets = listOf(
        PresetOption(
            id = "1080p_balanced",
            title = "1080p Balanced",
            details = stringResource(CoreR.string.downloads_preset_balanced),
        ),
        PresetOption(
            id = "1080p_high",
            title = "1080p High Quality",
            details = stringResource(CoreR.string.downloads_preset_high),
        ),
        PresetOption(
            id = "720p_mobile",
            title = "720p Mobile Saver",
            details = stringResource(CoreR.string.downloads_preset_mobile),
        ),
        PresetOption(
            id = "480p_data_saver",
            title = "480p Data Saver",
            details = stringResource(CoreR.string.downloads_preset_data_saver),
        ),
        PresetOption(
            id = "direct",
            title = "Direct Stream",
            details = stringResource(CoreR.string.downloads_preset_direct),
        ),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(CoreR.string.download_quality_preset_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPreset = preset.id }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (selectedPreset == preset.id),
                            onClick = { selectedPreset = preset.id },
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = preset.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (hasExternalAudio) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { downloadExternalAudio = !downloadExternalAudio }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = downloadExternalAudio,
                            onCheckedChange = { downloadExternalAudio = it },
                        )
                        Text(
                            text = stringResource(CoreR.string.include_external_audio),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedPreset, downloadExternalAudio)
                }
            ) {
                Text(text = stringResource(CoreR.string.download_button_description))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(CoreR.string.cancel))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun DownloadPresetDialogPreview() {
    FindroidTheme {
        DownloadPresetDialog(
            initialPresetId = "1080p_balanced",
            hasExternalAudio = true,
            onConfirm = { _, _ -> },
            onDismiss = {},
        )
    }
}

