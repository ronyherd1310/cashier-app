package com.cashierapp.photocheckout.domain.usecase

import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import com.cashierapp.photocheckout.recognizer.FakeRecognizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class ScanCounterTest {
    private val image = CapturedImage(bytes = byteArrayOf(1, 2, 3), width = 1024, height = 768, mimeType = "image/jpeg")

    private suspend fun seedCatalog(
        repo: FakeCatalogRepository,
        sku: String,
        priceMinor: Long,
    ) {
        repo.insert(sku = sku, name = "Item $sku", priceMinor = priceMinor, photoPaths = emptyList(), createdAt = 0L)
    }

    @Test
    public fun pricesComeFromCatalogNotRecognizerPayload() =
        runTest {
            val repo = FakeCatalogRepository()
            seedCatalog(repo, "SKU-0001", 15_000)
            val recognizer = FakeRecognizer()
            // confidence high; recognizer carries no price at all
            recognizer.returns(listOf(RecognizedItem("SKU-0001", quantity = 2, confidence = 0.95f)))
            val scanCounter = ScanCounter(repo, recognizer)

            val draft = scanCounter(image).getOrThrow()

            val line = draft.lines.single()
            assertEquals(15_000, line.unitPriceMinor)
            assertEquals(30_000, line.lineTotalMinor)
            assertEquals(30_000, draft.totalMinor)
        }

    @Test
    public fun unidentifiedSkuIsSurfacedNotDropped() =
        runTest {
            val repo = FakeCatalogRepository()
            seedCatalog(repo, "SKU-0001", 1_000)
            val recognizer = FakeRecognizer()
            recognizer.returns(
                listOf(
                    RecognizedItem("SKU-0001", 1, 0.9f),
                    RecognizedItem("SKU-9999", 1, 0.9f),
                ),
            )
            val scanCounter = ScanCounter(repo, recognizer)

            val draft = scanCounter(image).getOrThrow()

            assertEquals(1, draft.lines.size)
            assertEquals("SKU-9999", draft.unidentified.single().rawSku)
        }

    @Test
    public fun lowConfidenceIsFlagged() =
        runTest {
            val repo = FakeCatalogRepository()
            seedCatalog(repo, "SKU-0001", 1_000)
            val recognizer = FakeRecognizer()
            recognizer.returns(listOf(RecognizedItem("SKU-0001", 1, 0.3f)))
            val scanCounter = ScanCounter(repo, recognizer)

            val draft = scanCounter(image).getOrThrow()

            assertTrue(draft.lines.single().lowConfidence)
        }

    @Test
    public fun recognizerFailureMapsToFailureWithNoPartialDraft() =
        runTest {
            val repo = FakeCatalogRepository()
            seedCatalog(repo, "SKU-0001", 1_000)
            val recognizer = FakeRecognizer()
            recognizer.fails(RuntimeException("timeout"))
            val scanCounter = ScanCounter(repo, recognizer)

            val result = scanCounter(image)

            assertTrue(result.isFailure)
        }

    @Test
    public fun onlyActiveCatalogIsSentToRecognizer() =
        runTest {
            val repo = FakeCatalogRepository()
            seedCatalog(repo, "SKU-0001", 1_000)
            val recognizer = FakeRecognizer()
            val scanCounter = ScanCounter(repo, recognizer)

            scanCounter(image)

            assertEquals(listOf("SKU-0001"), recognizer.lastCatalog?.map { it.sku })
            assertEquals(image, recognizer.lastImage)
        }
}
