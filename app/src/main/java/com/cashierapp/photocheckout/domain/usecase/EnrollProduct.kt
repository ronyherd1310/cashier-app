package com.cashierapp.photocheckout.domain.usecase

import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import com.cashierapp.photocheckout.domain.catalog.SkuGenerator
import javax.inject.Inject

public class EnrollProduct
    @Inject
    constructor(
        private val catalogRepository: CatalogRepository,
    ) {
        public suspend operator fun invoke(input: EnrollProductInput): Long {
            val name = input.name.trim()
            require(name.isNotEmpty()) { "Product name is required." }
            require(input.priceMinor > 0L) { "Product price must be greater than zero." }
            require(input.photoPaths.isNotEmpty()) { "At least one reference photo is required." }

            val sku = SkuGenerator.generate(catalogRepository.nextSkuSequence())
            return catalogRepository.insert(
                sku = sku,
                name = name,
                priceMinor = input.priceMinor,
                photoPaths = input.photoPaths,
                createdAt = input.createdAt,
            )
        }
    }

public data class EnrollProductInput(
    val name: String,
    val priceMinor: Long,
    val photoPaths: List<String>,
    val createdAt: Long,
)
