package dev.jdtech.jellyfin

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidBoxSet
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidFolder
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.VolumeKeyEvent
import dev.jdtech.jellyfin.navigation.navigateOrReuse
import dev.jdtech.jellyfin.navigation.resumedAction
import dev.jdtech.jellyfin.navigation.setupEntries
import dev.jdtech.jellyfin.player.cast.models.CastConnectionState
import dev.jdtech.jellyfin.player.cast.presentation.CastSessionViewModel
import dev.jdtech.jellyfin.presentation.account.AccountMenuWrapper
import dev.jdtech.jellyfin.presentation.cast.CastExpandedPlayer
import dev.jdtech.jellyfin.presentation.cast.CastMiniPlayer
import dev.jdtech.jellyfin.presentation.cast.components.CastBottomSheet
import dev.jdtech.jellyfin.presentation.cast.components.CastButton
import dev.jdtech.jellyfin.presentation.download.DownloadsScreen
import dev.jdtech.jellyfin.presentation.download.ShowDownloadsScreen
import dev.jdtech.jellyfin.presentation.film.CollectionScreen
import dev.jdtech.jellyfin.presentation.film.EpisodeScreen
import dev.jdtech.jellyfin.presentation.film.FavoritesScreen
import dev.jdtech.jellyfin.presentation.film.HomeScreen
import dev.jdtech.jellyfin.presentation.film.LibraryScreen
import dev.jdtech.jellyfin.presentation.film.MediaScreen
import dev.jdtech.jellyfin.presentation.film.MovieScreen
import dev.jdtech.jellyfin.presentation.film.PersonScreen
import dev.jdtech.jellyfin.presentation.film.SearchScreen
import dev.jdtech.jellyfin.presentation.film.SeasonScreen
import dev.jdtech.jellyfin.presentation.film.ShowScreen
import dev.jdtech.jellyfin.presentation.settings.AboutScreen
import dev.jdtech.jellyfin.presentation.settings.DeviceScreen
import dev.jdtech.jellyfin.presentation.settings.DownloadPresetsScreen
import dev.jdtech.jellyfin.presentation.settings.SettingsFileEditScreen
import dev.jdtech.jellyfin.presentation.settings.SettingsScreen
import dev.jdtech.jellyfin.presentation.settings.SmartDownloadsScreen
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.restart
import dev.jdtech.jellyfin.viewmodels.MainViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

private val TOP_LEVEL_ROUTES = setOf(HomeRoute, MediaRoute, SearchRoute, DownloadsRoute)
private val GLOBAL_TRANSITION_SPEC = fadeIn(tween(300)) togetherWith fadeOut(tween(300))

data class TabBarItem(
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    val route: NavKey,
    val enabled: Boolean = true,
)

val homeTab =
    TabBarItem(title = CoreR.string.title_home, icon = CoreR.drawable.ic_home, route = HomeRoute)

val mediaTab =
    TabBarItem(
        title = CoreR.string.title_my_library,
        icon = CoreR.drawable.ic_library,
        route = MediaRoute,
    )

val searchTab =
    TabBarItem(
        title = CoreR.string.search,
        icon = CoreR.drawable.ic_search,
        route = SearchRoute,
    )

val downloadsTab =
    TabBarItem(
        title = CoreR.string.title_download,
        icon = CoreR.drawable.ic_download,
        route = DownloadsRoute,
    )

val LocalCastPlayerHeight = compositionLocalOf { 0.dp }

