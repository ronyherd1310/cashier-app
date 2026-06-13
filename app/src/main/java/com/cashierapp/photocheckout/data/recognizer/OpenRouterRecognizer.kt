package com.cashierapp.photocheckout.data.recognizer

import android.util.Base64
import android.util.Log
import com.cashierapp.photocheckout.data.config.RecognizerConfig
import com.cashierapp.photocheckout.data.image.ReferenceThumbnailStore
import com.cashierapp.photocheckout.data.recognizer.dto.ChatCompletionRequest
import com.cashierapp.photocheckout.data.recognizer.dto.ChatMessage
import com.cashierapp.photocheckout.data.recognizer.dto.ContentPart
import com.cashierapp.photocheckout.data.recognizer.dto.ImageUrl
import com.cashierapp.photocheckout.data.recognizer.dto.RecognitionPayload
import com.cashierapp.photocheckout.data.recognizer.dto.ResponseFormat
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.recognizer.BoundingBox
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import com.cashierapp.photocheckout.domain.recognizer.Recognizer
import kotlinx.serialization.json.Json

private const val TAG = "OpenRouterRecognizer"

private const val PROMPT =
    "You are a cashier vision assistant. Return one entry per physical item instance you can see, not one " +
        "entry per product type. For each instance give its bounding box as [left, top, right, bottom] with " +
        "each value between 0 and 1 using a top-left origin, the matching catalog SKU, and a confidence 0..1. " +
        "If you see three units of the same product, return three entries. Use ONLY SKUs from the catalog list. " +
        "Respond with JSON of the form " +
        "{\"items\":[{\"sku\":\"SKU-0001\",\"box\":[0.10,0.20,0.30,0.40],\"confidence\":0.0}]}. " +
        "Reference photos, when provided, show the actual packaging of catalog products; match the counter photo " +
        "against them. " +
        "Occlusion: Count an item if you can identify it from the visible part. If it is only partially visible, " +
        "set \"occluded\": true and lower its confidence. If you can see an item but cannot identify which catalog " +
        "product it is, return it with \"sku\": null and a box; do not guess and do not omit it. " +
        "Stacks: Items may be stacked or in rows. Count each visible unit by its rim, edge, or lid. If more units " +
        "of the same product may be hidden behind or under what you can see, set \"possiblyMore\": true on one of " +
        "that product's entries. " +
        "Look-alikes: Some catalog products differ only by flavor, color, or size. When you are torn between " +
        "specific SKUs, pick the best match and list close runner-up catalog SKUs in \"alternates\". " +
        "Catalog descriptions and look-alike group lines call out visual differences you should use carefully. " +
        "Never invent SKUs and never include prices."

/**
 * Cloud [Recognizer] backed by OpenRouter (OpenAI-compatible chat-completions). Sends
 * the downscaled image as a base64 data URL plus a compact catalog list, parses the
 * structured `{items:[...]}` payload, and maps it to [RecognizedItem]s. Any transport
 * or parse failure (incl. timeouts) maps to [Result.failure] with no partial draft
 * (SCAN-3, SCAN-9). All vendor/model detail stays in this package (X-2).
 */
