package com.cashierapp.photocheckout.domain.catalog

public object SkuGenerator {
    public fun generate(sequenceNumber: Long): String {
        require(sequenceNumber > 0) { "SKU sequence must be positive." }

        return "SKU-${sequenceNumber.toString().padStart(length = 4, padChar = '0')}"
    }
}
