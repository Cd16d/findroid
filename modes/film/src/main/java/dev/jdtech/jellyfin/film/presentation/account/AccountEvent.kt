package dev.jdtech.jellyfin.film.presentation.account

sealed class AccountEvent {
    data object UserSwitched : AccountEvent()
}
