package dev.jdtech.jellyfin.presentation.download.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun CircularSelectionIndicator(
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue =
            if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "selectionBorderColor",
    )
    val containerColor by animateColorAsState(
        targetValue =
            if (checked) MaterialTheme.colorScheme.primary
            else Color.Transparent,
        label = "selectionContainerColor",
    )

    Box(
        modifier =
            modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(width = 1.5.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = checked,
            enter = fadeIn(animationSpec = tween(150)) + scaleIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)) + scaleOut(animationSpec = tween(150)),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun StickySeasonHeader(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    isOverlapping: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val chipColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else if (isOverlapping) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.background

    val shadowElevation by animateDpAsState(
        targetValue = if (isOverlapping) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "shadowElevation",
    )

    val gradientAlpha by animateFloatAsState(
        targetValue = if (isOverlapping) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "seasonHeaderGradientAlpha",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0.0f to MaterialTheme.colorScheme.surface.copy(alpha = gradientAlpha),
                        1.0f to Color.Transparent,
                    )
                )
                .padding(contentPadding),
    ) {
        Surface(
            shape = CircleShape,
            color = chipColor,
            shadowElevation = shadowElevation,
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                if (isSelectionMode) {
                                    onToggleSelect()
                                }
                            },
                            onLongClick = onLongClick,
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter =
                        expandHorizontally(
                            animationSpec = tween(250),
                            expandFrom = Alignment.Start,
                        ) + fadeIn(animationSpec = tween(250)),
                    exit =
                        shrinkHorizontally(
                            animationSpec = tween(250),
                            shrinkTowards = Alignment.Start,
                        ) + fadeOut(animationSpec = tween(250)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularSelectionIndicator(
                            checked = isSelected,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.height(22.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StickySeasonHeaderPreview() {
    FindroidTheme {
        StickySeasonHeader(title = "Stagione 1 • 10 episodi")
    }
}

@Preview(showBackground = true)
@Composable
private fun StickySeasonHeaderOverlappingPreview() {
    FindroidTheme {
        StickySeasonHeader(
            title = "Stagione 1 • 10 episodi",
            isOverlapping = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StickySeasonHeaderSelectionModePreview() {
    FindroidTheme {
        StickySeasonHeader(
            title = "Stagione 1 • 10 episodi",
            isSelectionMode = true,
            isSelected = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StickySeasonHeaderSelectedPreview() {
    FindroidTheme {
        StickySeasonHeader(
            title = "Stagione 1 • 10 episodi",
            isSelectionMode = true,
            isSelected = true,
        )
    }
}
