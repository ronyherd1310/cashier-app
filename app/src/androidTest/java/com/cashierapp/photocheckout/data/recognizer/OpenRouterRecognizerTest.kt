package com.cashierapp.photocheckout.data.recognizer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cashierapp.photocheckout.data.config.RecognizerConfig
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

public class OpenRouterRecognizerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer
    private lateinit var recognizer: OpenRouterRecognizer

    private val image =
        CapturedImage(bytes = byteArrayOf(1, 2, 3, 4), width = 1024, height = 768, mimeType = "image/jpeg")
    private val catalog =
        listOf(
            CatalogItem(1, "SKU-0001", "Coffee", 15_000, true, emptyList(), 0L),
            CatalogItem(2, "SKU-0002", "Tea", 10_000, true, emptyList(), 0L),
        )

    @Before
    public fun setUp() {
        context.deleteSharedPreferences("recognizer_config")
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        val client =
            OkHttpClient
                .Builder()
                .readTimeout(1, TimeUnit.SECONDS)
                .build()
        val api =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenRouterApi::class.java)
        val config = RecognizerConfig(context).apply { setApiKey("test-key") }
        recognizer = OpenRouterRecognizer(api, config, json)
    }

    @After
    public fun tearDown() {
        server.shutdown()
    }

    @Test
    public fun successResponseParsesIntoRecognizedItems() =
        runBlocking {
            val content = """{\"items\":[{\"sku\":\"SKU-0001\",\"quantity\":2,\"confidence\":0.91}]}"""
            server.enqueue(
                MockResponse().setBody(
                    """{"choices":[{"message":{"content":"$content"}}]}""",
                ),
            )

            val result = recognizer.recognize(image, catalog)

            assertTrue(result.isSuccess)
            val items = result.getOrThrow()
            assertEquals(1, items.size)
            assertEquals("SKU-0001", items[0].sku)
            assertEquals(2, items[0].quantity)
            assertEquals(0.91f, items[0].confidence)
        }

    @Test
    public fun requestEncodesImageAndCatalog() =
        runBlocking {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{\"items\":[]}"}}]}"""))

            recognizer.recognize(image, catalog)

            val recorded = server.takeRequest()
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("data:image/jpeg;base64,"))
            assertTrue(body.contains("SKU-0001"))
            assertTrue(body.contains("Coffee"))
            assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        }

    @Test
    public fun httpErrorMapsToFailure() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500))
            assertTrue(recognizer.recognize(image, catalog).isFailure)
        }

    @Test
    public fun timeoutMapsToFailure() =
        runBlocking {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertTrue(recognizer.recognize(image, catalog).isFailure)
        }

    @Test
    public fun missingApiKeyMapsToFailure() =
        runBlocking {
            val json = Json { ignoreUnknownKeys = true }
            val api =
                Retrofit
                    .Builder()
                    .baseUrl(server.url("/"))
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(OpenRouterApi::class.java)
            val noKeyConfig = RecognizerConfig(context).apply { setApiKey(null) }
            val noKeyRecognizer = OpenRouterRecognizer(api, noKeyConfig, json)

            assertTrue(noKeyRecognizer.recognize(image, catalog).isFailure)
        }
}
