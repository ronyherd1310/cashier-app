package com.cashierapp.photocheckout.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

public class EnrollProductTest {
    @Test
    public fun generatesSkuAndPersistsProduct() =
        runTest {
            val repository = FakeCatalogRepository()
            val useCase = EnrollProduct(repository)

            val id =
                useCase(
                    EnrollProductInput(
                        name = " Nasi Goreng ",
                        priceMinor = 25_000L,
                        photoPaths = listOf("photo.jpg"),
                        createdAt = 10L,
                    ),
                )

            val product = repository.getById(id)
            assertEquals("SKU-0001", product?.sku)
            assertEquals("Nasi Goreng", product?.name)
            assertEquals(25_000L, product?.priceMinor)
        }

    @Test
    public fun rejectsMissingRequiredFields() =
        runTest {
            val useCase = EnrollProduct(FakeCatalogRepository())

            expectIllegalArgument {
                useCase(EnrollProductInput("", 25_000L, listOf("photo.jpg"), 1L))
            }
            expectIllegalArgument {
                useCase(EnrollProductInput("Name", 0L, listOf("photo.jpg"), 1L))
            }
            expectIllegalArgument {
                useCase(EnrollProductInput("Name", 25_000L, emptyList(), 1L))
            }
        }

    private suspend fun expectIllegalArgument(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException.")
        } catch (_: IllegalArgumentException) {
        }
    }
}
