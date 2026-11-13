package com.example.kabensatudata.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Helper untuk download file arsip dan membukanya di aplikasi eksternal
 * menggunakan Intent ACTION_VIEW + FileProvider.
 */
object ArsipFileHelper {

    private const val TAG = "ArsipFileHelper"

    // ─── MIME Type Detection ──────────────────────────────────────────────────

    fun getMimeType(filename: String): String = when {
        filename.endsWith(".pdf", true)  -> "application/pdf"
        filename.endsWith(".doc", true)  -> "application/msword"
        filename.endsWith(".docx", true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        filename.endsWith(".xls", true)  -> "application/vnd.ms-excel"
        filename.endsWith(".xlsx", true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        filename.endsWith(".ppt", true)  -> "application/vnd.ms-powerpoint"
        filename.endsWith(".pptx", true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        filename.endsWith(".jpg", true)  -> "image/jpeg"
        filename.endsWith(".jpeg", true) -> "image/jpeg"
        filename.endsWith(".png", true)  -> "image/png"
        filename.endsWith(".gif", true)  -> "image/gif"
        filename.endsWith(".txt", true)  -> "text/plain"
        filename.endsWith(".csv", true)  -> "text/csv"
        filename.endsWith(".zip", true)  -> "application/zip"
        else -> "*/*"
    }

    /**
     * Deteksi ekstensi file dari URL atau nama.
     * Prioritaskan ekstensi dari nama asli jika ada.
     */
    fun getExtensionFromUrl(url: String, fallbackName: String? = null): String {
        // Coba dapat dari nama file di URL
        val urlPath = url.substringBefore("?").substringAfterLast("/")
        val urlExt = urlPath.substringAfterLast(".", "")
        if (urlExt.length in 2..5 && urlExt.all { it.isLetter() }) return urlExt

        // Fallback dari nama
        val nameExt = fallbackName?.substringAfterLast(".", "") ?: ""
        if (nameExt.length in 2..5) return nameExt

        return "pdf" // default
    }

    /**
     * Sanitasi nama file agar aman disimpan di filesystem.
     */
    fun sanitizeFilename(name: String): String {
        val cleaned = name.replace(Regex("[^a-zA-Z0-9_\\-.]"), "_")
            .trim('_')
            .take(100)
        return cleaned.ifBlank { "arsip" }
    }

    // ─── Download & Open ──────────────────────────────────────────────────────

    /**
     * Download file dari URL ke direktori cache app, lalu buka dengan app lain.
     * Jika sudah ter-cache dan lastModified < 24 jam, gunakan cache.
     *
     * @param context   Context
     * @param url       URL download file (authenticated atau GDrive direct)
     * @param perihal   Judul/nama arsip untuk nama file
     * @param token     Bearer token (null jika tidak perlu auth, misal GDrive)
     * @return          Result: true = sukses, false = gagal
     */
    suspend fun downloadAndOpen(
        context: Context,
        url: String,
        perihal: String,
        token: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val ext = getExtensionFromUrl(url, perihal)
            val safeName = sanitizeFilename(perihal) + ".$ext"
            val cacheDir = File(context.cacheDir, "arsip").also { it.mkdirs() }
            val cacheFile = File(cacheDir, safeName)

            // Gunakan cache jika masih fresh (< 24 jam)
            val cacheAge = System.currentTimeMillis() - cacheFile.lastModified()
            if (cacheFile.exists() && cacheFile.length() > 0 && cacheAge < 24 * 60 * 60 * 1000L) {
                Log.d(TAG, "Using cached file: ${cacheFile.name}")
                return@withContext Result.success(cacheFile)
            }

            // Download dari server
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .build()

            val reqBuilder = Request.Builder().url(url)
            if (token != null) reqBuilder.addHeader("Authorization", "Bearer $token")

            val req = reqBuilder.build()
            val response = client.newCall(req).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download gagal: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Response body kosong"))

            FileOutputStream(cacheFile).use { out ->
                body.byteStream().use { input -> input.copyTo(out) }
            }

            Log.d(TAG, "Downloaded to: ${cacheFile.absolutePath} (${cacheFile.length()} bytes)")
            Result.success(cacheFile)
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }

    /**
     * Buka file di aplikasi eksternal dengan dialog "Buka dengan..."
     */
    fun openWithExternalApp(context: Context, file: File) {
        try {
            val mimeType = getMimeType(file.name)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Buka dengan...").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open file with external app", e)
        }
    }
}
