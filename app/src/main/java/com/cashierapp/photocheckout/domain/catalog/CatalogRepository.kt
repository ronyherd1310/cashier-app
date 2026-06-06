package com.cashierapp.photocheckout.domain.catalog

import com.cashierapp.photocheckout.domain.model.CatalogItem
import kotlinx.coroutines.flow.Flow

public interface CatalogRepository {
    public fun observeActiveProducts(): Flow<List<CatalogItem>>

    public fun observeProducts(): Flow<List<CatalogItem>>

    public suspend fun getById(id: Long): CatalogItem?

    public suspend fun getBySku(sku: String): CatalogItem?

    public suspend fun nextSkuSequence(): Long

    public suspend fun insert(
        sku: String,
        name: String,
        priceMinor: Long,
        photoPaths: List<String>,
        createdAt: Long,
    ): Long

    public suspend fun update(product: CatalogItem)

    public suspend fun setActive(
        productId: Long,
        active: Boolean,
        deactivatedAt: Long?,
    )
}
