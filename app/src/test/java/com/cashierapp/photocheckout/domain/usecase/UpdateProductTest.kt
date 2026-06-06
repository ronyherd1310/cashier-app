package com.cashierapp.photocheckout.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

public class UpdateProductTest {
    @Test
    public fun updatesProductWithoutChangingSku() =
        runTest {
            val repository = FakeCatalogRepository()
            val productId =
                EnrollProduct(repository)(
                    EnrollProductInput("Old Name", 10_000L, listOf("old.jpg"), 1L),
                )
            val useCase = UpdateProduct(repository)

            useCase(
                UpdateProductInput(
                    productId = productId,
                    name = "New Name",
                    priceMinor = 20_000L,
                    photoPaths = listOf("one.jpg", "two.jpg"),
                ),
            )

            val updated = repository.getById(productId)
            assertEquals("SKU-0001", updated?.sku)
            assertEquals("New Name", updated?.name)
            assertEquals(20_000L, updated?.priceMinor)
            assertEquals(2, updated?.photos?.size)
        }

    @Test
    public fun rejectsMoreThanThreePhotos() =
        runTest {
            val repository = FakeCatalogRepository()
            val productId =
                EnrollProduct(repository)(
                    EnrollProductInput("Name", 10_000L, listOf("old.jpg"), 1L),
                )
            val useCase = UpdateProduct(repository)

            try {
                useCase(
                    UpdateProductInput(
                        productId = productId,
                        name = "Name",
                        priceMinor = 10_000L,
                        photoPaths = listOf("1.jpg", "2.jpg", "3.jpg", "4.jpg"),
                    ),
                )
                fail("Expected IllegalArgumentException.")
            } catch (_: IllegalArgumentException) {
            }
        }
}
