package dev.jdtech.jellyfin.presentation.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.presentation.components.SpoilerParticleOverlay
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * TV spoiler mask container that blurs content and renders animated particle effect overlay.
 *
 * @param modifier Optional modifier applied to mask box. Defaults to [Modifier].
 * @param enabled Whether spoiler mask is enabled. Defaults to `true`.
 * @param shape Clipping shape for mask. Defaults to [RectangleShape].
 * @param blurRadius Blur radius when masked. Defaults to `16.dp`.
 * @param content Composable content protected by spoiler mask.
 */
@Composable
fun SpoilerMask(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    blurRadius: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier.clip(shape)) { content() }
        return
    }

    Box(modifier = modifier.clip(shape)) {
        Box(
            modifier =
                Modifier.clip(shape)
                    .then(
                        if (blurRadius > 0.dp) {
                            Modifier.blur(blurRadius)
                        } else {
                            Modifier
                        }
                    )
        ) {
            content()
        }

        SpoilerParticleOverlay(modifier = Modifier.matchParentSize())
    }
}

@Preview(name = "TV Spoiler Masked")
@Composable
private fun TvSpoilerMaskPreview() {
    FindroidTheme { SpoilerMask(enabled = true) { Text("TV Spoiler Content") } }
}

@Preview(name = "TV Spoiler Disabled")
@Composable
private fun TvSpoilerMaskDisabledPreview() {
    FindroidTheme { SpoilerMask(enabled = false) { Text("Unmasked TV Content") } }
}

@Preview(
    name = "TV Spoiler Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TvSpoilerMaskDarkModePreview() {
    FindroidTheme {
        Surface { SpoilerMask(enabled = true) { Text("Dark Mode TV Spoiler Content") } }
    }
}
