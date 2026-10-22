package dev.jdtech.jellyfin.presentation.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyServer
import dev.jdtech.jellyfin.core.presentation.dummy.dummyUser
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.presentation.components.ClockText
import dev.jdtech.jellyfin.presentation.components.LoadingIndicator
import dev.jdtech.jellyfin.presentation.components.ProfileButton
import dev.jdtech.jellyfin.presentation.film.HomeScreen
import dev.jdtech.jellyfin.presentation.film.MediaScreen
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.viewmodels.MainViewModel
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * Main TV screen component displaying navigation header tabs and section content.
 *
 * @param navigateToSettings Callback to navigate to settings screen.
 * @param navigateToLibrary Callback to navigate to library view screen.
 * @param navigateToMovie Callback to navigate to movie details screen.
 * @param navigateToShow Callback to navigate to show details screen.
 * @param navigateToPlayer Callback to navigate to media player.
 * @param mainViewModel MainViewModel instance managing main UI state.
 */
@Composable
fun MainScreen(
    navigateToSettings: () -> Unit,
    navigateToLibrary: (libraryId: UUID, libraryName: String, libraryType: CollectionType) -> Unit,
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToPlayer: (itemId: UUID, itemKind: BaseItemKind) -> Unit,
    mainViewModel: MainViewModel? = if (LocalInspectionMode.current) null else hiltViewModel(),
) {
    if (LocalInspectionMode.current || mainViewModel == null) {
        MainScreenLayout(
            uiState = MainViewModel.UiState.Normal(server = dummyServer, user = dummyUser),
            navigateToSettings = navigateToSettings,
            navigateToLibrary = navigateToLibrary,
            navigateToMovie = navigateToMovie,
            navigateToShow = navigateToShow,
            navigateToPlayer = navigateToPlayer,
        )
        return
    }

    val delegatedUiState by mainViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { mainViewModel.loadServerAndUser() }

    MainScreenLayout(
        uiState = delegatedUiState,
        navigateToSettings = navigateToSettings,
        navigateToLibrary = navigateToLibrary,
        navigateToMovie = navigateToMovie,
        navigateToShow = navigateToShow,
        navigateToPlayer = navigateToPlayer,
    )
}

enum class TabDestination(@param:DrawableRes val icon: Int, @param:StringRes val label: Int) {
    Home(CoreR.drawable.ic_home, CoreR.string.title_home),
    Libraries(CoreR.drawable.ic_library, CoreR.string.libraries),
}

