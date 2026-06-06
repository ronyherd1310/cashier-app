package com.cashierapp.photocheckout.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

public class ProductDaoTest {
    private lateinit var database: CashierDatabase
    private lateinit var dao: ProductDao

    @Before
    public fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    CashierDatabase::class.java,
                ).build()
        dao = database.productDao()
    }

    @After
    public fun tearDown() {
        database.close()
    }

    @Test
    public fun insertAndReadRoundTripsProductWithPhotos() =
        runTest {
            val id = insertProduct(sku = "SKU-0001")
            dao.insertPhotos(
                listOf(
                    ProductPhotoEntity(productId = id, path = "products/one.jpg", position = 0),
                ),
            )

            val product = dao.getBySku("SKU-0001")

            assertNotNull(product)
            assertEquals("Nasi Goreng", product?.product?.name)
            assertEquals(25_000L, product?.product?.priceMinor)
            assertEquals("products/one.jpg", product?.photos?.single()?.path)
        }

    @Test
    public fun duplicateSkuViolatesUniqueConstraint() =
        runTest {
            insertProduct(sku = "SKU-0001")

            assertThrows(SQLiteConstraintException::class.java) {
                runTest { insertProduct(sku = "SKU-0001") }
            }
        }

    @Test
    public fun activeProductsExcludeDeactivatedRows() =
        runTest {
            val activeId = insertProduct(sku = "SKU-0001")
            val inactiveId = insertProduct(sku = "SKU-0002")
            dao.setActive(productId = inactiveId, active = false, deactivatedAt = 10L)

            val activeProducts = dao.observeActiveProducts().first()

            assertEquals(activeId, activeProducts.single().product.id)
            assertNotNull(dao.getById(inactiveId))
            assertNull(dao.getById(999L))
        }

    private suspend fun insertProduct(sku: String): Long =
        dao.insertProduct(
            ProductEntity(
                sku = sku,
                name = "Nasi Goreng",
                priceMinor = 25_000L,
                active = true,
                createdAt = 1L,
                deactivatedAt = null,
            ),
        )
}
