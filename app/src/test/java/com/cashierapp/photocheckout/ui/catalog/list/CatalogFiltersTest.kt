package com.cashierapp.photocheckout.ui.catalog.list

import com.cashierapp.photocheckout.domain.model.CatalogItem
import org.junit.Assert.assertEquals
import org.junit.Test

public class CatalogFiltersTest {
    @Test
    public fun searchesByNameAndSku() {
        val result =
            filterAndSortProducts(
                products = listOf(product("SKU-0001", "Nasi Goreng"), product("SKU-0002", "Es Kopi")),
                query = "0002",
                statusFilter = CatalogStatusFilter.All,
                sortOrder = CatalogSortOrder.NameAscending,
            )

        assertEquals(listOf("SKU-0002"), result.map { it.sku })
    }

    @Test
    public fun filtersInactiveProducts() {
        val result =
            filterAndSortProducts(
                products = listOf(product("SKU-0001", "Active", active = true), product("SKU-0002", "Inactive", active = false)),
                query = "",
                statusFilter = CatalogStatusFilter.InactiveOnly,
                sortOrder = CatalogSortOrder.NameAscending,
            )

        assertEquals(listOf("SKU-0002"), result.map { it.sku })
    }

    @Test
    public fun sortsByPrice() {
        val result =
            filterAndSortProducts(
                products = listOf(product("SKU-0001", "A", price = 20_000L), product("SKU-0002", "B", price = 10_000L)),
                query = "",
                statusFilter = CatalogStatusFilter.All,
                sortOrder = CatalogSortOrder.PriceAscending,
            )

        assertEquals(listOf("SKU-0002", "SKU-0001"), result.map { it.sku })
    }

    private fun product(
        sku: String,
        name: String,
        active: Boolean = true,
        price: Long = 10_000L,
    ): CatalogItem =
        CatalogItem(
            id = sku.takeLast(4).toLong(),
            sku = sku,
            name = name,
            priceMinor = price,
            active = active,
            photos = emptyList(),
            createdAtEpochMillis = sku.takeLast(4).toLong(),
        )
}
