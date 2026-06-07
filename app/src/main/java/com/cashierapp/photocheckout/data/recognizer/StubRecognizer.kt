package com.cashierapp.photocheckout.data.recognizer

import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import com.cashierapp.photocheckout.domain.recognizer.Recognizer
import kotlinx.coroutines.delay
import javax.inject.Inject

private const val SIMULATED_LATENCY_MS = 1_200L
private const val SAMPLE_ITEM_COUNT = 3
private const val LOW_CONFIDENCE_SAMPLE = 0.45f
private const val HIGH_CONFIDENCE_SAMPLE = 0.92f

/**
 * Temporary, network-free [Recognizer] so the capture→draft→edit flow is runnable
 * on a device during Phase 2 (Checkpoint 2) before the cloud impl exists. It echoes
 * a few items from the active catalog with varied confidence so the draft UI
 * (including the low-confidence treatment) is exercisable.
 *
 * TODO(T12): replace this binding with config-driven selection between the real
 * OpenRouterRecognizer (T10) and a test fake. Production must not ship a recognizer
 * that fabricates detections once the cloud impl lands.
 */
public class StubRecognizer
    @Inject
    constructor() : Recognizer {
        override suspend fun recognize(
            image: CapturedImage,
            catalog: List<CatalogItem>,
        ): Result<List<RecognizedItem>> {
            delay(SIMULATED_LATENCY_MS)
            val items =
                catalog.take(SAMPLE_ITEM_COUNT).mapIndexed { index, item ->
                    RecognizedItem(
                        sku = item.sku,
                        quantity = index + 1,
                        confidence = if (index == 1) LOW_CONFIDENCE_SAMPLE else HIGH_CONFIDENCE_SAMPLE,
                    )
                }
            return Result.success(items)
        }
    }
