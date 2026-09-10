package dev.jdtech.jellyfin.presentation.setup.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.setup.R as SetupR
import dev.jdtech.jellyfin.setup.presentation.login.LoginAction
import dev.jdtech.jellyfin.setup.presentation.login.LoginEvent
import dev.jdtech.jellyfin.setup.presentation.login.LoginState
import dev.jdtech.jellyfin.setup.presentation.login.LoginViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun QuickConnectScreen(
    onSuccess: () -> Unit,
    onManualLoginClick: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadQuickConnectEnabled()
        viewModel.loadBranding()
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is LoginEvent.Success -> onSuccess()
        }
    }

    LaunchedEffect(state.isQuickConnectChecked, state.quickConnectEnabled) {
        if (state.isQuickConnectChecked && !state.quickConnectEnabled) {
            onManualLoginClick()
        }
    }

    LaunchedEffect(state.quickConnectEnabled) {
        if (state.quickConnectEnabled && state.quickConnectCode == null) {
            viewModel.onAction(LoginAction.OnQuickConnectClick)
        }
    }

    QuickConnectScreenLayout(
        state = state,
        onManualLoginClick = onManualLoginClick,
    )
}

@Composable
private fun QuickConnectScreenLayout(
    state: LoginState,
    onManualLoginClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = state.splashscreenUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier =
                Modifier.fillMaxSize().drawBehind {
                    val side1 = size.width
                    val side2 = size.height
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = 0.9f),
                                        Color.Black.copy(alpha = 0.6f),
                                        Color.Transparent,
                                    ),
                                center = Offset(side1, side2),
                                radius = if (side1 < side2) side1 else side2,
                            )
                    )
                }
        )

        Surface(
            modifier =
                Modifier.align(Alignment.BottomStart)
                    .padding(MaterialTheme.spacings.large)
                    .width(450.dp),
            tonalElevation = 5.dp,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(MaterialTheme.spacings.default),
            ) {
                Text(
                    text = stringResource(SetupR.string.login_btn_quick_connect),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                QuickConnectCodeDisplay(code = state.quickConnectCode)
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f).padding(end = MaterialTheme.spacings.medium)
                    )
                    androidx.compose.material3.Text(
                        text = stringResource(SetupR.string.or),
                        color = DividerDefaults.color,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider(
                        modifier =
                            Modifier.weight(1f).padding(start = MaterialTheme.spacings.medium)
                    )
                }
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                Box {
                    Button(
                        onClick = onManualLoginClick,
                        modifier = Modifier.width(360.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(id = SetupR.string.login_btn_login),
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
            }
        }

        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = Modifier.fillMaxSize(),
        ) {
            Image(
                painterResource(CoreR.drawable.ic_logo),
                contentDescription = null,
                alignment = Alignment.BottomEnd,
                modifier = Modifier.padding(MaterialTheme.spacings.large).size(64.dp),
            )
        }
    }
}

@Composable
private fun QuickConnectCodeDisplay(code: String?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 6) {
            val char = code?.getOrNull(i) ?: '0'
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                QuickConnectChar(
                    char = char,
                    isLoading = code == null,
                )
            }
        }
    }
}

@Composable
private fun QuickConnectChar(
    char: Char,
    isLoading: Boolean,
) {
    var displayChar by remember { mutableStateOf('0') }
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(isLoading, char) {
        if (isLoading) {
            displayChar = ('0'..'9').random()
            delay(Random.nextLong(0, 100).milliseconds)

            while (isActive) {
                displayChar = ('0'..'9').random()
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec =
                        tween(
                            durationMillis = Random.nextInt(600, 1000),
                            easing = LinearEasing,
                        ),
                )
            }
        } else {
            displayChar = char
            rotation.animateTo(
                targetValue = 0f,
                animationSpec =
                    tween(
                        durationMillis = Random.nextInt(600, 1000),
                        easing = FastOutSlowInEasing,
                    ),
            )
        }
    }

    Text(
        text = displayChar.toString(),
        style = MaterialTheme.typography.displayLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.graphicsLayer { rotationX = rotation.value },
    )
}

@Preview(device = "id:tv_1080p")
@Composable
private fun QuickConnectScreenPreview() {
    FindroidTheme {
        QuickConnectScreenLayout(
            state =
                LoginState(
                    quickConnectCode = "123456",
                    quickConnectEnabled = true,
                ),
            onManualLoginClick = {},
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun QuickConnectScreenLoadingPreview() {
    FindroidTheme {
        QuickConnectScreenLayout(
            state =
                LoginState(
                    quickConnectCode = null,
                    quickConnectEnabled = true,
                ),
            onManualLoginClick = {},
        )
    }
}
