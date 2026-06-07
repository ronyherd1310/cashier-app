package com.cashierapp.photocheckout.data.recognizer

import com.cashierapp.photocheckout.data.recognizer.dto.ChatCompletionRequest
import com.cashierapp.photocheckout.data.recognizer.dto.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/** Retrofit binding for OpenRouter's OpenAI-compatible chat-completions endpoint. */
public interface OpenRouterApi {
    @POST("chat/completions")
    public suspend fun complete(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest,
    ): ChatCompletionResponse
}
