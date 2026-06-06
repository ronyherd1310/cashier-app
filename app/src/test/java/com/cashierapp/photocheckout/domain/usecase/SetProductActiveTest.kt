package com.cashierapp.photocheckout.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class SetProductActiveTest {
    @Test
    public fun deactivatesAndReactivatesProduct() =
        runTest {
            val repository = FakeCatalogRepository()
            val productId =
                EnrollProduct(repository)(
                    EnrollProductInput("Name", 10_000L, listOf("photo.jpg"), 1L),
                )
            val useCase = SetProductActive(repository)

            useCase(productId = productId, active = false, changedAt = 20L)
            val inactive = repository.getById(productId)
            assertFalse(inactive?.active ?: true)

            useCase(productId = productId, active = true, changedAt = 30L)
            val active = repository.getById(productId)
            assertTrue(active?.active ?: false)
            assertNull(active?.deactivatedAtEpochMillis)
        }
}
