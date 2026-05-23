package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PickerItem(
    @Json(name = "url") val url: String,
    @Json(name = "thumb") val thumb: String? = null,
    @Json(name = "type") val type: String? = "photo" // "photo", "video"
)

@JsonClass(generateAdapter = true)
data class CobaltResponse(
    @Json(name = "status") val status: String, // "success", "stream", "redirect", "tunnel", "picker", "error"
    @Json(name = "url") val url: String? = null,
    @Json(name = "text") val text: String? = null, // Used for error description
    @Json(name = "picker") val picker: List<PickerItem>? = null,
    @Json(name = "pickerType") val pickerType: String? = null, // "photo", "video", "music"
    @Json(name = "audio") val audio: String? = null,
    @Json(name = "audioFilename") val audioFilename: String? = null,
    @Json(name = "filename") val filename: String? = null
)
