package com.cashierapp.photocheckout.domain.model

/**
 * An editable, deterministically-priced draft built from recognition output.
 * Every monetary field is integer IDR minor units (no floating-point money).
 */
public data class DraftReceipt(
    val lines: List<DraftLine>,
    val unidentified: List<UnidentifiedItem>,
    val subtotalMinor: Long,
    val taxMinor: Long,
    val totalMinor: Long,
)

/**
 * A single priced draft line. [unitPriceMinor]/[lineTotalMinor] come from the
 * catalog, never the recognizer. [confidence] is retained for per-line numeric
 * display; [lowConfidence] is the threshold-derived flag. [note] is display/edit
 * only and never affects pricing or recognition.
 */
public data class DraftLine(
    val sku: String,
    val name: String,
    val quantity: Int,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
    val confidence: Float,
    val lowConfidence: Boolean,
    val note: String? = null,
)

/**
 * A detection that did not map to an active catalog item. Surfaced to the cashier
 * for resolution — never silently dropped (SCAN-7).
 */
public data class UnidentifiedItem(
    val rawSku: String?,
    val quantity: Int,
    val confidence: Float,
)