val LocalVolumeKeyEvents = compositionLocalOf<SharedFlow<VolumeKeyEvent>> { MutableSharedFlow() }

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

    // Navigation state for both modes is hoisted so that it survives switching between them.
    val setupBackStack = rememberNavBackStack(setupStartRoute)
    val navigationState =
        rememberNavigationState(
            startRoute = HomeRoute,
            topLevelRoutes = TOP_LEVEL_ROUTES,
        )
    val navigator = remember(navigationState) { Navigator(navigationState) }

    // The entry decorators hold the per-entry SavedState and ViewModel stores. They must be
    // hoisted as well, otherwise the retained state would be discarded when switching modes.
    val saveableStateHolderDecorator = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<NavKey>()
    val entryDecorators =
        remember(saveableStateHolderDecorator, viewModelStoreDecorator) {
            listOf(saveableStateHolderDecorator, viewModelStoreDecorator)
        }

    // Live setup condition: keep the back stacks in a consistent state across mode switches. The
    // stacks are reconciled during composition (rather than in a LaunchedEffect, which only runs
    // after the first frame has been composed) so the first frame of the new mode already shows
    // the right screen.
    val appliedSetupMode = remember { AppliedSetupMode(setupComplete) }
    if (appliedSetupMode.setupComplete != setupComplete) {
        if (setupComplete) {
            // Setup completed: discard the setup flow and land on Home.
            navigator.navigateHome()
        } else {
            // Setup became incomplete: restart the setup flow at the appropriate screen.
            setupBackStack.clear()
            setupBackStack.add(setupStartRoute)
        }
        appliedSetupMode.setupComplete = setupComplete
    }

    // Exposed as a state object (rather than a plain value) so the entry content closures, which
    if (setupComplete) {
        MainNavigation(
            navigationState = navigationState,
            navigator = navigator,
            entryDecorators = entryDecorators,
            isOfflineMode = LocalOfflineMode.current,
        )
    } else {
        SetupNavigation(
            backStack = setupBackStack,
            entryDecorators = entryDecorators,
        )
    }
}

/**
 * Remembers the setup mode that the back stacks were last reconciled for. Deliberately not
 * observable state: it is written during composition, which must not schedule recompositions.
 */
