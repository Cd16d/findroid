package dev.jdtech.jellyfin.utils

import android.content.Context
import android.os.Build
import android.provider.Settings

object DeviceUtils {
    /**
     * Resolves a user-friendly device name for this device, matching what Android exposes in system
     * settings rather than the raw hardware model.
     */
    fun getDeviceName(context: Context): String {
        val globalName =
            try {
                Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            } catch (_: Exception) {
                null
            }
        if (!globalName.isNullOrBlank()) return globalName

        val bluetoothName =
            try {
                Settings.Secure.getString(context.contentResolver, "bluetooth_name")
            } catch (_: Exception) {
                null
            }
        if (!bluetoothName.isNullOrBlank()) return bluetoothName

        val systemName =
            try {
                Settings.System.getString(context.contentResolver, "device_name")
            } catch (_: Exception) {
                null
            }
        if (!systemName.isNullOrBlank()) return systemName

        return "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()
    }
}
