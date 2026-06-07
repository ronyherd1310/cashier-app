package com.cashierapp.photocheckout.ui.scan.additem

import com.cashierapp.photocheckout.domain.model.CatalogItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class AddItemFilterTest {
    private fun item(
        sku: String,
        name: String,
    ): CatalogItem =
        CatalogItem(
            id = 1,
            sku = sku,
            name = name,
            priceMinor = 1_000,
            active = true,
            photos = emptyList(),
            createdAtEpochMillis = 0L,
        )

    @Test
    public fun emptyQueryMatchesEverything() {
        assertTrue(matchesQuery(item("SKU-0001", "Coffee"), ""))
        assertTrue(matchesQuery(item("SKU-0001", "Coffee"), "   "))
    }

    @Test
    public fun matchesByNameCaseInsensitively() {
        assertTrue(matchesQuery(item("SKU-0001", "Es Kopi Susu"), "kopi"))
    }

    @Test
    public fun matchesBySku() {
        assertTrue(matchesQuery(item("SKU-0042", "Coffee"), "0042"))
    }

    @Test
    public fun rejectsNonMatches() {
        assertFalse(matchesQuery(item("SKU-0001", "Coffee"), "tea"))
    }
}
