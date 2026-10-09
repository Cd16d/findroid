package dev.jdtech.jellyfin.presentation.download.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(CoreR.string.delete)) }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
        title = { Text(text = title) },
        text = { Text(text = message) },
    )
}

@Preview(showBackground = true)
@Composable
private fun ConfirmDeleteDialogPreview() {
    FindroidTheme {
        ConfirmDeleteDialog(
            title = "Delete download",
            message = "Are you sure you want to delete this item from downloads?",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
