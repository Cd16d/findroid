package dev.jdtech.jellyfin.presentation.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.DownloadQualityPreset
import dev.jdtech.jellyfin.models.DownloadQualityPresets
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.DeviceCodecCapabilities
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun DownloadPresetsScreen(
    navigateBack: () -> Unit,
    viewModel: DownloadPresetsViewModel = hiltViewModel(),
) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()

    DownloadPresetsScreenContent(
        presets = presets,
        navigateBack = navigateBack,
        onAction = viewModel::onAction,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadPresetsScreenContent(
    presets: List<DownloadQualityPreset>,
    navigateBack: () -> Unit,
    onAction: (DownloadPresetsAction) -> Boolean = { true },
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val safePadding = rememberSafePadding()
    val paddingStart = safePadding.start + MaterialTheme.spacings.medium
    val paddingEnd = safePadding.end + MaterialTheme.spacings.medium
    val paddingBottom = safePadding.bottom + MaterialTheme.spacings.medium

    var showExtraInfo by remember { mutableStateOf(false) }
    var presetToEdit by remember { mutableStateOf<DownloadQualityPreset?>(null) }
    var isNewPreset by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    val copiedMessage = stringResource(CoreR.string.download_preset_export_copied)
    val exportChooserTitle = stringResource(CoreR.string.download_preset_export_title)
    val importSuccessMessage = stringResource(CoreR.string.download_preset_import_success)

    fun copyAndShare(text: String, title: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(title, text)
        clipboard?.setPrimaryClip(clip)

        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }

        try {
            val sendIntent =
                Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_TITLE, title)
                    type = "text/plain"
                }
            context.startActivity(Intent.createChooser(sendIntent, exportChooserTitle))
        } catch (_: Exception) {}
    }

    if (presetToEdit != null) {
        EditPresetDialog(
            preset = presetToEdit!!,
            isNew = isNewPreset,
            onSave = { updated ->
                onAction(DownloadPresetsAction.SavePreset(updated))
                presetToEdit = null
            },
            onDelete =
                if (!isNewPreset && !presetToEdit!!.isOriginal) {
                    {
                        onAction(DownloadPresetsAction.DeletePreset(presetToEdit!!.id))
                        presetToEdit = null
                    }
                } else null,
            onDismiss = { presetToEdit = null },
        )
    }

    if (showImportDialog) {
        ImportPresetsDialog(
            onImport = { json -> onAction(DownloadPresetsAction.ImportPresets(json)) },
            onDismiss = { showImportDialog = false },
            onSuccess = { scope.launch { snackbarHostState.showSnackbar(importSuccessMessage) } },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(CoreR.string.download_preset_profiles_title),
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
                actions = {
                    IconButton(
                        onClick = { showExtraInfo = !showExtraInfo },
                        shape = CircleShape,
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor =
                                    if (showExtraInfo) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                contentColor =
                                    if (showExtraInfo) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_info),
                            contentDescription =
                                stringResource(CoreR.string.download_preset_show_details),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = { onAction(DownloadPresetsAction.ResetToDefaults) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                            contentDescription =
                                stringResource(CoreR.string.download_preset_reset_defaults),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header_description") {
                Text(
                    text = stringResource(CoreR.string.download_preset_profiles_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = 6.dp),
                )
            }

            // Presets Cards List
            items(presets, key = { it.id }) { preset ->
                PresetDetailCard(
                    preset = preset,
                    showExtraInfo = showExtraInfo,
                    onEditPreset = {
                        isNewPreset = false
                        presetToEdit = it
                    },
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }

            // Add Preset
            item(key = "add_preset_button") {
                FilledTonalButton(
                    onClick = {
                        isNewPreset = true
                        presetToEdit =
                            DownloadQualityPreset(
                                id = UUID.randomUUID().toString(),
                                name = UiText.DynamicString(""),
                                maxBitrateBps = 4_500_000L,
                                maxWidth = 1920,
                                maxHeight = 1080,
                                audioBitrateBps = 192_000L,
                                audioChannels = 2,
                                audioCodec = "aac",
                                videoCodec = "h264",
                                isOriginal = false,
                                customName = "",
                            )
                    },
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_plus),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(CoreR.string.download_preset_add_preset))
                }
            }

            // Export / Import Buttons at Bottom
            item(key = "export_section") {
                Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                copyAndShare(
                                    DownloadQualityPresets.exportToJson(presets),
                                    "Presets",
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_external_link),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(CoreR.string.download_preset_export))
                        }
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_download),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(CoreR.string.download_preset_import))
                        }
                    }
                    Spacer(modifier = Modifier.height(safePadding.bottom + 24.dp))
                }
            }
        }
    }
}

