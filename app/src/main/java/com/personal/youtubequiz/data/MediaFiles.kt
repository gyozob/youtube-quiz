package com.personal.youtubequiz.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.graphics.scale
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Owns everything on disk. Images picked by the user are copied into internal
 * storage (so the app keeps working if the original is deleted or moved) and
 * downscaled, because the overlay only ever shows three of them at once and a
 * full-resolution camera photo would be wasteful to decode on a timer.
 */
class MediaFiles(private val context: Context) {

    val imagesDir: File = File(context.filesDir, "images").apply { mkdirs() }
    val thumbsDir: File = File(context.filesDir, "thumbs").apply { mkdirs() }
    val audioDir: File = File(context.filesDir, "audio").apply { mkdirs() }

    /** Copies [uri] into internal storage. Returns full-size and thumbnail paths. */
    fun importImage(uri: Uri): Pair<String, String>? {
        val name = UUID.randomUUID().toString()
        val full = File(imagesDir, "$name.jpg")
        val thumb = File(thumbsDir, "$name.jpg")

        // Anything thrown in here -- an unreadable URI, a revoked permission,
        // out of memory on a huge photo -- must be reported, not swallowed:
        // a silent null looks identical to "nothing was selected".
        return try {
            val bitmap = decodeScaled(uri, MAX_IMAGE_PX) ?: return null
            val oriented = applyExifRotation(uri, bitmap)
            try {
                writeJpeg(oriented, full, FULL_QUALITY)
                writeJpeg(scaleTo(oriented, MAX_THUMB_PX), thumb, THUMB_QUALITY)
                full.absolutePath to thumb.absolutePath
            } finally {
                oriented.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "failed to import $uri", e)
            full.delete()
            thumb.delete()
            null
        }
    }

    fun newAudioFile(prefix: String): File =
        File(audioDir, "$prefix-${UUID.randomUUID()}.m4a")

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    private fun decodeScaled(uri: Uri, maxPx: Int): Bitmap? {
        // Two passes: read the dimensions first so the real decode can
        // subsample instead of allocating the full image.
        //
        // Note the two-step read: with inJustDecodeBounds, decodeStream always
        // returns null and reports the size through the Options object, so the
        // stream must be null-checked on its own rather than through the
        // decode result.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = context.contentResolver.openInputStream(uri)
        if (boundsStream == null) {
            Log.w(TAG, "cannot open $uri")
            return null
        }
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "not a decodable image: $uri")
            return null
        }

        var sample = 1
        while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) {
            sample *= 2
        }

        val pixelStream = context.contentResolver.openInputStream(uri)
        if (pixelStream == null) {
            Log.w(TAG, "cannot re-open $uri for decoding")
            return null
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = pixelStream.use { BitmapFactory.decodeStream(it, null, options) }
        if (decoded == null) {
            Log.w(TAG, "decode failed for $uri (${bounds.outWidth}x${bounds.outHeight})")
            return null
        }

        return scaleTo(decoded, maxPx)
    }

    private fun scaleTo(source: Bitmap, maxPx: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxPx) return source
        val ratio = maxPx.toFloat() / longest
        val scaled = source.scale(
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
        )
        if (scaled !== source) source.recycle()
        return scaled
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: return bitmap

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun writeJpeg(bitmap: Bitmap, target: File, quality: Int) {
        FileOutputStream(target).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    companion object {
        private const val TAG = "YTQuiz"
        private const val MAX_IMAGE_PX = 1080
        private const val MAX_THUMB_PX = 320
        private const val FULL_QUALITY = 88
        private const val THUMB_QUALITY = 80
    }
}
