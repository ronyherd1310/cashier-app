package com.cashierapp.photocheckout.domain.pricing

import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.model.DraftLine
import com.cashierapp.photocheckout.domain.model.DraftReceipt
import com.cashierapp.photocheckout.domain.model.UnidentifiedItem
import com.cashierapp.photocheckout.domain.recognizer.CONFIDENCE_THRESHOLD
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem

/**
 * Deterministic pricing: builds a [DraftReceipt] from recognition output. Unit
 * prices and line totals come strictly from [catalog]; the model's payload never
 * reaches this function as a price (X-1, SCAN-4). Money stays in integer IDR
 * minor units throughout — no floating-point.
 *
 * - A recognized SKU absent from [catalog] becomes an [UnidentifiedItem] — never
 *   dropped or zero-priced (SCAN-7).
 * - Confidence below [CONFIDENCE_THRESHOLD] flags the line (SCAN-6).
 * - Quantities are clamped to >= 1.
 *
 * @param taxRateBps tax in basis points (1/100 of a percent); 0 = no tax (MVP).
 */
public fun priceDraft(
    recognized: List<RecognizedItem>,
    catalog: Map<String, CatalogItem>,
    taxRateBps: Int = 0,
): DraftReceipt {
    val lines = mutableListOf<DraftLine>()
    val unidentified = mutableListOf<UnidentifiedItem>()

    for (detection in recognized) {
        val quantity = detection.quantity.coerceAtLeast(1)
        val item = catalog[detection.sku]
        if (item == null) {
            unidentified +=
                UnidentifiedItem(
                    rawSku = detection.sku,
                    quantity = quantity,
                    confidence = detection.confidence,
                )
        } else {
            lines +=
                DraftLine(
                    sku = item.sku,
                    name = item.name,
                    quantity = quantity,
                    unitPriceMinor = item.priceMinor,
                    lineTotalMinor = item.priceMinor * quantity,
                    confidence = detection.confidence,
                    lowConfidence = detection.confidence < CONFIDENCE_THRESHOLD,
                    photoPath = item.photos.firstOrNull()?.path,
                )
        }
    }

    val subtotal = lines.sumOf { it.lineTotalMinor }
    val tax = subtotal * taxRateBps / 10_000
    return DraftReceipt(
        lines = lines,
        unidentified = unidentified,
        subtotalMinor = subtotal,
        taxMinor = tax,
        totalMinor = subtotal + tax,
    )
}
