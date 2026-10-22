package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.film.presentation.search.FilterCategory
import dev.jdtech.jellyfin.film.presentation.search.SearchState
import dev.jdtech.jellyfin.presentation.film.search.components.getCategoryIcon
import dev.jdtech.jellyfin.presentation.film.search.components.getCategoryLabel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * Filter chip for an individual filter category with category icon and optional badge.
 *
 * @param category Target filter category.
 * @param selected Whether this category filter is active.
 * @param onClick Callback when chip is clicked.
 * @param modifier Modifier to be applied to layout.
 * @param badgeText Optional badge text indicating active option count.
 */
@Composable
fun CategoryFilterChip(
    category: FilterCategory,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = getCategoryLabel(category)) },
        leadingIcon = {
            Icon(
                painter = painterResource(getCategoryIcon(category)),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        trailingIcon =
            if (badgeText != null) {
                {
                    Surface(
                        shape = CircleShape,
                        color =
                            if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            } else {
                null
            },
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurface,
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            ),
        modifier = modifier,
    )
}

/**
 * Scrollable row displaying filter category chips.
 *
 * @param state Current search state containing active filters.
 * @param isWideScreen Whether layout is running on a wide screen display.
 * @param onOpenFilterCategory Callback when a category filter chip is selected.
 * @param modifier Modifier to be applied to layout.
 * @param categories List of filter categories to show.
 * @param contentPadding Padding applied around chip row content.
 */
@Composable
fun FilterChipsRow(
    state: SearchState,
    isWideScreen: Boolean,
    onOpenFilterCategory: (FilterCategory) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<FilterCategory> = FilterCategory.entries,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement =
            if (isWideScreen) Arrangement.Center
            else Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(categories, key = { it.name }) { category ->
            CategoryFilterChip(
                category = category,
                selected = state.isCategoryActive(category),
                badgeText = state.getCategoryBadgeText(category),
                onClick = { onOpenFilterCategory(category) },
            )
            if (isWideScreen) {
                Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoryFilterChipPreview() {
    FindroidTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CategoryFilterChip(
                category = FilterCategory.VIDEO_QUALITY,
                selected = true,
                badgeText = "2",
                onClick = {},
            )
            CategoryFilterChip(
                category = FilterCategory.GENRES,
                selected = false,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FilterChipsRowPreview() {
    FindroidTheme {
        FilterChipsRow(
            state = SearchState(),
            isWideScreen = false,
            onOpenFilterCategory = {},
            contentPadding = PaddingValues(16.dp),
        )
    }
}
