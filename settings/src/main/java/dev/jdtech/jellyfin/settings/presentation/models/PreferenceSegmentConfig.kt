package dev.jdtech.jellyfin.settings.presentation.models

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.jdtech.jellyfin.settings.domain.AppPreferences.SegmentAction
import dev.jdtech.jellyfin.settings.domain.models.Preference as PreferenceBackend
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType

/**
 * Preference model representing configuration for a media segment type (action and color).
 *
 * @param nameStringResource String resource ID for the segment title.
 * @param descriptionStringRes Optional string resource ID for description.
 * @param iconDrawableId Optional drawable resource ID for icon.
 * @param enabled Whether preference is enabled.
 * @param dependencies List of boolean preferences this item depends on.
 * @param supportedDeviceTypes Supported target device types.
 * @param segmentType Identifier of the segment type (e.g. Intro, Outro).
 * @param action Currently configured [SegmentAction].
 * @param color Color value for the segment marker.
 * @param onActionChange Callback when action is modified.
 * @param onColorChange Callback when color is modified.
 */
data class PreferenceSegmentConfig(
    @param:StringRes override val nameStringResource: Int,
    @param:StringRes override val descriptionStringRes: Int? = null,
    @param:DrawableRes override val iconDrawableId: Int? = null,
    override val enabled: Boolean = true,
    override val dependencies: List<PreferenceBackend<Boolean>> = emptyList(),
    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.PHONE, DeviceType.TV),
    val segmentType: String,
    val action: SegmentAction = SegmentAction.NONE,
    val color: Long = 0xFF4CAF50L,
    val onActionChange: (SegmentAction) -> Unit = {},
    val onColorChange: (Long) -> Unit = {},
) : Preference
