package dev.jdtech.jellyfin.player.core.domain.models

import android.os.Parcelable
import java.util.Locale
import kotlinx.parcelize.Parcelize

/** Categorizes video streaming quality selection modes. */
enum class QualityType {
    /** Unconstrained source bitrate (Direct Play). */
    ORIGINAL,

    /** Automatically determined bitrate using on-demand network speed testing. */
    AUTO,

    /** User-specified fixed resolution and bitrate constraint. */
    FIXED,
}

/** Represents a resolution tier with its supported bitrate options. */
@Parcelize
data class ResolutionTier(
    val name: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val bitratesBps: List<Int>,
    val defaultBitrateBps: Int,
) : Parcelable

/**
 * Represents a video streaming quality / bitrate option.
 *
 * @property bitrateBps Target bitrate in bits per second, or null for Auto / Original.
 * @property label Human-readable description of the quality option.
 * @property resolutionName Name of the resolution group (e.g. "4K", "1080p", "720p", "360p",
 *   "240p").
 * @property maxWidth Optional video width limit constraint.
 * @property maxHeight Optional video height limit constraint.
 * @property type The [QualityType] classification.
 */
@Parcelize
data class VideoQualityOption(
    val bitrateBps: Int?,
    val label: String,
    val resolutionName: String? = null,
    val maxWidth: Int? = null,
    val maxHeight: Int? = null,
    val type: QualityType =
        if (bitrateBps == null || bitrateBps <= 0) QualityType.AUTO else QualityType.FIXED,
) : Parcelable {
    val isOriginal: Boolean
        get() = type == QualityType.ORIGINAL

    val isAuto: Boolean
        get() = type == QualityType.AUTO

    val isFixed: Boolean
        get() = type == QualityType.FIXED

    companion object {
        val ORIGINAL = VideoQualityOption(null, "Original", type = QualityType.ORIGINAL)
        val AUTO = VideoQualityOption(null, "Auto", type = QualityType.AUTO)

        val RESOLUTION_TIERS =
            listOf(
                ResolutionTier(
                    name = "4K",
                    maxWidth = 3840,
                    maxHeight = 2160,
                    bitratesBps = listOf(40_000_000, 60_000_000, 80_000_000, 120_000_000),
                    defaultBitrateBps = 60_000_000,
                ),
                ResolutionTier(
                    name = "1080p",
                    maxWidth = 1920,
                    maxHeight = 1080,
                    bitratesBps =
                        listOf(
                            4_000_000,
                            6_000_000,
                            8_000_000,
                            10_000_000,
                            15_000_000,
                            20_000_000,
                            40_000_000,
                        ),
                    defaultBitrateBps = 10_000_000,
                ),
                ResolutionTier(
                    name = "720p",
                    maxWidth = 1280,
                    maxHeight = 720,
                    bitratesBps = listOf(1_500_000, 2_000_000, 3_000_000, 4_000_000, 6_000_000),
                    defaultBitrateBps = 3_000_000,
                ),
                ResolutionTier(
                    name = "360p",
                    maxWidth = 640,
                    maxHeight = 360,
                    bitratesBps = listOf(420_000, 720_000, 1_000_000, 1_500_000),
                    defaultBitrateBps = 1_000_000,
                ),
                ResolutionTier(
                    name = "240p",
                    maxWidth = 426,
                    maxHeight = 240,
                    bitratesBps = listOf(320_000, 420_000, 600_000),
                    defaultBitrateBps = 420_000,
                ),
            )

        /**
         * Returns available resolution tiers based on source dimensions (width and height).
         *
         * - Tiers higher than the source resolution are hidden (aspect-ratio aware).
         * - All bitrates for allowed tiers are available for selection.
         */
        fun getAvailableResolutions(
            sourceWidth: Int?,
            sourceHeight: Int?,
        ): List<ResolutionTier> {
            val width = sourceWidth ?: 1920
            val height = sourceHeight ?: 1080
            return RESOLUTION_TIERS.mapNotNull { tier ->
                val isResolutionAllowed =
                    when (tier.name) {
                        "4K" -> width > 1920 || height > 1080
                        "1080p" -> width > 1280 || height > 720
                        "720p" -> width > 854 || height > 480
                        "360p" -> width > 426 || height > 240
                        "240p" -> true
                        else -> width >= tier.maxWidth || height >= tier.maxHeight
                    }
                if (!isResolutionAllowed) null else tier
            }
        }

        fun createFixedOption(resolutionName: String, bitrateBps: Int): VideoQualityOption {
            val tier = RESOLUTION_TIERS.firstOrNull {
                it.name.equals(resolutionName, ignoreCase = true)
            }
            val formatted = formatBitrate(bitrateBps)
            val label = "$resolutionName • $formatted"
            return VideoQualityOption(
                bitrateBps = bitrateBps,
                label = label,
                resolutionName = tier?.name ?: resolutionName,
                maxWidth = tier?.maxWidth,
                maxHeight = tier?.maxHeight,
                type = QualityType.FIXED,
            )
        }

        fun formatBitrate(bitrateBps: Int): String {
            if (bitrateBps <= 0) return ""
            val mbps = bitrateBps / 1_000_000.0
            return if (mbps >= 1.0) {
                if (mbps % 1.0 == 0.0) "${mbps.toInt()} Mbps"
                else String.format(Locale.US, "%.1f Mbps", mbps)
            } else {
                "${bitrateBps / 1000} kbps"
            }
        }
    }
}
