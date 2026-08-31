package dev.jdtech.jellyfin.presentation.account.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun AccountMenuOfflineBadge(
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 0.dp
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        shadowElevation = shadowElevation,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = stringResource(CoreR.string.offline),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Preview
@Composable
fun AccountMenuOfflineBadgePreview() {
    FindroidTheme {
        AccountMenuOfflineBadge()
    }
}
