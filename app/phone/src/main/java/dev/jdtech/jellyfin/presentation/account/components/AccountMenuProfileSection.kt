package dev.jdtech.jellyfin.presentation.account.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun AccountMenuProfileSection(
    userName: String,
    userImageUrl: Any?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(96.dp)
            ) {
                if (userImageUrl == null) {
                    Icon(
                        ImageVector.vectorResource(CoreR.drawable.ic_user),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                AsyncImage(
                    model = userImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Greeting Name
        Text(
            text = stringResource(CoreR.string.hi_user, userName),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Preview
@Composable
fun AccountMenuProfileSectionPreview() {
    FindroidTheme {
        Surface {
            AccountMenuProfileSection(
                userName = "Joe",
                userImageUrl = null,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
