package com.cashierapp.photocheckout.data.recognizer

import android.util.Base64
import android.util.Log
import com.cashierapp.photocheckout.data.config.RecognizerConfig
import com.cashierapp.photocheckout.data.recognizer.dto.ChatCompletionRequest
import com.cashierapp.photocheckout.data.recognizer.dto.ChatMessage
import com.cashierapp.photocheckout.data.recognizer.dto.ContentPart
import com.cashierapp.photocheckout.data.recognizer.dto.ImageUrl
import com.cashierapp.photocheckout.data.recognizer.dto.RecognitionPayload
import com.cashierapp.photocheckout.data.recognizer.dto.ResponseFormat
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import com.cashierapp.photocheckout.domain.recognizer.Recognizer
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val TAG = "OpenRouterRecognizer"

private const val PROMPT =
    "You are a cashier vision assistant. Identify which catalog items appear in the photo and how many of " +
        "each. Use ONLY the SKUs from the catalog list. Respond with JSON of the form " +
        "{\"items\":[{\"sku\":\"SKU-0001\",\"quantity\":1,\"confidence\":0.0}]}. confidence is 0..1. " +
        "Never invent SKUs and never include prices."

/**
 * Cloud [Recognizer] backed by OpenRouter (OpenAI-compatible chat-completions). Sends
 * the downscaled image as a base64 data URL plus a compact catalog list, parses the
 * structured `{items:[...]}` payload, and maps it to [RecognizedItem]s. Any transport
 * or parse failure (incl. timeouts) maps to [Result.failure] with no partial draft
 * (SCAN-3, SCAN-9). All vendor/model detail stays in this package (X-2).
 */
public class OpenRouterRecognizer
    @Inject
    constructor(
        private val api: OpenRouterApi,
        private val config: RecognizerConfig,
        private val json: Json,
    ) : Recognizer {
        override suspend fun recognize(
            image: CapturedImage,
            catalog: List<CatalogItem>,
        ): Result<List<RecognizedItem>> =
            runCatching {
                val apiKey = config.apiKey
                require(!apiKey.isNullOrBlank()) { "OpenRouter API key is not set." }
                Log.d(TAG, "Requesting recognition: model=${config.modelId}, catalog=${catalog.size} items")

                val request =
                    ChatCompletionRequest(
                        model = config.modelId,
                        messages =
                            listOf(
                                ChatMessage(
                                    role = "user",
                                    content =
                                        listOf(
                                            ContentPart(type = "text", text = PROMPT + "\n\n" + catalogContext(catalog)),
                                            ContentPart(type = "image_url", imageUrl = ImageUrl(url = dataUrl(image))),
                                        ),
                                ),
                            ),
                        responseFormat = ResponseFormat(type = "json_object"),
                    )

                val response = api.complete(authorization = "Bearer $apiKey", request = request)
                val content =
                    response.choices
                        .firstOrNull()
                        ?.message
                        ?.content
                        ?: error("No content in recognizer response.")
                Log.d(TAG, "Raw response content: $content")
                val payload = json.decodeFromString(RecognitionPayload.serializer(), content)
                payload.items.map { dto ->
                    RecognizedItem(
                        sku = dto.sku,
                        quantity = dto.quantity,
                        confidence = dto.confidence,
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
                catalog.forEach { item -> append(item.sku).append(" - ").append(item.name).append('\n') }
            }

        private fun dataUrl(image: CapturedImage): String {
            val base64 = Base64.encodeToString(image.bytes, Base64.NO_WRAP)
            return "data:${image.mimeType};base64,$base64"
        }
    }
