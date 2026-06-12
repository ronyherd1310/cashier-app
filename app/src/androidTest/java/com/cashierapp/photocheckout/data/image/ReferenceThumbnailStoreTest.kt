package com.cashierapp.photocheckout.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

public class ReferenceThumbnailStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var storage: PhotoStorage
    private lateinit var store: ReferenceThumbnailStore

    @Before
    public fun setUp() {
        File(context.filesDir, "product_photos").deleteRecursively()
        File(context.filesDir, "reference_thumbnails").deleteRecursively()
        storage = PhotoStorage(context)
        store = ReferenceThumbnailStore(storage, context)
    }

    @Test
    public fun generatesThumbnailWithinMaxEdgeAndCachesIt() =
        runBlocking {
            val path = storage.save(jpegBytes(width = 900, height = 300), fileName = "wide.jpg")

            val first = store.thumbnailFor(path)

            assertNotNull(first)
            first!!
            assertEquals("image/jpeg", first.mimeType)
            assertTrue(maxOf(first.width, first.height) <= 256)
            val decoded = BitmapFactory.decodeByteArray(first.bytes, 0, first.bytes.size)
            assertNotNull(decoded)
            assertTrue(maxOf(decoded.width, decoded.height) <= 256)

            assertTrue(storage.delete(path))
            val second = store.thumbnailFor(path)

            assertNotNull(second)
            assertArrayEquals(first.bytes, second!!.bytes)
        }

    @Test
    public fun returnsNullForMissingOrCorruptSources() =
        runBlocking {
            val corruptPath = storage.save(byteArrayOf(1, 2, 3), fileName = "corrupt.jpg")

            assertNull(store.thumbnailFor("missing.jpg"))
            assertNull(store.thumbnailFor(corruptPath))
        }

    @Test
    public fun distinctSourcePathsCreateDistinctCacheFiles() =
        runBlocking {
            val firstPath = storage.save(jpegBytes(width = 500, height = 300, color = 0xFFFF0000.toInt()), "first.jpg")
            val secondPath = storage.save(jpegBytes(width = 500, height = 300, color = 0xFF0000FF.toInt()), "second.jpg")

            val first = store.thumbnailFor(firstPath)
            val second = store.thumbnailFor(secondPath)

            assertNotNull(first)
            assertNotNull(second)
            assertNotEquals(firstPath, secondPath)
            assertFalse(first!!.bytes.contentEquals(second!!.bytes))
            assertTrue(File(context.filesDir, "reference_thumbnails/$firstPath").exists())
            assertTrue(File(context.filesDir, "reference_thumbnails/$secondPath").exists())
        }

    private fun jpegBytes(
        width: Int,
        height: Int,
        color: Int = 0xFF00AA00.toInt(),
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }
}
