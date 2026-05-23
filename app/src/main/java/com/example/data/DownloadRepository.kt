package com.example.data

import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()

    suspend fun getDownloadById(id: Int): DownloadItem? {
        return downloadDao.getDownloadById(id)
    }

    suspend fun getDownloadByDownloadId(downloadId: Long): DownloadItem? {
        return downloadDao.getDownloadByDownloadId(downloadId)
    }

    suspend fun insert(item: DownloadItem): Long {
        return downloadDao.insertDownload(item)
    }

    suspend fun update(item: DownloadItem) {
        return downloadDao.updateDownload(item)
    }

    suspend fun delete(item: DownloadItem) {
        return downloadDao.deleteDownload(item)
    }

    suspend fun deleteById(id: Int) {
        return downloadDao.deleteDownloadById(id)
    }

    suspend fun clearAll() {
        return downloadDao.clearAllDownloads()
    }
}
