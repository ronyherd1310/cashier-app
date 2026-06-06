package com.cashierapp.photocheckout.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [Index(value = ["sku"], unique = true)],
)
public data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sku: String,
    val name: String,
    val priceMinor: Long,
    val active: Boolean,
    val createdAt: Long,
    val deactivatedAt: Long?,
)
