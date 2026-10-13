package dev.jdtech.jellyfin.presentation.film

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
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
import dev.jdtech.jellyfin.presentation.film.components.HomeCarousel
import dev.jdtech.jellyfin.presentation.film.components.HomeSection
import dev.jdtech.jellyfin.presentation.film.components.HomeView
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.utils.toBlurHashPainter
import dev.jdtech.jellyfin.utils.toOptimizedImageUri
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
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
            navigateToPlayer = navigateToPlayer,
            navigateToLibrary = navigateToLibrary,
            onScrollOffsetChanged = onScrollOffsetChanged,
            contentFocusRequester = contentFocusRequester,
            upFocusRequester = upFocusRequester,
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
        navigateToPlayer = navigateToPlayer,
        navigateToLibrary = navigateToLibrary,
        onScrollOffsetChanged = onScrollOffsetChanged,
        contentFocusRequester = contentFocusRequester,
        upFocusRequester = upFocusRequester,
        onLoadShowDetails = { showId ->
            try {
                viewModel.repository.getShow(showId)
            } catch (_: Exception) {
                null
            }
        },
        onAction = { action ->
            when (action) {
                is HomeAction.OnItemClick -> {
                    when (val item = action.item) {
                        is FindroidMovie -> navigateToMovie(item.id)
                        is FindroidShow -> navigateToShow(item.id)
                        is FindroidSeason -> navigateToShow(item.seriesId)
                        is FindroidEpisode -> {
                            navigateToPlayer(item.id, BaseItemKind.EPISODE)
                        }
                        else -> Unit
                    }
                }
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private val NoOpBringIntoViewSpec =
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float = 0f
    }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HomeScreenLayout(
    state: HomeState,
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToPlayer: (itemId: UUID, itemKind: BaseItemKind) -> Unit,
    navigateToLibrary:
        ((libraryId: UUID, libraryName: String, libraryType: CollectionType) -> Unit)? =
        null,
    onScrollOffsetChanged: ((Boolean) -> Unit)? = null,
    contentFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onLoadShowDetails: (suspend (UUID) -> FindroidShow?)? = null,
    onAction: (HomeAction) -> Unit,
) {
    val itemsPadding = PaddingValues(horizontal = MaterialTheme.spacings.large)
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

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
    var showDetails by remember { mutableStateOf<FindroidShow?>(null) }

    val isCarouselActive = carouselItems.isNotEmpty() && focusedRowIndex < 0

    LaunchedEffect(focusedItem) {
        val item = focusedItem
        if (item is FindroidSeason && onLoadShowDetails != null) {
            showDetails = onLoadShowDetails(item.seriesId)
        } else {
            showDetails = null
        }
    }

    val carouselFocusRequester = remember { FocusRequester() }
    val firstSectionFocusRequester = remember { FocusRequester() }

    val effectiveCarouselPlayRequester =
        if (carouselItems.isNotEmpty()) (contentFocusRequester ?: carouselFocusRequester) else null
    val effectiveFirstSectionRequester =
        if (carouselItems.isNotEmpty()) firstSectionFocusRequester
        else (contentFocusRequester ?: firstSectionFocusRequester)

    val topHeaderHeight by
        animateDpAsState(
            targetValue = if (isCarouselActive) maxOf(360.dp, screenHeight - 56.dp) else 250.dp,
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
                if (!isCarouselActive && (focusedItem != null || focusedView != null)) 1f else 0f,
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
            lazyListState.animateScrollToItem(index = focusedRowIndex, scrollOffset = 0)
        }
    }

    val isResumeFirst = state.resumeSection != null
    val isNextUpFirst = !isResumeFirst && state.nextUpSection != null
    val isViewsFirst = !isResumeFirst && !isNextUpFirst
    Box(modifier = Modifier.fillMaxSize()) {
        // TOP HERO PREVIEW AREA (Shown when scrolling through rows)
        if (heroAlpha > 0.001f) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(screenHeight * 0.70f)
                        .alpha(heroAlpha)
                        .zIndex(0f)
            ) {
                HeroPreviewHeader(
                    item = focusedItem,
                    view = focusedView,
                    showDetails = showDetails,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // TOP CAROUSEL AREA (Shown when at the top / on the carousel)
        if (carouselItems.isNotEmpty()) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(topHeaderHeight)
                        .padding(top = 64.dp, bottom = 24.dp)
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

        // ROWS LIST: padded from top by topHeaderHeight so rows never overlap header/carousel
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoOpBringIntoViewSpec) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize().padding(top = topHeaderHeight).zIndex(1f),
                contentPadding =
                    PaddingValues(
                        top = 0.dp,
                        bottom = maxOf(300.dp, screenHeight - 250.dp - 120.dp),
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            ) {
                state.resumeSection?.let { section ->
                    item(key = section.id) {
                        HomeSection(
                            section = section.homeSection,
                            itemsPadding = itemsPadding,
                            onAction = onAction,
                            showLogos = state.showLogos,
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
                        )
                    }
                }
                state.nextUpSection?.let { section ->
                    item(key = section.id) {
                        val rowIndex = if (state.resumeSection != null) 1 else 0
                        HomeSection(
                            section = section.homeSection,
                            itemsPadding = itemsPadding,
                            onAction = onAction,
                            showLogos = state.showLogos,
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
                        )
                    }
                }
                itemsIndexed(state.views.distinctBy { it.id }, key = { _, it -> it.id }) {
                    viewIndex,
                    view ->
                    val baseIndex =
                        (if (state.resumeSection != null) 1 else 0) +
                            (if (state.nextUpSection != null) 1 else 0)
                    val rowIndex = baseIndex + viewIndex
                    val isThisFirstView = isViewsFirst && viewIndex == 0
                    HomeView(
                        view = view,
                        itemsPadding = itemsPadding,
                        onAction = onAction,
                        showLogos = state.showLogos,
                        firstItemFocusRequester =
                            if (isThisFirstView) effectiveFirstSectionRequester else null,
                        upFocusRequester =
                            if (isThisFirstView)
                                (effectiveCarouselPlayRequester ?: upFocusRequester)
                            else null,
                        navigateToLibrary = navigateToLibrary,
                        onItemFocused = {
                            focusedRowIndex = rowIndex
                            focusedItem = it
                            focusedView = null
                        },
                        onViewFocused = {
                            focusedRowIndex = rowIndex
                            focusedItem = null
                            focusedView = it
                        },
                    )
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
        when {
            view != null -> stringResource(id = CoreR.string.libraries)
            else -> stringResource(id = CoreR.string.title_home)
        }

    val episodeSubtitle =
        if (item is FindroidEpisode && item.parentIndexNumber > 0 && item.indexNumber > 0) {
            if (item.name.isNotBlank() && item.name != item.seriesName) {
                "S${item.parentIndexNumber}:E${item.indexNumber} • ${item.name}"
            } else {
                "S${item.parentIndexNumber}:E${item.indexNumber}"
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

    val runtimeText = item?.let {
        if (it.runtimeTicks > 0) {
            val minutes = (it.runtimeTicks / 600_000_000L).toInt()
            if (minutes >= 60) {
                val h = minutes / 60
                val m = minutes % 60
                if (m > 0) "${h} h ${m} min" else "${h} h"
            } else if (minutes > 0) {
                "${minutes} min"
            } else null
        } else null
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

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val imageWidth = screenWidth * 0.60f
    val imageHeight = screenHeight * 0.70f
    val imageUri =
        backdropImage
            ?.uri
            .toOptimizedImageUri(
                widthDp = imageWidth,
                heightDp = imageHeight,
            )
    val blurPlaceholder =
        remember(backdropImage?.blurHash) { backdropImage?.blurHash.toBlurHashPainter() }

    Box(modifier = modifier.fillMaxWidth()) {
        if (imageUri != null) {
            Box(
                modifier =
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(0.60f)
                        .align(Alignment.TopEnd)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Smooth oval radial gradient fading into transparency towards left and
                            // bottom
                            scale(
                                scaleX = size.width / size.height,
                                scaleY = 1.0f,
                                pivot = Offset(size.width, 0f),
                            ) {
                                drawRect(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    Color.Black,
                                                    Color.Black.copy(alpha = 0.9f),
                                                    Color.Black.copy(alpha = 0.5f),
                                                    Color.Black.copy(alpha = 0.15f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(size.width, 0f),
                                            radius = size.height * 1.05f,
                                        ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
            ) {
                AsyncImage(
                    model = imageUri,
                    placeholder = blurPlaceholder ?: ColorPainter(Color(0xFF141414)),
                    error = blurPlaceholder,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            modifier =
                Modifier.align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 20.dp)
                    .fillMaxWidth(0.50f)
        ) {
            Text(
                text = categoryLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

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
            navigateToPlayer = { _, _ -> },
            onAction = {},
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun HeroPreviewHeaderPreview() {
    FindroidTheme { HeroPreviewHeader(item = dummyMovie) }
}
