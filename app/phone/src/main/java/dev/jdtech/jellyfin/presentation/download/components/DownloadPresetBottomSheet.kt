package dev.jdtech.jellyfin.presentation.download.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.DownloadQualityPreset
import dev.jdtech.jellyfin.models.DownloadQualityPresets
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.player.core.domain.utils.TrackFormatter
import dev.jdtech.jellyfin.presentation.download.components.presets.CheckboxOptionRow
import dev.jdtech.jellyfin.presentation.download.components.presets.PresetCard
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.utils.DeviceCodecCapabilities
import java.util.Locale
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaStreamType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadPresetBottomSheet(
    onConfirm:
        (
            presetId: String,
            downloadExternalAudio: Boolean,
            rememberSetting: Boolean,
            audioStreamIndex: Int?,
        ) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    item: FindroidItem? = null,
    presets: List<DownloadQualityPreset> = emptyList(),
    initialPresetId: String = "1080p_balanced",
    hasExternalAudio: Boolean = false,
    initialDownloadExternalAudio: Boolean = false,
    initialRememberSetting: Boolean = false,
    onAddClick: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val allPresets = remember(presets) { presets.ifEmpty { DownloadQualityPresets.defaultPresets } }

    val source =
        remember(item) {
            item?.sources?.firstOrNull { it.type == FindroidSourceType.REMOTE }
                ?: item?.sources?.firstOrNull()
        }
    val audioStreams =
        remember(source) {
            source?.mediaStreams?.filter { it.type == MediaStreamType.AUDIO } ?: emptyList()
        }
    val effectiveHasExternalAudio =
        remember(hasExternalAudio, audioStreams) {
            hasExternalAudio || audioStreams.any { it.isExternal }
        }
    val defaultAudioStream =
        remember(audioStreams) {
            audioStreams.firstOrNull { it.isDefault == true } ?: audioStreams.firstOrNull()
        }
    var selectedAudioStreamIndex by
        remember(audioStreams) { mutableStateOf(defaultAudioStream?.index) }

    val effectiveRuntimeTicks =
        remember(item) {
            if (item is FindroidSeason && item.episodes.isNotEmpty()) {
                item.episodes.sumOf { it.runtimeTicks }
            } else {
                item?.runtimeTicks ?: 0L
            }
        }
    val effectiveSourceSize =
        remember(item, source) {
            if (item is FindroidSeason && item.episodes.isNotEmpty()) {
                item.episodes.sumOf { it.sources.firstOrNull()?.size ?: 0L }
            } else {
                source?.size ?: 0L
            }
        }
    val originalVideoStream =
        remember(source) { source?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO } }

    val isOriginalSupported =
        remember(originalVideoStream) {
            DeviceCodecCapabilities.isVideoCodecSupported(originalVideoStream?.codec)
        }

    val filteredPresets =
        remember(allPresets, effectiveSourceSize, effectiveRuntimeTicks, isOriginalSupported) {
            if (!isOriginalSupported) {
                allPresets
            } else if (effectiveSourceSize > 0 && effectiveRuntimeTicks > 0) {
                val durationSeconds = effectiveRuntimeTicks / 10_000_000.0
                allPresets.filter { preset ->
                    if (preset.isOriginal) true
                    else {
                        val totalBitrateBps = preset.maxBitrateBps + preset.audioBitrateBps
                        val estimatedBytes = ((totalBitrateBps * durationSeconds) / 8.0).toLong()
                        estimatedBytes <= effectiveSourceSize
                    }
                }
            } else {
                allPresets
            }
        }

    var selectedPresetId by
        remember(initialPresetId, isOriginalSupported, filteredPresets) {
            val initial = if (initialPresetId == "direct") "original" else initialPresetId
            val valid =
                if (!isOriginalSupported && initial == "original") {
                    filteredPresets.firstOrNull { !it.isOriginal }?.id ?: "original"
                } else if (filteredPresets.any { it.id == initial }) {
                    initial
                } else {
                    filteredPresets.firstOrNull()?.id ?: "original"
                }
            mutableStateOf(valid)
        }

    var downloadExternalAudio by
        remember(initialDownloadExternalAudio) { mutableStateOf(initialDownloadExternalAudio) }
    var rememberSetting by
        remember(initialRememberSetting) { mutableStateOf(initialRememberSetting) }
    var showDetails by remember { mutableStateOf(false) }

    var isAtTop by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        DownloadPresetBottomSheetLayout(
            presets = filteredPresets,
            selectedPresetId = selectedPresetId,
            onSelectPreset = { selectedPresetId = it },
            downloadExternalAudio = downloadExternalAudio,
            onDownloadExternalAudioChange = { downloadExternalAudio = it },
            hasExternalAudio = effectiveHasExternalAudio,
            audioStreams = audioStreams,
            selectedAudioStreamIndex = selectedAudioStreamIndex,
            onSelectedAudioStreamIndexChange = { selectedAudioStreamIndex = it },
            runtimeTicks = effectiveRuntimeTicks,
            sourceSize = effectiveSourceSize,
            originalVideoStream = originalVideoStream,
            isOriginalSupported = isOriginalSupported,
            rememberSetting = rememberSetting,
            onRememberSettingChange = { rememberSetting = it },
            showDetails = showDetails,
            onShowDetailsChange = { showDetails = it },
            onAddClick = onAddClick,
            onConfirm = {
                scope.launch {
                    sheetState.hide()
                    onConfirm(
                        selectedPresetId,
                        downloadExternalAudio,
                        rememberSetting,
                        selectedAudioStreamIndex,
                    )
                }
            },
            modifier =
                Modifier.onGloballyPositioned { coordinates ->
                    val topInWindow = coordinates.positionInWindow().y
                    isAtTop = topInWindow <= 32f
                },
        )
    }
}

