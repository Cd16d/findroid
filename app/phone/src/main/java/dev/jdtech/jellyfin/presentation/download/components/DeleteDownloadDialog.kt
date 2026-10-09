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
fun DeleteDownloadDialog(
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDelete) {
                Text(text = stringResource(CoreR.string.delete_download))
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
        title = { Text(text = stringResource(CoreR.string.delete_download)) },
        text = { Text(text = stringResource(CoreR.string.delete_download_message)) },
    )
}

@Composable
@Preview
private fun DeleteDownloadDialogPreview() {
    FindroidTheme { DeleteDownloadDialog(onDelete = {}, onDismiss = {}) }
}
