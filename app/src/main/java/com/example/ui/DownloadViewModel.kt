package com.example.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.DownloadItem
import com.example.data.DownloadRepository
import com.example.network.CobaltRequest
import com.example.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

sealed interface DownloadUiState {
    object Idle : DownloadUiState
    object Loading : DownloadUiState
    data class Success(val message: String) : DownloadUiState
    data class Error(val message: String) : DownloadUiState
}

class DownloadViewModel(private val repository: DownloadRepository) : ViewModel() {

    val urlInput = MutableStateFlow("")
    val selectedQuality = MutableStateFlow("720") // "max", "1080", "720", "480", "360", "audio"
    
    // Shared option for setting custom API URL
    val customApiUrl = MutableStateFlow("https://api.cobalt.tools")

    private val _uiState = MutableStateFlow<DownloadUiState>(DownloadUiState.Idle)
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    // Observe downloads from history database
    val downloadHistory: StateFlow<List<DownloadItem>> = repository.allDownloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var progressTrackingJob: Job? = null

    // Helper to detect platform from input URL
    fun detectPlatform(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "youtube"
            lower.contains("tiktok.com") -> "tiktok"
            lower.contains("instagram.com") -> "instagram"
            else -> "other"
        }
    }

    fun onUrlChange(url: String) {
        urlInput.value = url
        _uiState.value = DownloadUiState.Idle
    }

    fun selectQuality(quality: String) {
        selectedQuality.value = quality
        _uiState.value = DownloadUiState.Idle
    }

    fun setApiUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isNotEmpty()) {
            customApiUrl.value = trimmed
        }
    }

    fun deleteHistoryItem(item: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    fun startTrackingProgress(context: Context) {
        if (progressTrackingJob?.isActive == true) return
        progressTrackingJob = viewModelScope.launch(Dispatchers.IO) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            while (true) {
                // Fetch the current active items from Room
                val currentList = repository.allDownloads.firstOrNull() ?: emptyList()
                val activeItems = currentList.filter { it.status == "DOWNLOADING" || it.status == "PENDING" }
                
                if (activeItems.isEmpty()) {
                    delay(3000) // check less frequently if empty to save battery
                    continue
                }

                for (item in activeItems) {
                    if (item.downloadId == null) continue
                    val query = DownloadManager.Query().setFilterById(item.downloadId)
                    val cursor = downloadManager.query(query)
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                        val bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                        if (statusIdx != -1 && bytesDownloadedIdx != -1 && bytesTotalIdx != -1) {
                            val dmStatus = cursor.getInt(statusIdx)
                            val downloaded = cursor.getInt(bytesDownloadedIdx)
                            val total = cursor.getInt(bytesTotalIdx)

                            val progress = if (total > 0) ((downloaded.toLong() * 100) / total).toInt() else 0
                            val sizeFormatted = if (total > 0) {
                                String.format("%.2f MB", total / (1024.0 * 1024.0))
                            } else {
                                "Calculando..."
                            }

                            val newStatus = when (dmStatus) {
                                DownloadManager.STATUS_SUCCESSFUL -> "COMPLETED"
                                DownloadManager.STATUS_FAILED -> "FAILED"
                                DownloadManager.STATUS_PENDING -> "PENDING"
                                else -> "DOWNLOADING"
                            }

                            val updatedItem = item.copy(
                                progress = if (newStatus == "COMPLETED") 100 else progress,
                                status = newStatus,
                                fileSizeFormatted = sizeFormatted
                            )
                            repository.update(updatedItem)
                        }
                    } else {
                        // If no longer found in DownloadManager, mark completed or failed
                        val updatedItem = item.copy(status = "FAILED", progress = 0)
                        repository.update(updatedItem)
                    }
                    cursor?.close()
                }
                delay(1000) // Poll every 1 second
            }
        }
    }

    fun triggerDownload(context: Context) {
        val targetUrl = urlInput.value.trim()
        if (targetUrl.isEmpty()) {
            _uiState.value = DownloadUiState.Error("Por favor, introduce un enlace válido.")
            return
        }

        val platform = detectPlatform(targetUrl)
        if (platform == "other" && !targetUrl.startsWith("http")) {
            _uiState.value = DownloadUiState.Error("Enlace inválido. Debe comenzar con http:// o https://")
            return
        }

        _uiState.value = DownloadUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val quality = selectedQuality.value
                val request = if (quality == "audio") {
                    CobaltRequest(
                        url = targetUrl,
                        downloadMode = "audio",
                        isAudioOnly = true,
                        audioFormat = "mp3"
                    )
                } else {
                    CobaltRequest(
                        url = targetUrl,
                        videoQuality = quality,
                        downloadMode = "video"
                    )
                }

                val endpoint = customApiUrl.value
                val response = NetworkClient.cobaltService.getDownloadLink(endpoint, request)

                when (response.status) {
                    "success", "stream", "redirect", "tunnel" -> {
                        val directUrl = response.url
                        if (directUrl.isNullOrEmpty()) {
                            _uiState.value = DownloadUiState.Error("El servidor no devolvió un enlace de descarga válido.")
                            return@launch
                        }

                        val filename = response.filename ?: run {
                            val ext = if (quality == "audio") ".mp3" else ".mp4"
                            "vortex_${platform}_${System.currentTimeMillis()}$ext"
                        }

                        // Enqueue Download
                        val downloadId = enqueueDownload(context, directUrl, filename)

                        // Save to DB
                        val downloadItem = DownloadItem(
                            videoUrl = targetUrl,
                            downloadUrl = directUrl,
                            title = filename,
                            platform = platform,
                            quality = quality,
                            status = "DOWNLOADING",
                            downloadId = downloadId
                        )
                        repository.insert(downloadItem)

                        // Trigger visual progress tracking
                        viewModelScope.launch(Dispatchers.Main) {
                            startTrackingProgress(context)
                        }

                        urlInput.value = "" // clear input on success
                        _uiState.value = DownloadUiState.Success("Descarga agregada a la cola.")
                    }
                    "picker" -> {
                        // High quality multi-item slideshow (Photo carousel) or video list
                        val pickerItems = response.picker
                        if (pickerItems.isNullOrEmpty()) {
                            _uiState.value = DownloadUiState.Error("Carrusel vacío recibido del servidor.")
                            return@launch
                        }

                        var queuedCount = 0
                        pickerItems.forEachIndexed { idx, pickerItem ->
                            val itemUrl = pickerItem.url
                            val isVideo = pickerItem.type == "video"
                            val ext = if (isVideo) ".mp4" else ".jpg"
                            val itemFilename = (response.filename?.substringBeforeLast(".") ?: "vortex_picker_${platform}_${System.currentTimeMillis()}") + "_$idx$ext"

                            val downloadId = enqueueDownload(context, itemUrl, itemFilename)

                            val downloadItem = DownloadItem(
                                videoUrl = targetUrl,
                                downloadUrl = itemUrl,
                                title = itemFilename,
                                platform = platform,
                                quality = if (isVideo) "video" else "photo",
                                status = "DOWNLOADING",
                                downloadId = downloadId
                            )
                            repository.insert(downloadItem)
                            queuedCount++
                        }

                        // Also check if there's continuous audio to query
                        response.audio?.let { audioUrl ->
                            val audioFilename = response.audioFilename ?: "vortex_picker_audio_${System.currentTimeMillis()}.mp3"
                            val downloadId = enqueueDownload(context, audioUrl, audioFilename)
                            val downloadItem = DownloadItem(
                                videoUrl = targetUrl,
                                downloadUrl = audioUrl,
                                title = audioFilename,
                                platform = platform,
                                quality = "audio",
                                status = "DOWNLOADING",
                                downloadId = downloadId
                            )
                            repository.insert(downloadItem)
                            queuedCount++
                        }

                        viewModelScope.launch(Dispatchers.Main) {
                            startTrackingProgress(context)
                        }

                        urlInput.value = ""
                        _uiState.value = DownloadUiState.Success("$queuedCount elementos de carrusel agregados.")
                    }
                    "error" -> {
                        val message = response.text ?: "Ocurrió un error desconocido en el servidor de descarga."
                        _uiState.value = DownloadUiState.Error(message)
                    }
                    else -> {
                        _uiState.value = DownloadUiState.Error("Respuesta desconocida del servidor: ${response.status}")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val errorMsg = e.localizedMessage ?: "Revise su conexión a Internet o el enlace del video e intente nuevamente."
                _uiState.value = DownloadUiState.Error(errorMsg)
            }
        }
    }

    private fun enqueueDownload(context: Context, downloadUrl: String, filename: String): Long {
        val uri = Uri.parse(downloadUrl)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(uri)
            .setTitle(filename)
            .setDescription("Vortex Downloader - Guardando archivo...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        return downloadManager.enqueue(request)
    }
}

class DownloadViewModelFactory(private val repository: DownloadRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DownloadViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DownloadViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
