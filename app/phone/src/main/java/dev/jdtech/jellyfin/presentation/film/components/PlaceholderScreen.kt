package dev.jdtech.jellyfin.presentation.film.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
    isEmpty: Boolean = true,
    subtitle: String? = null,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    @DrawableRes image: Int? = null,
    content: @Composable () -> Unit = {},
) {
    if (isEmpty) {
        PlaceholderScreenContent(
            title = title,
            modifier = modifier,
            subtitle = subtitle,
            buttonText = buttonText,
            onButtonClick = onButtonClick,
            image = image,
        )
    } else {
        content()
    }
}

@Composable
fun PlaceholderScreenContent(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    @DrawableRes image: Int? = null,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val isExpanded =
        windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
        )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useSideBySide = isLandscape && !isExpanded && maxWidth >= 600.dp && maxHeight >= 320.dp
        val showImage = maxHeight >= 480.dp

        if (image != null && useSideBySide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacings.default),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Image(
                    painter = painterResource(image),
                    contentDescription = null,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                )
                Column(
                    modifier =
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.spacings.default),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    if (subtitle != null) {
                        Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                        Text(
                            text = subtitle,
                            modifier =
                                Modifier.padding(horizontal = MaterialTheme.spacings.default),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (buttonText != null && onButtonClick != null) {
                        Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                        Button(onClick = onButtonClick) { Text(text = buttonText) }
                    }
                }
            }
        } else if (image != null && showImage) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(
                            vertical = MaterialTheme.spacings.extraLarge,
                            horizontal = MaterialTheme.spacings.default,
                        ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                    Text(
                        text = subtitle,
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacings.default),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.large))
                Image(
                    painter = painterResource(image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                )
                if (buttonText != null && onButtonClick != null) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.large))
                    Button(onClick = onButtonClick) { Text(text = buttonText) }
                }
            }
        } else {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(
                            vertical = MaterialTheme.spacings.default,
                            horizontal = MaterialTheme.spacings.default,
                        ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                    Text(
                        text = subtitle,
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacings.default),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                }
                if (buttonText != null && onButtonClick != null) {
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                    Button(onClick = onButtonClick) { Text(text = buttonText) }
                }
            }
        }
    }
}

@PreviewScreenSizes
@Preview(name = "Split Screen Portrait", widthDp = 411, heightDp = 350, showBackground = true)
@Preview(name = "Split Screen Landscape", widthDp = 411, heightDp = 380, showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    FindroidTheme {
        PlaceholderScreenContent(
            title = stringResource(CoreR.string.no_downloads_title),
            subtitle = stringResource(CoreR.string.no_downloads),
            buttonText = stringResource(CoreR.string.explore_library),
            onButtonClick = {},
            image = CoreR.drawable.download_page_placeholder,
        )
    }
}

@Preview(name = "No Image", showBackground = true)
@Composable
private fun PlaceholderScreenNoImagePreview() {
    FindroidTheme {
        PlaceholderScreenContent(
            title = "No items",
            subtitle = "There is nothing to display",
            buttonText = stringResource(CoreR.string.retry),
            onButtonClick = {},
        )
    }
}
