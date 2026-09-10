package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class DownloadQualityPreset(
    val id: String,
    val name: UiText,
    val approxGbPerHour: UiText = UiText.DynamicString(""),
    val maxBitrateBps: Long,
    val maxWidth: Int,
    val maxHeight: Int,
    val audioBitrateBps: Long = 192_000L,
    val audioChannels: Int = 2,
    val audioCodec: String = "aac",
    val videoCodec: String = "h264",
    val audioSampleRate: Int = 48000,
    val isOriginal: Boolean = false,
    val customName: String? = null,
) {
    val totalBitrateBps: Long
        get() = maxBitrateBps + audioBitrateBps

    val expectedGbPerHour: Double
        get() = (totalBitrateBps * 3600.0) / (8.0 * 1_000_000_000.0)

    fun formattedGbPerHour(): String = String.format(Locale.US, "~%.1f GB/h", expectedGbPerHour)

    val displayName: UiText
        get() = if (!customName.isNullOrBlank()) UiText.DynamicString(customName) else name

    val displayApproxSize: UiText
        get() =
            if (isOriginal) {
                UiText.StringResource(CoreR.string.download_preset_source_size)
            } else {
                val s = (approxGbPerHour as? UiText.DynamicString)?.value
                if (!s.isNullOrBlank()) approxGbPerHour
                else UiText.DynamicString(formattedGbPerHour())
            }

    val resolutionText: UiText
        get() =
            if (isOriginal) {
                UiText.StringResource(CoreR.string.download_preset_original)
            } else {
                UiText.DynamicString("${maxHeight}p")
            }

    val bitrateText: UiText
        get() =
            if (isOriginal) {
                UiText.StringResource(CoreR.string.download_preset_source_bitrate)
            } else {
                val mbps = maxBitrateBps / 1_000_000.0
                val formatted =
                    if (mbps == mbps.toLong().toDouble()) "${mbps.toLong()} Mbps"
                    else "%.1f Mbps".format(Locale.US, mbps)
                UiText.DynamicString(formatted)
            }

    val audioText: UiText
        get() =
            if (isOriginal) {
                UiText.StringResource(CoreR.string.download_preset_original_audio)
            } else {
                val channelsLabel =
                    when (audioChannels) {
                        1 -> "Mono"
                        2 -> "Stereo"
                        6 -> "5.1 Surround"
                        8 -> "7.1 Surround"
                        else -> "$audioChannels ch"
                    }
                UiText.DynamicString("${audioBitrateBps / 1000} kbps • $channelsLabel")
            }
}

object DownloadQualityPresets {
    val ORIGINAL =
        DownloadQualityPreset(
            id = "original",
            name = UiText.StringResource(CoreR.string.download_preset_original),
            approxGbPerHour = UiText.StringResource(CoreR.string.download_preset_source_size),
            maxBitrateBps = 0L,
            maxWidth = 0,
            maxHeight = 0,
            isOriginal = true,
        )

    val HIGH =
        DownloadQualityPreset(
            id = "1080p_high",
            name = UiText.StringResource(CoreR.string.download_preset_1080p_high),
            approxGbPerHour = UiText.DynamicString("~3.7 GB/h"),
            maxBitrateBps = 8_000_000L,
            maxWidth = 1920,
            maxHeight = 1080,
            audioBitrateBps = 320_000L,
            audioChannels = 2,
        )

    val BALANCED =
        DownloadQualityPreset(
            id = "1080p_balanced",
            name = UiText.StringResource(CoreR.string.download_preset_1080p_balanced),
            approxGbPerHour = UiText.DynamicString("~2.1 GB/h"),
            maxBitrateBps = 4_500_000L,
            maxWidth = 1920,
            maxHeight = 1080,
            audioBitrateBps = 256_000L,
            audioChannels = 2,
        )

    val MOBILE =
        DownloadQualityPreset(
            id = "720p_mobile",
            name = UiText.StringResource(CoreR.string.download_preset_720p_mobile),
            approxGbPerHour = UiText.DynamicString("~1.2 GB/h"),
            maxBitrateBps = 2_500_000L,
            maxWidth = 1280,
            maxHeight = 720,
            audioBitrateBps = 160_000L,
            audioChannels = 2,
        )

    val DATA_SAVER =
        DownloadQualityPreset(
            id = "480p_data_saver",
            name = UiText.StringResource(CoreR.string.download_preset_480p_data_saver),
            approxGbPerHour = UiText.DynamicString("~0.6 GB/h"),
            maxBitrateBps = 1_200_000L,
            maxWidth = 854,
            maxHeight = 480,
            audioBitrateBps = 128_000L,
            audioChannels = 2,
        )

