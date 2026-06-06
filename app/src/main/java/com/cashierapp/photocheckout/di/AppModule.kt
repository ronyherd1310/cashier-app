package com.cashierapp.photocheckout.di

import android.content.Context
import androidx.room.Room
import com.cashierapp.photocheckout.data.db.CashierDatabase
import com.cashierapp.photocheckout.data.db.ProductDao
import com.cashierapp.photocheckout.data.db.ProductRepository
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
            ).build()

    @Provides
    public fun provideProductDao(database: CashierDatabase): ProductDao = database.productDao()

    @Provides
    public fun provideProductRepository(productDao: ProductDao): ProductRepository = ProductRepository(productDao)
}