@Composable
private fun MainScreenLayout(
    uiState: MainViewModel.UiState,
    navigateToSettings: () -> Unit,
    navigateToLibrary: (libraryId: UUID, libraryName: String, libraryType: CollectionType) -> Unit,
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToPlayer: (itemId: UUID, itemKind: BaseItemKind) -> Unit,
) {
    var activeTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var isHomeScrolled by rememberSaveable { mutableStateOf(false) }
    var hasRequestedAppInitialFocus by rememberSaveable { mutableStateOf(false) }
    val isHeaderVisible = activeTabIndex != 0 || !isHomeScrolled

    val homeTabFocusRequester = remember { FocusRequester() }
    val librariesTabFocusRequester = remember { FocusRequester() }
    val homeContentFocusRequester = remember { FocusRequester() }
    val librariesContentFocusRequester = remember { FocusRequester() }

    var isLoading by remember { mutableStateOf(false) }

    var user: User? = null
    when (uiState) {
        is MainViewModel.UiState.Normal -> {
            user = uiState.user
        }
        else -> Unit
    }

    val headerAlpha by
        animateFloatAsState(
            targetValue = if (isHeaderVisible) 1f else 0f,
            animationSpec = tween(300),
            label = "headerAlpha",
        )
    val headerOffset by
        animateDpAsState(
            targetValue = if (isHeaderVisible) 0.dp else (-64).dp,
            animationSpec = tween(300),
            label = "headerOffset",
        )
    val contentPaddingTop by
        animateDpAsState(
            targetValue = if (isHeaderVisible) 64.dp else 0.dp,
            animationSpec = tween(300),
            label = "contentPaddingTop",
        )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(top = contentPaddingTop)) {
            when (activeTabIndex) {
                1 -> {
                    MediaScreen(
                        navigateToLibrary = navigateToLibrary,
                        isLoading = { isLoading = it },
                        contentFocusRequester = librariesContentFocusRequester,
                        upFocusRequester = librariesTabFocusRequester,
                    )
                }
                else -> {
                    HomeScreen(
                        navigateToMovie = navigateToMovie,
                        navigateToShow = navigateToShow,
                        navigateToPlayer = navigateToPlayer,
                        navigateToLibrary = navigateToLibrary,
                        isLoading = { isLoading = it },
                        onScrollOffsetChanged = { isHomeScrolled = it },
                        contentFocusRequester = homeContentFocusRequester,
                        upFocusRequester = homeTabFocusRequester,
                        autoFocusContent = !hasRequestedAppInitialFocus,
                        onInitialFocusRequested = { hasRequestedAppInitialFocus = true },
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .zIndex(10f)
                    .graphicsLayer {
                        translationY = headerOffset.toPx()
                        alpha = headerAlpha
                    }
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent,
                                )
                        )
                    )
                    .padding(horizontal = MaterialTheme.spacings.large)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                ProfileButton(
                    user = user,
                    onClick = { navigateToSettings() },
                    modifier =
                        Modifier.focusProperties {
                            down =
                                if (activeTabIndex == 0) homeContentFocusRequester
                                else librariesContentFocusRequester
                        },
                )
                IconButton(
                    onClick = { /* Search */ },
                    scale = IconButtonDefaults.scale(focusedScale = 1.15f),
                    colors =
                        IconButtonDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White,
                            contentColor = Color.White,
                            focusedContentColor = Color.Black,
                        ),
                    modifier =
                        Modifier.size(40.dp).focusProperties {
                            down =
                                if (activeTabIndex == 0) homeContentFocusRequester
                                else librariesContentFocusRequester
                        },
                ) {
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_search),
                        contentDescription = stringResource(id = CoreR.string.search),
                        modifier = Modifier.size(20.dp),
                    )
                }
                TabDestination.entries.forEachIndexed { index, tab ->
                    val isSelected = activeTabIndex == index
                    Surface(
                        onClick = {
                            if (activeTabIndex == index) {
                                try {
                                    if (index == 0) homeContentFocusRequester.requestFocus()
                                    else librariesContentFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                            } else {
                                activeTabIndex = index
                            }
                        },
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor =
                                    if (isSelected) Color.White.copy(alpha = 0.18f)
                                    else Color.Transparent,
                                contentColor =
                                    if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                focusedContainerColor = Color.White,
                                focusedContentColor = Color.Black,
                            ),
                        modifier =
                            Modifier.focusRequester(
                                    if (index == 0) homeTabFocusRequester
                                    else librariesTabFocusRequester
                                )
                                .focusProperties {
                                    down =
                                        if (activeTabIndex == 0) homeContentFocusRequester
                                        else librariesContentFocusRequester
                                }
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        activeTabIndex = index
                                    }
                                },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = tab.icon),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(MaterialTheme.spacings.extraSmall))
                            Text(
                                text = stringResource(id = tab.label),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                if (isLoading) {
                    LoadingIndicator()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    ClockText(
                        style =
                            MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        color = Color.White,
                    )
                    Icon(
                        painter = painterResource(id = CoreR.drawable.ic_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun MainScreenLayoutPreview() {
    FindroidTheme {
        MainScreenLayout(
            uiState = MainViewModel.UiState.Normal(server = dummyServer, user = dummyUser),
            navigateToSettings = {},
            navigateToLibrary = { _, _, _ -> },
            navigateToMovie = {},
            navigateToShow = {},
            navigateToPlayer = { _, _ -> },
        )
    }
}
