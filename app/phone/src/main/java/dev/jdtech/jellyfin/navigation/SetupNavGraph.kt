package dev.jdtech.jellyfin.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.jdtech.jellyfin.AddServerRoute
import dev.jdtech.jellyfin.LoginRoute
import dev.jdtech.jellyfin.ServerAddressesRoute
import dev.jdtech.jellyfin.ServersRoute
import dev.jdtech.jellyfin.UsersRoute
import dev.jdtech.jellyfin.WelcomeRoute
import dev.jdtech.jellyfin.presentation.setup.addresses.ServerAddressesScreen
import dev.jdtech.jellyfin.presentation.setup.addserver.AddServerScreen
import dev.jdtech.jellyfin.presentation.setup.login.LoginScreen
import dev.jdtech.jellyfin.presentation.setup.servers.ServersScreen
import dev.jdtech.jellyfin.presentation.setup.users.UsersScreen
import dev.jdtech.jellyfin.presentation.setup.welcome.WelcomeScreen

/**
 * Registers the setup flow entries.
 *
 * The same entries serve both the dedicated setup mode and the setup flow reached from the main UI
 * (e.g. via "manage servers" in Settings). [onSetupComplete] captures the difference: in setup mode
 * completing the flow flips the live condition to the main UI (a no-op), while in the main UI it
 * navigates home. It is invoked through the nearest entry's RESUMED guard.
 */
fun EntryProviderScope<NavKey>.setupEntries(
    navigate: (NavKey) -> Unit,
    goBack: () -> Unit,
    navigateOrReuse: (NavKey) -> Unit,
    showBack: Boolean,
    onSetupComplete: () -> Unit,
) {
    entry<WelcomeRoute> {
        WelcomeScreen(onContinueClick = dropUnlessResumed { navigate(ServersRoute) })
    }
    entry<ServersRoute> {
        ServersScreen(
            navigateToUsers = dropUnlessResumed { navigate(UsersRoute) },
            navigateToAddresses =
                resumedAction { serverId -> navigate(ServerAddressesRoute(serverId)) },
            onAddClick = dropUnlessResumed { navigate(AddServerRoute) },
            onBackClick = dropUnlessResumed { goBack() },
            showBack = showBack,
        )
    }
    entry<AddServerRoute> {
        AddServerScreen(
            onSuccess = dropUnlessResumed { navigate(UsersRoute) },
            onBackClick = dropUnlessResumed { goBack() },
        )
    }
    entry<ServerAddressesRoute> { key ->
        ServerAddressesScreen(
            serverId = key.serverId,
            navigateBack = dropUnlessResumed { goBack() },
        )
    }
    entry<UsersRoute> {
        UsersScreen(
            navigateToHome = dropUnlessResumed { onSetupComplete() },
            onChangeServerClick = dropUnlessResumed { navigateOrReuse(ServersRoute) },
            onAddClick = dropUnlessResumed { navigate(LoginRoute()) },
            onBackClick = dropUnlessResumed { goBack() },
            onPublicUserClick =
                resumedAction { username -> navigate(LoginRoute(username = username)) },
            showBack = showBack,
        )
    }
    entry<LoginRoute> { key ->
        LoginScreen(
            onSuccess = dropUnlessResumed { onSetupComplete() },
            onChangeServerClick = dropUnlessResumed { navigateOrReuse(ServersRoute) },
            onBackClick = dropUnlessResumed { goBack() },
            prefilledUsername = key.username,
        )
    }
}
