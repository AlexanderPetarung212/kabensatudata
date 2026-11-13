package com.example.kabensatudata.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages offline file storage for E-Arsip Surat.
 * Files are stored permanently in filesDir/arsip/{id}.{ext}
 * so they survive cache clearing by the OS.
 */
@Singleton
class ArsipOfflineManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ArsipOfflineManager"
        private const val DIR_NAME = "arsip"
    }

    private val arsipDir: File
        get() = File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    // ─── Query ───────────────────────────────────────────────────────────

    /**
     * Get the local file for a given arsip ID and extension.
     * Returns null if the file doesn't exist or is empty.
     */
    fun getLocalFile(arsipId: Long, extension: String?): File? {
        val ext = extension?.lowercase()?.takeIf { it.isNotEmpty() } ?: "pdf"
        val file = File(arsipDir, "${arsipId}.${ext}")
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Check if a file is cached locally.
     */
    fun isFileCached(arsipId: Long, extension: String?): Boolean {
        return getLocalFile(arsipId, extension) != null
    }

    // ─── Download & Save ─────────────────────────────────────────────────

    /**
     * Download a single file from URL and save locally.
     * Returns the saved File on success, null on failure.
     */
    suspend fun downloadAndSave(
        arsipId: Long,
        downloadUrl: String,
        extension: String?,
        token: String?,
        isGDrive: Boolean = false
    ): File? = withContext(Dispatchers.IO) {
        try {
            val ext = extension?.lowercase()?.takeIf { it.isNotEmpty() } ?: "pdf"
            val targetFile = File(arsipDir, "${arsipId}.${ext}")

            // Skip if already cached
            if (targetFile.exists() && targetFile.length() > 0) {
                Log.d(TAG, "Already cached: ${targetFile.name}")
                return@withContext targetFile
            }

            Log.d(TAG, "Downloading arsip $arsipId from $downloadUrl")

            val conn = URL(downloadUrl).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            if (!isGDrive && !token.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/octet-stream")
            }
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.connect()

            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Saved: ${targetFile.name} (${targetFile.length()} bytes)")
                conn.disconnect()
                return@withContext targetFile
            } else {
                Log.w(TAG, "Download failed for arsip $arsipId: HTTP ${conn.responseCode}")
                conn.disconnect()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading arsip $arsipId", e)
            return@withContext null
        }
    }

    /**
     * Sync all files from a list of arsip items.
     * Downloads only files that aren't cached yet.
     * Returns the number of newly downloaded files.
     */
    suspend fun syncAllFiles(
        items: List<ArsipFileInfo>,
        token: String?
    ): Int = withContext(Dispatchers.IO) {
        var downloadedCount = 0
        for (item in items) {
            if (item.downloadUrl == null) continue
            if (isFileCached(item.id, item.fileExtension)) continue

            val result = downloadAndSave(
                arsipId = item.id,
                downloadUrl = item.downloadUrl,
                extension = item.fileExtension,
                token = token,
                isGDrive = item.isGDrive
            )
            if (result != null) downloadedCount++
        }
        Log.i(TAG, "Sync complete: $downloadedCount new files downloaded")
        downloadedCount
    }

    /**
     * Get total cached file size in bytes.
     */
    fun getTotalCacheSize(): Long {
        return arsipDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Clear all cached arsip files.
     */
    fun clearAll() {
        arsipDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "All cached arsip files cleared")
    }
}

/**
 * Simple data class for file sync info.
 */
data class ArsipFileInfo(
    val id: Long,
    val downloadUrl: String?,
    val fileExtension: String?,
    val isGDrive: Boolean = false
)
