package dev.jdtech.jellyfin.presentation.account.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode

@Composable
fun AccountMenuItemsList(
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToGithub: () -> Unit,
    onNavigateToKofi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 4.dp,
        bottomEnd = 4.dp
    )
    val bottomShape = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 4.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp
    )
    val middleShape = RoundedCornerShape(4.dp)
    val singleShape = RoundedCornerShape(24.dp)

    val isOffline = LocalOfflineMode.current
    
    Column(modifier = modifier) {
        // Block 2
        Surface(
            onClick = onNavigateToSettings,
            shape = singleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = { Text(stringResource(CoreR.string.title_settings)) },
                leadingContent = {
                    Icon(
                        ImageVector.vectorResource(CoreR.drawable.ic_settings),
                        contentDescription = null
                    )
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Block 3
        Column {
            Surface(
                onClick = onNavigateToAbout,
                shape = topShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(CoreR.string.about)) },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(CoreR.drawable.ic_info),
                            contentDescription = null
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    )
                )
            }
            Spacer(Modifier.height(2.dp))
            Surface(
                onClick = onNavigateToGithub,
                shape = middleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(CoreR.string.github)) },
                    supportingContent = { Text(stringResource(CoreR.string.view_source_code)) },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(CoreR.drawable.ic_github),
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = ImageVector.vectorResource(CoreR.drawable.ic_external_link),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    )
                )
            }
            Spacer(Modifier.height(2.dp))
            Surface(
                onClick = onNavigateToKofi,
                shape = bottomShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(CoreR.string.kofi)) },
                    supportingContent = { Text(stringResource(CoreR.string.sponsor_project)) },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(CoreR.drawable.ic_coffee),
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = ImageVector.vectorResource(CoreR.drawable.ic_external_link),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@Preview
@Composable
fun AccountMenuItemsListPreview() {
    FindroidTheme {
        Surface {
            AccountMenuItemsList(
                onOpenQuickConnect = {},
                onNavigateToSettings = {},
                onNavigateToAbout = {},
                onNavigateToGithub = {},
                onNavigateToKofi = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
