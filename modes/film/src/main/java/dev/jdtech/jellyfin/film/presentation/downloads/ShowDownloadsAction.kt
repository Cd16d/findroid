package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidEpisode
import java.util.UUID

sealed interface ShowDownloadsAction {
    data class DeleteEpisode(val episode: FindroidEpisode) : ShowDownloadsAction

    data class StageDeleteEpisode(val episode: FindroidEpisode) : ShowDownloadsAction

    data class UndoDelete(val id: UUID? = null) : ShowDownloadsAction

    data object CommitPendingDeletions : ShowDownloadsAction

    data class ToggleSelection(val id: UUID) : ShowDownloadsAction

    data class ToggleSeasonSelection(val episodeIds: Set<UUID>) : ShowDownloadsAction

    data object SelectAll : ShowDownloadsAction

    data object ClearSelection : ShowDownloadsAction

    data object DeleteSelected : ShowDownloadsAction

    data class PauseOrResumeSelected(val pause: Boolean) : ShowDownloadsAction

    data object EnterSelectionMode : ShowDownloadsAction

    data object ExitSelectionMode : ShowDownloadsAction

    data class MoveEpisodeStorage(val episode: FindroidEpisode, val targetStorageIndex: Int) :
        ShowDownloadsAction

    data class MoveSelected(val targetStorageIndex: Int) : ShowDownloadsAction

    data class CancelDownload(val id: UUID) : ShowDownloadsAction

    data class PauseDownload(val id: UUID) : ShowDownloadsAction

    data class ResumeDownload(val id: UUID) : ShowDownloadsAction
}
