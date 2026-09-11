package dev.jdtech.jellyfin.presentation.cast.components

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.cast.models.Device
import dev.jdtech.jellyfin.player.cast.presentation.CastPlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding

private tailrec fun Context.findViewModelStoreOwner(): ViewModelStoreOwner? =
    when (this) {
        is ViewModelStoreOwner -> this
        is ContextWrapper -> baseContext.findViewModelStoreOwner()
        else -> null
    }

@Composable
fun CastButton(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handleBottomInsets: Boolean = true,
    viewModel: CastPlayerViewModel =
        hiltViewModel(
            LocalContext.current.findViewModelStoreOwner()
                ?: checkNotNull(LocalViewModelStoreOwner.current)
        ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val devices = uiState.availableDevices

    CastButtonContent(
        deviceCount = devices.size,
        expanded = expanded,
        onClick = onClick,
        modifier = modifier,
        handleBottomInsets = handleBottomInsets,
    )
}

@Composable
fun CastButtonContent(
    deviceCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handleBottomInsets: Boolean = true,
) {
    val safePadding = rememberSafePadding(handleBottomInsets = handleBottomInsets)
    val label =
        if (deviceCount <= 0) {
            stringResource(CoreR.string.cast_connect_tv)
        } else {
            pluralStringResource(
                CoreR.plurals.cast_tvs_nearby,
                deviceCount,
                deviceCount,
            )
        }

    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = {
            Icon(
                painter = painterResource(CoreR.drawable.ic_cast),
                contentDescription = if (expanded) null else label,
            )
        },
        text = { Text(text = label) },
        modifier =
            modifier.padding(
                bottom = safePadding.bottom + MaterialTheme.spacings.medium,
                end = safePadding.end + MaterialTheme.spacings.medium,
            ),
    )
}

@Composable
fun CastButtonContent(
    devices: List<Device>,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handleBottomInsets: Boolean = true,
) {
    CastButtonContent(
        deviceCount = devices.size,
        expanded = expanded,
        onClick = onClick,
        modifier = modifier,
        handleBottomInsets = handleBottomInsets,
    )
}

@Preview(showBackground = true)
@Composable
private fun CastButtonPreview() {
    FindroidTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Expanded states")
            // No devices
            CastButtonContent(
                deviceCount = 0,
                expanded = true,
                onClick = {},
            )
            // One device
            CastButtonContent(
                deviceCount = 1,
                expanded = true,
                onClick = {},
            )
            // Multiple devices
            CastButtonContent(
                deviceCount = 2,
                expanded = true,
                onClick = {},
            )

            Text("Collapsed state", modifier = Modifier.padding(top = 16.dp))
            CastButtonContent(
                deviceCount = 1,
                expanded = false,
                onClick = {},
            )
        }
    }
}
