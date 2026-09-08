package dev.jdtech.jellyfin.film.presentation.downloads

import dev.jdtech.jellyfin.models.FindroidMovie
import java.util.UUID

sealed interface DownloadsAction {
    data class DeleteMovie(val movie: FindroidMovie) : DownloadsAction
    data class DeleteShow(val showItem: DownloadedShowItem) : DownloadsAction
    data class StageDeleteMovie(val movie: FindroidMovie) : DownloadsAction
    data class StageDeleteShow(val showItem: DownloadedShowItem) : DownloadsAction
    data class UndoDelete(val id: UUID? = null) : DownloadsAction
    data class ToggleSelection(val id: UUID) : DownloadsAction
    data object SelectAll : DownloadsAction
    data object ClearSelection : DownloadsAction
    data object DeleteSelected : DownloadsAction
    data class PauseOrResumeSelected(val pause: Boolean) : DownloadsAction
    data object EnterSelectionMode : DownloadsAction
    data object ExitSelectionMode : DownloadsAction
    data class MoveItemStorage(val id: UUID, val targetStorageIndex: Int) : DownloadsAction
    data class MoveSelected(val targetStorageIndex: Int) : DownloadsAction
    data class CancelDownload(val id: UUID) : DownloadsAction
    data class ResumeDownload(val id: UUID) : DownloadsAction
    data class PauseDownload(val id: UUID) : DownloadsAction
}
