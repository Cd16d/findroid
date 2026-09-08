package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.theme.DeleteRed
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * A floating, compact Material 3 capsule selection toolbar.
 * Floats above the content instead of taking the full width of the screen.
 */
@Composable
fun FloatingSelectionToolbar(
    selectedCount: Int,
    anyDownloadingOrPending: Boolean?,
    onPauseOrResume: ((pause: Boolean) -> Unit)?,
    hasSdCard: Boolean,
    onMoveStorage: (() -> Unit)?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(8.dp),
    ) {
        Surface(
            modifier = Modifier.height(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (anyDownloadingOrPending != null && onPauseOrResume != null) {
                IconButton(
                    onClick = { onPauseOrResume(anyDownloadingOrPending) },
                ) {
                    Icon(
                        painter = painterResource(
                            if (anyDownloadingOrPending) CoreR.drawable.ic_pause else CoreR.drawable.ic_play
                        ),
                        contentDescription = stringResource(
                            if (anyDownloadingOrPending) CoreR.string.pause_all else CoreR.string.resume_all
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (hasSdCard && onMoveStorage != null) {
                IconButton(
                    onClick = onMoveStorage,
                    enabled = selectedCount > 0,
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_hard_drive),
                        contentDescription = stringResource(CoreR.string.move_storage),
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                enabled = selectedCount > 0,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_trash),
                    contentDescription = stringResource(CoreR.string.delete),
                    tint = if (selectedCount > 0) DeleteRed
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}
}

@Preview(showBackground = true)
@Composable
private fun FloatingSelectionToolbarPreview() {
    FindroidTheme {
        FloatingSelectionToolbar(
            selectedCount = 2,
            anyDownloadingOrPending = true,
            onPauseOrResume = {},
            hasSdCard = true,
            onMoveStorage = {},
            onDelete = {},
        )
    }
}

