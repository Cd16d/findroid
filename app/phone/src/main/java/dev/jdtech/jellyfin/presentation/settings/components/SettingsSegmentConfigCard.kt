package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.components.BaseDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.GridCellsAdaptiveWithMinColumns
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.domain.AppPreferences.SegmentAction
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceSegmentConfig

val SegmentPalette =
    listOf(
        0xFF4CAF50L, // Green
        0xFF8BC34AL, // Light Green
        0xFF009688L, // Teal
        0xFF00BCD4L, // Cyan
        0xFF2196F3L, // Blue
        0xFF3F51B5L, // Indigo
        0xFF9C27B0L, // Purple
        0xFFE91E63L, // Pink
        0xFFF44336L, // Red
        0xFFFF5722L, // Deep Orange
        0xFFFF9800L, // Orange
        0xFFFFC107L, // Amber
        0xFFFFEB3BL, // Yellow
        0xFF795548L, // Brown
        0xFF607D8BL, // Blue Grey
        0xFF9E9E9EL, // Grey
    )

/**
 * Settings card for configuring segment actions and highlight colors.
 *
 * @param preference Segment configuration preference model.
 * @param onUpdate Callback when preference segment action or color changes.
 * @param modifier Optional modifier applied to card layout. Defaults to [Modifier].
 */
@Composable
fun SettingsSegmentConfigCard(
    preference: PreferenceSegmentConfig,
    onUpdate: (PreferenceSegmentConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActionDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    val actionSubtitle =
        when (preference.action) {
            SegmentAction.NONE -> stringResource(SettingsR.string.segment_action_do_nothing)
            SegmentAction.SHOW_BUTTON -> stringResource(SettingsR.string.segment_action_show_button)
            SegmentAction.AUTO_SKIP -> stringResource(SettingsR.string.segment_action_auto_skip)
        }

    SettingsBaseCard(
        preference = preference,
        onClick = { showActionDialog = true },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacings.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(preference.nameStringResource),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
                Text(
                    text = actionSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.width(MaterialTheme.spacings.medium))

            // Circular color chip / dot
            if (preference.color == 0L) {
                Box(
                    modifier =
                        Modifier.size(26.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { showColorDialog = true },
                    contentAlignment = Alignment.Center,
                ) {
                    val strokeColor = MaterialTheme.colorScheme.onSurfaceVariant
                    Canvas(modifier = Modifier.size(26.dp)) {
                        drawLine(
                            color = strokeColor,
                            start = Offset(size.width * 0.25f, size.height * 0.75f),
                            end = Offset(size.width * 0.75f, size.height * 0.25f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            } else {
                Box(
                    modifier =
                        Modifier.size(26.dp)
                            .clip(CircleShape)
                            .background(Color(preference.color))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable { showColorDialog = true }
                )
            }
        }
    }

    if (showActionDialog) {
        SegmentActionSelectionDialog(
            title = stringResource(preference.nameStringResource),
            currentAction = preference.action,
            onActionSelected = { action ->
                showActionDialog = false
                val updated = preference.copy(action = action)
                preference.onActionChange(action)
                onUpdate(updated)
            },
            onDismissRequest = { showActionDialog = false },
        )
    }

    if (showColorDialog) {
        SegmentColorPickerDialog(
            title = stringResource(preference.nameStringResource),
            currentColor = preference.color,
            onColorSelected = { color ->
                showColorDialog = false
                val updated = preference.copy(color = color)
                preference.onColorChange(color)
                onUpdate(updated)
            },
            onDismissRequest = { showColorDialog = false },
        )
    }
}

/**
 * Dialog for selecting the action to take for a media segment.
 *
 * @param title Dialog title string.
 * @param currentAction Currently selected segment action.
 * @param onActionSelected Callback when an action is selected.
 * @param onDismissRequest Callback when dialog is dismissed.
 */
@Composable
fun SegmentActionSelectionDialog(
    title: String,
    currentAction: SegmentAction,
    onActionSelected: (SegmentAction) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val options =
        listOf(
            SegmentAction.NONE to stringResource(SettingsR.string.segment_action_do_nothing),
            SegmentAction.SHOW_BUTTON to
                stringResource(SettingsR.string.segment_action_show_button),
            SegmentAction.AUTO_SKIP to stringResource(SettingsR.string.segment_action_auto_skip),
        )

    BaseDialog(
        title = title,
        onDismiss = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = MaterialTheme.spacings.default)
        ) {
            options.forEach { (action, label) ->
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { onActionSelected(action) }
                            .padding(horizontal = MaterialTheme.spacings.default, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentAction == action,
                        onClick = { onActionSelected(action) },
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.medium))
                    Text(text = label, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/**
 * Dialog for picking a color tint for a media segment marker.
 *
 * @param title Dialog title string.
 * @param currentColor Currently selected color value.
 * @param onColorSelected Callback when a color is selected.
 * @param onDismissRequest Callback when dialog is dismissed.
 */
@Composable
fun SegmentColorPickerDialog(
    title: String,
    currentColor: Long,
    onColorSelected: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val isDefaultSelected = currentColor == 0L

    BaseDialog(
        title = title,
        onDismiss = onDismissRequest,
    ) {
        Text(
            text = stringResource(SettingsR.string.choose_segment_color),
            modifier = Modifier.padding(horizontal = MaterialTheme.spacings.default).fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))

        // Full-width circular chip option for "No color (use bar default)"
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacings.default)
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isDefaultSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .border(
                        width = if (isDefaultSelected) 2.dp else 1.dp,
                        color =
                            if (isDefaultSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onColorSelected(0L) },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Box(
                    modifier =
                        Modifier.size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isDefaultSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                            )
                            .border(
                                1.dp,
                                if (isDefaultSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    val strokeColor =
                        if (isDefaultSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawLine(
                            color = strokeColor,
                            start = Offset(size.width * 0.25f, size.height * 0.75f),
                            end = Offset(size.width * 0.75f, size.height * 0.25f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(SettingsR.string.segment_color_default),
                    color =
                        if (isDefaultSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (isDefaultSelected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))

        LazyVerticalGrid(
            columns = GridCellsAdaptiveWithMinColumns(minSize = 48.dp, minColumns = 4),
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacings.default),
            contentPadding = PaddingValues(bottom = MaterialTheme.spacings.default),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(SegmentPalette) { colorLong ->
                val isSelected = colorLong == currentColor
                val color = Color(colorLong)
                val checkColor = if (color.luminance() > 0.5f) Color.Black else Color.White

                Box(
                    modifier =
                        Modifier.aspectRatio(1f)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color =
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.White.copy(alpha = 0.25f),
                                shape = CircleShape,
                            )
                            .clickable { onColorSelected(colorLong) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_check),
                            contentDescription = null,
                            tint = checkColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsSegmentConfigCardPreview() {
    FindroidTheme {
        SettingsSegmentConfigCard(
            preference =
                PreferenceSegmentConfig(
                    nameStringResource = CoreR.string.app_name,
                    segmentType = "Intro",
                ),
            onUpdate = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SegmentActionSelectionDialogPreview() {
    FindroidTheme {
        SegmentActionSelectionDialog(
            title = "Intro Segment",
            currentAction = SegmentAction.SHOW_BUTTON,
            onActionSelected = {},
            onDismissRequest = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SegmentColorPickerDialogPreview() {
    FindroidTheme {
        SegmentColorPickerDialog(
            title = "Intro Segment",
            currentColor = 0xFF4CAF50L,
            onColorSelected = {},
            onDismissRequest = {},
        )
    }
}
