package dev.jdtech.jellyfin.presentation.account

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.film.presentation.account.AccountAction
import dev.jdtech.jellyfin.film.presentation.account.AccountEvent
import dev.jdtech.jellyfin.film.presentation.account.AccountViewModel
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.presentation.account.components.AccountMenuChangeAccountSection
import dev.jdtech.jellyfin.presentation.account.components.AccountMenuFooter
import dev.jdtech.jellyfin.presentation.account.components.AccountMenuItemsList
import dev.jdtech.jellyfin.presentation.account.components.AccountMenuOfflineBadge
import dev.jdtech.jellyfin.presentation.account.components.AccountMenuProfileSection
import dev.jdtech.jellyfin.presentation.account.components.QuickConnectBottomSheet
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import dev.jdtech.jellyfin.core.R as CoreR

@Composable
fun AccountMenuWrapper(
    isMenuOpen: Boolean,
    onCloseMenu: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onAddUser: () -> Unit,
    onManageAccounts: () -> Unit,
    onSwitchUser: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showQuickConnect by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val configuration = LocalConfiguration.current

    LaunchedEffect(viewModel.events) {
        viewModel.events.collect { event ->
            when (event) {
                AccountEvent.UserSwitched -> onSwitchUser()
            }
        }
    }

    LaunchedEffect(isMenuOpen) {
        if (isMenuOpen) {
            viewModel.loadData()
        }
    }

    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val windowSizeClass = windowAdaptiveInfo.windowSizeClass
    val isTablet =
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
                windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (showQuickConnect) {
        QuickConnectBottomSheet(
            onDismissRequest = {
                showQuickConnect = false
                viewModel.onAction(AccountAction.ClearQuickConnectStatus)
            },
            onSubmit = { code ->
                viewModel.onAction(AccountAction.OnQuickConnectSubmit(code))
            },
            onClearError = {
                viewModel.onAction(AccountAction.ClearQuickConnectStatus)
            },
            isLoading = state.isQuickConnectLoading,
            error = if (state.quickConnectSuccess == false) stringResource(CoreR.string.invalid_code) else null,
            isSuccess = state.quickConnectSuccess == true
        )
    }

    LaunchedEffect(state.quickConnectSuccess) {
        if (state.quickConnectSuccess == true) {
            delay(1000.milliseconds)
            showQuickConnect = false
            viewModel.onAction(AccountAction.ClearQuickConnectStatus)
        }
    }

    BackHandler(enabled = isMenuOpen) {
        onCloseMenu()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content of the screen
        content()

        val enterTransition = if (isTablet) fadeIn() else slideInHorizontally { it }
        val exitTransition = if (isTablet) fadeOut() else slideOutHorizontally { it }

        // The Menu Overlay
        AnimatedVisibility(
            visible = isMenuOpen,
            enter = enterTransition,
            exit = exitTransition,
            modifier = Modifier.zIndex(10f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)) // Dim background
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCloseMenu
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Surface(
                    modifier = Modifier
                        .then(
                            if (isTablet) Modifier
                                .width(420.dp)
                                .wrapContentHeight()
                                .padding(top = 64.dp, bottom = 16.dp)
                            else Modifier.fillMaxSize()
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {} // Catch clicks to prevent closing when interacting with menu
                        ),
                    shape = if (isTablet) MaterialTheme.shapes.extraLarge else RectangleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp
                ) {
                    AccountMenuContent(
                        userName = state.user?.name ?: stringResource(CoreR.string.user_default),
                        userImageUrl = state.userImageUrl,
                        otherUsers = state.otherUsers,
                        isAccountListExpanded = state.isAccountListExpanded,
                        baseUrl = state.baseUrl,
                        isTablet = isTablet,
                        isLandscape = isLandscape,
                        onClose = onCloseMenu,
                        onOpenQuickConnect = { showQuickConnect = true },
                        onNavigateToSettings = {
                            onNavigateToSettings()
                        },
                        onNavigateToAbout = {
                            onNavigateToAbout()
                        },
                        onAddUser = onAddUser,
                        onManageAccounts = onManageAccounts,
                        onSwitchUser = { user -> viewModel.onAction(AccountAction.SwitchUser(user.id)) },
                        onToggleAccountList = { viewModel.onAction(AccountAction.ToggleAccountList) },
                        onNavigateToGithub = {
                            try {
                                uriHandler.openUri(
                                    "https://github.com/jarnedemeulemeester/findroid"
                                )
                            } catch (e: IllegalArgumentException) {
                                Toast.makeText(
                                    context,
                                    e.localizedMessage,
                                    Toast.LENGTH_SHORT,
                                )
                                    .show()
                            }
                        },
                        onNavigateToKofi = {
                            try {
                                uriHandler.openUri(
                                    "https://ko-fi.com/jarnedemeulemeester"
                                )
                            } catch (e: IllegalArgumentException) {
                                Toast.makeText(
                                    context,
                                    e.localizedMessage,
                                    Toast.LENGTH_SHORT,
                                )
                                    .show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AccountMenuContent(
    userName: String,
    userImageUrl: Any?,
    otherUsers: List<User>,
    isAccountListExpanded: Boolean,
    baseUrl: String,
    isTablet: Boolean,
    isLandscape: Boolean,
    onClose: () -> Unit,
    onOpenQuickConnect: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onAddUser: () -> Unit,
    onManageAccounts: () -> Unit,
    onSwitchUser: (User) -> Unit,
    onToggleAccountList: () -> Unit,
    onNavigateToGithub: () -> Unit,
    onNavigateToKofi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topPadding = if (!isTablet) WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() else 0.dp
    val startPadding = WindowInsets.safeDrawing.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current).coerceAtLeast(8.dp)
    val endPadding = WindowInsets.safeDrawing.asPaddingValues().calculateEndPadding(LocalLayoutDirection.current).coerceAtLeast(8.dp)
    val useSplitLayout = !isTablet && isLandscape
    val scrollState = rememberScrollState()
    val showButtonBackground by remember { derivedStateOf { scrollState.value > 10 } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                top = topPadding,
                start = startPadding,
                end = endPadding
            )
    ) {
        if (useSplitLayout) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Left side: Profile + Footer
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(
                            horizontal = 8.dp,
                            vertical = 16.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    AccountMenuProfileSection(
                        userName = userName,
                        userImageUrl = userImageUrl,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    AccountMenuFooter()
                }

                // Right side: Scrollable list
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(48.dp)) // Space for close button

                    AccountMenuChangeAccountSection(
                        otherUsers = otherUsers,
                        isExpanded = isAccountListExpanded,
                        onToggleExpand = onToggleAccountList,
                        onUserClick = onSwitchUser,
                        onAddUserClick = onAddUser,
                        onManageAccountsClick = onManageAccounts,
                        baseUrl = baseUrl
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AccountMenuItemsList(
                        onOpenQuickConnect = onOpenQuickConnect,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToAbout = onNavigateToAbout,
                        onNavigateToGithub = onNavigateToGithub,
                        onNavigateToKofi = onNavigateToKofi
                    )
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(64.dp)) // Header space

                AccountMenuProfileSection(
                    userName = userName,
                    userImageUrl = userImageUrl
                )

                Spacer(modifier = Modifier.height(32.dp))

                AccountMenuChangeAccountSection(
                    otherUsers = otherUsers,
                    isExpanded = isAccountListExpanded,
                    onToggleExpand = onToggleAccountList,
                    onUserClick = onSwitchUser,
                    onAddUserClick = onAddUser,
                    onManageAccountsClick = onManageAccounts,
                    baseUrl = baseUrl
                )

                Spacer(modifier = Modifier.height(16.dp))

                AccountMenuItemsList(
                    onOpenQuickConnect = onOpenQuickConnect,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToAbout = onNavigateToAbout,
                    onNavigateToGithub = onNavigateToGithub,
                    onNavigateToKofi = onNavigateToKofi
                )

                Spacer(modifier = Modifier.height(24.dp))

                AccountMenuFooter()
                
                Spacer(modifier = Modifier.height(64.dp))
            }
        }

        // Fixed Footer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                )
        )

        // Fixed Header
        val shadowElevation by animateDpAsState(
            targetValue = if (showButtonBackground) 8.dp else 0.dp,
            animationSpec = tween(durationMillis = 300),
            label = "shadowElevation"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            Color.Transparent
                        )
                    )
                )
        ) {
            if (LocalOfflineMode.current) {
                AccountMenuOfflineBadge(
                    modifier = Modifier.align(Alignment.Center),
                    shadowElevation = shadowElevation
                )
            }
            Surface(
                shape = CircleShape,
                color = if (showButtonBackground) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = shadowElevation,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = ImageVector.vectorResource(CoreR.drawable.ic_x),
                        contentDescription = stringResource(CoreR.string.close_menu)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun AccountMenuContentExpandedPreview() {
    FindroidTheme(darkTheme = false) {
        CompositionLocalProvider(LocalOfflineMode provides false) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                AccountMenuContent(
                    userName = "Joe",
                    userImageUrl = null,
                    otherUsers = listOf(
                        User(
                            id = UUID.randomUUID(),
                            name = "Jane",
                            serverId = "server1"
                        ),
                        User(
                            id = UUID.randomUUID(),
                            name = "Bob",
                            serverId = "server1"
                        ),
                        User(
                            id = UUID.randomUUID(),
                            name = "Alice",
                            serverId = "server1"
                        )
                    ),
                    isAccountListExpanded = true,
                    baseUrl = "",
                    isTablet = false,
                    isLandscape = false,
                    onClose = {},
                    onOpenQuickConnect = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onAddUser = {},
                    onManageAccounts = {},
                    onSwitchUser = {},
                    onToggleAccountList = {},
                    onNavigateToGithub = {},
                    onNavigateToKofi = {},
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun AccountMenuContentPreview() {
    FindroidTheme(darkTheme = false) {
        CompositionLocalProvider(LocalOfflineMode provides false) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                AccountMenuContent(
                    userName = "Joe",
                    userImageUrl = null,
                    otherUsers = listOf(
                        User(
                            id = UUID.randomUUID(),
                            name = "Jane",
                            serverId = "server1"
                        ),
                        User(
                            id = UUID.randomUUID(),
                            name = "Bob",
                            serverId = "server1"
                        ),
                        User(
                            id = UUID.randomUUID(),
                            name = "Alice",
                            serverId = "server1"
                        )
                    ),
                    isAccountListExpanded = false,
                    baseUrl = "",
                    isTablet = false,
                    isLandscape = false,
                    onClose = {},
                    onOpenQuickConnect = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onAddUser = {},
                    onManageAccounts = {},
                    onSwitchUser = {},
                    onToggleAccountList = {},
                    onNavigateToGithub = {},
                    onNavigateToKofi = {},
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun AccountMenuContentDarkPreview() {
    FindroidTheme(darkTheme = true) {
        CompositionLocalProvider(LocalOfflineMode provides false) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                AccountMenuContent(
                    userName = "Joe",
                    userImageUrl = null,
                    otherUsers = listOf(
                        User(
                            id = UUID.randomUUID(),
                            name = "Jane",
                            serverId = "server1"
                        ),
                        User(
                            id = UUID.randomUUID(),
                            name = "Bob",
                            serverId = "server1"
                        ),
                        User(
                            id = UUID.randomUUID(),
                            name = "Alice",
                            serverId = "server1"
                        )
                    ),
                    isAccountListExpanded = false,
                    baseUrl = "",
                    isTablet = false,
                    isLandscape = false,
                    onClose = {},
                    onOpenQuickConnect = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onAddUser = {},
                    onManageAccounts = {},
                    onSwitchUser = {},
                    onToggleAccountList = {},
                    onNavigateToGithub = {},
                    onNavigateToKofi = {},
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun AccountMenuContentOfflinePreview() {
    FindroidTheme(darkTheme = false) {
        CompositionLocalProvider(LocalOfflineMode provides true) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                AccountMenuContent(
                    userName = "Joe",
                    userImageUrl = null,
                    otherUsers = listOf(
                        User(
                            id = UUID.randomUUID(),
                            name = "Jane",
                            serverId = "server1"
                        )
                    ),
                    isAccountListExpanded = false,
                    baseUrl = "",
                    isTablet = false,
                    isLandscape = false,
                    onClose = {},
                    onOpenQuickConnect = {},
                    onNavigateToSettings = {},
                    onNavigateToAbout = {},
                    onAddUser = {},
                    onManageAccounts = {},
                    onSwitchUser = {},
                    onToggleAccountList = {},
                    onNavigateToGithub = {},
                    onNavigateToKofi = {},
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