@Composable
private fun PresetDetailCard(
    preset: DownloadQualityPreset,
    showExtraInfo: Boolean,
    onEditPreset: (DownloadQualityPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = preset.displayName.asString(),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (preset.isOriginal) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = stringResource(CoreR.string.download_preset_max_quality),
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = preset.displayApproxSize.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!preset.isOriginal) {
                    IconButton(onClick = { onEditPreset(preset) }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_wrench),
                            contentDescription = stringResource(CoreR.string.download_preset_edit),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = showExtraInfo) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.download_preset_resolution,
                                    preset.resolutionText.asString(),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text =
                                stringResource(
                                    CoreR.string.download_preset_bitrate,
                                    preset.bitrateText.asString(),
                                ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text =
                            stringResource(
                                CoreR.string.download_preset_audio,
                                preset.audioText.asString(),
                            ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPresetDialog(
    preset: DownloadQualityPreset,
    isNew: Boolean,
    onSave: (DownloadQualityPreset) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val initialName =
        preset.customName?.takeIf { it.isNotBlank() } ?: if (isNew) "" else preset.name.asString()
    var name by remember { mutableStateOf(initialName) }

    val resolutionOptions = remember {
        listOf(
            "4K (2160p)" to (3840 to 2160),
            "1080p" to (1920 to 1080),
            "720p" to (1280 to 720),
            "480p" to (854 to 480),
            "360p" to (640 to 360),
        )
    }

    var selectedResolutionOption by remember {
        val match = resolutionOptions.firstOrNull {
            it.second.first == preset.maxWidth && it.second.second == preset.maxHeight
        }
        mutableStateOf(match?.first ?: "Custom")
    }
    var customWidth by remember { mutableStateOf(preset.maxWidth.toString()) }
    var customHeight by remember { mutableStateOf(preset.maxHeight.toString()) }

    val initialBitrateMbps = remember {
        val mbps = preset.maxBitrateBps / 1_000_000.0
        if (mbps == mbps.toLong().toDouble()) mbps.toLong().toString()
        else String.format(Locale.US, "%.1f", mbps)
    }
    var bitrateMbpsText by remember { mutableStateOf(initialBitrateMbps) }

    val supportedAudioCodecs = remember {
        val list =
            listOf("aac", "mp3", "opus", "ac3", "eac3", "flac").filter {
                DeviceCodecCapabilities.isAudioCodecSupported(it)
            }
        list.ifEmpty { listOf("aac") }
    }
    var selectedAudioCodec by remember {
        mutableStateOf(
            if (supportedAudioCodecs.contains(preset.audioCodec.lowercase()))
                preset.audioCodec.lowercase()
            else supportedAudioCodecs.first()
        )
    }

    val channelOptions = remember {
        listOf(
            "Stereo (2.0)" to 2,
            "5.1 Surround" to 6,
            "7.1 Surround" to 8,
            "Mono (1.0)" to 1,
        )
    }
    var selectedChannels by remember { mutableIntStateOf(preset.audioChannels) }

    val audioBitrateOptions = remember {
        listOf(
            "320 kbps" to 320_000L,
            "256 kbps" to 256_000L,
            "192 kbps" to 192_000L,
            "128 kbps" to 128_000L,
            "96 kbps" to 96_000L,
            "64 kbps" to 64_000L,
        )
    }
    var selectedAudioBitrate by remember { mutableLongStateOf(preset.audioBitrateBps) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(CoreR.string.download_preset_delete)) },
            text = { Text(stringResource(CoreR.string.download_preset_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    Text(stringResource(CoreR.string.download_preset_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isNew) stringResource(CoreR.string.download_preset_add_preset)
                else stringResource(CoreR.string.download_preset_edit)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(CoreR.string.download_preset_name)) },
                    placeholder = { Text("e.g. 1080p Custom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Resolution
                Text(
                    text = stringResource(CoreR.string.download_preset_resolution_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    resolutionOptions.forEach { (label, res) ->
                        FilterChip(
                            selected = selectedResolutionOption == label,
                            onClick = {
                                selectedResolutionOption = label
                                customWidth = res.first.toString()
                                customHeight = res.second.toString()
                            },
                            label = { Text(label) },
                        )
                    }
                    FilterChip(
                        selected = selectedResolutionOption == "Custom",
                        onClick = { selectedResolutionOption = "Custom" },
                        label = { Text("Custom") },
                    )
                }

                if (selectedResolutionOption == "Custom") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = customWidth,
                            onValueChange = { customWidth = it.filter { c -> c.isDigit() } },
                            label = { Text("Width") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = customHeight,
                            onValueChange = { customHeight = it.filter { c -> c.isDigit() } },
                            label = { Text("Height") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                }

                // Video Bitrate
                OutlinedTextField(
                    value = bitrateMbpsText,
                    onValueChange = { bitrateMbpsText = it },
                    label = {
                        Text(
                            stringResource(CoreR.string.download_preset_video_bitrate_label) +
                                " (Mbps)"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Audio Codec
                Text(
                    text = stringResource(CoreR.string.download_preset_audio_codec_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    supportedAudioCodecs.forEach { codec ->
                        FilterChip(
                            selected = selectedAudioCodec == codec,
                            onClick = { selectedAudioCodec = codec },
                            label = { Text(codec.uppercase()) },
                        )
                    }
                }

                // Audio Channels
                Text(
                    text = stringResource(CoreR.string.download_preset_audio_channels_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    channelOptions.forEach { (label, channels) ->
                        FilterChip(
                            selected = selectedChannels == channels,
                            onClick = { selectedChannels = channels },
                            label = { Text(label) },
                        )
                    }
                }

                // Audio Bitrate
                Text(
                    text = stringResource(CoreR.string.download_preset_audio_bitrate_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    audioBitrateOptions.forEach { (label, bitrate) ->
                        FilterChip(
                            selected = selectedAudioBitrate == bitrate,
                            onClick = { selectedAudioBitrate = bitrate },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val w = customWidth.toIntOrNull() ?: preset.maxWidth
                    val h = customHeight.toIntOrNull() ?: preset.maxHeight
                    val mbps =
                        bitrateMbpsText.toDoubleOrNull() ?: (preset.maxBitrateBps / 1_000_000.0)
                    val bps = (mbps * 1_000_000.0).toLong().coerceAtLeast(100_000L)
                    val finalName = name.ifBlank { "${h}p Custom" }

                    val updated =
                        preset.copy(
                            customName = finalName,
                            name = UiText.DynamicString(finalName),
                            maxWidth = w,
                            maxHeight = h,
                            maxBitrateBps = bps,
                            audioCodec = selectedAudioCodec,
                            audioChannels = selectedChannels,
                            audioBitrateBps = selectedAudioBitrate,
                            approxGbPerHour = UiText.DynamicString(""),
                        )
                    onSave(updated)
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null && !preset.isOriginal) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                    ) {
                        Text(stringResource(CoreR.string.download_preset_delete))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun ImportPresetsDialog(
    onImport: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
) {
    val context = LocalContext.current
    var jsonText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = clipboard?.primaryClip?.getItemAt(0)
        val text = item?.text?.toString()
        if (!text.isNullOrBlank()) {
            jsonText = text
            isError = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(CoreR.string.download_preset_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(CoreR.string.download_preset_import_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = {
                        jsonText = it
                        isError = false
                    },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    isError = isError,
                    supportingText =
                        if (isError) {
                            {
                                Text(
                                    stringResource(CoreR.string.download_preset_import_error),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else null,
                )
                OutlinedButton(
                    onClick = { pasteFromClipboard() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_download),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(CoreR.string.download_preset_import_paste))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val success = onImport(jsonText)
                    if (success) {
                        onSuccess()
                        onDismiss()
                    } else {
                        isError = true
                    }
                }
            ) {
                Text(stringResource(CoreR.string.download_preset_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Preview(showBackground = true)
@PreviewScreenSizes
@Composable
private fun DownloadPresetsScreenPreview() {
    FindroidTheme {
        DownloadPresetsScreenContent(
            presets = DownloadQualityPresets.defaultPresets,
            navigateBack = {},
        )
    }
}
