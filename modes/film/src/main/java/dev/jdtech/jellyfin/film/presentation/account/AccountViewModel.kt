package dev.jdtech.jellyfin.film.presentation.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.models.getProfileImageModel
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val repository: JellyfinRepository,
    private val database: ServerDatabaseDao,
    private val appPreferences: AppPreferences,
    private val downloader: Downloader
) : ViewModel() {

    private val _state = MutableStateFlow(AccountState())
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<AccountEvent>()
    val events = eventsChannel.receiveAsFlow()

    fun loadData() {
        Timber.i("Loading data")
        viewModelScope.launch(Dispatchers.Default) {
            appPreferences.getValue(appPreferences.currentServer)?.let { serverId ->
                loadUser(serverId)
            }
        }
    }

    private suspend fun loadUser(serverId: String) {
        val user = database.getServerCurrentUser(serverId)
        val baseUrl = repository.getBaseUrl()

        val imageUrl = user?.getProfileImageModel(context, baseUrl)

        _state.emit(
            _state.value.copy(
                user = user,
                userImageUrl = imageUrl,
                baseUrl = baseUrl
            )
        )
    }

}