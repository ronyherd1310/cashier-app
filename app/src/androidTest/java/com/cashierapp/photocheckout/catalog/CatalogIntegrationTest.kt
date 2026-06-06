package com.cashierapp.photocheckout.catalog

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cashierapp.photocheckout.data.db.CashierDatabase
import com.cashierapp.photocheckout.data.db.ProductRepository
import com.cashierapp.photocheckout.domain.usecase.EnrollProduct
import com.cashierapp.photocheckout.domain.usecase.EnrollProductInput
import com.cashierapp.photocheckout.domain.usecase.SetProductActive
import com.cashierapp.photocheckout.ui.catalog.list.CatalogSortOrder
import com.cashierapp.photocheckout.ui.catalog.list.CatalogStatusFilter
import com.cashierapp.photocheckout.ui.catalog.list.filterAndSortProducts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

public class CatalogIntegrationTest {
    private lateinit var database: CashierDatabase
    private lateinit var repository: ProductRepository

    @Before
    public fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    CashierDatabase::class.java,
                ).build()
        repository = ProductRepository(database.productDao())
    }

    @After
    public fun tearDown() {
        database.close()
    }

    @Test
    public fun enrollWritesProductAndPhotoAndRetrievesBySku() =
        runTest {
            EnrollProduct(repository)(
                EnrollProductInput(
                    name = "Nasi Goreng",
                    priceMinor = 25_000L,
                    photoPaths = listOf("photo.jpg"),
                    createdAt = 10L,
                ),
            )

            val product = repository.getBySku("SKU-0001")

            assertNotNull(product)
            assertEquals("Nasi Goreng", product?.name)
            assertEquals(25_000L, product?.priceMinor)
            assertEquals("photo.jpg", product?.photos?.single()?.path)
        }

    @Test
    public fun softDeleteExcludesActiveQueryButRemainsResolvable() =
        runTest {
            val productId =
                EnrollProduct(repository)(
                    EnrollProductInput("Name", 10_000L, listOf("photo.jpg"), 1L),
                )

            SetProductActive(repository)(productId = productId, active = false, changedAt = 20L)

            assertTrue(repository.observeActiveProducts().first().isEmpty())
            assertNotNull(repository.getById(productId))
            assertEquals(false, repository.getById(productId)?.active)
        }

    @Test
    public fun searchAndFilterOverThreeHundredItemsReturnsExpectedRows() =
        runTest {
            repeat(300) { index ->
                repository.insert(
                    sku = "SKU-${(index + 1).toString().padStart(4, '0')}",
                    name = "Product $index",
                    priceMinor = (index + 1) * 1_000L,
                    photoPaths = listOf("photo-$index.jpg"),
                    createdAt = index.toLong(),
                )
            }
            repository.setActive(productId = 2L, active = false, deactivatedAt = 400L)

            val result =
                filterAndSortProducts(
                    products = repository.observeProducts().first(),
                    query = "SKU-0002",
                    statusFilter = CatalogStatusFilter.InactiveOnly,
                    sortOrder = CatalogSortOrder.PriceAscending,
                )

            assertEquals(listOf("SKU-0002"), result.map { it.sku })
        }
}
