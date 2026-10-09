package dev.jdtech.jellyfin.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartDownloadsScreen(
    navigateBack: () -> Unit,
    onNavigateToPresets: () -> Unit,
    viewModel: SmartDownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SmartDownloadsScreenLayout(
        state = state,
        onAction = viewModel::onAction,
        navigateBack = navigateBack,
        onNavigateToPresets = onNavigateToPresets,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SmartDownloadsScreenLayout(
    state: SmartDownloadsState,
    onAction: (SmartDownloadsAction) -> Unit,
    navigateBack: () -> Unit,
    onNavigateToPresets: () -> Unit,
) {
    val safePadding = rememberSafePadding()
    val paddingStart = safePadding.start + MaterialTheme.spacings.medium
    val paddingEnd = safePadding.end + MaterialTheme.spacings.medium
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.medium

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(CoreR.string.download_smart_downloads_title),
                        style =
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            contentPadding =
                PaddingValues(start = paddingStart, end = paddingEnd, bottom = paddingBottom),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Card 1: Next Episodes Auto-Download
            item(key = "auto_download_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                    border =
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text =
                                        stringResource(CoreR.string.download_smart_downloads_title),
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text =
                                        stringResource(CoreR.string.download_smart_downloads_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = state.smartDownloadNextEpisode,
                                onCheckedChange = {
                                    onAction(SmartDownloadsAction.SetSmartDownloadNextEpisode(it))
                                },
                            )
                        }

                        AnimatedVisibility(visible = state.smartDownloadNextEpisode) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                HorizontalDivider(
                                    color =
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    thickness = 0.5.dp,
                                )
                                Spacer(modifier = Modifier.height(14.dp))

                                // Number of Next Episodes
                                Text(
                                    text =
                                        stringResource(CoreR.string.download_next_episodes_label),
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(1, 2, 3, 5).forEach { count ->
                                        val isSelected = state.nextEpisodesCount == count
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                onAction(
                                                    SmartDownloadsAction.SetNextEpisodesCount(count)
                                                )
                                            },
                                            label = {
                                                val labelText =
                                                    if (count == 1)
                                                        stringResource(
                                                            CoreR.string.download_next_episodes_one
                                                        )
                                                    else
                                                        stringResource(
                                                            CoreR.string
                                                                .download_next_episodes_many,
                                                            count,
                                                        )
                                                Text(text = labelText)
                                            },
                                            colors =
                                                FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor =
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor =
                                                        MaterialTheme.colorScheme
                                                            .onPrimaryContainer,
                                                ),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Storage Quota Limit
                                Text(
                                    text =
                                        stringResource(CoreR.string.download_storage_limit_label),
                                    style =
                                        MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(CoreR.string.download_storage_limit_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    listOf(
                                            5 to "5 GB",
                                            10 to "10 GB",
                                            20 to "20 GB",
                                            50 to "50 GB",
                                            0 to "Illimitato",
                                        )
                                        .forEach { (limitGb, label) ->
                                            val isSelected = state.storageLimitGb == limitGb
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    onAction(
                                                        SmartDownloadsAction.SetStorageLimitGb(
                                                            limitGb
                                                        )
                                                    )
                                                },
                                                label = { Text(text = label) },
                                                colors =
                                                    FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor =
                                                            MaterialTheme.colorScheme
                                                                .primaryContainer,
                                                        selectedLabelColor =
                                                            MaterialTheme.colorScheme
                                                                .onPrimaryContainer,
                                                    ),
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }

            // Card 2: Auto Delete Watched Episodes
            item(key = "auto_delete_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                    border =
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(CoreR.string.download_auto_delete_title),
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(CoreR.string.download_auto_delete_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = state.autoDeleteWatched,
                                onCheckedChange = {
                                    onAction(SmartDownloadsAction.SetAutoDeleteWatched(it))
                                },
                            )
                        }

                        // Safe deletion note
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp).padding(top = 2.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(CoreR.string.download_safe_deletion_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            // Card 3: Link to Download Quality Presets
            item(key = "presets_link_card") {
                Card(
                    modifier =
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable {
                            onNavigateToPresets()
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                    border =
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(42.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_settings),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text =
                                        stringResource(CoreR.string.download_quality_presets_title),
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text =
                                        stringResource(CoreR.string.download_quality_presets_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(safePadding.bottom + 24.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SmartDownloadsScreenPreview() {
    FindroidTheme {
        SmartDownloadsScreenLayout(
            state =
                SmartDownloadsState(
                    smartDownloadNextEpisode = true,
                    nextEpisodesCount = 3,
                    storageLimitGb = 10,
                    autoDeleteWatched = true,
                ),
            onAction = {},
            navigateBack = {},
            onNavigateToPresets = {},
        )
    }
}
