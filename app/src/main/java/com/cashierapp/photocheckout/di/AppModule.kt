package com.cashierapp.photocheckout.di

import android.content.Context
import androidx.room.Room
import com.cashierapp.photocheckout.data.db.CashierDatabase
import com.cashierapp.photocheckout.data.db.ProductDao
import com.cashierapp.photocheckout.data.db.ProductRepository
import com.cashierapp.photocheckout.data.image.AndroidImageDownscaler
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import com.cashierapp.photocheckout.data.telemetry.LoggingScanTelemetry
import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import com.cashierapp.photocheckout.domain.image.ImageDownscaler
import com.cashierapp.photocheckout.domain.telemetry.ScanTelemetry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public object AppModule {
    @Provides
    @Singleton
    public fun provideDatabase(
        @ApplicationContext context: Context,
    ): CashierDatabase =
        Room
            .databaseBuilder(
                context = context,
                klass = CashierDatabase::class.java,
                name = "cashier.db",
            ).addMigrations(CashierDatabase.MIGRATION_1_2)
            .build()

    @Provides
    public fun provideProductDao(database: CashierDatabase): ProductDao = database.productDao()

    @Provides
    public fun provideProductRepository(productDao: ProductDao): ProductRepository = ProductRepository(productDao)

    @Provides
    public fun provideCatalogRepository(productRepository: ProductRepository): CatalogRepository = productRepository

    @Provides
    public fun providePhotoStorage(
        @ApplicationContext context: Context,
    ): PhotoStorage = PhotoStorage(context)

    @Provides
    public fun provideImageDownscaler(downscaler: AndroidImageDownscaler): ImageDownscaler = downscaler

    @Provides
    public fun provideScanTelemetry(telemetry: LoggingScanTelemetry): ScanTelemetry = telemetry
}
