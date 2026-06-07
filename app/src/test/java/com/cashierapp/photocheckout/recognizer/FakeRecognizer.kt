package com.cashierapp.photocheckout.recognizer

import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import com.cashierapp.photocheckout.domain.recognizer.Recognizer

/**
 * A canned [Recognizer] that drives the capture→draft→edit flow in tests with no
 * network or live model (SCAN-10). Configure it with a fixed result or an error
 * to exercise happy-path, wrong-qty, low-confidence, unidentified, and
 * error/timeout modes (E2E-2/E2E-5).
 */
public class FakeRecognizer(
    private var result: Result<List<RecognizedItem>> = Result.success(emptyList()),
) : Recognizer {
    public var lastImage: CapturedImage? = null
        private set

    public var lastCatalog: List<CatalogItem>? = null
        private set

    public fun returns(items: List<RecognizedItem>) {
        result = Result.success(items)
    }

    public fun fails(error: Throwable) {
        result = Result.failure(error)
    }

    override suspend fun recognize(
        image: CapturedImage,
        catalog: List<CatalogItem>,
    ): Result<List<RecognizedItem>> {
        lastImage = image
        lastCatalog = catalog
        return result
    }
}
