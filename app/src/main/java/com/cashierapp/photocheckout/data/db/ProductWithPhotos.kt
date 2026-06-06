package com.cashierapp.photocheckout.data.db

import androidx.room.Embedded
import androidx.room.Relation

public data class ProductWithPhotos(
    @Embedded
    val product: ProductEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "productId",
    )
    val photos: List<ProductPhotoEntity>,
)
