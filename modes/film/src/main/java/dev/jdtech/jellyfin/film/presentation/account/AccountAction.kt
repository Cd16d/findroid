package dev.jdtech.jellyfin.film.presentation.account

import java.util.UUID

sealed class AccountAction {
    data class OnQuickConnectSubmit(val code: String) : AccountAction()

    data object ClearQuickConnectStatus : AccountAction()

    data class SwitchUser(val userId: UUID) : AccountAction()

    data object ToggleAccountList : AccountAction()
}
