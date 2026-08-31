package dev.jdtech.jellyfin.presentation.account.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.models.getProfileImageModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.UUID

@Composable
fun AccountMenuChangeAccountSection(
    otherUsers: List<User>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onUserClick: (User) -> Unit,
    onAddUserClick: () -> Unit,
    onManageAccountsClick: () -> Unit,
    baseUrl: String,
    modifier: Modifier = Modifier
) {
    val singleShape = RoundedCornerShape(24.dp)
    val topShape =
        RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val bottomShape =
        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    val middleShape = RoundedCornerShape(4.dp)

    Column(modifier = modifier) {
        Surface(
            onClick = onToggleExpand,
            shape = if (isExpanded) topShape else singleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            ListItem(
                headlineContent = { Text(stringResource(CoreR.string.change_user)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Avatars of other users
                        if (!isExpanded) {
                            otherUsers.take(2).forEachIndexed { index, user ->
                                val userImageUrl =
                                    user.getProfileImageModel(LocalContext.current, baseUrl)
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    if (userImageUrl != null) {
                                        AsyncImage(
                                            model = userImageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = user.name.take(1).uppercase(),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                                if (index == 0 && otherUsers.size > 1) {
                                    Spacer(Modifier.width(4.dp))
                                }
                            }

                            if (otherUsers.size > 2) {
                                Spacer(Modifier.width(4.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .height(24.dp)
                                        .wrapContentWidth()
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .widthIn(min = 24.dp)
                                            .padding(horizontal = MaterialTheme.spacings.extraSmall)
                                    ) {
                                        Text(
                                            text = "+${otherUsers.size - 2}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(8.dp))
                        }
                        Surface(
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(
                                    if (isExpanded) CoreR.drawable.ic_chevron_up else CoreR.drawable.ic_chevron_down
                                ),
                                contentDescription = null
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                otherUsers.forEach { user ->
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        onClick = { onUserClick(user) },
                        shape = middleShape,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text(user.name) },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    val userImageUrl =
                                        user.getProfileImageModel(LocalContext.current, baseUrl)
                                    if (userImageUrl != null) {
                                        AsyncImage(
                                            model = userImageUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = ImageVector.vectorResource(CoreR.drawable.ic_user),
                                            contentDescription = null,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                // Add User
                Spacer(Modifier.height(2.dp))
                Surface(
                    onClick = onAddUserClick,
                    shape = middleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(CoreR.string.add_user)) },
                        leadingContent = {
                            Surface(
                                shape = CircleShape,
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(CoreR.drawable.ic_plus),
                                    contentDescription = null
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                // Manage Accounts
                Spacer(Modifier.height(2.dp))
                Surface(
                    onClick = onManageAccountsClick,
                    shape = bottomShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(CoreR.string.manage_accounts)) },
                        leadingContent = {
                            Icon(
                                imageVector = ImageVector.vectorResource(CoreR.drawable.ic_user_settings),
                                contentDescription = null
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun AccountMenuChangeAccountSectionPreview() {
    FindroidTheme {
        Surface {
            AccountMenuChangeAccountSection(
                otherUsers = listOf(
                    User(UUID.randomUUID(), "Jane", "server1"),
                    User(UUID.randomUUID(), "Bob", "server1")
                ),
                isExpanded = true,
                onToggleExpand = {},
                onUserClick = {},
                onAddUserClick = {},
                onManageAccountsClick = {},
                baseUrl = "",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
