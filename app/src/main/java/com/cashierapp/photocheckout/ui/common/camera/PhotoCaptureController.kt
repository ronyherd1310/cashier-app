package com.cashierapp.photocheckout.ui.common.camera

import com.cashierapp.photocheckout.data.storage.PhotoStorage

public class PhotoCaptureController(
    private val imageSource: ImageSource,
    private val photoStorage: PhotoStorage,
) {
    public suspend fun capture(fileName: String = "reference.jpg"): String =
        photoStorage.save(
            bytes = imageSource.capture(),
            fileName = fileName,
        )
}
