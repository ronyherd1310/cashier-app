package com.cashierapp.photocheckout.di

import com.cashierapp.photocheckout.data.config.RecognizerConfig
import com.cashierapp.photocheckout.data.recognizer.OPENROUTER_BASE_URL
import com.cashierapp.photocheckout.data.recognizer.OpenRouterApi
import com.cashierapp.photocheckout.data.recognizer.OpenRouterRecognizer
import com.cashierapp.photocheckout.data.recognizer.StubRecognizer
import com.cashierapp.photocheckout.domain.recognizer.Recognizer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton

private const val NETWORK_TIMEOUT_SECONDS = 30L

/**
 * Selects the active [Recognizer] from config — the single swap point (X-2). Adding a
 * new provider is one impl plus a branch here; nothing in `domain/`, `pricing/`, or
 * `ui/` changes. With an API key set the cloud OpenRouter impl runs; otherwise the
 * network-free stub keeps the capture→draft flow demonstrable.
 */
@Module
@InstallIn(SingletonComponent::class)
public object RecognizerModule {
    @Provides
    @Singleton
    public fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    public fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .callTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    public fun provideOpenRouterApi(
        client: OkHttpClient,
        json: Json,
    ): OpenRouterApi =
        Retrofit
            .Builder()
            .baseUrl(OPENROUTER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenRouterApi::class.java)

    @Provides
    public fun provideRecognizer(
        config: RecognizerConfig,
        openRouter: Provider<OpenRouterRecognizer>,
        stub: Provider<StubRecognizer>,
    ): Recognizer = if (config.hasApiKey()) openRouter.get() else stub.get()
}
