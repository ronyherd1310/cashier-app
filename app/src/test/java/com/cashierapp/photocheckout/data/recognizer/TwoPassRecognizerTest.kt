package com.cashierapp.photocheckout.data.recognizer

import com.cashierapp.photocheckout.domain.image.ImageCropper
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.recognizer.BoundingBox
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import com.cashierapp.photocheckout.domain.recognizer.Recognizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

public class TwoPassRecognizerTest {
    private val box = BoundingBox(left = 0.1f, top = 0.2f, right = 0.3f, bottom = 0.4f)
    private val image = CapturedImage(bytes = byteArrayOf(1), width = 100, height = 100, mimeType = "image/jpeg")

    @Test
    public fun confidentPlainDetectionsDoNotCallVerifier() =
        runBlocking {
            val pass1 = listOf(item("SKU-1", confidence = 0.95f, boundingBox = box))
            val primary = StaticRecognizer(Result.success(pass1))
            val verifier = RecordingRecognizer()
            val cropper = RecordingCropper()

            val result = TwoPassRecognizer(primary, verifier, cropper).recognize(image, catalog("SKU-1"))

            assertEquals(pass1, result.getOrThrow())
            assertEquals(0, verifier.calls.size)
            assertEquals(0, cropper.calls.size)
        }

    @Test
    public fun escalatesDoubtSignalsButNotPossiblyMoreOrBoxlessDetections() =
        runBlocking {
            val detections =
                listOf(
                    item("LOW", confidence = 0.2f, boundingBox = box),
                    item("OCC", confidence = 0.9f, boundingBox = box, occluded = true),
                    item(null, confidence = 0.9f, boundingBox = box),
                    item("GROUP-A", confidence = 0.9f, boundingBox = box),
                    item("PLAIN", confidence = 0.9f, boundingBox = box),
                    item("MORE", confidence = 0.9f, boundingBox = box, possiblyMore = true),
                    item("NOBOX", confidence = 0.2f, boundingBox = null),
                )
            val verifier = RecordingRecognizer { call ->
                listOf(item(call.catalog.first().sku, confidence = 0.99f))
            }

            val result =
                TwoPassRecognizer(
                    primary = StaticRecognizer(Result.success(detections)),
                    verifier = verifier,
                    cropper = RecordingCropper(),
                ).recognize(
                    image,
                    catalog("LOW", "OCC", "GROUP-A", "GROUP-B", "PLAIN", "MORE", "NOBOX", grouped = setOf("GROUP-A", "GROUP-B")),
                )

            assertTrue(result.isSuccess)
            assertEquals(listOf("LOW", "OCC", null, "GROUP-A"), verifier.calls.map { it.originalSku })
        }

    @Test
    public fun verifierReceivesCandidateSubCatalog() =
        runBlocking {
            val detections =
                listOf(
                    item("GROUP-A", confidence = 0.2f, boundingBox = box, alternates = listOf("ALT")),
                    item(null, confidence = 0.2f, boundingBox = box),
                )
            val catalog = catalog("GROUP-A", "GROUP-B", "ALT", "PLAIN", inactive = setOf("INACTIVE"), grouped = setOf("GROUP-A", "GROUP-B"))
            val verifier = RecordingRecognizer { call -> listOf(item(call.catalog.first().sku, confidence = 0.9f)) }

            TwoPassRecognizer(StaticRecognizer(Result.success(detections)), verifier, RecordingCropper()).recognize(image, catalog)

            assertEquals(setOf("GROUP-A", "GROUP-B", "ALT"), verifier.calls[0].catalog.map { it.sku }.toSet())
            assertEquals(setOf("GROUP-A", "GROUP-B", "ALT", "PLAIN"), verifier.calls[1].catalog.map { it.sku }.toSet())
        }

