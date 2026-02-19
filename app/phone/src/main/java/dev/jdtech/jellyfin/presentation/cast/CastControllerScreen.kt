package dev.jdtech.jellyfin.presentation.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import dev.jdtech.jellyfin.core.R as CoreR
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastControllerScreen(
    navigateBack: () -> Unit,
    viewModel: CastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isExpanded =
        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
        )
    val isMedium =
        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        ) && !isExpanded

    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = stringResource(CoreR.string.cast_controller_title)) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = stringResource(CoreR.string.back),
                        )
                    }
                },
                actions = {
                    if (isMedium) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_cast),
                                contentDescription = stringResource(CoreR.string.cast_devices),
                            )
                        }
                    }
                },
            )

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Card(
                    modifier =
                        Modifier.padding(16.dp)
                            .widthIn(max = if (isExpanded) 560.dp else 640.dp)
                            .fillMaxWidth(),
                ) {
                    CastControllerContent(
                        state = state,
                        onTogglePlayback = viewModel::togglePlayback,
                    )
                }
            }
        }
    }

    if (isExpanded) {
        Row(modifier = Modifier.fillMaxSize()) {
            content()
            Card(
                modifier = Modifier.fillMaxHeight().width(320.dp).padding(16.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                CastDeviceSheetContent(
                    state = state,
                    onConnect = viewModel::connect,
                    onDisconnect = viewModel::disconnect,
                    onTogglePlayback = viewModel::togglePlayback,
                    onOpenController = {},
                    showControllerButton = false,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
        }
    } else if (isMedium) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                Card(
                    modifier = Modifier.fillMaxHeight().width(320.dp),
                    shape = RectangleShape,
                ) {
                    CastDeviceSheetContent(
                        state = state,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                        onTogglePlayback = viewModel::togglePlayback,
                        onOpenController = {},
                        showControllerButton = false,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    )
                }
            },
            content = content,
        )
    } else {
        content()
    }
}

@Composable
private fun CastControllerContent(
    state: CastUiState,
    onTogglePlayback: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CastArtwork(modifier = Modifier.weight(1f))
                CastControllerDetails(state = state, onTogglePlayback = onTogglePlayback)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CastArtwork(modifier = Modifier.fillMaxWidth())
                CastControllerDetails(state = state, onTogglePlayback = onTogglePlayback)
            }
        }
    }
}

@Composable
private fun CastArtwork(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .aspectRatio(16f / 9f)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_play),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun CastControllerDetails(
    state: CastUiState,
    onTogglePlayback: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(CoreR.string.cast_controller_now_playing),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text =
                state.connectedDevice?.name
                    ?: stringResource(CoreR.string.cast_not_connected),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_rewind),
                    contentDescription = stringResource(CoreR.string.cast_seek_back),
                )
            }
            IconButton(onClick = onTogglePlayback) {
                Icon(
                    painter =
                        painterResource(
                            if (state.playbackState == CastPlaybackState.Playing) {
                                CoreR.drawable.ic_pause
                            } else {
                                CoreR.drawable.ic_play
                            }
                        ),
                    contentDescription = stringResource(CoreR.string.cast_toggle_playback),
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_fast_forward),
                    contentDescription = stringResource(CoreR.string.cast_seek_forward),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(CoreR.string.cast_volume),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(value = 0.6f, onValueChange = {})
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = {}) {
                Text(text = stringResource(CoreR.string.cast_audio_tracks))
            }
            TextButton(onClick = {}) {
                Text(text = stringResource(CoreR.string.cast_subtitle_tracks))
            }
        }
    }
}