    val defaultPresets: List<DownloadQualityPreset> =
        listOf(
            ORIGINAL,
            HIGH,
            BALANCED,
            MOBILE,
            DATA_SAVER,
        )

    val all: List<DownloadQualityPreset>
        get() = defaultPresets

    fun loadPresets(appPreferences: AppPreferences): List<DownloadQualityPreset> {
        val json = appPreferences.getValue(appPreferences.customTranscodePresetsJson)
        if (json.isNotBlank()) {
            val imported = importFromJson(json)
            if (!imported.isNullOrEmpty()) {
                val withoutOriginal = imported.filter { !it.isOriginal }
                return listOf(ORIGINAL) + withoutOriginal
            }
        }
        return defaultPresets
    }

    fun savePresets(appPreferences: AppPreferences, presets: List<DownloadQualityPreset>) {
        val json = exportToJson(presets)
        appPreferences.setValue(appPreferences.customTranscodePresetsJson, json)
    }

    fun resetToDefaults(appPreferences: AppPreferences) {
        appPreferences.setValue(appPreferences.customTranscodePresetsJson, "")
    }

    fun getById(id: String, appPreferences: AppPreferences? = null): DownloadQualityPreset {
        val list = if (appPreferences != null) loadPresets(appPreferences) else all
        return list.firstOrNull { it.id == id || (id == "direct" && it.id == "original") }
            ?: BALANCED
    }

    fun isTranscodingPreset(id: String?, appPreferences: AppPreferences? = null): Boolean {
        if (id == null || id == "original" || id == "direct") return false
        val list = if (appPreferences != null) loadPresets(appPreferences) else all
        val preset = list.firstOrNull { it.id == id } ?: return true
        return !preset.isOriginal
    }

    fun exportToJson(presets: List<DownloadQualityPreset> = all): String {
        val jsonArray = JSONArray()
        presets.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            if (!p.customName.isNullOrBlank()) {
                obj.put("name", p.customName)
            }
            obj.put("maxBitrateBps", p.maxBitrateBps)
            obj.put("maxWidth", p.maxWidth)
            obj.put("maxHeight", p.maxHeight)
            obj.put("audioBitrateBps", p.audioBitrateBps)
            obj.put("audioChannels", p.audioChannels)
            obj.put("audioCodec", p.audioCodec)
            obj.put("videoCodec", p.videoCodec)
            obj.put("audioSampleRate", p.audioSampleRate)
            obj.put("isOriginal", p.isOriginal)
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    fun importFromJson(jsonString: String): List<DownloadQualityPreset>? {
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<DownloadQualityPreset>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val isOriginal = obj.optBoolean("isOriginal", id == "original")
                val customName = obj.optString("name").takeIf { it.isNotBlank() }
                val maxHeight = obj.optInt("maxHeight", if (isOriginal) 0 else 1080)
                val maxWidth = obj.optInt("maxWidth", if (isOriginal) 0 else 1920)
                val maxBitrateBps = obj.optLong("maxBitrateBps", if (isOriginal) 0L else 4_500_000L)
                val audioBitrateBps = obj.optLong("audioBitrateBps", 192_000L)
                val audioChannels = obj.optInt("audioChannels", 2)
                val audioCodec = obj.optString("audioCodec", "aac")
                val videoCodec = obj.optString("videoCodec", "h264")
                val audioSampleRate = obj.optInt("audioSampleRate", 48000)

                val name =
                    when {
                        isOriginal -> UiText.StringResource(CoreR.string.download_preset_original)
                        customName != null -> UiText.DynamicString(customName)
                        id == "1080p_high" ->
                            UiText.StringResource(CoreR.string.download_preset_1080p_high)
                        id == "1080p_balanced" ->
                            UiText.StringResource(CoreR.string.download_preset_1080p_balanced)
                        id == "720p_mobile" ->
                            UiText.StringResource(CoreR.string.download_preset_720p_mobile)
                        id == "480p_data_saver" ->
                            UiText.StringResource(CoreR.string.download_preset_480p_data_saver)
                        else -> UiText.DynamicString("${maxHeight}p Preset")
                    }

                list.add(
                    DownloadQualityPreset(
                        id = id,
                        name = name,
                        approxGbPerHour = UiText.DynamicString(""),
                        maxBitrateBps = maxBitrateBps,
                        maxWidth = maxWidth,
                        maxHeight = maxHeight,
                        audioBitrateBps = audioBitrateBps,
                        audioChannels = audioChannels,
                        audioCodec = audioCodec,
                        videoCodec = videoCodec,
                        audioSampleRate = audioSampleRate,
                        isOriginal = isOriginal,
                        customName = customName,
                    )
                )
            }
            list
        } catch (e: Exception) {
            null
        }
    }
}
