package com.cashierapp.photocheckout.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

public class AndroidImageDownscalerTest {
    private val downscaler = AndroidImageDownscaler()

    @Test
    public fun downscaledImageCarriesOriginalSource() =
        runBlocking {
            val sourceBytes = jpegBytes(width = 1800, height = 900)

            val image = downscaler.downscale(sourceBytes)

            assertTrue(maxOf(image.width, image.height) <= 1024)
            val original = image.original
            assertNotNull(original)
            original!!
            assertEquals(1800, original.width)
            assertEquals(900, original.height)
            assertEquals("image/jpeg", original.mimeType)
            assertTrue(sourceBytes.contentEquals(original.bytes))
            assertNotNull(BitmapFactory.decodeByteArray(original.bytes, 0, original.bytes.size))
        }

    @Test
    public fun smallImageStillCarriesOriginalSource() =
        runBlocking {
            val sourceBytes = jpegBytes(width = 640, height = 480)

            val image = downscaler.downscale(sourceBytes)

            assertEquals(640, image.width)
            assertEquals(480, image.height)
            val original = image.original
            assertNotNull(original)
            original!!
            assertEquals(640, original.width)
            assertEquals(480, original.height)
            assertTrue(sourceBytes.contentEquals(original.bytes))
        }

    @Test
    public fun corruptImageReturnsEmptyDimensionsWithoutOriginal() =
        runBlocking {
            val image = downscaler.downscale(byteArrayOf(1, 2, 3))

            assertEquals(0, image.width)
            assertEquals(0, image.height)
            assertNull(image.original)
        }

    private fun jpegBytes(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF336699.toInt())
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }
}
