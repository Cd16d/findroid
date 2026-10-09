package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.domain.models.Preference
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceStepper

@Composable
fun SettingsStepperCard(
    preference: PreferenceStepper,
    onUpdate: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsBaseCard(
        preference = preference,
        onClick = {},
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacings.medium, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(preference.nameStringResource),
                style = MaterialTheme.typography.titleMedium,
                color =
                    if (preference.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
            preference.descriptionStringRes?.let {
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
                Text(
                    text = stringResource(id = it),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (preference.enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stepper controls: (-) [ Value ] (+) placed underneath text
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.spacedBy(
                        space = 16.dp,
                        alignment = Alignment.CenterHorizontally,
                    ),
            ) {
                // Circular Minus Button
                FilledTonalIconButton(
                    onClick = {
                        val newVal =
                            (preference.value - preference.step).coerceAtLeast(preference.minValue)
                        onUpdate(newVal)
                    },
                    enabled = preference.enabled && preference.value > preference.minValue,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_minus),
                        contentDescription = stringResource(CoreR.string.download_minus),
                    )
                }

                // Small text / number container box
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.height(36.dp).weight(1f),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    ) {
                        val zeroLabel = preference.zeroLabelRes
                        val displayValue =
                            if (preference.value == 0 && zeroLabel != null) {
                                stringResource(zeroLabel)
                            } else {
                                "${preference.value}${preference.suffix}"
                            }
                        Text(
                            text = displayValue,
                            style =
                                MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color =
                                if (preference.enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                        )
                    }
                }

                // Circular Plus Button
                FilledTonalIconButton(
                    onClick = {
                        val newVal =
                            (preference.value + preference.step).coerceAtMost(preference.maxValue)
                        onUpdate(newVal)
                    },
                    enabled = preference.enabled && preference.value < preference.maxValue,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_plus),
                        contentDescription = stringResource(CoreR.string.download_plus),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsStepperCardPreview() {
    FindroidTheme {
        SettingsStepperCard(
            preference =
                PreferenceStepper(
                    nameStringResource = SettingsR.string.downloads_smart_count,
                    descriptionStringRes = SettingsR.string.downloads_smart_count_summary,
                    backendPreference = Preference("preview_int", 3),
                    value = 3,
                    minValue = 1,
                    maxValue = 10,
                    step = 1,
                ),
            onUpdate = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsStepperCardDisabledPreview() {
    FindroidTheme {
        SettingsStepperCard(
            preference =
                PreferenceStepper(
                    nameStringResource = SettingsR.string.downloads_smart_count,
                    descriptionStringRes = SettingsR.string.downloads_smart_count_summary,
                    backendPreference = Preference("", 0),
                    value = 3,
                    minValue = 1,
                    maxValue = 10,
                    step = 1,
                    enabled = false,
                ),
            onUpdate = {},
        )
    }
}
