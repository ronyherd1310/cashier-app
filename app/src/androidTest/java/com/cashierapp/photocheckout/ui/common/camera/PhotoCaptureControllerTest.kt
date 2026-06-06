package com.cashierapp.photocheckout.ui.common.camera

import androidx.test.core.app.ApplicationProvider
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class PhotoCaptureControllerTest {
    @Test
    public fun fakeImageSourceCapturesAndPersistsPhoto() =
        runTest {
            val bytes = byteArrayOf(9, 8, 7, 6)
            val storage = PhotoStorage(ApplicationProvider.getApplicationContext())
            val controller =
                PhotoCaptureController(
                    imageSource = FakeImageSource(bytes),
                    photoStorage = storage,
                )

            val path = controller.capture(fileName = "reference.jpg")

            assertTrue(storage.exists(path))
            assertArrayEquals(bytes, storage.read(path))
        }

    private class FakeImageSource(
        private val bytes: ByteArray,
    ) : ImageSource {
        override suspend fun capture(): ByteArray = bytes
    }
}
