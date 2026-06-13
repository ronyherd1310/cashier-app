package com.cashierapp.photocheckout.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProductEntity::class,
        ProductPhotoEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
public abstract class CashierDatabase : RoomDatabase() {
    public abstract fun productDao(): ProductDao

    public companion object {
        public val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE products ADD COLUMN description TEXT")
                    db.execSQL("ALTER TABLE products ADD COLUMN confusionGroup TEXT")
                }
            }
    }
}