    @Test
    public fun mergeReplacesOnlyIdentityConfidenceAndAlternatesForEscalatedInstance() =
        runBlocking {
            val untouched = item("PLAIN", confidence = 0.95f, boundingBox = box)
            val escalated =
                item(
                    "OLD",
                    confidence = 0.2f,
                    boundingBox = box,
                    occluded = true,
                    possiblyMore = true,
                    alternates = listOf("NEW"),
                )
            val verifier = RecordingRecognizer { listOf(item("NEW", confidence = 0.99f, alternates = listOf("OLD"))) }

            val result =
                TwoPassRecognizer(
                    StaticRecognizer(Result.success(listOf(untouched, escalated))),
                    verifier,
                    RecordingCropper(),
                ).recognize(image, catalog("PLAIN", "OLD", "NEW"))

            assertEquals(
                listOf(
                    untouched,
                    escalated.copy(sku = "NEW", confidence = 0.99f, alternates = listOf("OLD")),
                ),
                result.getOrThrow(),
            )
            assertEquals(escalated.boundingBox, result.getOrThrow()[1].boundingBox)
            assertTrue(result.getOrThrow()[1].occluded)
            assertTrue(result.getOrThrow()[1].possiblyMore)
        }

    @Test
    public fun perCropFailuresKeepPassOneDetection() =
        runBlocking {
            val detections =
                listOf(
                    item("CROP-NULL", confidence = 0.1f, boundingBox = box),
                    item("VERIFY-EMPTY", confidence = 0.2f, boundingBox = box),
                    item("VERIFY-ERROR", confidence = 0.3f, boundingBox = box),
                    item("OFF-CATALOG", confidence = 0.4f, boundingBox = box),
                )
            val cropper = RecordingCropper(nullForSkus = setOf("CROP-NULL"))
            val verifier =
                RecordingRecognizer { call ->
                    when (call.originalSku) {
                        "VERIFY-EMPTY" -> emptyList()
                        "VERIFY-ERROR" -> error("boom")
                        "OFF-CATALOG" -> listOf(item("OTHER", confidence = 0.99f))
                        else -> listOf(item(call.originalSku, confidence = 0.99f))
                    }
                }

            val result = TwoPassRecognizer(StaticRecognizer(Result.success(detections)), verifier, cropper).recognize(image, catalog("CROP-NULL", "VERIFY-EMPTY", "VERIFY-ERROR", "OFF-CATALOG"))

            assertEquals(detections, result.getOrThrow())
        }

    @Test
    public fun passOneFailureDoesNotCallVerifier() =
        runBlocking {
            val verifier = RecordingRecognizer()

            val result = TwoPassRecognizer(StaticRecognizer(Result.failure(IllegalStateException("nope"))), verifier, RecordingCropper()).recognize(image, catalog("SKU-1"))

            assertTrue(result.isFailure)
            assertEquals(0, verifier.calls.size)
        }

    @Test
    public fun capsEscalationsToMostUncertainDetections() =
        runBlocking {
            val detections =
                listOf(
                    item("A", confidence = 0.5f, boundingBox = box),
                    item("B", confidence = 0.1f, boundingBox = box),
                    item("C", confidence = 0.3f, boundingBox = box),
                )
            val verifier = RecordingRecognizer { call -> listOf(item(call.originalSku, confidence = 0.99f)) }

            val result =
                TwoPassRecognizer(
                    primary = StaticRecognizer(Result.success(detections)),
                    verifier = verifier,
                    cropper = RecordingCropper(),
                    maxEscalations = 2,
                ).recognize(image, catalog("A", "B", "C"))

            assertEquals(listOf("B", "C"), verifier.calls.map { it.originalSku })
            assertEquals(0.5f, result.getOrThrow()[0].confidence)
            assertEquals(0.99f, result.getOrThrow()[1].confidence)
            assertEquals(0.99f, result.getOrThrow()[2].confidence)
        }