public class OpenRouterRecognizer
    constructor(
        private val api: OpenRouterApi,
        private val config: RecognizerConfig,
        private val json: Json,
        private val referenceThumbnailStore: ReferenceThumbnailStore,
        private val referencePhotoSkuCap: Int = REFERENCE_PHOTO_SKU_CAP,
    ) : Recognizer {
        override suspend fun recognize(
            image: CapturedImage,
            catalog: List<CatalogItem>,
        ): Result<List<RecognizedItem>> =
            runCatching {
                val apiKey = config.apiKey
                require(!apiKey.isNullOrBlank()) { "OpenRouter API key is not set." }
                Log.d(TAG, "Requesting recognition: model=${config.modelId}, catalog=${catalog.size} items")

                val referenceParts = referenceParts(catalog)
                val requestContent =
                    buildList {
                        add(ContentPart(type = "text", text = PROMPT + "\n\n" + catalogContext(catalog)))
                        addAll(referenceParts)
                        if (referenceParts.isNotEmpty()) {
                            add(ContentPart(type = "text", text = "Counter photo to itemize:"))
                        }
                        add(ContentPart(type = "image_url", imageUrl = ImageUrl(url = dataUrl(image))))
                    }
                val request =
                    ChatCompletionRequest(
                        model = config.modelId,
                        messages =
                            listOf(
                                ChatMessage(
                                    role = "user",
                                    content = requestContent,
                                ),
                            ),
                        responseFormat = ResponseFormat(type = "json_object"),
                    )
                val requestBodyBytes = json.encodeToString(ChatCompletionRequest.serializer(), request).toByteArray().size
                val attachedReferenceCount = referenceParts.count { it.imageUrl != null }
                Log.d(TAG, "Reference photos attached=$attachedReferenceCount requestBytes=$requestBodyBytes")

                val response = api.complete(authorization = "Bearer $apiKey", request = request)
                val content =
                    response.choices
                        .firstOrNull()
                        ?.message
                        ?.content
                        ?: error("No content in recognizer response.")
                Log.d(TAG, "Raw response content: $content")
                val payload = json.decodeFromString(RecognitionPayload.serializer(), content)
                val catalogSkus = catalog.mapTo(HashSet()) { it.sku }
                payload.items.map { dto ->
                    RecognizedItem(
                        sku = dto.sku,
                        quantity = dto.quantity,
                        confidence = dto.confidence,
                        boundingBox = dto.box?.toBoundingBoxOrNull(),
                        occluded = dto.occluded,
                        possiblyMore = dto.possiblyMore,
                        alternates = dto.alternates.filter { it in catalogSkus },
                    )
                }
            }.onSuccess { items ->
                Log.d(TAG, "Recognition succeeded: ${items.size} item(s) -> $items")
            }.onFailure { error ->
                Log.e(TAG, "Recognition failed: ${error.message}", error)
            }

        /** Candidate-narrowing seam (MVP: full active catalog as compact {sku, name} text). */
        private fun catalogContext(catalog: List<CatalogItem>): String =
            buildString {
                append("Catalog:\n")
                catalog.forEach { item ->
                    append(item.sku).append(" - ").append(item.name)
                    item.description?.takeIf(String::isNotBlank)?.let { description ->
                        append(" | ").append(description)
                    }
                    append('\n')
                }
                val lookAlikeGroups =
                    catalog
                        .asSequence()
                        .filter(CatalogItem::active)
                        .mapNotNull { item ->
                            item.confusionGroup
                                ?.takeIf(String::isNotBlank)
                                ?.let { group -> group to item.sku }
                        }.groupBy(
                            keySelector = { (group, _) -> group },
                            valueTransform = { (_, sku) -> sku },
                        ).values
                        .filter { skus -> skus.size >= 2 }
                        .toList()
                if (lookAlikeGroups.isNotEmpty()) {
                    append('\n')
                    append("Look-alike groups (distinguish carefully by label color/text):\n")
                    lookAlikeGroups.forEach { skus ->
                        append("- ").append(skus.joinToString()).append('\n')
                    }
                }
            }

        private suspend fun referenceParts(catalog: List<CatalogItem>): List<ContentPart> {
            if (catalog.size > referencePhotoSkuCap) {
                return emptyList()
            }
            val parts = mutableListOf<ContentPart>()
            catalog.forEach { item ->
                val primaryPhoto = item.photos.minByOrNull { it.position } ?: return@forEach
                parts += ContentPart(type = "text", text = "${item.sku} \u2014 ${item.name}:")
                val thumbnail = referenceThumbnailStore.thumbnailFor(primaryPhoto.path)
                if (thumbnail != null) {
                    parts += ContentPart(type = "image_url", imageUrl = ImageUrl(url = dataUrl(thumbnail)))
                }
            }
            if (parts.isEmpty()) {
                return emptyList()
            }
            return listOf(
                ContentPart(type = "text", text = "Reference photos follow, one per labeled SKU. Then the counter photo."),
            ) + parts
        }

        private fun dataUrl(image: CapturedImage): String {
            val base64 = Base64.encodeToString(image.bytes, Base64.NO_WRAP)
            return "data:${image.mimeType};base64,$base64"
        }

        private fun List<Float>.toBoundingBoxOrNull(): BoundingBox? {
            if (size != 4 || any { !it.isFinite() || it !in 0f..1f }) {
                return null
            }
            val (left, top, right, bottom) = this
            if (left >= right || top >= bottom) {
                return null
            }
            return BoundingBox(left = left, top = top, right = right, bottom = bottom)
        }
    }
