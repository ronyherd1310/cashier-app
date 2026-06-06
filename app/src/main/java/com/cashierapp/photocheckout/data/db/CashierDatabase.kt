package com.cashierapp.photocheckout.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductEntity::class,
        ProductPhotoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
public abstract class CashierDatabase : RoomDatabase() {
    public abstract fun productDao(): ProductDao
}