private class AppliedSetupMode(var setupComplete: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavigation(
    navigationState: NavigationState,
    navigator: Navigator,
    entryDecorators: List<NavEntryDecorator<NavKey>>,
    isOfflineMode: Boolean,
) {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = hiltViewModel(context as ViewModelStoreOwner)
    val castSessionViewModel: CastSessionViewModel = hiltViewModel(context as ViewModelStoreOwner)
    val searchViewModel: SearchViewModel = hiltViewModel(context as ViewModelStoreOwner)

    val navigationItems =
        when (isOfflineMode) {
            false -> listOf(homeTab, mediaTab, searchTab, downloadsTab)
            true -> listOf(homeTab, downloadsTab)
        }
    val tabRoutes = navigationItems.map { it.route }

    val topLevelRoute = navigationState.topLevelRoute
    val atTabRoot = navigationState.currentBackStack().last() == topLevelRoute
    val showBottomBar = atTabRoot && topLevelRoute in tabRoutes

    val safePadding =
        rememberSafePadding(
            handleStartInsets = false,
            handleBottomInsets = !showBottomBar,
        )

    val navigationSuiteScaffoldState = rememberNavigationSuiteScaffoldState()

    LaunchedEffect(showBottomBar) {
        if (showBottomBar) {
            navigationSuiteScaffoldState.show()
        } else {
            navigationSuiteScaffoldState.hide()
        }
    }

    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val isExpandedScreen =
        windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
        )
    val isMediumScreen =
        windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        )
    val customNavSuiteType =
        with(windowAdaptiveInfo.windowSizeClass) {
            when {
                // Compact Width (Phone Portrait) -> Always Compact Bar
                !isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> {
                    NavigationSuiteType.ShortNavigationBarCompact
                }

                // Expanded Width (Tablet Landscape/PC) OR Compact Height (Phone Landscape) -> Rail
                isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ||
                    !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) -> {
                    NavigationSuiteType.WideNavigationRailCollapsed
                }

                // Medium Width + Medium Height (Tablet Portrait) -> Medium Bar
                else -> {
                    NavigationSuiteType.ShortNavigationBarMedium
                }
            }
        }

    var isAccountMenuOpen by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    var castPlayerHeight by remember { mutableStateOf(MaterialTheme.spacings.default) }
    var showCastSheet by remember { mutableStateOf(false) }
    var showCastExpandedPlayer by remember { mutableStateOf(false) }
    var isDismissingCastExpandedPlayer by remember { mutableStateOf(false) }
    var castExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(showCastExpandedPlayer, isDismissingCastExpandedPlayer) {
        mainViewModel.setCastPlayerOpen(showCastExpandedPlayer && !isDismissingCastExpandedPlayer)
    }

    val connectionState by castSessionViewModel.connectionState.collectAsStateWithLifecycle()
    val currentRoute = navigationState.currentBackStack().lastOrNull()
    val isCastRoute =
        when (currentRoute) {
            is HomeRoute,
            is ShowRoute,
            is MovieRoute,
            is LibraryRoute,
            is EpisodeRoute,
            is SeasonRoute,
            is FavoritesRoute,
            is CollectionRoute -> true
            else -> false
        }
    val showCastButton =
        isCastRoute && !isOfflineMode && castSessionViewModel.sessionManager.isSupported

    val showCastMiniPlayer by
        remember(
            showCastButton,
            connectionState,
            showCastExpandedPlayer,
            isDismissingCastExpandedPlayer,
        ) {
            derivedStateOf {
                val connected = showCastButton && connectionState == CastConnectionState.CONNECTED
                connected && (!showCastExpandedPlayer || isDismissingCastExpandedPlayer)
            }
        }

    LaunchedEffect(showCastMiniPlayer) {
        if (!showCastMiniPlayer) castPlayerHeight = MaterialTheme.spacings.default
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (consumed.y < -15f) {
                    castExpanded = false
                } else if (consumed.y > 15f) {
                    castExpanded = true
                }
                return Offset.Zero
            }
        }
    }

    val entryProvider = entryProvider {
        mainEntries(
            navigator = navigator,
            onUserClick = { isAccountMenuOpen = true },
        )
    }

    CompositionLocalProvider(LocalCastPlayerHeight provides castPlayerHeight) {
        AccountMenuWrapper(
            isMenuOpen = isAccountMenuOpen,
            onCloseMenu = { isAccountMenuOpen = false },
            onNavigateToSettings = {
                navigator.navigate(SettingsRoute(indexes = listOf(CoreR.string.title_settings)))
                isAccountMenuOpen = false
            },
            onNavigateToAbout = {
                navigator.navigate(AboutRoute)
                isAccountMenuOpen = false
            },
            onAddUser = {
                navigator.navigate(LoginRoute())
                isAccountMenuOpen = false
            },
            onManageAccounts = {
                navigator.navigate(UsersRoute)
                isAccountMenuOpen = false
            },
            onSwitchUser = {
                navigator.navigateHome()
                isAccountMenuOpen = false
            },
        ) {
            NavigationSuiteScaffold(
                navigationItems = {
                    val haptics = LocalHapticFeedback.current
                    navigationItems.forEach { item ->
                        NavigationSuiteItem(
                            selected = topLevelRoute == item.route,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                if (topLevelRoute == item.route) {
                                    if (item.route == SearchRoute) {
                                        searchViewModel.onAction(SearchAction.OnTabReselected)
                                    }
                                } else {
                                    navigator.navigate(item.route)
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(item.icon),
                                    contentDescription = stringResource(item.title),
                                )
                            },
                            enabled = item.enabled,
                            label = { Text(text = stringResource(item.title)) },
                            navigationSuiteType = customNavSuiteType,
                        )
                    }
                },
                navigationItemVerticalArrangement = Arrangement.Center,
                navigationSuiteType = customNavSuiteType,
                state = navigationSuiteScaffoldState,
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).nestedScroll(nestedScrollConnection)) {
                        NavDisplay(
                            entries =
                                navigationState.toDecoratedEntries(entryDecorators, entryProvider),
                            onBack = { navigator.goBack() },
                            transitionSpec = { GLOBAL_TRANSITION_SPEC },
                            popTransitionSpec = { GLOBAL_TRANSITION_SPEC },
                            predictivePopTransitionSpec = { _ -> GLOBAL_TRANSITION_SPEC },
                        )

                        if (showCastButton && connectionState != CastConnectionState.CONNECTED) {
                            CastButton(
                                expanded = castExpanded,
                                onClick = { showCastSheet = true },
                                modifier = Modifier.align(Alignment.BottomEnd),
                                handleBottomInsets = !showBottomBar,
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showCastExpandedPlayer && isExpandedScreen,
                        enter =
                            slideInHorizontally(initialOffsetX = { it }) +
                                expandHorizontally(expandFrom = Alignment.End) +
                                fadeIn(),
                        exit =
                            slideOutHorizontally(targetOffsetX = { it }) +
                                shrinkHorizontally(shrinkTowards = Alignment.End) +
                                fadeOut(),
                    ) {
                        CastExpandedPlayer(
                            onDeviceClick = { showCastSheet = true },
                            onClose = {
                                showCastExpandedPlayer = false
                                isDismissingCastExpandedPlayer = false
                            },
                        )
                    }
                }

                // Cast Mini Player
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment =
                        if (isMediumScreen) Alignment.BottomEnd else Alignment.BottomCenter,
                ) {
                    AnimatedVisibility(
                        visible = showCastMiniPlayer,
                        enter =
                            (if (isExpandedScreen) {
                                slideInHorizontally(initialOffsetX = { it })
                            } else {
                                slideInVertically(initialOffsetY = { it })
                            }) + fadeIn(),
                        exit =
                            (if (isExpandedScreen) {
                                slideOutHorizontally(targetOffsetX = { it })
                            } else {
                                slideOutVertically(targetOffsetY = { it })
                            }) + fadeOut(),
                    ) {
                        CastMiniPlayer(
                            onClick = {
                                isDismissingCastExpandedPlayer = false
                                showCastExpandedPlayer = true
                            },
                            modifier =
                                Modifier.onSizeChanged { size ->
                                    val measuredHeight = with(density) { size.height.toDp() }
                                    castPlayerHeight =
                                        if (!isMediumScreen) {
                                            measuredHeight - safePadding.bottom
                                        } else {
                                            MaterialTheme.spacings.default
                                        }
                                },
                            handleBottomInsets = !showBottomBar,
                        )
                    }
                }
            }
        }

        if (showCastSheet) {
            CastBottomSheet(onDismissRequest = { showCastSheet = false })
        }

        // Cast Expanded Player
        if (showCastExpandedPlayer && !isExpandedScreen) {
            CastExpandedPlayer(
                onDeviceClick = { showCastSheet = true },
                onClose = {
                    showCastExpandedPlayer = false
                    isDismissingCastExpandedPlayer = false
                },
                onDismissStarted = { isDismissingCastExpandedPlayer = true },
                onDismissCanceled = { isDismissingCastExpandedPlayer = false },
            )
        }
    }
}

