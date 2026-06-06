package com.cashierapp.photocheckout.domain.usecase

import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import com.cashierapp.photocheckout.domain.model.ProductPhoto
import javax.inject.Inject

public class UpdateProduct
    @Inject
    constructor(
        private val catalogRepository: CatalogRepository,
    ) {
        public suspend operator fun invoke(input: UpdateProductInput) {
            val existing =
                requireNotNull(catalogRepository.getById(input.productId)) {
                    "Product not found."
                }
            val name = input.name.trim()
            require(name.isNotEmpty()) { "Product name is required." }
            require(input.priceMinor > 0L) { "Product price must be greater than zero." }
            require(input.photoPaths.isNotEmpty()) { "At least one reference photo is required." }
            require(input.photoPaths.size <= MAX_PHOTOS) { "A product can have at most 3 photos." }

            catalogRepository.update(
                existing.copy(
                    name = name,
                    priceMinor = input.priceMinor,
                    photos =
                        input.photoPaths.mapIndexed { index, path ->
                            ProductPhoto(path = path, position = index)
                        },
                ),
            )
        }

        private companion object {
            const val MAX_PHOTOS = 3
        }
    }

public data class UpdateProductInput(
    val productId: Long,
    val name: String,
    val priceMinor: Long,
    val photoPaths: List<String>,
)
