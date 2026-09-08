package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.EmeraldGreen
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceStorageInfo
import dev.jdtech.jellyfin.settings.presentation.models.StorageDevice

@Composable
fun SettingsStorageInfoCard(
    preference: PreferenceStorageInfo,
    modifier: Modifier = Modifier,
) {
    val storages =
        remember(preference.storages) {
            preference.storages.ifEmpty {
                listOf(
                    StorageDevice(
                        index = 0,
                        name = "",
                        isPrimary = true,
                        isRemovable = false,
                        isDefault = false,
                    )
                )
            }
        }

    if (storages.size > 1) {
        val pagerState =
            rememberPagerState(
                initialPage = 0,
                pageCount = { storages.size },
            )

        Column(modifier = modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                StorageCardContent(
                    storage = storages[page],
                    showDefaultBadge = true,
                    modifier =
                        Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
                )
            }

            // Pager dots indicator
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(bottom = MaterialTheme.spacings.small),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(storages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val dotWidth by
                        animateDpAsState(
                            targetValue = if (isSelected) 18.dp else 6.dp,
                            label = "dotWidth",
                        )
                    Box(
                        modifier =
                            Modifier.padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                ),
                    )
                }
            }
        }
    } else {
        StorageCardContent(
            storage = storages.first(),
            showDefaultBadge = false,
            modifier = modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
        )
    }
}

@Composable
private fun StorageCardContent(
    storage: StorageDevice,
    showDefaultBadge: Boolean,
    modifier: Modifier = Modifier,
) {
    val storageName =
        storage.name.ifBlank {
            stringResource(
                if (storage.isRemovable) SettingsR.string.downloads_storage_sdcard
                else SettingsR.string.downloads_storage_internal
            )
        }

    Column(modifier = modifier) {
        // Device name, icon, and default badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (storage.isRemovable) CoreR.drawable.ic_hard_drive
                            else SettingsR.drawable.ic_smartphone
                        ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = storageName,
                    style =
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (showDefaultBadge && storage.isDefault) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = stringResource(SettingsR.string.downloads_storage_default),
                        style =
                            MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large percentage used and compact GB ratio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text =
                    stringResource(
                        SettingsR.string.downloads_storage_percent_used,
                        storage.usedPercent,
                    ),
                style =
                    MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (storage.totalBytes > 0L) {
                Text(
                    text =
                        stringResource(
                            SettingsR.string.downloads_storage_ratio,
                            storage.usedFormatted,
                            storage.totalFormatted,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Multi-segment storage bar
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            val dlWeight = storage.downloadedFraction
            val otherWeight = storage.otherUsedFraction
            val freeWeight = storage.freeFraction
            val total = dlWeight + otherWeight + freeWeight

            if (total > 0f) {
                if (otherWeight > 0.001f) {
                    Box(
                        modifier =
                            Modifier.fillMaxHeight()
                                .weight(otherWeight)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                if (dlWeight > 0.001f) {
                    Box(
                        modifier =
                            Modifier.fillMaxHeight()
                                .weight(dlWeight)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
                if (freeWeight > 0.001f) {
                    Box(
                        modifier =
                            Modifier.fillMaxHeight()
                                .weight(freeWeight)
                                .background(EmeraldGreen),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Legend with dots and amounts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StorageLegendItem(
                color = MaterialTheme.colorScheme.outlineVariant,
                label = stringResource(SettingsR.string.downloads_storage_legend_other_apps),
                value = storage.otherUsedFormatted,
            )
            StorageLegendItem(
                color = MaterialTheme.colorScheme.primary,
                label = stringResource(CoreR.string.app_name),
                value = storage.downloadedFormatted,
            )
            StorageLegendItem(
                color = EmeraldGreen,
                label = stringResource(SettingsR.string.downloads_storage_legend_free_space),
                value = storage.freeFormatted,
            )
        }
    }
}

@Composable
private fun StorageLegendItem(
    color: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier =
                Modifier.size(10.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsStorageInfoCardSinglePreview() {
    FindroidTheme {
        SettingsStorageInfoCard(
            preference =
                PreferenceStorageInfo(
                    storages =
                        listOf(
                            StorageDevice(
                                index = 0,
                                name = "Internal Storage",
                                isPrimary = true,
                                isRemovable = false,
                                isDefault = true,
                                totalBytes = 128_000_000_000L,
                                availableBytes = 45_200_000_000L,
                                downloadedBytes = 12_400_000_000L,
                            )
                        )
                )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsStorageInfoCardMultiPreview() {
    FindroidTheme {
        SettingsStorageInfoCard(
            preference =
                PreferenceStorageInfo(
                    storages =
                        listOf(
                            StorageDevice(
                                index = 0,
                                name = "Internal Storage",
                                isPrimary = true,
                                isRemovable = false,
                                isDefault = true,
                                totalBytes = 128_000_000_000L,
                                availableBytes = 45_200_000_000L,
                                downloadedBytes = 12_400_000_000L,
                            ),
                            StorageDevice(
                                index = 1,
                                name = "Giancarlo",
                                isPrimary = false,
                                isRemovable = true,
                                isDefault = false,
                                totalBytes = 64_000_000_000L,
                                availableBytes = 28_000_000_000L,
                                downloadedBytes = 18_000_000_000L,
                            ),
                        )
                )
        )
    }
}
