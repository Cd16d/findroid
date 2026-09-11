package dev.jdtech.jellyfin.presentation.cast.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun CastExpandedPlayerHeader(
    deviceName: String?,
    onClose: () -> Unit,
    onDeviceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterStart),
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_x),
                contentDescription = stringResource(CoreR.string.close),
            )
        }

        // Device Pill
        Surface(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            onClick = onDeviceClick,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_cast),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        deviceName?.ifBlank { null }
                            ?: stringResource(CoreR.string.cast_select_device),
                    modifier = Modifier.weight(1f, fill = false),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CastExpandedPlayerHeaderPreview() {
    FindroidTheme {
        CastExpandedPlayerHeader(
            deviceName = "Living Room TV",
            onClose = {},
            onDeviceClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CastExpandedPlayerHeaderNoDevicePreview() {
    FindroidTheme {
        CastExpandedPlayerHeader(
            deviceName = null,
            onClose = {},
            onDeviceClick = {},
        )
    }
}
