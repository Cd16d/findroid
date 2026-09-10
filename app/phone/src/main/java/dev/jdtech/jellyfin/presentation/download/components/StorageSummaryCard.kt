package dev.jdtech.jellyfin.presentation.download.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.presentation.theme.EmeraldGreen
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.core.R as CoreR

@Composable
fun StorageSummaryCard(
    usedStorageFormatted: String,
    freeStorageFormatted: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isSmartDownloadsActive: Boolean = false,
) {
    Card(
        modifier =
            if (onClick != null) {
                modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
            } else {
                modifier.fillMaxWidth()
            },
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row {
                    Text(
                        text = stringResource(CoreR.string.storage_downloaded_part, usedStorageFormatted),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(CoreR.string.storage_free_part, freeStorageFormatted),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
                if (isSmartDownloadsActive) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.extraSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_sparkles),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = stringResource(CoreR.string.smart_downloads_active),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (onClick != null) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StorageSummaryCardPreview() {
    FindroidTheme {
        StorageSummaryCard(
            usedStorageFormatted = "4.2 GB",
            freeStorageFormatted = "42.5 GB",
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StorageSummaryCardCompactPreview() {
    FindroidTheme {
        StorageSummaryCard(
            usedStorageFormatted = "4.2 GB",
            freeStorageFormatted = "42.5 GB",
            onClick = {},
            isSmartDownloadsActive = true,
        )
    }
}
