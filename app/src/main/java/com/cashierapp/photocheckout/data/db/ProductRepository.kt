package com.cashierapp.photocheckout.data.db

import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.model.ProductPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public class ProductRepository(
    private val dao: ProductDao,
) : CatalogRepository {
    override fun observeActiveProducts(): Flow<List<CatalogItem>> =
        dao.observeActiveProducts().map { products ->
            products.map(ProductWithPhotos::toDomain)
        }

    override fun observeProducts(): Flow<List<CatalogItem>> =
        dao.observeProducts().map { products ->
            products.map(ProductWithPhotos::toDomain)
        }

    override suspend fun getById(id: Long): CatalogItem? = dao.getById(id)?.toDomain()

    override suspend fun getBySku(sku: String): CatalogItem? = dao.getBySku(sku)?.toDomain()

    override suspend fun nextSkuSequence(): Long = dao.nextSkuSequence()

    override suspend fun insert(
        sku: String,
        name: String,
        priceMinor: Long,
        photoPaths: List<String>,
        createdAt: Long,
    ): Long {
        val productId =
            dao.insertProduct(
                ProductEntity(
                    sku = sku,
                    name = name,
                    priceMinor = priceMinor,
                    active = true,
                    createdAt = createdAt,
                    deactivatedAt = null,
                ),
            )
        dao.insertPhotos(
            photoPaths.mapIndexed { index, path ->
                ProductPhotoEntity(productId = productId, path = path, position = index)
            },
        )
        return productId
    }

    override suspend fun update(product: CatalogItem) {
        dao.updateProduct(
            ProductEntity(
                id = product.id,
                sku = product.sku,
                name = product.name,
                priceMinor = product.priceMinor,
                active = product.active,
                createdAt = product.createdAtEpochMillis,
                deactivatedAt = product.deactivatedAtEpochMillis,
            ),
        )
        dao.deletePhotosForProduct(product.id)
        dao.insertPhotos(
            product.photos.mapIndexed { index, photo ->
                ProductPhotoEntity(productId = product.id, path = photo.path, position = index)
            },
        )
    }

    override suspend fun setActive(
        productId: Long,
        active: Boolean,
        deactivatedAt: Long?,
    ) {
        dao.setActive(productId = productId, active = active, deactivatedAt = deactivatedAt)
    }
}

private fun ProductWithPhotos.toDomain(): CatalogItem =
    CatalogItem(
        id = product.id,
        sku = product.sku,
        name = product.name,
        priceMinor = product.priceMinor,
        active = product.active,
        photos =
            photos
                .sortedBy(ProductPhotoEntity::position)
                .map { photo -> ProductPhoto(path = photo.path, position = photo.position) },
        createdAtEpochMillis = product.createdAt,
        deactivatedAtEpochMillis = product.deactivatedAt,
    )
