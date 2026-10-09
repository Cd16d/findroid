package dev.jdtech.jellyfin.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

@HiltWorker
class ImagesDownloaderWorker
@AssistedInject
constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val repository: JellyfinRepository,
    private val client: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val itemIdString = params.inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        val itemId = UUID.fromString(itemIdString)
        val type = params.inputData.getString(KEY_TYPE) ?: TYPE_USER
        val imageTag = params.inputData.getString(KEY_IMAGE_TAG)

        if (type == TYPE_USER) {
            downloadUserImage(userId = itemId, imageTag = imageTag)
        }
        return Result.success()
    }

    private suspend fun downloadUserImage(userId: UUID, imageTag: String?) {
        withContext(Dispatchers.IO) {
            val baseUrl = repository.getBaseUrl()
            if (baseUrl.isBlank()) return@withContext

            val imageUrl =
                "$baseUrl/Users/$userId/Images/Primary" +
                    (if (imageTag != null) "?tag=$imageTag" else "")
            val basePath = "images/users/$userId"
            val fileName = if (imageTag != null) "primary_$imageTag" else "primary"

            if (imageTag != null) {
                val baseDir = File(appContext.filesDir, basePath)
                baseDir
                    .listFiles { _, name -> name.startsWith("primary_") && name != fileName }
                    ?.forEach { it.delete() }
            }

            downloadAndSave(imageUrl, basePath, fileName)
        }
    }

    private fun downloadAndSave(url: String, basePath: String, fileName: String) {
        val baseDir = File(appContext.filesDir, basePath)
        val file = File(baseDir, fileName)

        // Do not download images if they are already present and we are not forcing
        if (file.exists() && file.length() > 0) return

        val request = Request.Builder().url(url).build()

        try {
            baseDir.mkdirs()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.e("Failed to download image: ${response.code}")
                    return
                }

                response.body.byteStream().use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
    }

    companion object {
        const val KEY_ITEM_ID = "KEY_ITEM_ID"
        const val KEY_TYPE = "KEY_TYPE"
        const val KEY_IMAGE_TAG = "KEY_IMAGE_TAG"
        const val TYPE_USER = "USER"
    }
}
