package dev.jdtech.jellyfin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.navigation.navigateOrReuse
import dev.jdtech.jellyfin.navigation.resumedAction
import dev.jdtech.jellyfin.presentation.components.AppScreensaver
import dev.jdtech.jellyfin.presentation.components.KeepScreenOn
import dev.jdtech.jellyfin.presentation.film.LibraryScreen
import dev.jdtech.jellyfin.presentation.film.MovieScreen
import dev.jdtech.jellyfin.presentation.film.SeasonScreen
import dev.jdtech.jellyfin.presentation.film.ShowScreen
import dev.jdtech.jellyfin.presentation.main.MainScreen
import dev.jdtech.jellyfin.presentation.player.PlayerScreen
import dev.jdtech.jellyfin.presentation.settings.SettingsScreen
import dev.jdtech.jellyfin.presentation.settings.SettingsSubScreen
import dev.jdtech.jellyfin.presentation.setup.addserver.AddServerScreen
import dev.jdtech.jellyfin.presentation.setup.login.LoginScreen
import dev.jdtech.jellyfin.presentation.setup.login.QuickConnectScreen
import dev.jdtech.jellyfin.presentation.setup.servers.ServersScreen
import dev.jdtech.jellyfin.presentation.setup.users.UsersScreen
import dev.jdtech.jellyfin.presentation.setup.welcome.WelcomeScreen
import dev.jdtech.jellyfin.viewmodels.ScreensaverViewModel
import java.util.UUID
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun NavigationRoot(
    hasServers: Boolean,
    hasCurrentServer: Boolean,
    hasCurrentUser: Boolean,
) {
    val setupComplete = hasServers && hasCurrentServer && hasCurrentUser

    val setupStartRoute: NavKey =
        when {
            hasServers && hasCurrentServer -> UsersRoute
            hasServers -> ServersRoute
            else -> WelcomeRoute
        }

    val backStack = rememberNavBackStack(if (setupComplete) MainRoute else setupStartRoute)

    // The entry decorators hold the per-entry SavedState and ViewModel stores. They must be
    // hoisted here so the retained state survives the setup/main switch.
    val saveableStateHolderDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val entryDecorators =
        remember(saveableStateHolderDecorator, viewModelStoreDecorator) {
            listOf(saveableStateHolderDecorator, viewModelStoreDecorator)
        }

    // Live setup condition: reset the stack when the app enters or leaves the setup flow. The
    // reset happens during composition (rather than in a LaunchedEffect, which only runs after
    // the first frame has been composed) so the first frame of the new mode already shows the
    // right screen.
    val appliedSetupMode = remember { AppliedSetupMode(setupComplete) }
    if (appliedSetupMode.setupComplete != setupComplete) {
        backStack.clear()
        backStack.add(if (setupComplete) MainRoute else setupStartRoute)
        appliedSetupMode.setupComplete = setupComplete
    }

    val currentRoute = backStack.lastOrNull()
    val isPlayerScreen = currentRoute is PlayerRoute

    val screensaverViewModel: ScreensaverViewModel = hiltViewModel()
    val screensaverSlides by screensaverViewModel.slides.collectAsStateWithLifecycle()
    val isScreensaverEnabled by
        screensaverViewModel.isScreensaverEnabled.collectAsStateWithLifecycle(initialValue = true)

    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isScreensaverVisible by remember { mutableStateOf(false) }
    var isDismissingScreensaver by remember { mutableStateOf(false) }

    LaunchedEffect(setupComplete, isScreensaverEnabled) {
        if (setupComplete && isScreensaverEnabled) {
            screensaverViewModel.loadSlides()
        }
    }

    LaunchedEffect(
        lastInteractionTime,
        isPlayerScreen,
        isScreensaverVisible,
        isScreensaverEnabled,
        screensaverSlides.isNotEmpty(),
    ) {
        if (
            isScreensaverEnabled &&
                !isPlayerScreen &&
                !isScreensaverVisible &&
                screensaverSlides.isNotEmpty()
        ) {
            delay(60_000L)
            isScreensaverVisible = true
        } else if (!isScreensaverEnabled) {
            isScreensaverVisible = false
        }
    }

    if (isScreensaverEnabled && isScreensaverVisible && !isPlayerScreen) {
        KeepScreenOn()
    }

    val navigate: (NavKey) -> Unit = backStack::add
    val navigateToMain: () -> Unit = {
        backStack.clear()
        backStack.add(MainRoute)
    }
    val navigateOrReuse: (NavKey) -> Unit = backStack::navigateOrReuse
    val onBack: () -> Unit = { backStack.removeLastOrNull() }

    Box(
        modifier =
            Modifier.fillMaxSize().onPreviewKeyEvent { keyEvent ->
                lastInteractionTime = System.currentTimeMillis()
                if (isScreensaverVisible) {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        isScreensaverVisible = false
                        isDismissingScreensaver = true
                    }
                    return@onPreviewKeyEvent true
                }
                if (isDismissingScreensaver) {
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        isDismissingScreensaver = false
                    }
                    return@onPreviewKeyEvent true
                }
                false
            }
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = onBack,
            entryDecorators = entryDecorators,
            entryProvider =
                entryProvider {
                    tvEntries(
                        inMainMode = setupComplete,
                        navigate = navigate,
                        navigateToMain = navigateToMain,
                        navigateOrReuse = navigateOrReuse,
                        onBack = onBack,
                    )
                },
        )

        AnimatedVisibility(
            visible = isScreensaverEnabled && isScreensaverVisible && !isPlayerScreen,
            enter = fadeIn(animationSpec = tween(600)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize().zIndex(999f),
        ) {
            AppScreensaver(
                slides = screensaverSlides,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Remembers the setup mode that the back stack was last reconciled for. Deliberately not observable
 * state: it is written during composition, which must not schedule recompositions.
 */
private class AppliedSetupMode(var setupComplete: Boolean)

private fun EntryProviderScope<NavKey>.tvEntries(
    inMainMode: Boolean,
    navigate: (NavKey) -> Unit,
    navigateToMain: () -> Unit,
    navigateOrReuse: (NavKey) -> Unit,
    onBack: () -> Unit,
) {
    entry<WelcomeRoute> {
        WelcomeScreen(onContinueClick = dropUnlessResumed { navigate(ServersRoute) })
    }
    entry<ServersRoute> {
        ServersScreen(
            navigateToUsers = dropUnlessResumed { navigate(UsersRoute) },
            onAddClick = dropUnlessResumed { navigate(AddServerRoute) },
        )
    }
    entry<AddServerRoute> {
        AddServerScreen(onSuccess = dropUnlessResumed { navigate(UsersRoute) })
    }
    entry<UsersRoute> {
        UsersScreen(
            navigateToHome =
                if (inMainMode) {
                    // Switching users does not change the setup condition: navigate to Main.
                    dropUnlessResumed { navigateToMain() }
                } else {
                    // Selecting a user completes the setup: the live condition switches to Main.
                    {}
                },
            onChangeServerClick = dropUnlessResumed { navigateOrReuse(ServersRoute) },
            onAddClick = dropUnlessResumed { navigate(QuickConnectRoute()) },
            onPublicUserClick =
                resumedAction { username -> navigate(QuickConnectRoute(username = username)) },
        )
    }
    entry<QuickConnectRoute> { key ->
        QuickConnectScreen(
            onSuccess =
                if (inMainMode) {
                    dropUnlessResumed { navigateToMain() }
                } else {
                    {}
                },
            onManualLoginClick =
                dropUnlessResumed { navigate(LoginRoute(username = key.username)) },
        )
    }
    entry<LoginRoute> { key ->
        LoginScreen(
            onSuccess =
                if (inMainMode) {
                    dropUnlessResumed { navigateToMain() }
                } else {
                    // A successful login completes the setup: the live condition switches to Main.
                    {}
                },
            onChangeServerClick = dropUnlessResumed { navigateOrReuse(ServersRoute) },
            prefilledUsername = key.username,
        )
    }
    entry<MainRoute> {
        MainScreen(
            navigateToSettings = dropUnlessResumed { navigate(SettingsRoute) },
            navigateToLibrary =
                resumedAction { libraryId, libraryName, libraryType ->
                    navigate(
                        LibraryRoute(
                            libraryId = libraryId.toString(),
                            libraryName = libraryName,
                            libraryType = libraryType,
                        )
                    )
                },
            navigateToMovie = resumedAction { itemId -> navigate(MovieRoute(itemId.toString())) },
            navigateToShow = resumedAction { itemId -> navigate(ShowRoute(itemId.toString())) },
            navigateToPlayer =
                resumedAction { itemId, itemKind ->
                    navigate(
                        PlayerRoute(itemId = itemId.toString(), itemKind = itemKind.serialName)
                    )
                },
        )
    }
    entry<LibraryRoute> { key ->
        LibraryScreen(
            libraryId = UUID.fromString(key.libraryId),
            libraryName = key.libraryName,
            libraryType = key.libraryType,
            navigateToLibrary =
                resumedAction { libraryId, libraryName, libraryType ->
                    navigate(
                        LibraryRoute(
                            libraryId = libraryId.toString(),
                            libraryName = libraryName,
                            libraryType = libraryType,
                        )
                    )
                },
            navigateToMovie = resumedAction { itemId -> navigate(MovieRoute(itemId.toString())) },
            navigateToShow = resumedAction { itemId -> navigate(ShowRoute(itemId.toString())) },
        )
    }
    entry<MovieRoute> { key ->
        MovieScreen(
            movieId = UUID.fromString(key.itemId),
            navigateToPlayer =
                resumedAction { itemId ->
                    navigate(
                        PlayerRoute(
                            itemId = itemId.toString(),
                            itemKind = BaseItemKind.MOVIE.serialName,
                        )
                    )
                },
        )
    }
    entry<ShowRoute> { key ->
        ShowScreen(
            showId = UUID.fromString(key.itemId),
            navigateToItem =
                resumedAction { item ->
                    when (item) {
                        is FindroidSeason -> navigate(SeasonRoute(seasonId = item.id.toString()))
                        else -> Unit
                    }
                },
            navigateToPlayer =
                resumedAction { itemId ->
                    navigate(
                        PlayerRoute(
                            itemId = itemId.toString(),
                            itemKind = BaseItemKind.SERIES.serialName,
                        )
                    )
                },
        )
    }
    entry<SeasonRoute> { key ->
        SeasonScreen(
            seasonId = UUID.fromString(key.seasonId),
            navigateToPlayer =
                resumedAction { itemId ->
                    navigate(
                        PlayerRoute(
                            itemId = itemId.toString(),
                            itemKind = BaseItemKind.EPISODE.serialName,
                        )
                    )
                },
        )
    }
    entry<PlayerRoute> { key ->
        PlayerScreen(
            itemId = UUID.fromString(key.itemId),
            itemKind = key.itemKind,
            startFromBeginning = false,
            onNavigateToShow =
                resumedAction { showId -> navigate(ShowRoute(itemId = showId.toString())) },
            onBack = dropUnlessResumed { onBack() },
        )
    }
    entry<SettingsRoute> {
        SettingsScreen(
            navigateToUsers = dropUnlessResumed { navigate(UsersRoute) },
            navigateToServers = dropUnlessResumed { navigate(ServersRoute) },
            navigateToSubSettings =
                resumedAction { indexes -> navigate(SettingsSubRoute(indexes = indexes)) },
        )
    }
    entry<SettingsSubRoute> { key ->
        SettingsSubScreen(
            indexes = key.indexes,
            navigateToUsers = dropUnlessResumed { navigate(UsersRoute) },
            navigateToServers = dropUnlessResumed { navigate(ServersRoute) },
            navigateToSubSettings =
                resumedAction { indexes -> navigate(SettingsSubRoute(indexes = indexes)) },
        )
    }
}
