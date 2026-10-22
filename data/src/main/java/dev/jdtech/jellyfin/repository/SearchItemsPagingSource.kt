package dev.jdtech.jellyfin.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.jdtech.jellyfin.models.FindroidItem
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.SeriesStatus
import org.jellyfin.sdk.model.api.VideoType
import timber.log.Timber

/**
 * PagingSource for fetching filtered search items from the Jellyfin repository with pagination
 * support.
 */
class SearchItemsPagingSource(
    private val jellyfinRepository: JellyfinRepository,
    private val query: String,
    private val includeItemTypes: List<BaseItemKind>?,
    private val seriesStatuses: List<SeriesStatus>?,
    private val genres: List<String>?,
    private val tags: List<String>?,
    private val officialRatings: List<String>?,
    private val years: List<Int>?,
    private val studios: List<String>?,
    private val audioLanguages: List<String>?,
    private val subtitleLanguages: List<String>?,
    private val filters: List<ItemFilter>?,
    private val is3D: Boolean?,
    private val hasSubtitles: Boolean?,
    private val is4K: Boolean?,
    private val isHD: Boolean?,
    private val videoTypes: List<VideoType>?,
    private val hasTrailer: Boolean?,
    private val hasSpecialFeatures: Boolean?,
    private val hasThemeSong: Boolean?,
    private val hasThemeVideo: Boolean?,
    private val minCommunityRating: Double? = null,
) : PagingSource<Int, FindroidItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FindroidItem> {
        val position = params.key ?: 0
        val limit = params.loadSize

        return try {
            val items =
                jellyfinRepository.getSearchItemsFiltered(
                    query = query,
                    includeItemTypes = includeItemTypes,
                    seriesStatuses = seriesStatuses,
                    genres = genres,
                    tags = tags,
                    officialRatings = officialRatings,
                    years = years,
                    studios = studios,
                    audioLanguages = audioLanguages,
                    subtitleLanguages = subtitleLanguages,
                    filters = filters,
                    is3D = is3D,
                    hasSubtitles = hasSubtitles,
                    is4K = is4K,
                    isHD = isHD,
                    videoTypes = videoTypes,
                    hasTrailer = hasTrailer,
                    hasSpecialFeatures = hasSpecialFeatures,
                    hasThemeSong = hasThemeSong,
                    hasThemeVideo = hasThemeVideo,
                    minCommunityRating = minCommunityRating,
                    startIndex = position,
                    limit = limit,
                )
            LoadResult.Page(
                data = items,
                prevKey = if (position == 0) null else position - limit,
                nextKey =
                    if (items.isEmpty() || items.size < limit) null else position + items.size,
            )
        } catch (e: Exception) {
            Timber.e(e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, FindroidItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(state.config.pageSize)
                ?: anchorPage?.nextKey?.minus(state.config.pageSize)
        }
    }
}
