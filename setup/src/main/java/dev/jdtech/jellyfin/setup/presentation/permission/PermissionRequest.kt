package dev.jdtech.jellyfin.setup.presentation.permission

import dev.jdtech.jellyfin.settings.domain.models.Permission
import dev.jdtech.jellyfin.settings.domain.models.Preference
import dev.jdtech.jellyfin.settings.presentation.enums.DeviceType

/** A runtime permission the app asks the user to grant. */
data class PermissionRequest(
    val permission: Permission,
    val decidedPreference: Preference<Boolean>,
    val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.PHONE, DeviceType.TV),
)