    @Test
    public fun cropperUsesOriginalWhenPresentAndDownscaledFallbackOtherwise() =
        runBlocking {
            val original = CapturedImage(bytes = byteArrayOf(9), width = 200, height = 200, mimeType = "image/jpeg")
            val downscaled = image.copy(original = original)
            val cropper = RecordingCropper()

            TwoPassRecognizer(
                StaticRecognizer(Result.success(listOf(item("SKU-1", confidence = 0.1f, boundingBox = box)))),
                RecordingRecognizer { listOf(item("SKU-1", confidence = 0.99f)) },
                cropper,
            ).recognize(downscaled, catalog("SKU-1"))

            assertSame(original, cropper.calls.single().image)

            val fallbackCropper = RecordingCropper()
            TwoPassRecognizer(
                StaticRecognizer(Result.success(listOf(item("SKU-1", confidence = 0.1f, boundingBox = box)))),
                RecordingRecognizer { listOf(item("SKU-1", confidence = 0.99f)) },
                fallbackCropper,
            ).recognize(image, catalog("SKU-1"))

            assertSame(image, fallbackCropper.calls.single().image)
        }

    private class StaticRecognizer(
        private val result: Result<List<RecognizedItem>>,
    ) : Recognizer {
        override suspend fun recognize(
            image: CapturedImage,
            catalog: List<CatalogItem>,
        ): Result<List<RecognizedItem>> = result
    }

    private class RecordingRecognizer(
        private val response: (Call) -> List<RecognizedItem> = { emptyList() },
    ) : Recognizer {
        val calls = mutableListOf<Call>()

        override suspend fun recognize(
            image: CapturedImage,
            catalog: List<CatalogItem>,
        ): Result<List<RecognizedItem>> =
            runCatching {
                val originalSku = image.mimeType.removePrefix("crop/").takeIf(String::isNotEmpty)
                val call = Call(image, catalog, originalSku)
                calls += call
                response(call)
            }
    }

    private class RecordingCropper(
        private val nullForSkus: Set<String?> = emptySet(),
    ) : ImageCropper {
        val calls = mutableListOf<CropCall>()

        override suspend fun crop(
            image: CapturedImage,
            box: BoundingBox,
        ): CapturedImage? {
            val sku = boxSku(box)
            calls += CropCall(image, box)
            if (sku in nullForSkus) {
                return null
            }
            return CapturedImage(bytes = byteArrayOf(calls.size.toByte()), width = 10, height = 10, mimeType = "crop/${sku.orEmpty()}")
        }
    }

    private data class Call(
        val image: CapturedImage,
        val catalog: List<CatalogItem>,
        val originalSku: String?,
    )

    private data class CropCall(
        val image: CapturedImage,
        val box: BoundingBox,
    )

    private fun item(
        sku: String?,
        confidence: Float,
        boundingBox: BoundingBox? = null,
        occluded: Boolean = false,
        possiblyMore: Boolean = false,
        alternates: List<String> = emptyList(),
    ): RecognizedItem =
        RecognizedItem(
            sku = sku,
            quantity = 1,
            confidence = confidence,
            boundingBox = boundingBox?.taggedFor(sku),
            occluded = occluded,
            possiblyMore = possiblyMore,
            alternates = alternates,
        )

    private fun BoundingBox.taggedFor(sku: String?): BoundingBox =
        copy(left = skuTag(sku))

    private fun catalog(
        vararg skus: String,
        inactive: Set<String> = emptySet(),
        grouped: Set<String> = emptySet(),
    ): List<CatalogItem> =
        skus.mapIndexed { index, sku ->
            CatalogItem(
                id = index.toLong(),
                sku = sku,
                name = sku,
                priceMinor = 1_000,
                active = sku !in inactive,
                photos = emptyList(),
                createdAtEpochMillis = 1L,
                confusionGroup = if (sku in grouped) "lookalike" else null,
            )
        }

    private companion object {
        val tagBySku = mutableMapOf<String?, Float>()
        val skuByTag = mutableMapOf<Float, String?>()

        fun boxSku(box: BoundingBox): String? =
            skuByTag[box.left]

        fun skuTag(sku: String?): Float {
            val existing = tagBySku[sku]
            if (existing != null) {
                return existing
            }
            val next = (tagBySku.size + 1) / 100f
            tagBySku[sku] = next
            skuByTag[next] = sku
            return next
        }
    }
}