@Composable
private fun SetupNavigation(
    backStack: NavBackStack<NavKey>,
    entryDecorators: List<NavEntryDecorator<NavKey>>,
) {
    val navigate: (NavKey) -> Unit = backStack::add
    val goBack: () -> Unit = { backStack.removeLastOrNull() }
    val navigateOrReuse: (NavKey) -> Unit = backStack::navigateOrReuse

    val entryProvider = entryProvider {
        setupEntries(
            navigate = navigate,
            goBack = goBack,
            navigateOrReuse = navigateOrReuse,
            showBack = backStack.size > 1,
            onSetupComplete = {},
        )
    }

    NavDisplay(
        backStack = backStack,
        onBack = goBack,
        entryDecorators = entryDecorators,
        entryProvider = entryProvider,
        transitionSpec = { GLOBAL_TRANSITION_SPEC },
        popTransitionSpec = { GLOBAL_TRANSITION_SPEC },
        predictivePopTransitionSpec = { _ -> GLOBAL_TRANSITION_SPEC },
    )
}

private fun EntryProviderScope<NavKey>.mainEntries(
    navigator: Navigator,
    onUserClick: () -> Unit,
) {
    setupEntries(
        navigate = navigator::navigate,
        goBack = navigator::goBack,
        navigateOrReuse = navigator::navigateOrReuse,
        showBack = true,
        onSetupComplete = { navigator.navigateHome() },
    )
    entry<HomeRoute> {
        HomeScreen(
            onLibraryClick =
                resumedAction { library ->
                    navigator.navigate(
                        LibraryRoute(
                            libraryId = library.id.toString(),
                            libraryName = library.name,
                            libraryType = library.type,
                        )
                    )
                },
            onUserClick = dropUnlessResumed { onUserClick() },
            onManageServers = dropUnlessResumed { navigator.navigate(ServersRoute) },
            onItemClick = resumedAction { item -> navigateToItem(navigator, item) },
        )
    }
    entry<MediaRoute> {
        MediaScreen(
            onItemClick = resumedAction { item -> navigateToItem(navigator, item) },
            onFavoritesClick = dropUnlessResumed { navigator.navigate(FavoritesRoute) },
        )
    }
    entry<SearchRoute> {
        SearchScreen(onItemClick = resumedAction { item -> navigateToItem(navigator, item) })
    }
    entry<DownloadsRoute> {
        val context = LocalContext.current
        DownloadsScreen(
            onMovieClick =
                resumedAction { movie ->
                    navigator.navigate(MovieRoute(movieId = movie.id.toString()))
                },
            onShowClick =
                resumedAction { show ->
                    navigator.navigate(
                        ShowDownloadsRoute(
                            showId = show.id.toString(),
                            showTitle = show.name,
                        )
                    )
                },
            onStorageClick =
                dropUnlessResumed {
                    navigator.navigate(SettingsRoute(indexes = listOf(CoreR.string.title_download)))
                },
            onExploreLibraryClick = dropUnlessResumed { navigator.navigate(MediaRoute) },
            onGoOnlineClick =
                dropUnlessResumed {
                    val appPreferences = AppPreferences(context)
                    appPreferences.setValue(
                        appPreferences.offlineMode,
                        false,
                    )
                    try {
                        (context as Activity).restart()
                    } catch (_: Exception) {}
                },
        )
    }
    entry<ShowDownloadsRoute> { key ->
        ShowDownloadsScreen(
            showId = UUID.fromString(key.showId),
            showTitle = key.showTitle,
            onEpisodeClick =
                resumedAction { episode ->
                    navigator.navigate(EpisodeRoute(episodeId = episode.id.toString()))
                },
            navigateBack = dropUnlessResumed { navigator.goBack() },
        )
    }
    entry<LibraryRoute> { key ->
        LibraryScreen(
            libraryId = UUID.fromString(key.libraryId),
            libraryName = key.libraryName,
            libraryType = key.libraryType,
            onItemClick = resumedAction { item -> navigateToItem(navigator, item) },
            navigateBack = dropUnlessResumed { navigator.goBack() },
        )
    }
    entry<CollectionRoute> { key ->
        CollectionScreen(
            collectionId = UUID.fromString(key.collectionId),
            collectionName = key.collectionName,
            onItemClick = resumedAction { item -> navigateToItem(navigator, item) },
            navigateBack = dropUnlessResumed { navigator.goBack() },
        )
    }
    entry<FavoritesRoute> {
        FavoritesScreen(
            onItemClick = resumedAction { item -> navigateToItem(navigator, item) },
            navigateBack = dropUnlessResumed { navigator.goBack() },
            onExploreLibraryClick = dropUnlessResumed { navigator.navigate(MediaRoute) },
        )
    }
    entry<MovieRoute> { key ->
        MovieScreen(
            movieId = UUID.fromString(key.movieId),
            navigateBack = dropUnlessResumed { navigator.goBack() },
            navigateHome = dropUnlessResumed { navigator.navigateHome() },
            navigateToPerson =
                resumedAction { personId ->
                    navigator.navigate(PersonRoute(personId = personId.toString()))
                },
            navigateToDownloadPresets =
                dropUnlessResumed { navigator.navigate(DownloadPresetsRoute) },
        )
    }
    entry<ShowRoute> { key ->
        ShowScreen(
            showId = UUID.fromString(key.showId),
            navigateBack = dropUnlessResumed { navigator.goBack() },
            navigateHome = dropUnlessResumed { navigator.navigateHome() },
            navigateToItem =
                resumedAction { item ->
                    navigateToItem(
                        navigator,
                        item,
                    )
                },
            navigateToPerson =
                resumedAction { personId ->
                    navigator.navigate(PersonRoute(personId = personId.toString()))
                },
        )
    }
    entry<SeasonRoute> { key ->
        SeasonScreen(
            seasonId = UUID.fromString(key.seasonId),
            navigateBack = dropUnlessResumed { navigator.goBack() },
            navigateHome = dropUnlessResumed { navigator.navigateHome() },
            navigateToItem =
                resumedAction { item ->
                    navigateToItem(
                        navigator,
                        item,
                    )
                },
            navigateToSeries =
                resumedAction { seriesId ->
                    navigator.navigateOrReuse(ShowRoute(showId = seriesId.toString()))
                },
            navigateToDownloadPresets =
                dropUnlessResumed { navigator.navigate(DownloadPresetsRoute) },
        )
    }
    entry<EpisodeRoute> { key ->
        EpisodeScreen(
            episodeId = UUID.fromString(key.episodeId),
            navigateBack = dropUnlessResumed { navigator.goBack() },
            navigateHome = dropUnlessResumed { navigator.navigateHome() },
            navigateToPerson =
                resumedAction { personId ->
                    navigator.navigate(PersonRoute(personId = personId.toString()))
                },
            navigateToSeason =
                resumedAction { seasonId ->
                    navigator.navigateOrReuse(SeasonRoute(seasonId = seasonId.toString()))
                },
            navigateToDownloadPresets =
                dropUnlessResumed { navigator.navigate(DownloadPresetsRoute) },
        )
    }
    entry<PersonRoute> { key ->
        PersonScreen(
            personId = UUID.fromString(key.personId),
            navigateBack = dropUnlessResumed { navigator.goBack() },
            navigateHome = dropUnlessResumed { navigator.navigateHome() },
            navigateToItem =
                resumedAction { item ->
                    navigateToItem(
                        navigator,
                        item,
                    )
                },
        )
    }
    entry<SettingsRoute> { key ->
        SettingsScreen(
            indexes = key.indexes,
            navigateToSettings =
                resumedAction { indexes -> navigator.navigate(SettingsRoute(indexes = indexes)) },
            navigateToSettingsFileEdit =
                resumedAction { filePath ->
                    navigator.navigate(SettingsFileEditRoute(filePath = filePath))
                },
            navigateToServers = dropUnlessResumed { navigator.navigate(ServersRoute) },
            navigateToUsers = dropUnlessResumed { navigator.navigate(UsersRoute) },
            navigateToAbout = dropUnlessResumed { navigator.navigate(AboutRoute) },
            navigateToDevice = dropUnlessResumed { navigator.navigate(DeviceRoute) },
            navigateToDownloadPresets =
                dropUnlessResumed { navigator.navigate(DownloadPresetsRoute) },
            navigateBack = dropUnlessResumed { navigator.goBack() },
        )
    }
    entry<SettingsFileEditRoute> { key ->
        SettingsFileEditScreen(
            filePath = key.filePath,
            navigateBack = dropUnlessResumed { navigator.goBack() },
        )
    }
    entry<AboutRoute> { AboutScreen(navigateBack = dropUnlessResumed { navigator.goBack() }) }
    entry<DeviceRoute> { DeviceScreen(navigateBack = dropUnlessResumed { navigator.goBack() }) }
    entry<SmartDownloadsRoute> {
        SmartDownloadsScreen(
            navigateBack = dropUnlessResumed { navigator.goBack() },
            onNavigateToPresets = dropUnlessResumed { navigator.navigate(DownloadPresetsRoute) },
        )
    }
    entry<DownloadPresetsRoute> {
        DownloadPresetsScreen(navigateBack = dropUnlessResumed { navigator.goBack() })
    }
}

private fun navigateToItem(navigator: Navigator, item: FindroidItem) {
    when (item) {
        is FindroidBoxSet ->
            navigator.navigate(
                CollectionRoute(collectionId = item.id.toString(), collectionName = item.name)
            )

        is FindroidMovie -> navigator.navigate(MovieRoute(movieId = item.id.toString()))
        is FindroidShow -> navigator.navigate(ShowRoute(showId = item.id.toString()))
        is FindroidSeason -> navigator.navigate(SeasonRoute(seasonId = item.id.toString()))
        is FindroidEpisode -> navigator.navigate(EpisodeRoute(episodeId = item.id.toString()))
        is FindroidCollection ->
            navigator.navigate(
                LibraryRoute(
                    libraryId = item.id.toString(),
                    libraryName = item.name,
                    libraryType = item.type,
                )
            )

        is FindroidFolder ->
            navigator.navigate(
                LibraryRoute(
                    libraryId = item.id.toString(),
                    libraryName = item.name,
                    libraryType = CollectionType.Folders,
                )
            )

        else -> Unit
    }
}
