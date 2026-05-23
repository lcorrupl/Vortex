package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CobaltRequest(
    @Json(name = "url") val url: String,
    @Json(name = "videoQuality") val videoQuality: String? = null, // "max", "1080", "720", "480", "360", "240", "144"
    @Json(name = "downloadMode") val downloadMode: String? = null, // "auto", "audio", "video"
    @Json(name = "isAudioOnly") val isAudioOnly: Boolean? = null, // fallback/legacy support
    @Json(name = "audioFormat") val audioFormat: String? = null, // "mp3", "ogg", "wav", "opus"
    @Json(name = "filenamePattern") val filenamePattern: String? = "classic", // "classic", "pretty", "basic", "nerdy"
    @Json(name = "tiktokFullAudio") val tiktokFullAudio: Boolean? = true,
    @Json(name = "dubbedAudio") val dubbedAudio: Boolean? = false
)
