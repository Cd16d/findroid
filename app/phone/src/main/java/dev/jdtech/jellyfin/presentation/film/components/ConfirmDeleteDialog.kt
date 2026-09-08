package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.core.R as CoreR

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(CoreR.string.delete))
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
private fun ConfirmDeleteDialogPreview() {
    FindroidTheme {
        ConfirmDeleteDialog(
            title = "Elimina download",
            message = "Sei sicuro di voler eliminare questo elemento dai download?",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

