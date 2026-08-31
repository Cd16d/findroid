package dev.jdtech.jellyfin.film.presentation.account

import java.util.UUID

sealed class AccountAction {
    data class SwitchUser(val userId: UUID) : AccountAction()
    data object ToggleAccountList : AccountAction()
}