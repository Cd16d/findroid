package dev.jdtech.jellyfin.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.theme.EmeraldGreen
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.utils.CodecInfo
import dev.jdtech.jellyfin.utils.DeviceCodecsOverview

@Composable
fun DeviceScreen(
    navigateBack: () -> Unit,
    viewModel: DeviceViewModel = hiltViewModel(),
) {
    val deviceName by viewModel.deviceName.collectAsStateWithLifecycle()

    DeviceScreenContent(
        deviceName = deviceName,
        overview = viewModel.codecsOverview,
        onUpdateDeviceName = viewModel::updateDeviceName,
        navigateBack = navigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreenContent(
    deviceName: String,
    overview: DeviceCodecsOverview,
    onUpdateDeviceName: (String) -> Unit,
    navigateBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showEditDialog by remember { mutableStateOf(false) }
    var editedName by remember(deviceName) { mutableStateOf(deviceName) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(SettingsR.string.settings_category_device)) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        var videoExpanded by rememberSaveable { mutableStateOf(false) }
        var audioExpanded by rememberSaveable { mutableStateOf(false) }

        val videoSupportedCount = overview.videoCodecs.count { it.isSupported }
        val audioSupportedCount = overview.audioCodecs.count { it.isSupported }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    start = MaterialTheme.spacings.default,
                    end = MaterialTheme.spacings.default,
                    top = innerPadding.calculateTopPadding() + MaterialTheme.spacings.default,
                    bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacings.default,
                ),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Group 1: Device Information
            item(key = "device_info_group") {
                Column(modifier = Modifier.widthIn(max = 640.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                    ) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        editedName = deviceName
                                        showEditDialog = true
                                    }
                                    .padding(MaterialTheme.spacings.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(SettingsR.string.device_name_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = deviceName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
            }

            // Group 2: Supported Video Codecs (Collapsible)
            item(key = "video_codecs_group") {
                Column(modifier = Modifier.widthIn(max = 640.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                    ) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { videoExpanded = !videoExpanded }
                                    .padding(MaterialTheme.spacings.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(SettingsR.string.device_codecs_video),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text =
                                        stringResource(
                                            SettingsR.string.codecs_supported_ratio,
                                            videoSupportedCount,
                                            overview.videoCodecs.size,
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                painter =
                                    painterResource(
                                        if (videoExpanded) CoreR.drawable.ic_chevron_up
                                        else CoreR.drawable.ic_chevron_down
                                    ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        AnimatedVisibility(visible = videoExpanded) {
                            Column {
                                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.2f))
                                overview.videoCodecs.forEachIndexed { index, codec ->
                                    CodecItemRow(codec = codec)
                                    if (index < overview.videoCodecs.lastIndex) {
                                        HorizontalDivider(
                                            color = DividerDefaults.color.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Group 3: Supported Audio Codecs (Collapsible)
            item(key = "audio_codecs_group") {
                Column(modifier = Modifier.widthIn(max = 640.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                    ) {
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clickable { audioExpanded = !audioExpanded }
                                    .padding(MaterialTheme.spacings.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = stringResource(SettingsR.string.device_codecs_audio),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text =
                                        stringResource(
                                            SettingsR.string.codecs_supported_ratio,
                                            audioSupportedCount,
                                            overview.audioCodecs.size,
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                painter =
                                    painterResource(
                                        if (audioExpanded) CoreR.drawable.ic_chevron_up
                                        else CoreR.drawable.ic_chevron_down
                                    ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        AnimatedVisibility(visible = audioExpanded) {
                            Column {
                                HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.2f))
                                overview.audioCodecs.forEachIndexed { index, codec ->
                                    CodecItemRow(codec = codec)
                                    if (index < overview.audioCodecs.lastIndex) {
                                        HorizontalDivider(
                                            color = DividerDefaults.color.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(SettingsR.string.device_name_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(SettingsR.string.device_name_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateDeviceName(editedName)
                        showEditDialog = false
                    },
                    enabled = editedName.isNotBlank(),
                ) {
                    Text(stringResource(SettingsR.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(stringResource(CoreR.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CodecItemRow(
    codec: CodecInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacings.medium, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = codec.name,
                style = MaterialTheme.typography.titleMedium,
            )
            codec.mimeTypes.firstOrNull()?.let { mime ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (codec.isSupported) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_check),
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(SettingsR.string.codec_supported),
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = EmeraldGreen,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_x),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(SettingsR.string.codec_not_supported),
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun DeviceScreenPreview() {
    FindroidTheme {
        DeviceScreenContent(
            deviceName = "Pixel 8 Pro",
            overview =
                DeviceCodecsOverview(
                    deviceName = "Pixel 8 Pro",
                    videoCodecs =
                        listOf(
                            CodecInfo(
                                id = "h264",
                                name = "H.264",
                                mimeTypes = listOf("video/avc"),
                                isSupported = true,
                            ),
                            CodecInfo(
                                id = "h265",
                                name = "H.265 / HEVC",
                                mimeTypes = listOf("video/hevc"),
                                isSupported = true,
                            ),
                            CodecInfo(
                                id = "av1",
                                name = "AV1",
                                mimeTypes = listOf("video/av01"),
                                isSupported = false,
                            ),
                        ),
                    audioCodecs =
                        listOf(
                            CodecInfo(
                                id = "aac",
                                name = "AAC",
                                mimeTypes = listOf("audio/mp4a-latm"),
                                isSupported = true,
                            ),
                            CodecInfo(
                                id = "flac",
                                name = "FLAC",
                                mimeTypes = listOf("audio/flac"),
                                isSupported = true,
                            ),
                            CodecInfo(
                                id = "ac3",
                                name = "AC-3",
                                mimeTypes = listOf("audio/ac3"),
                                isSupported = false,
                            ),
                        ),
                ),
            onUpdateDeviceName = {},
            navigateBack = {},
        )
    }
}
