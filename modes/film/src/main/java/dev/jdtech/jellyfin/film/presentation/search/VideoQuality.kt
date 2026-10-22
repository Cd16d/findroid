package dev.jdtech.jellyfin.film.presentation.search

/**
 * Enum representing video quality and media type filtering options (SD, HD, 4K, 3D, DVD, Blu-ray,
 * ISO).
 */
enum class VideoQuality(val displayName: String) {
    SD("SD"),
    HD("HD"),
    FOUR_K("4K"),
    THREE_D("3D"),
    DVD("DVD"),
    BLU_RAY("Blu-ray"),
    ISO("ISO"),
}