private fun formatAudioTrackName(
    stream: FindroidMediaStream,
    currentLocale: Locale,
): String {
    val track = TrackFormatter.toTrack(stream)
    val langOrTitle =
        TrackFormatter.formatLanguageName(track.language, currentLocale)
            ?: track.label
            ?: "Audio #${stream.index ?: 0}"
    val details =
        listOfNotNull(
            TrackFormatter.formatCodec(track.codec),
            TrackFormatter.formatChannelLayout(track.channelLayout),
        )
    return if (details.isNotEmpty()) {
        "$langOrTitle • ${details.joinToString(" • ")}"
    } else {
        langOrTitle
    }
}

@Composable
fun DownloadPresetBottomSheetLayout(
    selectedPresetId: String,
    onSelectPreset: (String) -> Unit,
    downloadExternalAudio: Boolean,
    onDownloadExternalAudioChange: (Boolean) -> Unit,
    hasExternalAudio: Boolean,
    rememberSetting: Boolean,
    onRememberSettingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<DownloadQualityPreset> = DownloadQualityPresets.all,
    audioStreams: List<FindroidMediaStream> = emptyList(),
    selectedAudioStreamIndex: Int? = null,
    onSelectedAudioStreamIndexChange: (Int?) -> Unit = {},
    runtimeTicks: Long = 0L,
    sourceSize: Long = 0L,
    originalVideoStream: FindroidMediaStream? = null,
    isOriginalSupported: Boolean = true,
    showDetails: Boolean = false,
    onShowDetailsChange: (Boolean) -> Unit = {},
    onAddClick: () -> Unit = {},
    onConfirm: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val currentLocale = configuration.locales[0]
    var audioExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(CoreR.string.download_quality_preset_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(CoreR.string.download_preset_sheet_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = { onShowDetailsChange(!showDetails) },
                shape = CircleShape,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor =
                            if (showDetails) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor =
                            if (showDetails) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_info),
                    contentDescription = stringResource(CoreR.string.download_preset_show_details),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val scrollState = rememberScrollState()
        val stopScrollPropagation = remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    return available
                }
            }
        }

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f, fill = false)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        val edgeHeight = 16.dp.toPx()

                        if (scrollState.canScrollBackward) {
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startY = 0f,
                                        endY = edgeHeight,
                                    ),
                                blendMode = BlendMode.DstIn,
                            )
                        }

                        if (scrollState.canScrollForward) {
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = size.height - edgeHeight,
                                        endY = size.height,
                                    ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    }
                    .nestedScroll(stopScrollPropagation)
                    .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            presets.forEach { preset ->
                key(preset.id) {
                    PresetCard(
                        preset = preset,
                        isSelected = preset.id == selectedPresetId,
                        showDetails = showDetails,
                        runtimeTicks = runtimeTicks,
                        sourceSize = sourceSize,
                        isOriginalSupported = isOriginalSupported,
                        originalVideoStream = originalVideoStream,
                        onSelect = { onSelectPreset(preset.id) },
                        audioStreams = audioStreams,
                        currentLocale = currentLocale,
                    )
                }
            }

            OutlinedButton(
                onClick = onAddClick,
                modifier = Modifier.defaultMinSize(minHeight = 32.dp),
                shape = RoundedCornerShape(12.dp),
                border =
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(CoreR.string.download_preset_add_preset),
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
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
            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedContent(
                    targetState = selectedPresetId == "original",
                    label = "optionsRowContent",
                ) { isOriginal ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isOriginal) {
                            if (hasExternalAudio) {
                                CheckboxOptionRow(
                                    checked = downloadExternalAudio,
                                    onCheckedChange = onDownloadExternalAudioChange,
                                    label = stringResource(CoreR.string.include_external_audio),
                                    subtitle = null,
                                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color =
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        } else if (audioStreams.isNotEmpty()) {
                            val chevronRotation by
                                animateFloatAsState(
                                    targetValue = if (audioExpanded) 180f else 0f,
                                    label = "audioChevronRotation",
                                )
                            val selectedStream =
                                audioStreams.firstOrNull { it.index == selectedAudioStreamIndex }
                                    ?: audioStreams.firstOrNull()

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .clip(
                                                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                            )
                                            .clickable(role = Role.Button) {
                                                audioExpanded = !audioExpanded
                                            }
                                            .defaultMinSize(minHeight = 48.dp)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            painter = painterResource(CoreR.drawable.ic_volume_100),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text =
                                                    stringResource(
                                                        CoreR.string.download_select_audio_track
                                                    ),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val audioLabel =
                                                    if (selectedStream != null) {
                                                        formatAudioTrackName(
                                                            selectedStream,
                                                            currentLocale,
                                                        )
                                                    } else {
                                                        stringResource(
                                                            CoreR.string
                                                                .download_preset_original_audio
                                                        )
                                                    }
                                                Text(
                                                    text = audioLabel,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                )
                                                if (selectedStream?.isDefault == true) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color =
                                                            MaterialTheme.colorScheme
                                                                .surfaceContainerHighest,
                                                    ) {
                                                        Text(
                                                            text =
                                                                stringResource(
                                                                    CoreR.string.track_default
                                                                ),
                                                            style =
                                                                MaterialTheme.typography.labelSmall,
                                                            color =
                                                                MaterialTheme.colorScheme
                                                                    .onSurfaceVariant,
                                                            modifier =
                                                                Modifier.padding(
                                                                    horizontal = 4.dp,
                                                                    vertical = 1.dp,
                                                                ),
                                                        )
                                                    }
                                                }
                                                if (selectedStream?.isExternal == true) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color =
                                                            MaterialTheme.colorScheme
                                                                .secondaryContainer,
                                                    ) {
                                                        Text(
                                                            text =
                                                                stringResource(
                                                                    CoreR.string.external
                                                                ),
                                                            style =
                                                                MaterialTheme.typography.labelSmall,
                                                            color =
                                                                MaterialTheme.colorScheme
                                                                    .onSecondaryContainer,
                                                            modifier =
                                                                Modifier.padding(
                                                                    horizontal = 4.dp,
                                                                    vertical = 1.dp,
                                                                ),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_chevron_down),
                                        contentDescription = null,
                                        modifier =
                                            Modifier.size(20.dp)
                                                .graphicsLayer(rotationZ = chevronRotation),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                AnimatedVisibility(
                                    visible = audioExpanded,
                                    enter = expandVertically(animationSpec = tween(150)),
                                    exit = shrinkVertically(animationSpec = tween(150)),
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        audioStreams.forEach { stream ->
                                            key(stream.index) {
                                                val isSelected =
                                                    stream.index == selectedAudioStreamIndex
                                                val streamTitle =
                                                    formatAudioTrackName(stream, currentLocale)

                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                    color =
                                                        MaterialTheme.colorScheme.outlineVariant
                                                            .copy(alpha = 0.3f),
                                                )
                                                Row(
                                                    modifier =
                                                        Modifier.fillMaxWidth()
                                                            .clickable(role = Role.RadioButton) {
                                                                onSelectedAudioStreamIndexChange(
                                                                    stream.index
                                                                )
                                                                if (stream.isExternal) {
                                                                    onDownloadExternalAudioChange(
                                                                        true
                                                                    )
                                                                }
                                                                audioExpanded = false
                                                            }
                                                            .defaultMinSize(minHeight = 48.dp)
                                                            .padding(
                                                                horizontal = 20.dp,
                                                                vertical = 10.dp,
                                                            ),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement =
                                                        Arrangement.SpaceBetween,
                                                ) {
                                                    Row(
                                                        verticalAlignment =
                                                            Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f),
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(
                                                                painter =
                                                                    painterResource(
                                                                        CoreR.drawable.ic_check
                                                                    ),
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp),
                                                                tint =
                                                                    MaterialTheme.colorScheme
                                                                        .primary,
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                        } else {
                                                            Spacer(modifier = Modifier.width(24.dp))
                                                        }
                                                        Text(
                                                            text = streamTitle,
                                                            style =
                                                                MaterialTheme.typography.bodyMedium,
                                                            fontWeight =
                                                                if (isSelected) FontWeight.Bold
                                                                else FontWeight.Normal,
                                                            color =
                                                                if (isSelected)
                                                                    MaterialTheme.colorScheme
                                                                        .primary
                                                                else
                                                                    MaterialTheme.colorScheme
                                                                        .onSurface,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier =
                                                                Modifier.weight(1f, fill = false),
                                                        )
                                                        if (stream.isDefault == true) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color =
                                                                    MaterialTheme.colorScheme
                                                                        .surfaceContainerHighest,
                                                            ) {
                                                                Text(
                                                                    text =
                                                                        stringResource(
                                                                            CoreR.string
                                                                                .track_default
                                                                        ),
                                                                    style =
                                                                        MaterialTheme.typography
                                                                            .labelSmall,
                                                                    color =
                                                                        MaterialTheme.colorScheme
                                                                            .onSurfaceVariant,
                                                                    modifier =
                                                                        Modifier.padding(
                                                                            horizontal = 4.dp,
                                                                            vertical = 1.dp,
                                                                        ),
                                                                )
                                                            }
                                                        }
                                                        if (stream.isExternal) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color =
                                                                    MaterialTheme.colorScheme
                                                                        .secondaryContainer,
                                                            ) {
                                                                Text(
                                                                    text =
                                                                        stringResource(
                                                                            CoreR.string.external
                                                                        ),
                                                                    style =
                                                                        MaterialTheme.typography
                                                                            .labelSmall,
                                                                    color =
                                                                        MaterialTheme.colorScheme
                                                                            .onSecondaryContainer,
                                                                    modifier =
                                                                        Modifier.padding(
                                                                            horizontal = 4.dp,
                                                                            vertical = 1.dp,
                                                                        ),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color =
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }

                CheckboxOptionRow(
                    checked = rememberSetting,
                    onCheckedChange = onRememberSettingChange,
                    label = stringResource(CoreR.string.download_preset_remember_setting),
                    shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_download),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(CoreR.string.download_button_description),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Preview
@Composable
private fun DownloadPresetBottomSheetPreview() {
    FindroidTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            DownloadPresetBottomSheetLayout(
                selectedPresetId = "1080p_balanced",
                onSelectPreset = {},
                downloadExternalAudio = false,
                onDownloadExternalAudioChange = {},
                hasExternalAudio = true,
                runtimeTicks = 72_000_000_000L,
                sourceSize = 3_450_000_000L,
                audioStreams =
                    listOf(
                        FindroidMediaStream(
                            title = "Italian (Default)",
                            displayTitle = "Italian - Dolby Digital 5.1 (Default)",
                            language = "ita",
                            type = MediaStreamType.AUDIO,
                            codec = "ac3",
                            isExternal = false,
                            path = null,
                            channelLayout = "5.1",
                            videoRangeType = null,
                            height = null,
                            width = null,
                            videoDoViTitle = null,
                            index = 1,
                            isDefault = true,
                        ),
                        FindroidMediaStream(
                            title = "English Commentary",
                            displayTitle = "English - Stereo (Commentary)",
                            language = "eng",
                            type = MediaStreamType.AUDIO,
                            codec = "aac",
                            isExternal = true,
                            path = null,
                            channelLayout = "stereo",
                            videoRangeType = null,
                            height = null,
                            width = null,
                            videoDoViTitle = null,
                            index = 2,
                            isDefault = false,
                        ),
                    ),
                selectedAudioStreamIndex = 1,
                rememberSetting = false,
                onRememberSettingChange = {},
                showDetails = true,
                onShowDetailsChange = {},
                onAddClick = {},
                onConfirm = {},
            )
        }
    }
}
