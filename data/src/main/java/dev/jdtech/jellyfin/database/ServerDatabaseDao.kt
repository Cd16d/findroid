package dev.jdtech.jellyfin.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.jdtech.jellyfin.models.FindroidEpisodeDto
import dev.jdtech.jellyfin.models.FindroidMediaStreamDto
import dev.jdtech.jellyfin.models.FindroidMovieDto
import dev.jdtech.jellyfin.models.FindroidPartDto
import dev.jdtech.jellyfin.models.FindroidSeasonDto
import dev.jdtech.jellyfin.models.FindroidSegmentDto
import dev.jdtech.jellyfin.models.FindroidShowDto
import dev.jdtech.jellyfin.models.FindroidShowWithEpisodes
import dev.jdtech.jellyfin.models.FindroidSourceDto
import dev.jdtech.jellyfin.models.FindroidTrickplayInfoDto
import dev.jdtech.jellyfin.models.FindroidUserDataDto
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.ServerAddress
import dev.jdtech.jellyfin.models.ServerWithAddressAndUser
import dev.jdtech.jellyfin.models.ServerWithAddresses
import dev.jdtech.jellyfin.models.ServerWithAddressesAndUsers
import dev.jdtech.jellyfin.models.User
import dev.jdtech.jellyfin.models.UserDownloadDto
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDatabaseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertServer(server: Server)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServerAddress(address: ServerAddress)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertUser(user: User)

    @Update suspend fun updateServer(server: Server)

    @Query("SELECT * FROM servers WHERE id = :id") suspend fun getServer(id: String): Server?

    @Query("SELECT * FROM users WHERE id = :id") suspend fun getUser(id: UUID): User?

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerWithAddresses(id: String): ServerWithAddresses

    @Query("SELECT * FROM serverAddresses WHERE id = :id")
    suspend fun getAddress(id: UUID): ServerAddress

    @Query("SELECT * FROM users WHERE serverId = :serverId")
    suspend fun getUsers(serverId: String): List<User>

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerWithAddressesAndUsers(id: String): ServerWithAddressesAndUsers?

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerWithAddressAndUser(id: String): ServerWithAddressAndUser?

    @Transaction
    @Query("SELECT * FROM servers")
    suspend fun getServersWithAddresses(): List<ServerWithAddresses>

    @Query("SELECT * FROM servers") suspend fun getServers(): List<Server>

    @Query("SELECT COUNT(*) FROM servers") suspend fun getServersCount(): Int

    @Query("DELETE FROM servers WHERE id = :id") suspend fun deleteServer(id: String)

    @Query("DELETE FROM users WHERE id = :id") suspend fun deleteUser(id: UUID)

    @Query("DELETE FROM serverAddresses WHERE id = :id") suspend fun deleteServerAddress(id: UUID)

    @Query("UPDATE servers SET currentUserId = :userId WHERE id = :serverId")
    suspend fun updateServerCurrentUser(serverId: String, userId: UUID)

    @Query(
        "SELECT * FROM users WHERE id = (SELECT currentUserId FROM servers WHERE id = :serverId)"
    )
    suspend fun getServerCurrentUser(serverId: String): User?

    @Query(
        "SELECT * FROM serverAddresses WHERE id = (SELECT currentServerAddressId FROM servers WHERE id = :serverId)"
    )
    suspend fun getServerCurrentAddress(serverId: String): ServerAddress?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMovie(movie: FindroidMovieDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: FindroidSourceDto)

    @Query("SELECT * FROM movies WHERE id = :id") suspend fun getMovie(id: UUID): FindroidMovieDto

    @Query("SELECT * FROM sources WHERE itemId = :itemId")
    suspend fun getSources(itemId: UUID): List<FindroidSourceDto>

    @Query("SELECT * FROM sources") fun getAllSources(): List<FindroidSourceDto>

    @Query("SELECT * FROM sources WHERE downloadId = :downloadId")
    suspend fun getSourceByDownloadId(downloadId: Long): FindroidSourceDto?

    @Query("SELECT * FROM sources WHERE downloadId IS NOT NULL AND path LIKE '%.download'")
    suspend fun getIncompleteSources(): List<FindroidSourceDto>

    @Query("UPDATE sources SET downloadId = :downloadId WHERE id = :id")
    suspend fun setSourceDownloadId(id: String, downloadId: Long)

    @Query("UPDATE sources SET path = :path WHERE id = :id")
    suspend fun setSourcePath(id: String, path: String)

    @Query("DELETE FROM sources WHERE id = :id") suspend fun deleteSource(id: String)

    @Query("DELETE FROM movies WHERE id = :id") suspend fun deleteMovie(id: UUID)

    @Query(
        "UPDATE userdata SET playbackPositionTicks = :playbackPositionTicks WHERE itemId = :itemId AND userid = :userId"
    )
    suspend fun setPlaybackPositionTicks(itemId: UUID, userId: UUID, playbackPositionTicks: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaStream(mediaStream: FindroidMediaStreamDto)

    @Query("SELECT * FROM mediastreams WHERE sourceId = :sourceId")
    suspend fun getMediaStreamsBySourceId(sourceId: String): List<FindroidMediaStreamDto>

    @Query("SELECT * FROM mediastreams WHERE downloadId = :downloadId")
    suspend fun getMediaStreamByDownloadId(downloadId: Long): FindroidMediaStreamDto?

    @Query("UPDATE mediastreams SET downloadId = :downloadId WHERE id = :id")
    suspend fun setMediaStreamDownloadId(id: UUID, downloadId: Long)

    @Query("UPDATE mediastreams SET path = :path WHERE id = :id")
    suspend fun setMediaStreamPath(id: UUID, path: String)

    @Query("DELETE FROM mediastreams WHERE id = :id") suspend fun deleteMediaStream(id: UUID)

    @Query("DELETE FROM mediastreams WHERE sourceId = :sourceId")
    suspend fun deleteMediaStreamsBySourceId(sourceId: String)

    @Query("UPDATE userdata SET played = :played WHERE userId = :userId AND itemId = :itemId")
    suspend fun setPlayed(userId: UUID, itemId: UUID, played: Boolean)

    @Query("UPDATE userdata SET favorite = :favorite WHERE userId = :userId AND itemId = :itemId")
    suspend fun setFavorite(userId: UUID, itemId: UUID, favorite: Boolean)

    @Query("SELECT * FROM movies ORDER BY name ASC") suspend fun getMovies(): List<FindroidMovieDto>

    @Query("SELECT * FROM movies WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getMoviesByServerId(serverId: String): List<FindroidMovieDto>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertShow(show: FindroidShowDto)

    @Query("SELECT * FROM shows WHERE id = :id") suspend fun getShow(id: UUID): FindroidShowDto

    @Query("SELECT * FROM shows ORDER BY name ASC") suspend fun getShows(): List<FindroidShowDto>

    @Query("SELECT * FROM shows WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getShowsByServerId(serverId: String): List<FindroidShowDto>

    @Transaction
    @Query("SELECT * FROM shows WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getDownloadedShowsWithEpisodes(serverId: String): List<FindroidShowWithEpisodes>

    @Query("DELETE FROM shows WHERE id = :id") suspend fun deleteShow(id: UUID)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeason(show: FindroidSeasonDto)

    @Query("SELECT * FROM seasons WHERE id = :id")
    suspend fun getSeason(id: UUID): FindroidSeasonDto

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY indexNumber ASC")
    suspend fun getSeasonsByShowId(seriesId: UUID): List<FindroidSeasonDto>

    @Query("DELETE FROM seasons WHERE id = :id") suspend fun deleteSeason(id: UUID)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisode(episode: FindroidEpisodeDto)

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisode(id: UUID): FindroidEpisodeDto

    @Query(
        "SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY parentIndexNumber ASC, indexNumber ASC"
    )
    suspend fun getEpisodesByShowId(seriesId: UUID): List<FindroidEpisodeDto>

    @Query(
        "SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY parentIndexNumber ASC, indexNumber ASC"
    )
    fun getDownloadedEpisodesByShowId(seriesId: UUID): Flow<List<FindroidEpisodeDto>>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY indexNumber ASC")
    suspend fun getEpisodesBySeasonId(seasonId: UUID): List<FindroidEpisodeDto>

    @Query(
        "SELECT * FROM episodes WHERE serverId = :serverId ORDER BY seriesName ASC, parentIndexNumber ASC, indexNumber ASC"
    )
    suspend fun getEpisodesByServerId(serverId: String): List<FindroidEpisodeDto>

    @Query(
        "SELECT episodes.* FROM episodes INNER JOIN userdata ON episodes.id = userdata.itemId WHERE serverId = :serverId AND playbackPositionTicks > 0 ORDER BY episodes.parentIndexNumber ASC, episodes.indexNumber ASC"
    )
    suspend fun getEpisodeResumeItems(serverId: String): List<FindroidEpisodeDto>

    @Query("DELETE FROM episodes WHERE id = :id") suspend fun deleteEpisode(id: UUID)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegment(segment: FindroidSegmentDto)

    @Query("SELECT * FROM segments WHERE itemId = :itemId")
    suspend fun getSegments(itemId: UUID): List<FindroidSegmentDto>

    @Query("SELECT * FROM seasons") suspend fun getSeasons(): List<FindroidSeasonDto>

    @Query("SELECT * FROM episodes") suspend fun getEpisodes(): List<FindroidEpisodeDto>

    @Query("SELECT * FROM userdata WHERE itemId = :itemId AND userId = :userId")
    suspend fun getUserData(itemId: UUID, userId: UUID): FindroidUserDataDto?

    @Transaction
    suspend fun getUserDataOrCreateNew(itemId: UUID, userId: UUID): FindroidUserDataDto {
        var userData = getUserData(itemId, userId)

        // Create user data when there is none
        if (userData == null) {
            userData =
                FindroidUserDataDto(
                    userId = userId,
                    itemId = itemId,
                    played = false,
                    favorite = false,
                    playbackPositionTicks = 0L,
                )
            insertUserData(userData)
        }

        return userData
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserData(userData: FindroidUserDataDto)

    @Query("DELETE FROM userdata WHERE itemId = :itemId") suspend fun deleteUserData(itemId: UUID)

    @Query("SELECT * FROM userdata WHERE userId = :userId AND itemId = :itemId AND toBeSynced = 1")
    suspend fun getUserDataToBeSynced(userId: UUID, itemId: UUID): FindroidUserDataDto?

    @Query("SELECT * FROM userdata WHERE userId = :userId AND toBeSynced = 1")
    fun getAllUserDataToBeSynced(userId: UUID): List<FindroidUserDataDto>

    @Query("SELECT COUNT(*) FROM userdata WHERE itemId = :itemId AND toBeSynced = 1")
    fun countUserDataToBeSynced(itemId: UUID): Int

    @Query(
        "UPDATE userdata SET toBeSynced = :toBeSynced WHERE itemId = :itemId AND userId = :userId"
    )
    suspend fun setUserDataToBeSynced(userId: UUID, itemId: UUID, toBeSynced: Boolean)

    @Query("SELECT * FROM movies WHERE serverId = :serverId AND name LIKE '%' || :name || '%'")
    suspend fun searchMovies(serverId: String, name: String): List<FindroidMovieDto>

    @Query("SELECT * FROM shows WHERE serverId = :serverId AND name LIKE '%' || :name || '%'")
    suspend fun searchShows(serverId: String, name: String): List<FindroidShowDto>

    @Query("SELECT * FROM episodes WHERE serverId = :serverId AND name LIKE '%' || :name || '%'")
    suspend fun searchEpisodes(serverId: String, name: String): List<FindroidEpisodeDto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrickplayInfo(trickplayInfoDto: FindroidTrickplayInfoDto)

    @Query("SELECT * FROM trickplayInfos WHERE sourceId = :sourceId")
    suspend fun getTrickplayInfo(sourceId: String): FindroidTrickplayInfoDto?

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPart(part: FindroidPartDto)

    @Query("SELECT * FROM parts WHERE id IN (:ids)")
    suspend fun getParts(ids: List<UUID>): List<FindroidPartDto>

    @Query("DELETE FROM parts WHERE id = :id") suspend fun deletePart(id: UUID)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUserDownload(userDownload: UserDownloadDto)

    @Query("DELETE FROM user_downloads WHERE userId = :userId AND itemId = :itemId")
    suspend fun deleteUserDownload(userId: UUID, itemId: UUID)

    @Query("DELETE FROM user_downloads WHERE itemId = :itemId")
    suspend fun deleteUserDownloadsByItemId(itemId: UUID)

    @Query("SELECT COUNT(*) FROM user_downloads WHERE itemId = :itemId")
    suspend fun countUserDownloads(itemId: UUID): Int

    @Query(
        "SELECT EXISTS(SELECT 1 FROM user_downloads WHERE userId = :userId AND itemId = :itemId)"
    )
    suspend fun isItemDownloadedForUser(userId: UUID, itemId: UUID): Boolean

    @Query(
        "SELECT movies.* FROM movies INNER JOIN user_downloads ON movies.id = user_downloads.itemId WHERE user_downloads.userId = :userId ORDER BY movies.name ASC"
    )
    suspend fun getDownloadedMoviesByUser(userId: UUID): List<FindroidMovieDto>

    @Query(
        "SELECT movies.* FROM movies INNER JOIN user_downloads ON movies.id = user_downloads.itemId WHERE movies.serverId = :serverId AND user_downloads.userId = :userId ORDER BY movies.name ASC"
    )
    suspend fun getDownloadedMoviesByServerAndUser(
        serverId: String,
        userId: UUID,
    ): List<FindroidMovieDto>

    @Query(
        "SELECT DISTINCT shows.* FROM shows INNER JOIN episodes ON shows.id = episodes.seriesId INNER JOIN user_downloads ON episodes.id = user_downloads.itemId WHERE user_downloads.userId = :userId ORDER BY shows.name ASC"
    )
    suspend fun getDownloadedShowsByUser(userId: UUID): List<FindroidShowDto>

    @Query(
        "SELECT DISTINCT shows.* FROM shows INNER JOIN episodes ON shows.id = episodes.seriesId INNER JOIN user_downloads ON episodes.id = user_downloads.itemId WHERE shows.serverId = :serverId AND user_downloads.userId = :userId ORDER BY shows.name ASC"
    )
    suspend fun getDownloadedShowsByServerAndUser(
        serverId: String,
        userId: UUID,
    ): List<FindroidShowDto>

    @Query(
        "SELECT episodes.* FROM episodes INNER JOIN user_downloads ON episodes.id = user_downloads.itemId WHERE episodes.seriesId = :seriesId AND user_downloads.userId = :userId ORDER BY episodes.parentIndexNumber ASC, episodes.indexNumber ASC"
    )
    suspend fun getDownloadedEpisodesByShowAndUser(
        seriesId: UUID,
        userId: UUID,
    ): List<FindroidEpisodeDto>

    @Query(
        "SELECT episodes.* FROM episodes INNER JOIN user_downloads ON episodes.id = user_downloads.itemId WHERE episodes.seasonId = :seasonId AND user_downloads.userId = :userId ORDER BY episodes.indexNumber ASC"
    )
    suspend fun getDownloadedEpisodesBySeasonAndUser(
        seasonId: UUID,
        userId: UUID,
    ): List<FindroidEpisodeDto>

    @Query(
        "SELECT episodes.* FROM episodes INNER JOIN user_downloads ON episodes.id = user_downloads.itemId WHERE episodes.serverId = :serverId AND user_downloads.userId = :userId ORDER BY episodes.seriesName ASC, episodes.parentIndexNumber ASC, episodes.indexNumber ASC"
    )
    suspend fun getDownloadedEpisodesByServerAndUser(
        serverId: String,
        userId: UUID,
    ): List<FindroidEpisodeDto>

    @Query(
        "SELECT movies.* FROM movies INNER JOIN user_downloads ON movies.id = user_downloads.itemId WHERE movies.serverId = :serverId AND user_downloads.userId = :userId AND movies.name LIKE '%' || :name || '%' ORDER BY movies.name ASC"
    )
    suspend fun searchDownloadedMovies(
        serverId: String,
        userId: UUID,
        name: String,
    ): List<FindroidMovieDto>

    @Query(
        "SELECT DISTINCT shows.* FROM shows INNER JOIN episodes ON shows.id = episodes.seriesId INNER JOIN user_downloads ON episodes.id = user_downloads.itemId WHERE shows.serverId = :serverId AND user_downloads.userId = :userId AND shows.name LIKE '%' || :name || '%' ORDER BY shows.name ASC"
    )
    suspend fun searchDownloadedShows(
        serverId: String,
        userId: UUID,
        name: String,
    ): List<FindroidShowDto>

    @Query(
        "SELECT episodes.* FROM episodes INNER JOIN user_downloads ON episodes.id = user_downloads.itemId WHERE episodes.serverId = :serverId AND user_downloads.userId = :userId AND episodes.name LIKE '%' || :name || '%' ORDER BY episodes.seriesName ASC, episodes.parentIndexNumber ASC, episodes.indexNumber ASC"
    )
    suspend fun searchDownloadedEpisodes(
        serverId: String,
        userId: UUID,
        name: String,
    ): List<FindroidEpisodeDto>

    @Transaction
    suspend fun linkServerDownloadsToUser(serverId: String, userId: UUID) {
        val movies = getMoviesByServerId(serverId)
        val now = System.currentTimeMillis()
        for (movie in movies) {
            insertUserDownload(
                UserDownloadDto(userId = userId, itemId = movie.id, downloadedAt = now)
            )
        }
        val episodes = getEpisodesByServerId(serverId)
        for (episode in episodes) {
            insertUserDownload(
                UserDownloadDto(userId = userId, itemId = episode.id, downloadedAt = now)
            )
        }
    }
}
