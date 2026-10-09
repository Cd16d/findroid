package dev.jdtech.jellyfin.setup.presentation.login

import dev.jdtech.jellyfin.models.UiText

data class LoginState(
    val serverName: String? = null,
    val disclaimer: String? = null,
    val splashscreenUrl: String? = null,
    val logoUrl: String? = null,
    val quickConnectEnabled: Boolean = false,
    val isQuickConnectChecked: Boolean = false,
    val quickConnectCode: String? = null,
    val isLoading: Boolean = false,
    val error: UiText? = null,
)
