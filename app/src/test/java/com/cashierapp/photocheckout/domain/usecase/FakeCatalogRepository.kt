package com.cashierapp.photocheckout.domain.usecase

import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.model.ProductPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeCatalogRepository : CatalogRepository {
    private val products = MutableStateFlow<List<CatalogItem>>(emptyList())
    private var nextId = 1L

    override fun observeActiveProducts(): Flow<List<CatalogItem>> = products

    override suspend fun getById(id: Long): CatalogItem? = products.value.firstOrNull { it.id == id }

    override suspend fun getBySku(sku: String): CatalogItem? = products.value.firstOrNull { it.sku == sku }

    override suspend fun nextSkuSequence(): Long = products.value.size + 1L

    override suspend fun insert(
        sku: String,
        name: String,
        priceMinor: Long,
        photoPaths: List<String>,
        createdAt: Long,
    ): Long {
        val id = nextId++
        products.value =
            products.value +
            CatalogItem(
                id = id,
                sku = sku,
                name = name,
                priceMinor = priceMinor,
                active = true,
                photos = photoPaths.mapIndexed { index, path -> ProductPhoto(path, index) },
                createdAtEpochMillis = createdAt,
            )
        return id
    }

    override suspend fun update(product: CatalogItem) {
        products.value = products.value.map { if (it.id == product.id) product else it }
    }

    override suspend fun setActive(
        productId: Long,
        active: Boolean,
        deactivatedAt: Long?,
    ) {
        products.value =
            products.value.map {
                if (it.id == productId) {
                    it.copy(active = active, deactivatedAtEpochMillis = deactivatedAt)
                } else {
                    it
                }
            }
    }
}
