package dev.jdtech.jellyfin.presentation.cast

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.player.core.presentation.cast.CastDevice
import dev.jdtech.jellyfin.player.core.presentation.cast.CastPlaybackState
import dev.jdtech.jellyfin.player.core.presentation.cast.CastUiState
import dev.jdtech.jellyfin.player.core.presentation.cast.CastViewModel
import dev.jdtech.jellyfin.core.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastFabHost(
    showFab: Boolean,
    onOpenController: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    val requiredPermissions =
        remember {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.NEARBY_WIFI_DEVICES,
                    )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    )
                else -> emptyArray()
            }
        }


    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.values.all { it }) {
                showSheet = true
            }
        }

    if (showFab && state.isCastingEnabled) {
        FloatingActionButton(
            onClick = {
                val hasNearbyPermissions =
                    requiredPermissions.all { permission ->
                        ContextCompat.checkSelfPermission(context, permission) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                if (requiredPermissions.isEmpty() || hasNearbyPermissions) {
                    showSheet = true
                } else {
                    permissionLauncher.launch(requiredPermissions)
                }
            },
            modifier = modifier,
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_cast),
                contentDescription = stringResource(CoreR.string.cast_devices),
            )
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            CastDeviceSheetContent(
                state = state,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onTogglePlayback = viewModel::togglePlayback,
                onOpenController = {
                    onOpenController()
                    showSheet = false
                },
                showControllerButton = true,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
fun CastDeviceSheetContent(
    state: CastUiState,
    onConnect: (CastDevice) -> Unit,
    onDisconnect: () -> Unit,
    onTogglePlayback: () -> Unit,
    onOpenController: () -> Unit,
    showControllerButton: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(CoreR.string.cast_devices),
            style = MaterialTheme.typography.titleLarge,
        )

        Spacer(modifier = Modifier.size(16.dp))

        state.connectedDevice?.let { device ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(CoreR.string.cast_connected_to, device.name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text =
                            when (state.playbackState) {
                                CastPlaybackState.Playing ->
                                    stringResource(CoreR.string.cast_state_playing)
                                CastPlaybackState.Paused ->
                                    stringResource(CoreR.string.cast_state_paused)
                                CastPlaybackState.Idle ->
                                    stringResource(CoreR.string.cast_state_idle)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
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
                                contentDescription =
                                    stringResource(CoreR.string.cast_toggle_playback),
                            )
                        }
                        if (showControllerButton) {
                            androidx.compose.material3.Button(onClick = onOpenController) {
                                Text(text = stringResource(CoreR.string.cast_open_controller))
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = onDisconnect) {
                            Text(text = stringResource(CoreR.string.cast_disconnect))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.size(24.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.devices, key = { it.id }) { device ->
                ListItem(
                    headlineContent = { Text(text = device.name) },
                    supportingContent = { Text(text = device.location) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_cast),
                            contentDescription = null,
                        )
                    },
                    trailingContent = {
                        val isConnected = state.connectedDevice?.id == device.id
                        if (isConnected) {
                            Text(text = stringResource(CoreR.string.cast_connected))
                        } else {
                            androidx.compose.material3.TextButton(onClick = { onConnect(device) }) {
                                Text(text = stringResource(CoreR.string.cast_connect))
                            }
                        }
                    },
                )
            }
        }
    }
}
