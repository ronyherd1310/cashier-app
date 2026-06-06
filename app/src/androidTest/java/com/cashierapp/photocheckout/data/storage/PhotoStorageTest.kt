package com.cashierapp.photocheckout.data.storage

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class PhotoStorageTest {
    @Test
    public fun saveReadAndDeletePhotoBytes() {
        val storage = PhotoStorage(ApplicationProvider.getApplicationContext())
        val bytes = byteArrayOf(1, 2, 3, 4)

        val path = storage.save(bytes, fileName = "product.jpg")

        assertArrayEquals(bytes, storage.read(path))
        assertTrue(storage.delete(path))
        assertFalse(storage.exists(path))
    }
}
