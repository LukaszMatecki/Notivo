package com.lumatech.notivo.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import android.widget.ImageView
import androidx.core.view.doOnAttach
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Handles importing gallery photos into app-private storage (downsampled + EXIF-oriented)
 * and async LRU-cached thumbnail loading for notes and cards.
 */
object NoteImageStorage {

    private const val IMAGES_DIR = "note_images"
    private const val MAX_IMPORT_DIMENSION = 1440
    private const val JPEG_QUALITY = 85

    private val bitmapCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun importFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = resolver.openInputStream(uri) ?: error("Cannot open selected image")
            boundsStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                error("Invalid image bounds")
            }

            val orientation = resolver.openInputStream(uri)?.use { stream ->
                runCatching {
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val sampleSize = calculateInSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                MAX_IMPORT_DIMENSION,
                MAX_IMPORT_DIMENSION
            )
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val decodeStream = resolver.openInputStream(uri) ?: error("Cannot reopen selected image")
            val decoded = decodeStream.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: error("Cannot decode selected image")

            val oriented = applyExifOrientation(decoded, orientation)
            if (oriented !== decoded) {
                decoded.recycle()
            }

            val dir = File(context.filesDir, IMAGES_DIR).apply { mkdirs() }
            val destFile = File(dir, "img_${UUID.randomUUID()}.jpg")
            FileOutputStream(destFile).use { out ->
                oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            oriented.recycle()
            destFile.absolutePath
        }
    }

    fun loadInto(imageView: ImageView, path: String, targetWidthPx: Int = 720, targetHeightPx: Int = 720) {
        val cacheKey = "$path@$targetWidthPx"
        imageView.tag = cacheKey
        val cached = bitmapCache.get(cacheKey)
        if (cached != null && !cached.isRecycled) {
            imageView.setImageBitmap(cached)
            return
        }

        imageView.setImageDrawable(null)
        imageView.doOnAttach { attachedView ->
            val scope = attachedView.findViewTreeLifecycleOwner()?.lifecycleScope ?: return@doOnAttach
            scope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    decodeSampledFile(path, targetWidthPx, targetHeightPx)
                }
                if (decoded != null) {
                    bitmapCache.put(cacheKey, decoded)
                    if (imageView.tag == cacheKey) {
                        imageView.setImageBitmap(decoded)
                    }
                }
            }
        }
    }

    fun deleteFileQuietly(path: String) {
        runCatching {
            val file = File(path)
            if (file.exists() && file.parentFile?.name == IMAGES_DIR) {
                file.delete()
            }
        }
    }

    private fun decodeSampledFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
