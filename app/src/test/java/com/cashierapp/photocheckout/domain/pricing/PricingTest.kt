package com.cashierapp.photocheckout.domain.pricing

import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class PricingTest {
    private fun item(
        sku: String,
        priceMinor: Long,
    ): CatalogItem =
        CatalogItem(
            id = sku.hashCode().toLong(),
            sku = sku,
            name = "Item $sku",
            priceMinor = priceMinor,
            active = true,
            photos = emptyList(),
            createdAtEpochMillis = 0L,
        )

    private fun catalog(vararg items: CatalogItem): Map<String, CatalogItem> = items.associateBy { it.sku }

    @Test
    public fun emptyRecognitionProducesEmptyDraft() {
        val draft = priceDraft(emptyList(), emptyMap())
        assertTrue(draft.lines.isEmpty())
        assertTrue(draft.unidentified.isEmpty())
        assertEquals(0L, draft.subtotalMinor)
        assertEquals(0L, draft.taxMinor)
        assertEquals(0L, draft.totalMinor)
    }

    @Test
    public fun singleLinePricesFromCatalogNotModel() {
        val catalog = catalog(item("SKU-0001", 15_000))
        val recognized = listOf(RecognizedItem("SKU-0001", quantity = 1, confidence = 0.9f))

        val draft = priceDraft(recognized, catalog)

        val line = draft.lines.single()
        assertEquals(15_000, line.unitPriceMinor)
        assertEquals(15_000, line.lineTotalMinor)
        assertEquals(15_000, draft.subtotalMinor)
        assertEquals(15_000, draft.totalMinor)
        assertFalse(line.lowConfidence)
    }

    @Test
    public fun multipleQuantitiesMultiplyDeterministically() {
        val catalog = catalog(item("SKU-0001", 15_000), item("SKU-0002", 4_500))
        val recognized =
            listOf(
                RecognizedItem("SKU-0001", quantity = 3, confidence = 0.95f),
                RecognizedItem("SKU-0002", quantity = 2, confidence = 0.8f),
            )

        val draft = priceDraft(recognized, catalog)

        assertEquals(45_000, draft.lines[0].lineTotalMinor)
        assertEquals(9_000, draft.lines[1].lineTotalMinor)
        assertEquals(54_000, draft.subtotalMinor)
        assertEquals(54_000, draft.totalMinor)
    }

    @Test
    public fun confidenceBelowThresholdFlagsLowConfidence() {
        val catalog = catalog(item("SKU-0001", 1_000))
        val draft = priceDraft(listOf(RecognizedItem("SKU-0001", 1, 0.59f)), catalog)
        assertTrue(draft.lines.single().lowConfidence)
        assertEquals(0.59f, draft.lines.single().confidence)
    }

    @Test
    public fun confidenceAtThresholdIsNotLowConfidence() {
        val catalog = catalog(item("SKU-0001", 1_000))
        val draft = priceDraft(listOf(RecognizedItem("SKU-0001", 1, 0.6f)), catalog)
        assertFalse(draft.lines.single().lowConfidence)
    }

    @Test
    public fun unknownSkuBecomesUnidentifiedNotDropped() {
        val catalog = catalog(item("SKU-0001", 1_000))
        val recognized =
            listOf(
                RecognizedItem("SKU-0001", 1, 0.9f),
                RecognizedItem("SKU-9999", 2, 0.7f),
            )

        val draft = priceDraft(recognized, catalog)

        assertEquals(1, draft.lines.size)
        val unidentified = draft.unidentified.single()
        assertEquals("SKU-9999", unidentified.rawSku)
        assertEquals(2, unidentified.quantity)
        assertEquals(0.7f, unidentified.confidence)
        // unidentified items do not contribute to totals
        assertEquals(1_000, draft.totalMinor)
    }

    @Test
    public fun nonPositiveQuantityNormalizedToOne() {
        val catalog = catalog(item("SKU-0001", 2_000))
        val draft =
            priceDraft(
                listOf(
                    RecognizedItem("SKU-0001", quantity = 0, confidence = 0.9f),
                    RecognizedItem("SKU-0001", quantity = -5, confidence = 0.9f),
                ),
                catalog,
            )
        assertTrue(draft.lines.all { it.quantity == 1 })
        assertTrue(draft.lines.all { it.lineTotalMinor == 2_000L })
    }

    @Test
    public fun taxRateAppliedInBasisPoints() {
        val catalog = catalog(item("SKU-0001", 100_000))
        val draft = priceDraft(listOf(RecognizedItem("SKU-0001", 1, 0.9f)), catalog, taxRateBps = 1_100)
        assertEquals(100_000, draft.subtotalMinor)
        assertEquals(11_000, draft.taxMinor)
        assertEquals(111_000, draft.totalMinor)
    }

    @Test
    public fun largeTotalsStayExactToTheMinorUnit() {
        val catalog = catalog(item("SKU-0001", 999_999_999L))
        val draft = priceDraft(listOf(RecognizedItem("SKU-0001", 1_000, 0.9f)), catalog)
        assertEquals(999_999_999_000L, draft.subtotalMinor)
        assertEquals(999_999_999_000L, draft.totalMinor)
    }
}
