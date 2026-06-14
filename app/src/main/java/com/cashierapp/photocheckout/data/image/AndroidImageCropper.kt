package com.cashierapp.photocheckout.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cashierapp.photocheckout.data.recognizer.CROP_PADDING_FRACTION
import com.cashierapp.photocheckout.domain.image.ImageCropper
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.recognizer.BoundingBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.floor
import kotlin.math.roundToInt

private const val JPEG_QUALITY = 90
private const val MIME_JPEG = "image/jpeg"

public class AndroidImageCropper
    @Inject
    constructor() : ImageCropper {
        override suspend fun crop(
            image: CapturedImage,
            box: BoundingBox,
        ): CapturedImage? =
            withContext(Dispatchers.Default) {
                val source =
                    BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
                        ?: return@withContext null
                try {
                    val rect = box.toPaddedPixelRect(source.width, source.height) ?: return@withContext null
                    val crop =
                        Bitmap.createBitmap(
                            source,
                            rect.left,
                            rect.top,
                            rect.width,
                            rect.height,
                        )
                    val bytes =
                        ByteArrayOutputStream().use { stream ->
                            crop.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                            stream.toByteArray()
                        }
                    crop.recycle()
                    CapturedImage(bytes = bytes, width = rect.width, height = rect.height, mimeType = MIME_JPEG)
                } catch (_: IllegalArgumentException) {
                    null
                } finally {
                    source.recycle()
                }
            }

        private fun BoundingBox.toPaddedPixelRect(
            sourceWidth: Int,
            sourceHeight: Int,
        ): PixelRect? {
            if (left >= right || top >= bottom) {
                return null
            }
            val rawLeft = left * sourceWidth
            val rawTop = top * sourceHeight
            val rawRight = right * sourceWidth
            val rawBottom = bottom * sourceHeight
            val width = rawRight - rawLeft
            val height = rawBottom - rawTop
            if (width <= 0f || height <= 0f) {
                return null
            }

            val padX = width * CROP_PADDING_FRACTION
            val padY = height * CROP_PADDING_FRACTION
            val paddedLeft = floor(rawLeft - padX).roundToInt().coerceIn(0, sourceWidth)
            val paddedTop = floor(rawTop - padY).roundToInt().coerceIn(0, sourceHeight)
            val paddedRight = (rawRight + padX).roundToInt().coerceIn(0, sourceWidth)
            val paddedBottom = (rawBottom + padY).roundToInt().coerceIn(0, sourceHeight)
            if (paddedLeft >= paddedRight || paddedTop >= paddedBottom) {
                return null
            }
            return PixelRect(
                left = paddedLeft,
                top = paddedTop,
                width = paddedRight - paddedLeft,
                height = paddedBottom - paddedTop,
            )
        }

        private data class PixelRect(
            val left: Int,
            val top: Int,
            val width: Int,
            val height: Int,
        )
    }
