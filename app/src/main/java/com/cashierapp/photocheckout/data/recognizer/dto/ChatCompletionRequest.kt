package com.cashierapp.photocheckout.data.recognizer.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible chat-completions request (OpenRouter). The image is sent as a
 * base64 `data:` URL in an `image_url` part; the catalog as a text part; structured
 * output is requested via [responseFormat]. Vendor DTOs live only in data/recognizer.
 */
@Serializable
public data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
)

@Serializable
public data class ChatMessage(
    val role: String,
    val content: List<ContentPart>,
)

@Serializable
public data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ImageUrl? = null,
)

@Serializable
public data class ImageUrl(
    val url: String,
)

@Serializable
public data class ResponseFormat(
    val type: String,
)
