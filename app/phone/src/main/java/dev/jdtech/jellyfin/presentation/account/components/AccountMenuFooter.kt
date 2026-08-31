package dev.jdtech.jellyfin.presentation.account.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.BuildConfig
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun AccountMenuFooter(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(CoreR.string.app_description),
            style = MaterialTheme.typography.labelSmall
        )
        Text("•", style = MaterialTheme.typography.labelSmall)
        Text(
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Preview
@Composable
fun AccountMenuFooterPreview() {
    FindroidTheme {
        AccountMenuFooter()
    }
}
