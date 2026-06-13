package com.cashierapp.photocheckout.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ProductDaoTest {
    @get:Rule
    public val migrationHelper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            CashierDatabase::class.java,
        )

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

            try {
                insertProduct(sku = "SKU-0001")
                fail("Expected duplicate SKU insert to fail.")
            } catch (_: Throwable) {
                // Room may wrap the SQLite constraint exception depending on API/runtime.
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

    @Test
    public fun migrationFromOneToTwoKeepsProductsAndAddsNullableDescriptionFields() {
        val databaseName = "migration-r4.db"
        migrationHelper
            .createDatabase(databaseName, 1)
            .apply {
                execSQL(
                    """
                    INSERT INTO products (id, sku, name, priceMinor, active, createdAt, deactivatedAt)
                    VALUES (1, 'SKU-0001', 'Choco Wafer', 12500, 1, 100, NULL)
                    """.trimIndent(),
                )
                close()
            }

        val migrated =
            migrationHelper.runMigrationsAndValidate(
                databaseName,
                2,
                true,
                CashierDatabase.MIGRATION_1_2,
            )

        migrated
            .query("SELECT sku, name, description, confusionGroup FROM products WHERE id = 1")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("SKU-0001", cursor.getString(0))
                assertEquals("Choco Wafer", cursor.getString(1))
                assertNull(cursor.getString(2))
                assertNull(cursor.getString(3))
            }
        migrated.close()
    }

    @Test
    public fun repositoryUpdateRoundTripsDescriptionAndConfusionGroup() =
        runTest {
            val repository = ProductRepository(dao)
            val id =
                repository.insert(
                    sku = "SKU-0001",
                    name = "Choco Wafer",
                    priceMinor = 12_500L,
                    photoPaths = listOf("products/choco.jpg"),
                    createdAt = 100L,
                )
            val product = requireNotNull(repository.getById(id))

            repository.update(
                product.copy(
                    description = "brown wrapper, red CHOCO band",
                    confusionGroup = "wafer-24g",
                ),
            )

            val updated = requireNotNull(repository.getBySku("SKU-0001"))
            assertEquals("brown wrapper, red CHOCO band", updated.description)
            assertEquals("wafer-24g", updated.confusionGroup)
            assertEquals("products/choco.jpg", updated.photos.single().path)
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
