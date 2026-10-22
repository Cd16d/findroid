package dev.jdtech.jellyfin.presentation.setup.addresses

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.setup.R as SetupR

/**
 * Confirmation dialog for removing a saved Jellyfin server address.
 *
 * @param address Server address URL string to be deleted.
 * @param onConfirm Callback when removal is confirmed.
 * @param onDismiss Callback when dialog is dismissed.
 */
@Composable
fun DeleteServerAddressDialog(address: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        title = { Text(text = stringResource(SetupR.string.remove_server_address)) },
        text = {
            Text(text = stringResource(SetupR.string.remove_server_address_dialog_text, address))
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(SetupR.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(SetupR.string.cancel)) }
        },
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun DeleteServerAddressDialogPreview() {
    dev.jdtech.jellyfin.presentation.theme.FindroidTheme {
        DeleteServerAddressDialog(
            address = "https://jellyfin.example.com",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
