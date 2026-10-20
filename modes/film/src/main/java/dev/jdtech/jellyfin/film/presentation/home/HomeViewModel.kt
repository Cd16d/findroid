package dev.jdtech.jellyfin.film.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.models.getProfileImageModel
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.toView
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class HomeViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val repository: JellyfinRepository,
    val appPreferences: AppPreferences,
    val database: ServerDatabaseDao,
) : ViewModel() {
    private val _state =
        MutableStateFlow(cachedHomeState?.copy(isLoading = true) ?: HomeState(isLoading = true))
    val state = _state.asStateFlow()

    private val uuidSuggestions = UUID.fromString("31e47044-9b79-4bb0-99d0-0e477ed65420")
    private val uuidContinueWatching =
        UUID(4937169328197226115, -4704919157662094443) // 44845958-8326-4e83-beb4-c4f42e9eeb95
    private val uuidNextUp =
        UUID(1783371395749072194, -6164625418200444295) // 18bfced5-f237-4d42-aa72-d9d7fed19279

    private val uiTextContinueWatching = UiText.StringResource(FilmR.string.continue_watching)
    private val uiTextNextUp = UiText.StringResource(FilmR.string.next_up)

    init {
        viewModelScope.launch {
            appPreferences.observe(appPreferences.hideEpisodeSpoilers).collect { hideSpoilers ->
                _state.update { it.copy(hideEpisodeSpoilers = hideSpoilers) }
            }
        }
        viewModelScope.launch {
            appPreferences.observe(appPreferences.tvShowLogos).collect { showLogos ->
                _state.update { it.copy(showLogos = showLogos) }
            }
        }
    }

    fun loadData(forceRefreshSuggestions: Boolean = false) {
        Timber.i("Loading data")
        viewModelScope.launch(Dispatchers.Default) {
            val hideEpisodeSpoilers = appPreferences.getValue(appPreferences.hideEpisodeSpoilers)
            val showLogos = appPreferences.getValue(appPreferences.tvShowLogos)
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    hideEpisodeSpoilers = hideEpisodeSpoilers,
                    showLogos = showLogos,
                )
            }
            try {
                appPreferences.getValue(appPreferences.currentServer)?.let { serverId ->
                    val server = database.getServer(serverId)
                    val user = database.getServerCurrentUser(serverId)
                    val baseUrl = repository.getBaseUrl()
                    val imageUrl = user?.getProfileImageModel(context, baseUrl)
                    _state.update {
                        it.copy(
                            server = server ?: it.server,
                            userImageUrl = imageUrl ?: it.userImageUrl,
                        )
                    }
                }

                coroutineScope {
                    val suggestionsDeferred = async { fetchSuggestions(forceRefreshSuggestions) }
                    val resumeDeferred = async { fetchResumeItems() }
                    val nextUpDeferred = async { fetchNextUpItems() }
                    val viewsDeferred = async { fetchViews() }

                    val suggestions = suggestionsDeferred.await()
                    val resume = resumeDeferred.await()
                    val nextUp = nextUpDeferred.await()
                    val views = viewsDeferred.await()

                    _state.update {
                        it.copy(
                            suggestionsSection = suggestions,
                            resumeSection = resume,
                            nextUpSection = nextUp,
                            views = views,
                            isLoading = false,
                            error = null,
                        )
                    }
                    cachedHomeState = _state.value
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load home data")
                _state.update { it.copy(error = e, isLoading = false) }
            }
        }
    }

    private suspend fun fetchSuggestions(forceRefresh: Boolean = false): HomeItem.Suggestions? {
        Timber.i("Fetching suggestions")
        if (!appPreferences.getValue(appPreferences.homeSuggestions)) {
            return null
        }

        val items = repository.getSuggestions(forceRefresh)

        return if (items.isEmpty()) {
            null
        } else {
            HomeItem.Suggestions(id = uuidSuggestions, items = items)
        }
    }

    private suspend fun fetchResumeItems(): HomeItem.Section? {
        Timber.i("Fetching resume items")
        if (!appPreferences.getValue(appPreferences.homeContinueWatching)) {
            return null
        }

        val resumeItems = repository.getResumeItems()

        return if (resumeItems.isEmpty()) {
            null
        } else {
            HomeItem.Section(HomeSection(uuidContinueWatching, uiTextContinueWatching, resumeItems))
        }
    }

    private suspend fun fetchNextUpItems(): HomeItem.Section? {
        Timber.i("Fetching next up items")
        if (!appPreferences.getValue(appPreferences.homeNextUp)) {
            return null
        }

        val nextUpItems = repository.getNextUp()

        return if (nextUpItems.isEmpty()) {
            null
        } else {
            HomeItem.Section(HomeSection(uuidNextUp, uiTextNextUp, nextUpItems))
        }
    }

    private suspend fun fetchViews(): List<HomeItem.ViewItem> {
        Timber.i("Fetching views")
        return if (appPreferences.getValue(appPreferences.homeLatest)) {
            val supportedViews =
                repository.getUserViews().filter { view ->
                    CollectionType.fromString(view.collectionType?.serialName) in
                        CollectionType.supported
                }
            coroutineScope {
                supportedViews
                    .map { view ->
                        async {
                            val latest = repository.getLatestMedia(view.id)
                            view to latest
                        }
                    }
                    .awaitAll()
            }
                .filter { (_, latest) -> latest.isNotEmpty() }
                .map { (view, latest) -> view.toView(latest) }
                .map { HomeItem.ViewItem(it) }
        } else {
            emptyList()
        }
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.OnRetryClick -> {
                loadData()
            }
            else -> Unit
        }
    }

    suspend fun getShow(showId: UUID): FindroidShow = repository.getShow(showId)

    companion object {
        private var cachedHomeState: HomeState? = null
    }
}
