package com.cashierapp.photocheckout.domain.image

import com.cashierapp.photocheckout.domain.model.CapturedImage

/**
 * Downscales an encoded capture before it is handed to the [Recognizer] to control
 * cost and latency (SCAN-2). A port so the Android Bitmap impl lives in `data/`
 * and tests can substitute a fake. Pure-Kotlin contract — no Android types here.
 */
public interface ImageDownscaler {
    /** Downscale [jpegBytes] and return the reduced-size [CapturedImage]. */
    public suspend fun downscale(jpegBytes: ByteArray): CapturedImage
}
