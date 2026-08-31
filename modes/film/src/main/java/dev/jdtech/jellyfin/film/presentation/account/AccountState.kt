package dev.jdtech.jellyfin.film.presentation.account

import dev.jdtech.jellyfin.models.User

data class AccountState(
    val user: User? = null,
    val userImageUrl: Any? = null,
    val otherUsers: List<User> = emptyList(),
    val isAccountListExpanded: Boolean = false,
    val baseUrl: String = "",
)
