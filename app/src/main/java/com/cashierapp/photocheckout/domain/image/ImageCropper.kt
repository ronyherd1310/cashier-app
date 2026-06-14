package com.cashierapp.photocheckout.domain.image

import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.recognizer.BoundingBox

/**
 * Crops a normalized detection box from an encoded capture. Pure-Kotlin contract
 * so Android Bitmap details stay in `data/`.
 */
public interface ImageCropper {
    /** Return a JPEG crop of [box] from [image], or null when decoding/cropping fails. */
    public suspend fun crop(
        image: CapturedImage,
        box: BoundingBox,
    ): CapturedImage?
}
