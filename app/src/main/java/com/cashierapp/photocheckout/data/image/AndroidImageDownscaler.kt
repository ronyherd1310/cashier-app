package com.cashierapp.photocheckout.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cashierapp.photocheckout.domain.image.ImageDownscaler
import com.cashierapp.photocheckout.domain.model.CapturedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.roundToInt

private const val MAX_EDGE_PX = 1024
private const val JPEG_QUALITY = 80
private const val MIME_JPEG = "image/jpeg"

/**
 * Bitmap-backed [ImageDownscaler]: scales the longest edge to [MAX_EDGE_PX] and
 * re-encodes as JPEG at [JPEG_QUALITY] before upload (SCAN-2, decision D2). Decode
 * and compression run off the main thread.
 */
public class AndroidImageDownscaler
    @Inject
    constructor() : ImageDownscaler {
        override suspend fun downscale(jpegBytes: ByteArray): CapturedImage =
            withContext(Dispatchers.Default) {
                val source =
                    BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                        ?: return@withContext CapturedImage(jpegBytes, width = 0, height = 0, mimeType = MIME_JPEG)
                val scaled = source.scaledToMaxEdge()
                if (scaled !== source) {
                    source.recycle()
                }
                val bytes =
                    ByteArrayOutputStream().use { stream ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                        stream.toByteArray()
                    }
                val result = CapturedImage(bytes = bytes, width = scaled.width, height = scaled.height, mimeType = MIME_JPEG)
                scaled.recycle()
                result
            }

        private fun Bitmap.scaledToMaxEdge(): Bitmap {
            val longest = maxOf(width, height)
            if (longest <= MAX_EDGE_PX) {
                return this
            }
            val ratio = MAX_EDGE_PX.toFloat() / longest
            val targetWidth = (width * ratio).roundToInt().coerceAtLeast(1)
            val targetHeight = (height * ratio).roundToInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        }
    }
