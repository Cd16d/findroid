package dev.jdtech.jellyfin.presentation.download.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.download.DownloadStatus
import kotlin.math.roundToInt

@Composable
fun DownloaderCard(
    state: DownloaderState,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by
        animateFloatAsState(
            targetValue = state.progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "downloader_progress",
        )

    val progressPercent by remember {
        derivedStateOf { (animatedProgress.coerceIn(0f, 1f) * 100).roundToInt() }
    }

    val textColor =
        when (state.status) {
            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }

    val statusText =
        when (state.status) {
            DownloadStatus.PENDING -> stringResource(CoreR.string.download_pending)
            DownloadStatus.PAUSED -> stringResource(CoreR.string.download_paused)
            DownloadStatus.FAILED -> stringResource(CoreR.string.download_failed)
            else -> stringResource(CoreR.string.download_downloading)
        }

    val progressIndicatorColor =
        when (state.status) {
            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
            DownloadStatus.SUCCESSFUL -> MaterialTheme.colorScheme.primary
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> ProgressIndicatorDefaults.linearColor
        }

    val progressTrackColor =
        when (state.status) {
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
            else -> ProgressIndicatorDefaults.linearTrackColor
        }

    OutlinedCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacings.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = statusText,
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "$progressPercent%",
                        color = textColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                when (state.status) {
                    DownloadStatus.PENDING -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        LinearProgressIndicator(
                            progress = { animatedProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = progressIndicatorColor,
                            trackColor = progressTrackColor,
                        )
                    }
                }
                val extraInfo = state.extraInfo
                if (extraInfo != null) {
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    Text(
                        text = extraInfo,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val errorText = state.errorText
                if (errorText != null) {
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    Text(
                        text = errorText.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            when (state.status) {
                DownloadStatus.PENDING,
                DownloadStatus.RUNNING,
                DownloadStatus.PAUSED -> {
                    FilledTonalIconButton(onClick = onCancelClick) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_x),
                            contentDescription = stringResource(CoreR.string.cancel),
                        )
                    }
                }
                DownloadStatus.FAILED -> {
                    FilledTonalIconButton(onClick = onRetryClick) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_rotate_ccw),
                            contentDescription = stringResource(CoreR.string.retry),
                        )
                    }
                }

                else -> {}
            }
        }
    }
}

@Composable
@Preview
private fun DownloaderCardPendingPreview() {
    FindroidTheme {
        DownloaderCard(
            state = DownloaderState(status = DownloadStatus.PENDING),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview
private fun DownloaderCardDownloadingPreview() {
    FindroidTheme {
        DownloaderCard(
            state = DownloaderState(status = DownloadStatus.RUNNING, progress = 0.5f),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview
private fun DownloaderCardPausedPreview() {
    FindroidTheme {
        DownloaderCard(
            state = DownloaderState(status = DownloadStatus.PAUSED, progress = 0.5f),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}

@Composable
@Preview
private fun DownloaderCardFailedPreview() {
    FindroidTheme {
        DownloaderCard(
            state =
                DownloaderState(
                    status = DownloadStatus.FAILED,
                    progress = 0.5f,
                    errorText = UiText.DynamicString("Not enough storage space"),
                ),
            onCancelClick = {},
            onRetryClick = {},
        )
    }
}
