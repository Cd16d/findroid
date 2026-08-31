package dev.jdtech.jellyfin.film.presentation.account

import dev.jdtech.jellyfin.models.User

data class AccountState(
    val user: User? = null,
    val userImageUrl: Any? = null,
    val baseUrl: String = "",
    val isQuickConnectLoading: Boolean = false,
    val quickConnectSuccess: Boolean? = null,
)
