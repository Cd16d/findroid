package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Container badge box with rounded background container color.
 *
 * @param modifier Modifier applied to the badge box layout. Defaults to [Modifier].
 * @param containerColor Background color for the badge container. Defaults to
 *   `MaterialTheme.colorScheme.primary`.
 * @param content Composable content rendered inside the badge.
 */
@Composable
fun BaseBadge(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
    ) {
        content()
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun BaseBadgePreview() {
    dev.jdtech.jellyfin.presentation.theme.FindroidTheme {
        BaseBadge {
            androidx.compose.material3.Text(
                text = "Badge",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
