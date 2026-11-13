package com.example.kabensatudata.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageCompressor {
    /**
     * Compresses an image from the given Uri to a ByteArray containing a JPEG.
     * Downscales the image so its longest side is at most [maxDimension],
     * and compresses it with the given [quality].
     */
    fun compressImage(context: Context, uri: Uri, maxDimension: Int = 1024, quality: Int = 70): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ByteArray(0)
        
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream.close()

        // Calculate inSampleSize
        var inSampleSize = 1
        val height = options.outHeight
        val width = options.outWidth
        if (height > maxDimension || width > maxDimension) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                inSampleSize *= 2
            }
        }

        // Decode bitmap with inSampleSize set
        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val inputStream2 = context.contentResolver.openInputStream(uri) ?: return ByteArray(0)
        val bitmap = BitmapFactory.decodeStream(inputStream2, null, decodeOptions)
        inputStream2.close()

        if (bitmap == null) return ByteArray(0)

        // Compress
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        
        // Clean up
        bitmap.recycle()
        
        return outputStream.toByteArray()
    }
}
