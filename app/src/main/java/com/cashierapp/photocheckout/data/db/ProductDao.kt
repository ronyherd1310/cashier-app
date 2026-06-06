package com.cashierapp.photocheckout.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
public interface ProductDao {
    @Insert
    public suspend fun insertProduct(product: ProductEntity): Long

    @Insert
    public suspend fun insertPhotos(photos: List<ProductPhotoEntity>)

    @Update
    public suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM product_photos WHERE productId = :productId")
    public suspend fun deletePhotosForProduct(productId: Long)

    @Transaction
    @Query("SELECT * FROM products WHERE active = 1 ORDER BY name COLLATE NOCASE ASC")
    public fun observeActiveProducts(): Flow<List<ProductWithPhotos>>

    @Transaction
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE ASC")
    public fun observeProducts(): Flow<List<ProductWithPhotos>>

    @Transaction
    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    public suspend fun getById(id: Long): ProductWithPhotos?

    @Transaction
    @Query("SELECT * FROM products WHERE sku = :sku LIMIT 1")
    public suspend fun getBySku(sku: String): ProductWithPhotos?

    @Query("UPDATE products SET active = :active, deactivatedAt = :deactivatedAt WHERE id = :productId")
    public suspend fun setActive(
        productId: Long,
        active: Boolean,
        deactivatedAt: Long?,
    )

    @Query("SELECT COUNT(*) + 1 FROM products")
    public suspend fun nextSkuSequence(): Long
}
