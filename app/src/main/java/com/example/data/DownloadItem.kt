package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val videoUrl: String,
    val downloadUrl: String? = null,
    val title: String,
    val platform: String, // "youtube", "tiktok", "instagram", "other"
    val quality: String, // "max", "1080", "720", "480", "360", "audio"
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null,
    val status: String = "PENDING", // "PENDING", "DOWNLOADING", "COMPLETED", "FAILED"
    val downloadId: Long? = null, // ID assigned by DownloadManager
    val progress: Int = 0,
    val fileSizeFormatted: String? = null
)
