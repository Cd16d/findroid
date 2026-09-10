package dev.jdtech.jellyfin.presentation.film.components

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.models.DownloadQualityPreset
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.models.isDownloaded
import dev.jdtech.jellyfin.presentation.download.components.CancelDownloadDialog
import dev.jdtech.jellyfin.presentation.download.components.DeleteDownloadDialog
import dev.jdtech.jellyfin.presentation.download.components.DownloadPresetBottomSheet
import dev.jdtech.jellyfin.presentation.download.components.DownloaderCard
import dev.jdtech.jellyfin.presentation.download.components.StorageSelectionDialog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.download.DownloadStatus
import org.jellyfin.sdk.model.api.MediaStreamType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemButtonsBar(
    item: FindroidItem,
    onPlayClick: (startFromBeginning: Boolean) -> Unit,
    onMarkAsPlayedClick: () -> Unit,
    onMarkAsFavoriteClick: () -> Unit,
    onDownloadClick:
        (
            storageIndex: Int,
            presetId: String?,
            downloadExternalAudio: Boolean,
            audioStreamIndex: Int?,
        ) -> Unit,
    onDownloadCancelClick: () -> Unit,
    onDownloadDeleteClick: () -> Unit,
    onTrailerClick: (uri: String) -> Unit,
    modifier: Modifier = Modifier,
    downloaderState: DownloaderState? = null,
    askPresetBeforeDownload: Boolean = true,
    userCanTranscode: Boolean = true,
    presets: List<DownloadQualityPreset> = emptyList(),
    defaultPresetId: String = "1080p_balanced",
    defaultDownloadExternalAudio: Boolean = false,
    onRememberSettings: (presetId: String, downloadExternalAudio: Boolean) -> Unit = { _, _ -> },
    onNavigateToPresets: () -> Unit = {},
    defaultStorageIndex: Int = -1,
    // Used by seasons, episodes are loaded in the state and are used to
    // determine this. Combined with item
    isItemDownloaded: Boolean = false,
    // Used by seasons, episodes are loaded in the state and are used to
    // determine this. Combined with item.
    canDownload: Boolean = false,
    canPlay: Boolean = true,
) {
    val context = LocalContext.current
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    val trailerUri =
        when (item) {
            is FindroidMovie -> {
                item.trailer
            }
            is FindroidShow -> {
                item.trailer
            }
            else -> null
        }

    var storageSelectionDialogOpen by remember { mutableStateOf(false) }
    var cancelDownloadDialogOpen by remember { mutableStateOf(false) }
    var deleteDownloadDialogOpen by remember { mutableStateOf(false) }
    var presetDialogOpen by remember { mutableStateOf(false) }

    var selectedStorageIndex by remember { mutableIntStateOf(0) }
    data class StorageOption(val originalIndex: Int, val label: String)
    var mountedStorageOptions by remember { mutableStateOf<List<StorageOption>>(emptyList()) }

    fun refreshMountedStorage(): List<StorageOption> {
        val dirs = context.getExternalFilesDirs(null)
        val valid = dirs.mapIndexedNotNull { index, dir ->
            if (
                dir != null && Environment.getExternalStorageState(dir) == Environment.MEDIA_MOUNTED
            ) {
                try {
                    val stat = StatFs(dir.path)
                    val locationStringRes =
                        if (Environment.isExternalStorageRemovable(dir)) CoreR.string.external
                        else CoreR.string.internal
                    val locationString = context.applicationContext.getString(locationStringRes)
                    val availableMegaBytes = stat.availableBytes.div(1000000)
                    val label =
                        context.applicationContext.getString(
                            CoreR.string.storage_name,
                            locationString,
                            availableMegaBytes,
                        )
                    StorageOption(index, label)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        }
        mountedStorageOptions = valid
        return valid
    }

    val hasExternalAudio =
        remember(item) {
            (item as? FindroidSources)?.sources?.any { src ->
                src.mediaStreams.any { it.isExternal && it.type == MediaStreamType.AUDIO }
            } ?: false
        }

    fun startDownloadFlow(storageIndex: Int) {
        selectedStorageIndex = storageIndex
        if (askPresetBeforeDownload && userCanTranscode) {
            presetDialogOpen = true
        } else {
            val preset = if (userCanTranscode) defaultPresetId else "original"
            onDownloadClick(selectedStorageIndex, preset, defaultDownloadExternalAudio, null)
        }
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            if (
                !windowSizeClass.isWidthAtLeastBreakpoint(
                    WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
                )
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                    PlayButton(
                        item = item,
                        onClick = { onPlayClick(false) },
                        modifier = Modifier.weight(weight = 1f, fill = true),
                        enabled = item.canPlay && canPlay,
                    )
                    if (item.playbackPositionTicks.div(600000000) > 0) {
                        FilledTonalIconButton(onClick = { onPlayClick(true) }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                if (
                    windowSizeClass.isWidthAtLeastBreakpoint(
                        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
                    )
                ) {
                    PlayButton(
                        item = item,
                        onClick = { onPlayClick(false) },
                        enabled = item.canPlay && canPlay,
                    )
                    if (item.playbackPositionTicks.div(600000000) > 0) {
                        FilledTonalIconButton(onClick = { onPlayClick(true) }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                                contentDescription = null,
                            )
                        }
                    }
                }
                trailerUri?.let { uri ->
                    FilledTonalIconButton(onClick = { onTrailerClick(uri) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_film),
                            contentDescription = null,
                        )
                    }
                }
                FilledTonalIconButton(onClick = onMarkAsPlayedClick) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_check),
                        contentDescription = null,
                        tint = if (item.played) Color.Red else LocalContentColor.current,
                    )
                }
                FilledTonalIconButton(onClick = onMarkAsFavoriteClick) {
                    when (item.favorite) {
                        true -> {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_heart_filled),
                                contentDescription = null,
                                tint = Color.Red,
                            )
                        }
                        false -> {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_heart),
                                contentDescription = null,
                            )
                        }
                    }
                }
                if (downloaderState != null && !downloaderState.isDownloading) {
                    // Render both Delete and Download buttons for seasons, which may have
                    // some episodes downloaded and some not.
                    if (isItemDownloaded || item.isDownloaded()) {
                        FilledTonalIconButton(onClick = { deleteDownloadDialogOpen = true }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_trash),
                                contentDescription = null,
                            )
                        }
                    }
                    // canDownload is for seasons. Else, an item should only render the download
                    // button if item is not downloaded.
                    if (canDownload || (item.canDownload && !item.isDownloaded())) {
                        FilledTonalIconButton(
                            onClick = {
                                val options = refreshMountedStorage()
                                val target = options.find {
                                    it.originalIndex == defaultStorageIndex
                                }
                                if (defaultStorageIndex >= 0 && target != null) {
                                    startDownloadFlow(target.originalIndex)
                                } else if (options.size > 1) {
                                    storageSelectionDialogOpen = true
                                } else {
                                    startDownloadFlow(options.firstOrNull()?.originalIndex ?: 0)
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_download),
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
            if (downloaderState != null) {
                AnimatedVisibility(downloaderState.isDownloading) {
                    Column {
                        DownloaderCard(
                            state = downloaderState,
                            onCancelClick = { cancelDownloadDialogOpen = true },
                            onRetryClick = { startDownloadFlow(selectedStorageIndex) },
                        )
                        Spacer(Modifier.height(MaterialTheme.spacings.small))
                    }
                }
            }
        }
        if (storageSelectionDialogOpen) {
            StorageSelectionDialog(
                storageLocations = mountedStorageOptions.map { it.label },
                onSelect = { displayIndex ->
                    storageSelectionDialogOpen = false
                    val actualStorageIndex =
                        mountedStorageOptions.getOrNull(displayIndex)?.originalIndex ?: 0
                    startDownloadFlow(actualStorageIndex)
                },
                onDismiss = { storageSelectionDialogOpen = false },
            )
        }
        if (presetDialogOpen) {
            DownloadPresetBottomSheet(
                item = item,
                presets = presets,
                initialPresetId = defaultPresetId,
                hasExternalAudio = hasExternalAudio,
                initialDownloadExternalAudio = defaultDownloadExternalAudio,
                onAddClick = {
                    presetDialogOpen = false
                    onNavigateToPresets()
                },
                onConfirm = { presetId, downloadExternalAudio, rememberSetting, audioStreamIndex ->
                    presetDialogOpen = false
                    if (rememberSetting) {
                        onRememberSettings(presetId, downloadExternalAudio)
                    }
                    onDownloadClick(
                        selectedStorageIndex,
                        presetId,
                        downloadExternalAudio,
                        audioStreamIndex,
                    )
                },
                onDismiss = { presetDialogOpen = false },
            )
        }
        if (cancelDownloadDialogOpen) {
            CancelDownloadDialog(
                onCancel = {
                    onDownloadCancelClick()
                    cancelDownloadDialogOpen = false
                },
                onDismiss = { cancelDownloadDialogOpen = false },
            )
        }
        if (deleteDownloadDialogOpen) {
            DeleteDownloadDialog(
                onDelete = {
                    onDownloadDeleteClick()
                    deleteDownloadDialogOpen = false
                },
                onDismiss = { deleteDownloadDialogOpen = false },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemButtonsBarPreview() {
    FindroidTheme {
        ItemButtonsBar(
            item = dummyEpisode,
            onPlayClick = {},
            canDownload = true,
            onMarkAsPlayedClick = {},
            onMarkAsFavoriteClick = {},
            onDownloadClick = { _, _, _, _ -> },
            onDownloadCancelClick = {},
            onDownloadDeleteClick = {},
            onTrailerClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ItemButtonsBarDownloadingPreview() {
    FindroidTheme {
        ItemButtonsBar(
            item = dummyEpisode,
            downloaderState = DownloaderState(status = DownloadStatus.RUNNING, progress = 0.3f),
            canDownload = true,
            onPlayClick = {},
            onMarkAsPlayedClick = {},
            onMarkAsFavoriteClick = {},
            onDownloadClick = { _, _, _, _ -> },
            onDownloadCancelClick = {},
            onDownloadDeleteClick = {},
            onTrailerClick = {},
        )
    }
}
