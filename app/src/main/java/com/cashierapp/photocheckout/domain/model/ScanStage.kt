package com.cashierapp.photocheckout.domain.model

/**
 * The stages the scan pipeline walks through, in order. Drives the staged
 * processing overlay (mockup 08): sending image → recognizing items → building
 * draft. Pure Kotlin so both the use case (emit) and the UI (render) can see it.
 */
public enum class ScanStage {
    Uploading,
    Recognizing,
    Pricing,
}
