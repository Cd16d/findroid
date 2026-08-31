package dev.jdtech.jellyfin.presentation.account.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import dev.jdtech.jellyfin.core.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickConnectBottomSheet(
    onDismissRequest: () -> Unit,
    onSubmit: (String) -> Unit,
    onClearError: () -> Unit = {},
    isLoading: Boolean = false,
    error: String? = null,
    isSuccess: Boolean = false
) {
    var code by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = {}
    ) {
        QuickConnectCodeInput(
            code = code,
            onCodeChange = { 
                code = it 
                onClearError()
            },
            onSubmit = { onSubmit(code) },
            isLoading = isLoading,
            error = error,
            isSuccess = isSuccess
        )
    }
}

@Composable
fun QuickConnectCodeInput(
    modifier: Modifier = Modifier,
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
    isSuccess: Boolean = false
) {
    val isComplete = code.length == 6
    val isError = error != null

    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(400.milliseconds) // Delay to ensure sheet is fully settled
        focusRequester.requestFocus()
    }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null) {
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
            repeat(4) {
                shake.animateTo(10f, tween(50))
                shake.animateTo(-10f, tween(50))
            }
            shake.animateTo(0f, tween(50))
        }
    }

    val waveOffsets = remember { List(6) { Animatable(0f) } }
    val waveScales = remember { List(6) { Animatable(1f) } }
    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            waveOffsets.forEachIndexed { index, animatable ->
                launch {
                    delay((index * 100).milliseconds)
                    launch {
                        waveScales[index].animateTo(1.2f, tween(200, easing = FastOutSlowInEasing))
                        waveScales[index].animateTo(1f, tween(200, easing = FastOutSlowInEasing))
                    }
                    animatable.animateTo(-20f, tween(200, easing = FastOutSlowInEasing))
                    animatable.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(all = MaterialTheme.spacings.default)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(CoreR.string.quick_connect),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(CoreR.string.quick_connect_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        BasicTextField(
            value = code,
            onValueChange = { newValue ->
                if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                    onCodeChange(newValue)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSubmit() }
            ),
            modifier = Modifier
                .focusRequester(focusRequester)
                .offset { IntOffset(shake.value.roundToInt(), 0) },
            decorationBox = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(6) { index ->
                        val char = code.getOrNull(index)
                        val isFocused = index == code.length
                        
                        val targetBackgroundColor = when {
                            isSuccess -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                            isError -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val backgroundColor by animateColorAsState(targetBackgroundColor, label = "BoxBackground")
                        
                        val targetTextColor = when {
                            isSuccess && char != null -> Color(0xFF4CAF50)
                            isError && char != null -> MaterialTheme.colorScheme.error
                            char != null -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        }
                        val textColor by animateColorAsState(targetTextColor, label = "BoxText")

                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 56.dp)
                                .graphicsLayer {
                                    translationY = waveOffsets[index].value.dp.toPx()
                                    scaleX = waveScales[index].value
                                    scaleY = waveScales[index].value
                                }
                                .background(backgroundColor, MaterialTheme.shapes.small)
                                .border(
                                    width = 2.dp,
                                    color = when {
                                        isFocused -> MaterialTheme.colorScheme.primary
                                        else -> Color.Transparent
                                    },
                                    shape = MaterialTheme.shapes.small
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = char?.toString() ?: "-",
                                style = MaterialTheme.typography.headlineMedium,
                                color = textColor
                            )
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box {
            if (isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.CenterStart)
                        .offset(x = 16.dp),
                )
            }
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onSubmit()
                },
                enabled = isComplete && !isLoading && !isSuccess,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(
                    text = if (isSuccess) stringResource(CoreR.string.authenticated) else stringResource(CoreR.string.authenticate)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QuickConnectBottomSheetPreview() {
    FindroidTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            QuickConnectCodeInput(
                code = "",
                onCodeChange = {},
                onSubmit = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QuickConnectBottomSheetFullPreview() {
    FindroidTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            QuickConnectCodeInput(
                code = "123456",
                onCodeChange = {},
                onSubmit = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QuickConnectBottomSheetLoadingPreview() {
    FindroidTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            QuickConnectCodeInput(
                code = "123456",
                onCodeChange = {},
                onSubmit = {},
                isLoading = true
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QuickConnectBottomSheetErrorPreview() {
    FindroidTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            QuickConnectCodeInput(
                code = "123456",
                onCodeChange = {},
                onSubmit = {},
                error = "error"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QuickConnectBottomSheetSuccessPreview() {
    FindroidTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            QuickConnectCodeInput(
                code = "123456",
                onCodeChange = {},
                onSubmit = {},
                isSuccess = true
            )
        }
    }
}
