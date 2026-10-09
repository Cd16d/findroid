package dev.jdtech.jellyfin.presentation.download.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun DownloadSectionHeader(
    title: String,
    itemCount: Int,
    totalSizeFormatted: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        val countText =
            pluralStringResource(CoreR.plurals.download_items_count, itemCount, itemCount)
        val infoText =
            if (totalSizeFormatted.isNotEmpty()) "$countText • $totalSizeFormatted" else countText
        Text(
            text = infoText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadSectionHeaderPreview() {
    FindroidTheme {
        DownloadSectionHeader(
            title = "Film",
            itemCount = 3,
            totalSizeFormatted = "12.4 GB",
        )
    }
}
