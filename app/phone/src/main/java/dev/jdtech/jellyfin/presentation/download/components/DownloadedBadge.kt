package dev.jdtech.jellyfin.presentation.download.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.film.components.BaseBadge
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun DownloadedBadge(modifier: Modifier = Modifier) {
    BaseBadge(modifier = modifier) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_download),
            contentDescription = "",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp).align(Alignment.Center),
        )
    }
}

@Composable
fun DownloadingBadge(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    isPending: Boolean = false,
) {
    BaseBadge(
        modifier = modifier,
        containerColor =
            if (isPending) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.primaryContainer,
    ) {
        if (progress != null && progress > 0f) {
            CircularProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.size(16.dp).align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )
        } else if (isPending) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_hourglass),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(14.dp).align(Alignment.Center),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp).align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
@Preview
private fun DownloadedBadgePreview() {
    FindroidTheme { DownloadedBadge() }
}
