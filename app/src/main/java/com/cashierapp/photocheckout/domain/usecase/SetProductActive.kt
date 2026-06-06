package com.cashierapp.photocheckout.domain.usecase

import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import javax.inject.Inject

public class SetProductActive
    @Inject
    constructor(
        private val catalogRepository: CatalogRepository,
    ) {
        public suspend operator fun invoke(
            productId: Long,
            active: Boolean,
            changedAt: Long,
        ) {
            catalogRepository.setActive(
                productId = productId,
                active = active,
                deactivatedAt = if (active) null else changedAt,
            )
        }
    }
