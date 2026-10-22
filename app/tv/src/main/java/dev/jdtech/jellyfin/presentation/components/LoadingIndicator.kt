package dev.jdtech.jellyfin.presentation.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Indeterminate circular loading indicator styled for TV interfaces. */
@Composable
fun LoadingIndicator() {
    CircularProgressIndicator(
        color = Color.White,
        strokeWidth = 2.dp,
        trackColor = Color.Transparent,
        modifier = Modifier.size(32.dp),
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun LoadingIndicatorPreview() {
    dev.jdtech.jellyfin.presentation.theme.FindroidTheme { LoadingIndicator() }
}
