package com.cashierapp.photocheckout.domain.usecase

import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.DraftReceipt
import com.cashierapp.photocheckout.domain.pricing.priceDraft
import com.cashierapp.photocheckout.domain.recognizer.Recognizer
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Assembles the scan pipeline: pull the active catalog, run the injected
 * [Recognizer], then price the result deterministically from the catalog. The
 * use case orchestrates but never decides prices — pricing is [priceDraft]'s job
 * and the catalog's, never the model's (SCAN-4, X-1).
 *
 * A recognizer failure maps to [Result.failure] with no partial draft (SCAN-9).
 */
public class ScanCounter
    @Inject
    constructor(
        private val catalogRepository: CatalogRepository,
        private val recognizer: Recognizer,
    ) {
        public suspend operator fun invoke(
            image: CapturedImage,
            taxRateBps: Int = 0,
        ): Result<DraftReceipt> {
            val catalog = catalogRepository.observeActiveProducts().first()
            return recognizer.recognize(image, catalog).map { recognized ->
                priceDraft(
                    recognized = recognized,
                    catalog = catalog.associateBy { it.sku },
                    taxRateBps = taxRateBps,
                )
            }
        }
    }
