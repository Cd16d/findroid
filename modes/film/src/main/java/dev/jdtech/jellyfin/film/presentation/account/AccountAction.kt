package dev.jdtech.jellyfin.film.presentation.account


sealed class AccountAction {
    data class OnQuickConnectSubmit(val code: String) : AccountAction()
    data object ClearQuickConnectStatus : AccountAction()
}