package com.cashierapp.photocheckout.domain.model

public data class CatalogItem(
    val id: Long,
    val sku: String,
    val name: String,
    val priceMinor: Long,
    val active: Boolean,
    val photos: List<ProductPhoto>,
    val createdAtEpochMillis: Long,
    val deactivatedAtEpochMillis: Long? = null,
    val description: String? = null,
    val confusionGroup: String? = null,
)
