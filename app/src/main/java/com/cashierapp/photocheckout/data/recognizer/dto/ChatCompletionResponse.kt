package com.cashierapp.photocheckout.data.recognizer.dto

import kotlinx.serialization.Serializable

/** OpenAI-compatible chat-completions response. The model's JSON payload is a string in [content]. */
@Serializable
public data class ChatCompletionResponse(
    val choices: List<Choice> = emptyList(),
)

@Serializable
public data class Choice(
    val message: ResponseMessage,
)

@Serializable
public data class ResponseMessage(
    val content: String,
)

/** The structured payload the model returns inside the message content. */
@Serializable
public data class RecognitionPayload(
    val items: List<RecognizedItemDto> = emptyList(),
)

@Serializable
public data class RecognizedItemDto(
    val sku: String,
    val quantity: Int = 1,
    val confidence: Float = 0f,
    val box: List<Float>? = null,
)
