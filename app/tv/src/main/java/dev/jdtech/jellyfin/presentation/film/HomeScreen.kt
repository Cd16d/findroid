package dev.jdtech.jellyfin.presentation.film

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSuggestions
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeView
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.film.presentation.home.HomeViewModel
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.View
import dev.jdtech.jellyfin.presentation.components.Direction
import dev.jdtech.jellyfin.presentation.film.components.HomeCarousel
import dev.jdtech.jellyfin.presentation.film.components.HomeSection
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.NoOpBringIntoViewSpec
import dev.jdtech.jellyfin.presentation.utils.TvHorizontalKeyline
import dev.jdtech.jellyfin.utils.toBlurHashPainter
import dev.jdtech.jellyfin.utils.toOptimizedImageUri
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType

@Composable
fun HomeScreen(
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToPlayer: (itemId: UUID, itemKind: BaseItemKind) -> Unit,
    navigateToLibrary:
        ((libraryId: UUID, libraryName: String, libraryType: CollectionType) -> Unit)? =
        null,
    isLoading: ((Boolean) -> Unit)? = null,
    onScrollOffsetChanged: ((Boolean) -> Unit)? = null,
    contentFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    autoFocusContent: Boolean = false,
    onInitialFocusRequested: () -> Unit = {},
    viewModel: HomeViewModel? = if (LocalInspectionMode.current) null else hiltViewModel(),
) {
    if (LocalInspectionMode.current || viewModel == null) {
        HomeScreenLayout(
            state =
                HomeState(
                    suggestionsSection = dummyHomeSuggestions,
                    resumeSection = dummyHomeSection,
                    views = listOf(dummyHomeView),
                ),
            navigateToMovie = navigateToMovie,
            navigateToShow = navigateToShow,
            navigateToLibrary = navigateToLibrary,
            onScrollOffsetChanged = onScrollOffsetChanged,
            contentFocusRequester = contentFocusRequester,
            upFocusRequester = upFocusRequester,
            autoFocusContent = autoFocusContent,
            onInitialFocusRequested = onInitialFocusRequested,
            onLoadShowDetails = null,
            onAction = {},
        )
        return
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadData() }
    LaunchedEffect(state.isLoading) { isLoading?.invoke(state.isLoading) }

    HomeScreenLayout(
        state = state,
        navigateToMovie = navigateToMovie,
        navigateToShow = navigateToShow,
        navigateToLibrary = navigateToLibrary,
        onScrollOffsetChanged = onScrollOffsetChanged,
        contentFocusRequester = contentFocusRequester,
        upFocusRequester = upFocusRequester,
        autoFocusContent = autoFocusContent,
        onInitialFocusRequested = onInitialFocusRequested,
        onLoadShowDetails = { showId ->
            try {
                viewModel.getShow(showId)
            } catch (_: Exception) {
                null
            }
        },
        onAction = { action ->
            if (action is HomeAction.OnItemClick) {
                when (val item = action.item) {
                    is FindroidMovie -> navigateToMovie(item.id)
                    is FindroidShow -> navigateToShow(item.id)
                    is FindroidSeason -> navigateToShow(item.seriesId)
                    is FindroidEpisode -> navigateToPlayer(item.id, BaseItemKind.EPISODE)
                    else -> Unit
                }
            }
            viewModel.onAction(action)
        },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeScreenLayout(
    state: HomeState,
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToLibrary:
        ((libraryId: UUID, libraryName: String, libraryType: CollectionType) -> Unit)? =
        null,
    onScrollOffsetChanged: ((Boolean) -> Unit)? = null,
    contentFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    autoFocusContent: Boolean = false,
    onInitialFocusRequested: () -> Unit = {},
    onLoadShowDetails: (suspend (UUID) -> FindroidShow?)? = null,
    onAction: (HomeAction) -> Unit,
) {
    val itemsPadding = PaddingValues(horizontal = TvHorizontalKeyline)
    val screenHeight = LocalWindowInfo.current.containerDpSize.height

    val carouselItems =
        remember(state) {
            state.suggestionsSection?.items?.ifEmpty { null }
                ?: state.resumeSection?.homeSection?.items?.take(6)?.ifEmpty { null }
                ?: state.nextUpSection?.homeSection?.items?.take(6)?.ifEmpty { null }
                ?: state.views.firstOrNull()?.view?.items?.take(6)
                ?: emptyList()
        }

    var focusedRowIndex by remember { mutableIntStateOf(-1) }
    var focusedItem by remember { mutableStateOf<FindroidItem?>(null) }
    var focusedView by remember { mutableStateOf<View?>(null) }

    var heroItem by remember { mutableStateOf<FindroidItem?>(null) }
    var heroView by remember { mutableStateOf<View?>(null) }
    var showDetails by remember { mutableStateOf<FindroidShow?>(null) }

    val isCarouselActive = carouselItems.isNotEmpty() && focusedRowIndex < 0

    LaunchedEffect(focusedItem, focusedView) {
        if (focusedItem == null && focusedView == null) {
            heroItem = null
            heroView = null
            showDetails = null
            return@LaunchedEffect
        }
        if (heroItem != null || heroView != null) {
            delay(90.milliseconds)
        }
        heroItem = focusedItem
        heroView = focusedView
        val item = focusedItem
        showDetails =
            if (item is FindroidSeason && onLoadShowDetails != null) {
                onLoadShowDetails(item.seriesId)
            } else {
                null
            }
    }

    val carouselFocusRequester = remember { FocusRequester() }
    val firstSectionFocusRequester = remember { FocusRequester() }

    val effectiveCarouselPlayRequester =
        if (carouselItems.isNotEmpty()) (contentFocusRequester ?: carouselFocusRequester) else null
    val effectiveFirstSectionRequester =
        if (carouselItems.isNotEmpty()) firstSectionFocusRequester
        else (contentFocusRequester ?: firstSectionFocusRequester)

    LaunchedEffect(autoFocusContent, carouselItems.isNotEmpty(), state.isLoading) {
        if (autoFocusContent) {
            if (carouselItems.isNotEmpty()) {
                for (attempt in 0..15) {
                    try {
                        effectiveCarouselPlayRequester?.requestFocus()
                        onInitialFocusRequested()
                        break
                    } catch (_: Exception) {
                        delay(60.milliseconds)
                    }
                }
            } else if (
                !state.isLoading &&
                    (state.views.isNotEmpty() ||
                        state.resumeSection != null ||
                        state.nextUpSection != null ||
                        state.error != null)
            ) {
                for (attempt in 0..15) {
                    try {
                        effectiveFirstSectionRequester.requestFocus()
                        onInitialFocusRequested()
                        break
                    } catch (_: Exception) {
                        delay(60.milliseconds)
                    }
                }
            }
        }
    }

    val topHeaderHeight by
        animateDpAsState(
            targetValue =
                if (isCarouselActive) (screenHeight - 200.dp).coerceAtLeast(380.dp)
                else (screenHeight * 0.44f).coerceIn(285.dp, 320.dp),
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            label = "topHeaderHeight",
        )
    val carouselAlpha by
        animateFloatAsState(
            targetValue = if (isCarouselActive) 1f else 0f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "carouselAlpha",
        )
    val heroAlpha by
        animateFloatAsState(
            targetValue =
                if (!isCarouselActive && (heroItem != null || heroView != null)) 1f else 0f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "heroAlpha",
        )
    val lazyListState = rememberLazyListState()

    LaunchedEffect(isCarouselActive, focusedRowIndex) {
        val isScrolled = if (carouselItems.isNotEmpty()) !isCarouselActive else focusedRowIndex >= 0
        onScrollOffsetChanged?.invoke(isScrolled)
        if (isCarouselActive) {
            lazyListState.scrollToItem(0)
        } else if (focusedRowIndex >= 0) {
            val visibleRow =
                lazyListState.layoutInfo.visibleItemsInfo.firstOrNull {
                    it.index == focusedRowIndex
                }
            if (visibleRow != null) {
                val delta = visibleRow.offset.toFloat()
                if (abs(delta) > 1f) {
                    lazyListState.animateScrollBy(
                        value = delta,
                        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                    )
                }
            } else {
                lazyListState.animateScrollToItem(index = focusedRowIndex, scrollOffset = 0)
            }
        }
    }

    val isResumeFirst = state.resumeSection != null
    val isNextUpFirst = !isResumeFirst && state.nextUpSection != null
    val isViewsFirst = !isResumeFirst && !isNextUpFirst

    Box(modifier = Modifier.fillMaxSize()) {
        if (heroAlpha > 0.001f) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopStart).fillMaxSize().alpha(heroAlpha).zIndex(0f)
            ) {
                HeroPreviewHeader(
                    item = heroItem,
                    view = heroView,
                    showDetails = showDetails,
                    showLogo = state.showLogos,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (carouselItems.isNotEmpty()) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(topHeaderHeight)
                        .padding(
                            top = MaterialTheme.spacings.small,
                            bottom = MaterialTheme.spacings.medium,
                        )
                        .alpha(carouselAlpha)
                        .zIndex(if (isCarouselActive) 2f else 0f)
            ) {
                HomeCarousel(
                    items = carouselItems,
                    onDetails = { item ->
                        when (item) {
                            is FindroidMovie -> navigateToMovie(item.id)
                            is FindroidEpisode -> navigateToShow(item.seriesId)
                            is FindroidShow -> navigateToShow(item.id)
                            is FindroidSeason -> navigateToShow(item.seriesId)
                            else -> Unit
                        }
                    },
                    isActive = isCarouselActive,
                    showLogo = state.showLogos,
                    playFocusRequester = effectiveCarouselPlayRequester,
                    upFocusRequester = upFocusRequester,
                    downFocusRequester = effectiveFirstSectionRequester,
                    onFocusChanged = { isFocused ->
                        if (isFocused) {
                            focusedRowIndex = -1
                            focusedItem = null
                            focusedView = null
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        CompositionLocalProvider(LocalBringIntoViewSpec provides NoOpBringIntoViewSpec) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().padding(top = topHeaderHeight).zIndex(1f),
                contentPadding =
                    PaddingValues(
                        top = 0.dp,
                        bottom = maxOf(300.dp, screenHeight - 370.dp),
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.resumeSection?.let { section ->
                    item(key = section.id) {
                        FadingRowContainer(
                            rowIndex = 0,
                            focusedRowIndex = focusedRowIndex,
                        ) {
                            HomeSection(
                                title = section.homeSection.name.asString(),
                                items = section.homeSection.items,
                                rowKey = section.id,
                                itemsPadding = itemsPadding,
                                firstItemFocusRequester =
                                    if (isResumeFirst) effectiveFirstSectionRequester else null,
                                upFocusRequester =
                                    if (isResumeFirst)
                                        (effectiveCarouselPlayRequester ?: upFocusRequester)
                                    else null,
                                onItemFocused = {
                                    focusedRowIndex = 0
                                    focusedItem = it
                                    focusedView = null
                                },
                                onAction = onAction,
                                hideEpisodeSpoilers = state.hideEpisodeSpoilers,
                            )
                        }
                    }
                }
                state.nextUpSection?.let { section ->
                    item(key = section.id) {
                        val rowIndex = if (state.resumeSection != null) 1 else 0
                        FadingRowContainer(
                            rowIndex = rowIndex,
                            focusedRowIndex = focusedRowIndex,
                        ) {
                            HomeSection(
                                title = section.homeSection.name.asString(),
                                items = section.homeSection.items,
                                rowKey = section.id,
                                itemsPadding = itemsPadding,
                                firstItemFocusRequester =
                                    if (isNextUpFirst) effectiveFirstSectionRequester else null,
                                upFocusRequester =
                                    if (isNextUpFirst)
                                        (effectiveCarouselPlayRequester ?: upFocusRequester)
                                    else null,
                                onItemFocused = {
                                    focusedRowIndex = rowIndex
                                    focusedItem = it
                                    focusedView = null
                                },
                                onAction = onAction,
                                hideEpisodeSpoilers = state.hideEpisodeSpoilers,
                            )
                        }
                    }
                }
                itemsIndexed(
                    state.views.distinctBy { it.id },
                    key = { _, it -> it.id },
                ) { viewIndex, view ->
                    val baseIndex =
                        (if (state.resumeSection != null) 1 else 0) +
                            (if (state.nextUpSection != null) 1 else 0)
                    val rowIndex = baseIndex + viewIndex
                    val isThisFirstView = isViewsFirst && viewIndex == 0
                    val viewAllLabel =
                        when (view.view.type) {
                            CollectionType.Movies -> stringResource(FilmR.string.go_to_movies)
                            CollectionType.TvShows -> stringResource(FilmR.string.go_to_shows)
                            else -> stringResource(FilmR.string.go_to_library, view.view.name)
                        }
                    FadingRowContainer(
                        rowIndex = rowIndex,
                        focusedRowIndex = focusedRowIndex,
                    ) {
                        HomeSection(
                            title = stringResource(FilmR.string.latest_library, view.view.name),
                            items = view.view.items,
                            direction = Direction.VERTICAL,
                            itemWidth = 96.dp,
                            rowKey = view.id,
                            itemsPadding = itemsPadding,
                            firstItemFocusRequester =
                                if (isThisFirstView) effectiveFirstSectionRequester else null,
                            upFocusRequester =
                                if (isThisFirstView)
                                    (effectiveCarouselPlayRequester ?: upFocusRequester)
                                else null,
                            viewAllLabel = viewAllLabel,
                            onViewAllClick =
                                navigateToLibrary?.let { nav ->
                                    { nav(view.view.id, view.view.name, view.view.type) }
                                },
                            onViewAllFocus = {
                                focusedRowIndex = rowIndex
                                focusedItem = null
                                focusedView = view.view
                            },
                            onItemFocused = {
                                focusedRowIndex = rowIndex
                                focusedItem = it
                                focusedView = null
                            },
                            onAction = onAction,
                            hideEpisodeSpoilers = state.hideEpisodeSpoilers,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroPreviewHeader(
    item: FindroidItem?,
    modifier: Modifier = Modifier,
    view: View? = null,
    showDetails: FindroidShow? = null,
    showLogo: Boolean = false,
) {
    if (item == null && view == null) return

    val title =
        when {
            view != null -> view.name
            item is FindroidEpisode -> item.seriesName
            item is FindroidSeason -> item.seriesName
            item != null -> item.name
            else -> ""
        }

    val categoryLabel =
        stringResource(id = if (view != null) CoreR.string.libraries else CoreR.string.title_home)

    val episodeSubtitle =
        if (item is FindroidEpisode && item.parentIndexNumber > 0 && item.indexNumber > 0) {
            val indexEnd = item.indexNumberEnd
            val epNumberStr =
                if (indexEnd != null && indexEnd > item.indexNumber) {
                    "${item.indexNumber}-$indexEnd"
                } else {
                    "${item.indexNumber}"
                }
            if (item.name.isNotBlank() && item.name != item.seriesName) {
                "S${item.parentIndexNumber}:E$epNumberStr • ${item.name}"
            } else {
                "S${item.parentIndexNumber}:E$epNumberStr"
            }
        } else {
            null
        }

    val rating =
        when (item) {
            is FindroidMovie -> item.communityRating
            is FindroidShow -> item.communityRating
            is FindroidEpisode -> item.communityRating
            is FindroidSeason -> showDetails?.communityRating
            else -> null
        }

    val year =
        when (item) {
            is FindroidMovie -> item.productionYear
            is FindroidShow -> item.productionYear
            is FindroidSeason -> showDetails?.productionYear
            else -> null
        }

    val runtimeText =
        item
            ?.runtimeTicks
            ?.takeIf { it > 0 }
            ?.let { ticks ->
                val minutes = (ticks / 600_000_000L).toInt()
                when {
                    minutes >= 60 -> {
                        val h = minutes / 60
                        val m = minutes % 60
                        if (m > 0) "$h h $m min" else "$h h"
                    }
                    minutes > 0 -> "$minutes min"
                    else -> null
                }
            }

    val remainingMinutes = item?.let {
        if (it.playbackPositionTicks > 0L && it.runtimeTicks > it.playbackPositionTicks) {
            ((it.runtimeTicks - it.playbackPositionTicks) / 600_000_000L).toInt()
        } else null
    }

    val genres =
        when (item) {
            is FindroidMovie -> item.genres
            is FindroidShow -> item.genres
            is FindroidSeason -> showDetails?.genres ?: emptyList()
            else -> emptyList()
        }

    val hasClosedCaptions =
        remember(item) {
            item?.sources?.any { source ->
                source.mediaStreams.any { stream ->
                    stream.type == MediaStreamType.SUBTITLE &&
                        (stream.isHearingImpaired == true ||
                            stream.codec.contains("608", ignoreCase = true) ||
                            stream.codec.contains("708", ignoreCase = true) ||
                            stream.title.contains("cc", ignoreCase = true) ||
                            stream.displayTitle?.contains("cc", ignoreCase = true) == true ||
                            stream.title.contains("sdh", ignoreCase = true) ||
                            stream.displayTitle?.contains("sdh", ignoreCase = true) == true)
                }
            } == true
        }

    val overview =
        when {
            view != null ->
                when (view.type) {
                    CollectionType.Movies -> stringResource(FilmR.string.go_to_movies)
                    CollectionType.TvShows -> stringResource(FilmR.string.go_to_shows)
                    else -> stringResource(FilmR.string.go_to_library, view.name)
                }
            item is FindroidSeason -> showDetails?.overview?.ifBlank { null } ?: item.overview
            item != null -> item.overview
            else -> ""
        }

    val backdropImage =
        when {
            view != null ->
                view.items.firstOrNull()?.images?.let {
                    it.backdrop ?: it.showBackdrop ?: it.primary
                }
            item is FindroidEpisode ->
                item.images.showBackdrop ?: item.images.backdrop ?: item.images.primary
            item is FindroidSeason ->
                showDetails?.images?.backdrop
                    ?: item.images.showBackdrop
                    ?: item.images.backdrop
                    ?: item.images.primary
            item != null -> item.images.backdrop ?: item.images.primary
            else -> null
        }

    val logo = if (showLogo) item?.images?.let { it.logo ?: it.showLogo } else null
    val logoUri =
        logo
            ?.uri
            .toOptimizedImageUri(
                widthDp = 400.dp,
                heightDp = 120.dp,
                imageType = ImageType.LOGO,
            )
    var isLogoFailed by remember(logoUri) { mutableStateOf(false) }

    val windowSize = LocalWindowInfo.current.containerDpSize
    val imageWidthDp = windowSize.width * 0.68f
    val imageHeightDp = imageWidthDp / (16f / 9f)
    val imageUri =
        backdropImage
            ?.uri
            .toOptimizedImageUri(
                widthDp = imageWidthDp,
                heightDp = imageHeightDp,
            )
    val blurPlaceholder =
        remember(backdropImage?.blurHash) { backdropImage?.blurHash.toBlurHashPainter() }

    Box(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth(0.68f).aspectRatio(16f / 9f).align(Alignment.TopEnd)) {
            AnimatedContent(
                targetState = imageUri,
                transitionSpec = {
                    fadeIn(tween(220, easing = LinearOutSlowInEasing))
                        .togetherWith(fadeOut(tween(180, easing = FastOutLinearInEasing)))
                },
                label = "heroArtwork",
                modifier = Modifier.fillMaxSize(),
            ) { targetUri ->
                if (targetUri != null) {
                    AsyncImage(
                        model = targetUri,
                        placeholder = blurPlaceholder ?: ColorPainter(Color(0xFF141414)),
                        error = blurPlaceholder,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopEnd,
                        modifier =
                            Modifier.fillMaxSize()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush =
                                            Brush.horizontalGradient(
                                                0.0f to Color.Transparent,
                                                0.25f to Color.Black,
                                                1.0f to Color.Black,
                                                startX = 0f,
                                                endX = size.width,
                                            ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                    drawRect(
                                        brush =
                                            Brush.verticalGradient(
                                                0.0f to Color.Black,
                                                0.75f to Color.Black,
                                                1.0f to Color.Transparent,
                                                startY = 0f,
                                                endY = size.height,
                                            ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier.align(Alignment.TopStart)
                    .padding(start = TvHorizontalKeyline, top = 20.dp)
                    .fillMaxWidth(0.50f)
        ) {
            Text(
                text = categoryLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (logoUri != null && !isLogoFailed) {
                    AsyncImage(
                        model = logoUri,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        onError = { isLogoFailed = true },
                        modifier =
                            Modifier.sizeIn(
                                    maxWidth = 260.dp,
                                    maxHeight = 90.dp,
                                )
                                .padding(bottom = 4.dp),
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (rating != null && rating > 0f) {
                        Text(
                            text = "★ %.1f".format(rating),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFD700),
                        )
                    }
                    if (runtimeText != null) {
                        Text(
                            text = runtimeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                    if (year != null && year > 0) {
                        Text(
                            text = year.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                    if (hasClosedCaptions) {
                        Text(
                            text = "CC",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier =
                                Modifier.border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.4f),
                                        RoundedCornerShape(3.dp),
                                    )
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                    if (genres.isNotEmpty()) {
                        Text(
                            text = genres.take(2).joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (episodeSubtitle != null) {
                    Text(
                        text = episodeSubtitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (overview.isNotBlank()) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray.copy(alpha = 0.85f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (remainingMinutes != null && remainingMinutes > 0) {
                    Text(
                        text =
                            stringResource(
                                CoreR.string.runtime_minutes_left,
                                remainingMinutes,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun HomeScreenLayoutPreview() {
    FindroidTheme {
        HomeScreenLayout(
            state =
                HomeState(
                    suggestionsSection = dummyHomeSuggestions,
                    resumeSection = dummyHomeSection,
                    views = listOf(dummyHomeView),
                ),
            navigateToMovie = {},
            navigateToShow = {},
            onAction = {},
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun HeroPreviewHeaderPreview() {
    FindroidTheme { HeroPreviewHeader(item = dummyMovie) }
}

@Composable
private fun FadingRowContainer(
    rowIndex: Int,
    focusedRowIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isAboveFocused = focusedRowIndex >= 0 && rowIndex < focusedRowIndex
    val alpha by
        animateFloatAsState(
            targetValue = if (isAboveFocused) 0f else 1f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "rowAlpha_$rowIndex",
        )
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }) { content() }
}
