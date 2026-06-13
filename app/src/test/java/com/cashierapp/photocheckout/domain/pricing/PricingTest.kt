package com.cashierapp.photocheckout.domain.pricing

import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.model.ProductPhoto
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class PricingTest {
    private fun item(
        sku: String,
        priceMinor: Long,
        active: Boolean = true,
        confusionGroup: String? = null,
    ): CatalogItem =
        CatalogItem(
            id = sku.hashCode().toLong(),
            sku = sku,
            name = "Item $sku",
            priceMinor = priceMinor,
            active = active,
            photos = emptyList(),
            createdAtEpochMillis = 0L,
            confusionGroup = confusionGroup,
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
    public fun perInstanceDetectionsForSameSkuProduceOneSummedLine() {
        val catalog = catalog(item("SKU-0001", 15_000))
        val recognized =
            listOf(
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.95f),
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.9f),
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.85f),
            )

        val draft = priceDraft(recognized, catalog)

        val line = draft.lines.single()
        assertEquals("SKU-0001", line.sku)
        assertEquals(3, line.quantity)
        assertEquals(15_000, line.unitPriceMinor)
        assertEquals(45_000, line.lineTotalMinor)
        assertEquals(45_000, draft.subtotalMinor)
    }

    @Test
    public fun mixedPerInstanceDetectionsGroupIdentifiedAndUnidentifiedSkus() {
        val catalog = catalog(item("SKU-0001", 10_000))
        val recognized =
            listOf(
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.9f),
                RecognizedItem("SKU-UNKNOWN", quantity = 1, confidence = 0.8f),
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.85f),
                RecognizedItem("SKU-UNKNOWN", quantity = 1, confidence = 0.7f),
            )

        val draft = priceDraft(recognized, catalog)

        val line = draft.lines.single()
        assertEquals("SKU-0001", line.sku)
        assertEquals(2, line.quantity)
        assertEquals(20_000, line.lineTotalMinor)

        val unidentified = draft.unidentified.single()
        assertEquals("SKU-UNKNOWN", unidentified.rawSku)
        assertEquals(2, unidentified.quantity)
        assertEquals(0.7f, unidentified.confidence)
    }

    @Test
    public fun groupedLineUsesMinimumConfidenceForLowConfidenceFlag() {
        val catalog = catalog(item("SKU-0001", 1_000), item("SKU-0002", 1_000))
        val recognized =
            listOf(
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.9f),
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.5f),
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.9f),
                RecognizedItem("SKU-0002", quantity = 1, confidence = 0.6f),
                RecognizedItem("SKU-0002", quantity = 1, confidence = 0.8f),
            )

        val draft = priceDraft(recognized, catalog)

        val lowLine = draft.lines.first { it.sku == "SKU-0001" }
        assertEquals(0.5f, lowLine.confidence)
        assertTrue(lowLine.lowConfidence)

        val thresholdLine = draft.lines.first { it.sku == "SKU-0002" }
        assertEquals(0.6f, thresholdLine.confidence)
        assertFalse(thresholdLine.lowConfidence)
    }

    @Test
    public fun legacyAggregateDetectionStillProducesOneEquivalentLine() {
        val catalog = catalog(item("SKU-0001", 15_000))
        val recognized = listOf(RecognizedItem("SKU-0001", quantity = 2, confidence = 0.9f))

        val draft = priceDraft(recognized, catalog)

        val line = draft.lines.single()
        assertEquals("SKU-0001", line.sku)
        assertEquals(2, line.quantity)
        assertEquals(15_000, line.unitPriceMinor)
        assertEquals(30_000, line.lineTotalMinor)
        assertEquals(0.9f, line.confidence)
        assertFalse(line.lowConfidence)
    }

    @Test
    public fun groupedLinesFollowFirstAppearanceSkuOrder() {
        val catalog =
            catalog(
                item("SKU-0001", 1_000),
                item("SKU-0002", 1_000),
                item("SKU-0003", 1_000),
            )
        val recognized =
            listOf(
                RecognizedItem("SKU-0002", quantity = 1, confidence = 0.9f),
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.9f),
                RecognizedItem("SKU-0002", quantity = 1, confidence = 0.9f),
                RecognizedItem("SKU-0003", quantity = 1, confidence = 0.9f),
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.9f),
            )

        val draft = priceDraft(recognized, catalog)

        assertEquals(listOf("SKU-0002", "SKU-0001", "SKU-0003"), draft.lines.map { it.sku })
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
    public fun activeConfusionGroupForcesLowConfidenceForAllMemberSkus() {
        val catalog =
            catalog(
                item("SKU-0001", 1_000, confusionGroup = "wafer-24g"),
                item("SKU-0002", 1_200, confusionGroup = "wafer-24g"),
            )
        val draft =
            priceDraft(
                listOf(
                    RecognizedItem("SKU-0001", quantity = 1, confidence = 0.95f),
                    RecognizedItem("SKU-0002", quantity = 1, confidence = 0.95f),
                ),
                catalog,
            )

        assertTrue(draft.lines.first { it.sku == "SKU-0001" }.lowConfidence)
        assertTrue(draft.lines.first { it.sku == "SKU-0002" }.lowConfidence)
    }

    @Test
    public fun singletonNullAndBlankConfusionGroupsDoNotForceLowConfidence() {
        val catalog =
            catalog(
                item("SKU-0001", 1_000, confusionGroup = "singleton"),
                item("SKU-0002", 1_200, confusionGroup = null),
                item("SKU-0003", 1_300, confusionGroup = ""),
                item("SKU-0004", 1_400, confusionGroup = "   "),
            )
        val draft =
            priceDraft(
                listOf(
                    RecognizedItem("SKU-0001", quantity = 1, confidence = 0.95f),
                    RecognizedItem("SKU-0002", quantity = 1, confidence = 0.95f),
                    RecognizedItem("SKU-0003", quantity = 1, confidence = 0.95f),
                    RecognizedItem("SKU-0004", quantity = 1, confidence = 0.95f),
                ),
                catalog,
            )

        assertTrue(draft.lines.all { !it.lowConfidence })
    }

    @Test
    public fun groupedSkuWithDeactivatedSiblingAbsentFromActiveCatalogIsNotForceFlagged() {
        val activeCatalog =
            catalog(
                item("SKU-0001", 1_000, confusionGroup = "wafer-24g"),
            )
        val draft = priceDraft(listOf(RecognizedItem("SKU-0001", 1, 0.95f)), activeCatalog)

        assertFalse(draft.lines.single().lowConfidence)
    }

    @Test
    public fun confusionGroupForcedFlagDoesNotChangeMoneyMathOrUnidentifiedItems() {
        val catalog =
            catalog(
                item("SKU-0001", 10_000, confusionGroup = "wafer-24g"),
                item("SKU-0002", 12_000, confusionGroup = "wafer-24g"),
            )
        val draft =
            priceDraft(
                listOf(
                    RecognizedItem("SKU-0001", quantity = 2, confidence = 0.95f),
                    RecognizedItem("SKU-UNKNOWN", quantity = 3, confidence = 0.95f),
                ),
                catalog,
                taxRateBps = 1_000,
            )

        val line = draft.lines.single()
        assertTrue(line.lowConfidence)
        assertEquals(2, line.quantity)
        assertEquals(10_000L, line.unitPriceMinor)
        assertEquals(20_000L, line.lineTotalMinor)
        assertEquals(20_000L, draft.subtotalMinor)
        assertEquals(2_000L, draft.taxMinor)
        assertEquals(22_000L, draft.totalMinor)

        val unidentified = draft.unidentified.single()
        assertEquals("SKU-UNKNOWN", unidentified.rawSku)
        assertEquals(3, unidentified.quantity)
        assertEquals(0.95f, unidentified.confidence)
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
    public fun nullSkuBecomesUnidentifiedNotDroppedOrPriced() {
        val catalog = catalog(item("SKU-0001", 1_000))
        val recognized =
            listOf(
                RecognizedItem(null, quantity = 1, confidence = 0.5f),
                RecognizedItem(null, quantity = 2, confidence = 0.8f),
            )

        val draft = priceDraft(recognized, catalog)

        assertTrue(draft.lines.isEmpty())
        val unidentified = draft.unidentified.single()
        assertEquals(null, unidentified.rawSku)
        assertEquals(3, unidentified.quantity)
        assertEquals(0.5f, unidentified.confidence)
        assertEquals(0L, draft.totalMinor)
    }

    @Test
    public fun mixedKnownAndNullSkuDetectionsGroupWithoutChangingMoneyMath() {
        val catalog = catalog(item("SKU-0001", 10_000))
        val recognized =
            listOf(
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.9f),
                RecognizedItem(null, quantity = 1, confidence = 0.4f),
                RecognizedItem("SKU-0001", quantity = 1, confidence = 0.8f),
            )

        val draft = priceDraft(recognized, catalog)

        val line = draft.lines.single()
        assertEquals("SKU-0001", line.sku)
        assertEquals(2, line.quantity)
        assertEquals(10_000, line.unitPriceMinor)
        assertEquals(20_000, line.lineTotalMinor)
        assertEquals(20_000, draft.totalMinor)

        val unidentified = draft.unidentified.single()
        assertEquals(null, unidentified.rawSku)
        assertEquals(1, unidentified.quantity)
        assertEquals(0.4f, unidentified.confidence)
    }

    @Test
    public fun uncertaintyFieldsDoNotAffectPricingOrConfidenceRule() {
        val catalog = catalog(item("SKU-0001", 10_000))
        val baseline =
            priceDraft(
                listOf(
                    RecognizedItem("SKU-0001", quantity = 2, confidence = 0.7f),
                ),
                catalog,
            )
        val withUncertainty =
            priceDraft(
                listOf(
                    RecognizedItem(
                        sku = "SKU-0001",
                        quantity = 2,
                        confidence = 0.7f,
                        occluded = true,
                        possiblyMore = true,
                        alternates = listOf("SKU-0002"),
                    ),
                ),
                catalog,
            )

        assertEquals(baseline.lines.single(), withUncertainty.lines.single())
        assertEquals(baseline.subtotalMinor, withUncertainty.subtotalMinor)
        assertEquals(baseline.totalMinor, withUncertainty.totalMinor)
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
        val line = draft.lines.single()
        assertEquals(2, line.quantity)
        assertEquals(4_000L, line.lineTotalMinor)
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
    public fun lineCarriesFirstCatalogPhotoForThumbnail() {
        val withPhoto =
            item("SKU-0001", 1_000).copy(photos = listOf(ProductPhoto("photos/a.jpg", 0)))
        val draft = priceDraft(listOf(RecognizedItem("SKU-0001", 1, 0.9f)), mapOf("SKU-0001" to withPhoto))
        assertEquals("photos/a.jpg", draft.lines.single().photoPath)
    }

    @Test
    public fun largeTotalsStayExactToTheMinorUnit() {
        val catalog = catalog(item("SKU-0001", 999_999_999L))
        val draft = priceDraft(listOf(RecognizedItem("SKU-0001", 1_000, 0.9f)), catalog)
        assertEquals(999_999_999_000L, draft.subtotalMinor)
        assertEquals(999_999_999_000L, draft.totalMinor)
    }
}
