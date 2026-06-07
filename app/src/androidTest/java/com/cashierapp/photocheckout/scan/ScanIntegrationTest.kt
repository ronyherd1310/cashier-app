package com.cashierapp.photocheckout.scan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cashierapp.photocheckout.data.db.CashierDatabase
import com.cashierapp.photocheckout.data.db.ProductRepository
import com.cashierapp.photocheckout.data.image.AndroidImageDownscaler
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import com.cashierapp.photocheckout.domain.recognizer.Recognizer
import com.cashierapp.photocheckout.domain.telemetry.ScanTelemetry
import com.cashierapp.photocheckout.domain.telemetry.ScanTelemetryEvent
import com.cashierapp.photocheckout.domain.usecase.EnrollProduct
import com.cashierapp.photocheckout.domain.usecase.EnrollProductInput
import com.cashierapp.photocheckout.domain.usecase.ScanCounter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Scan integration suite (SCAN-INT-1…3, 5): real use case + Room + real pricing,
 * faking only the recognizer boundary. SCAN-INT-4 (cloud impl vs MockWebServer)
 * lives in OpenRouterRecognizerTest.
 */
public class ScanIntegrationTest {
    private lateinit var database: CashierDatabase
    private lateinit var repository: ProductRepository

    private val image =
        CapturedImage(bytes = byteArrayOf(1, 2, 3), width = 1024, height = 768, mimeType = "image/jpeg")

    @Before
    public fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    CashierDatabase::class.java,
                ).build()
        repository = ProductRepository(database.productDao())
    }

    @After
    public fun tearDown() {
        database.close()
    }

    private suspend fun enroll(
        name: String,
        priceMinor: Long,
    ) {
        EnrollProduct(repository)(
            EnrollProductInput(name = name, priceMinor = priceMinor, photoPaths = listOf("p.jpg"), createdAt = 0L),
        )
    }

    @Test
    public fun pricesComeFromCatalogNotRecognizerPayload() =
        runTest {
            enroll("Coffee", 15_000) // SKU-0001
            val recognizer = cannedRecognizer(listOf(RecognizedItem("SKU-0001", quantity = 2, confidence = 0.9f)))

            val draft = ScanCounter(repository, recognizer)(image).getOrThrow()

            assertEquals(15_000L, draft.lines.single().unitPriceMinor)
            assertEquals(30_000L, draft.totalMinor)
        }

    @Test
    public fun absentSkuBecomesUnidentifiedAndLowConfidenceFlagged() =
        runTest {
            enroll("Coffee", 1_000) // SKU-0001
            val recognizer =
                cannedRecognizer(
                    listOf(
                        RecognizedItem("SKU-0001", 1, 0.3f),
                        RecognizedItem("SKU-9999", 1, 0.9f),
                    ),
                )

            val draft = ScanCounter(repository, recognizer)(image).getOrThrow()

            assertTrue(draft.lines.single().lowConfidence)
            assertEquals("SKU-9999", draft.unidentified.single().rawSku)
        }

    @Test
    public fun downscalerReducesLargeImageBeforeRecognition() =
        runTest {
            // SCAN-INT-2: a 4000px image is downscaled to <=1024 longest edge.
            val largeJpeg = makeJpeg(width = 4000, height = 3000)
            val downscaler = AndroidImageDownscaler()

            val downscaled = downscaler.downscale(largeJpeg)

            assertTrue("longest edge should be <= 1024", maxOf(downscaled.width, downscaled.height) <= 1024)
        }

    @Test
    public fun telemetryRecordedThroughRealPath() =
        runTest {
            enroll("Coffee", 1_000) // SKU-0001
            val recognizer = cannedRecognizer(listOf(RecognizedItem("SKU-0001", 2, 0.9f)))
            val recorded = mutableListOf<ScanTelemetryEvent>()
            val telemetry =
                object : ScanTelemetry {
                    override fun record(event: ScanTelemetryEvent) {
                        recorded += event
                    }
                }

            ScanCounter(repository, recognizer, telemetry)(image)

            val event = recorded.single()
            assertTrue(event.success)
            assertEquals(1, event.itemCount)
            assertTrue(event.latencyMs >= 0)
        }

    private fun cannedRecognizer(items: List<RecognizedItem>): Recognizer =
        object : Recognizer {
            override suspend fun recognize(
                image: CapturedImage,
                catalog: List<CatalogItem>,
            ): Result<List<RecognizedItem>> = Result.success(items)
        }

    private fun makeJpeg(
        width: Int,
        height: Int,
    ): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        return java.io.ByteArrayOutputStream().use { stream ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }
    }
}
