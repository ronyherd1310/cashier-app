package com.cashierapp.photocheckout.domain.model

import com.cashierapp.photocheckout.domain.recognizer.BoundingBox
import com.cashierapp.photocheckout.domain.recognizer.CONFIDENCE_THRESHOLD
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class ScanDomainModelsTest {
    @Test
    public fun confidenceThresholdMatchesSpec() {
        assertEquals(0.6f, CONFIDENCE_THRESHOLD)
    }

    @Test
    public fun recognizedItemDefaultsBoundingBoxToNull() {
        val item = RecognizedItem(sku = "SKU-0001", quantity = 2, confidence = 0.92f)
        assertNull(item.boundingBox)
        assertEquals("SKU-0001", item.sku)
        assertEquals(2, item.quantity)
    }

    @Test
    public fun recognizedItemAcceptsBoundingBox() {
        val box = BoundingBox(left = 0f, top = 0f, right = 1f, bottom = 1f)
        val item = RecognizedItem("SKU-0002", 1, 0.5f, box)
        assertEquals(box, item.boundingBox)
    }

    @Test
    public fun capturedImageHoldsBytesAndDimensions() {
        val bytes = byteArrayOf(1, 2, 3)
        val image = CapturedImage(bytes = bytes, width = 1024, height = 768, mimeType = "image/jpeg")
        assertEquals(1024, image.width)
        assertEquals(768, image.height)
        assertEquals("image/jpeg", image.mimeType)
        assertEquals(3, image.bytes.size)
    }

    @Test
    public fun capturedImageEqualityIgnoresOriginalSource() {
        val bytes = byteArrayOf(1, 2, 3)
        val original = CapturedImage(bytes = byteArrayOf(9, 8, 7), width = 2048, height = 1536, mimeType = "image/jpeg")

        val withoutOriginal = CapturedImage(bytes = bytes, width = 1024, height = 768, mimeType = "image/jpeg")
        val withOriginal = CapturedImage(bytes = bytes, width = 1024, height = 768, mimeType = "image/jpeg", original = original)

        assertEquals(withoutOriginal, withOriginal)
        assertEquals(withoutOriginal.hashCode(), withOriginal.hashCode())
    }

    @Test
    public fun draftLineDefaultsNoteToNull() {
        val line =
            DraftLine(
                sku = "SKU-0001",
                name = "Coffee",
                quantity = 2,
                unitPriceMinor = 15_000,
                lineTotalMinor = 30_000,
                confidence = 0.92f,
                lowConfidence = false,
            )
        assertNull(line.note)
        assertFalse(line.lowConfidence)
    }

    @Test
    public fun draftReceiptAggregatesLinesAndUnidentified() {
        val line =
            DraftLine(
                sku = "SKU-0001",
                name = "Coffee",
                quantity = 1,
                unitPriceMinor = 15_000,
                lineTotalMinor = 15_000,
                confidence = 0.4f,
                lowConfidence = true,
                note = "extra hot",
            )
        val unidentified = UnidentifiedItem(rawSku = "???", quantity = 1, confidence = 0.2f)
        val receipt =
            DraftReceipt(
                lines = listOf(line),
                unidentified = listOf(unidentified),
                subtotalMinor = 15_000,
                taxMinor = 0,
                totalMinor = 15_000,
            )
        assertTrue(receipt.lines.single().lowConfidence)
        assertEquals("extra hot", receipt.lines.single().note)
        assertEquals(1, receipt.unidentified.size)
        assertEquals(15_000, receipt.totalMinor)
    }

    @Test
    public fun scanStageEnumeratesPipelineSteps() {
        assertEquals(
            listOf(ScanStage.Uploading, ScanStage.Recognizing, ScanStage.Pricing),
            ScanStage.entries,
        )
    }
}
