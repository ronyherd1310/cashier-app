package com.cashierapp.photocheckout.domain.recognizer

import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem

/**
 * The single recognition seam every engine implements. Implementations identify
 * catalog items present in an image; pricing is never this layer's job — the
 * recognizer returns identity + quantity + confidence + optional evidence only,
 * never a price.
 *
 * Vendor/DTO/model-id code lives only in `data/recognizer/`.
 */
public interface Recognizer {
    /** Identify catalog items present in [image]. Pricing is NOT this layer's job. */
    public suspend fun recognize(
        image: CapturedImage,
        catalog: List<CatalogItem>,
    ): Result<List<RecognizedItem>>
}

/** A single recognized detection: identity + quantity + confidence (+ optional evidence). */
public data class RecognizedItem(
    val sku: String?,
    val quantity: Int,
    val confidence: Float,
    val boundingBox: BoundingBox? = null,
    val occluded: Boolean = false,
    val possiblyMore: Boolean = false,
    val alternates: List<String> = emptyList(),
)

/** Normalized 0f..1f bounding box of a detection within the captured image. */
public data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Below this confidence, a draft line is flagged for cashier review. Tune from L6. */
public const val CONFIDENCE_THRESHOLD: Float = 0.6f
