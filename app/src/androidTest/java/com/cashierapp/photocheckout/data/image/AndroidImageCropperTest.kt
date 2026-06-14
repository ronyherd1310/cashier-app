package com.cashierapp.photocheckout.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.recognizer.BoundingBox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

public class AndroidImageCropperTest {
    private val cropper = AndroidImageCropper()

    @Test
    public fun cropsNormalizedBoxWithPadding() =
        runBlocking {
            val image = capturedImage(width = 1000, height = 500)

            val crop =
                cropper.crop(
                    image,
                    BoundingBox(left = 0.2f, top = 0.2f, right = 0.4f, bottom = 0.6f),
                )

            assertNotNull(crop)
            crop!!
            assertEquals("image/jpeg", crop.mimeType)
            assertEquals(240, crop.width)
            assertEquals(240, crop.height)
            assertNotNull(BitmapFactory.decodeByteArray(crop.bytes, 0, crop.bytes.size))
        }

    @Test
    public fun paddingClampsToSourceEdges() =
        runBlocking {
            val image = capturedImage(width = 1000, height = 500)

            val crop =
                cropper.crop(
                    image,
                    BoundingBox(left = 0f, top = 0f, right = 0.1f, bottom = 0.2f),
                )

            assertNotNull(crop)
            crop!!
            assertEquals(110, crop.width)
            assertEquals(110, crop.height)
            assertTrue(crop.width <= image.width)
            assertTrue(crop.height <= image.height)
        }

    @Test
    public fun malformedBoxReturnsNull() =
        runBlocking {
            val image = capturedImage(width = 1000, height = 500)

            assertNull(cropper.crop(image, BoundingBox(left = 0.5f, top = 0.2f, right = 0.5f, bottom = 0.6f)))
            assertNull(cropper.crop(image, BoundingBox(left = 0.6f, top = 0.2f, right = 0.4f, bottom = 0.6f)))
        }

    @Test
    public fun undecodableImageReturnsNull() =
        runBlocking {
            val image = CapturedImage(bytes = byteArrayOf(1, 2, 3), width = 100, height = 100, mimeType = "image/jpeg")

            assertNull(cropper.crop(image, BoundingBox(left = 0f, top = 0f, right = 1f, bottom = 1f)))
        }

    private fun capturedImage(
        width: Int,
        height: Int,
    ): CapturedImage {
        val bytes = jpegBytes(width, height)
        return CapturedImage(bytes = bytes, width = width, height = height, mimeType = "image/jpeg")
    }

    private fun jpegBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF884422.toInt())
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }
}
