package com.cashierapp.photocheckout.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import com.cashierapp.photocheckout.domain.model.CapturedImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val THUMB_MAX_EDGE_PX = 256
private const val THUMB_JPEG_QUALITY = 70
private const val MIME_JPEG = "image/jpeg"
private const val DIRECTORY_NAME = "reference_thumbnails"

/**
 * Resolves enrolled product photos to small cached JPEG thumbnails for recognizer
 * prompt attachment (R1). Source read/decode failures return null so reference-photo
 * problems never fail a scan.
 */
@Singleton
public class ReferenceThumbnailStore
    @Inject
    constructor(
        private val photoStorage: PhotoStorage,
        @ApplicationContext context: Context,
    ) {
        private val cacheDirectory = File(context.filesDir, DIRECTORY_NAME)

        init {
            cacheDirectory.mkdirs()
        }

        public suspend fun thumbnailFor(photoPath: String): CapturedImage? =
            withContext(Dispatchers.Default) {
                cachedThumbnail(photoPath) ?: generateThumbnail(photoPath)
            }

        private fun cachedThumbnail(photoPath: String): CapturedImage? {
            val cacheFile = cacheFile(photoPath)
            if (!cacheFile.exists()) {
                return null
            }
            return cacheFile.readCapturedImageOrNull()
        }

        private fun generateThumbnail(photoPath: String): CapturedImage? {
            val sourceBytes =
                runCatching { photoStorage.read(photoPath) }
                    .getOrNull()
                    ?: return null
            val source =
                BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
                    ?: return null
            val scaled = source.scaledToMaxEdge()
            if (scaled !== source) {
                source.recycle()
            }
            val thumbnailBytes =
                ByteArrayOutputStream().use { stream ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, THUMB_JPEG_QUALITY, stream)
                    stream.toByteArray()
                }
            val result =
                CapturedImage(
                    bytes = thumbnailBytes,
                    width = scaled.width,
                    height = scaled.height,
                    mimeType = MIME_JPEG,
                )
            scaled.recycle()
            runCatching {
                val file = cacheFile(photoPath)
                file.parentFile?.mkdirs()
                file.writeBytes(thumbnailBytes)
            }
            return result
        }

        private fun Bitmap.scaledToMaxEdge(): Bitmap {
            val longest = maxOf(width, height)
            if (longest <= THUMB_MAX_EDGE_PX) {
                return this
            }
            val ratio = THUMB_MAX_EDGE_PX.toFloat() / longest
            val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
            val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        }

        private fun cacheFile(photoPath: String): File {
            require(!photoPath.contains("..")) { "Photo path must be relative." }
            return File(cacheDirectory, photoPath)
        }

        private fun File.readCapturedImageOrNull(): CapturedImage? {
            val bytes = runCatching { readBytes() }.getOrNull() ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val result = CapturedImage(bytes = bytes, width = bitmap.width, height = bitmap.height, mimeType = MIME_JPEG)
            bitmap.recycle()
            return result
        }
    }
