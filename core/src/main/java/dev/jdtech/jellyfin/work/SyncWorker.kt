package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.api.JellyfinApi
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import timber.log.Timber

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
    @Assisted private val context: Context,
    @Assisted private val workerParams: WorkerParameters,
    val database: ServerDatabaseDao,
    val appPreferences: AppPreferences,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val jellyfinApi =
            JellyfinApi(
                androidContext = context.applicationContext,
                requestTimeout = appPreferences.getValue(appPreferences.requestTimeout),
                connectTimeout = appPreferences.getValue(appPreferences.connectTimeout),
                socketTimeout = appPreferences.getValue(appPreferences.socketTimeout),
                okHttpClient = okHttpClient,
            )

        return withContext(Dispatchers.IO) {
            val servers = database.getServers()

            for (server in servers) {
                val serverWithAddressesAndUsers =
                    database.getServerWithAddressesAndUsers(server.id) ?: continue
                val serverAddress =
                    serverWithAddressesAndUsers.addresses.firstOrNull {
                        it.id == server.currentServerAddressId
                    } ?: continue
                for (user in serverWithAddressesAndUsers.users) {
                    jellyfinApi.apply {
                        api.update(baseUrl = serverAddress.address, accessToken = user.accessToken)
                        userId = user.id
                    }

                    val pendingUserData = database.getAllUserDataToBeSynced(user.id)
                    for (userData in pendingUserData) {
                        try {
                            jellyfinApi.itemsApi.updateItemUserData(
                                itemId = userData.itemId,
                                userId = user.id,
                                data =
                                    UpdateUserItemDataDto(
                                        playbackPositionTicks = userData.playbackPositionTicks,
                                        isFavorite = userData.favorite,
                                        played = userData.played,
                                    ),
                            )

                            database.setUserDataToBeSynced(user.id, userData.itemId, false)

                            // If this item was deleted and only kept to sync progress, clean up its
                            // userdata now
                            if (
                                database.getSources(userData.itemId).isEmpty() &&
                                    database.countUserDataToBeSynced(userData.itemId) == 0
                            ) {
                                database.deleteUserData(userData.itemId)
                            }
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "SyncWorker: failed to sync user data for item ${userData.itemId}",
                            )
                        }
                    }
                }
            }

            Result.success()
        }
    }
}
