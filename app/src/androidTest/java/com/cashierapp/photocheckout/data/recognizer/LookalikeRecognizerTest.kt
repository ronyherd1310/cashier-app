package com.cashierapp.photocheckout.data.recognizer

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.cashierapp.photocheckout.data.config.RecognizerConfig
import com.cashierapp.photocheckout.data.image.ReferenceThumbnailStore
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.model.ProductPhoto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File

public class LookalikeRecognizerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private lateinit var recognizer: OpenRouterRecognizer
    private lateinit var photoStorage: PhotoStorage

    @Before
    public fun setUp() {
        context.deleteSharedPreferences("recognizer_config")
        File(context.filesDir, "product_photos").deleteRecursively()
        File(context.filesDir, "reference_thumbnails").deleteRecursively()
        server = MockWebServer()
        server.start()
        photoStorage = PhotoStorage(context)
        val api =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .client(OkHttpClient())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenRouterApi::class.java)
        val config = RecognizerConfig(context).apply { setApiKey("test-key") }
        recognizer = OpenRouterRecognizer(api, config, json, ReferenceThumbnailStore(photoStorage, context))
    }

    @After
    public fun tearDown() {
        server.shutdown()
    }

    @Test
    public fun lookalikeRequestPairsEachVariantThumbnailWithItsOwnSkuLabel() =
        runBlocking {
            server.enqueue(MockResponse().setBody(responseBody(sku = "SKU-0001", confidence = 0.93f)))

            val result = recognizer.recognize(counterImage(), lookalikeCatalog())

            assertEquals("SKU-0001", result.getOrThrow().single().sku)
            val parts =
                json
                    .parseToJsonElement(server.takeRequest().body.readUtf8())
                    .jsonObject["messages"]!!
                    .jsonArray[0]
                    .jsonObject["content"]!!
                    .jsonArray
            val chocoLabelIndex = parts.indexOfText("SKU-0001 \u2014 Choco Wafer:")
            val vanillaLabelIndex = parts.indexOfText("SKU-0002 \u2014 Vanilla Wafer:")
            assertTrue(chocoLabelIndex >= 0)
            assertTrue(vanillaLabelIndex >= 0)
            assertTrue(parts[chocoLabelIndex + 1].isImageUrl())
            assertTrue(parts[vanillaLabelIndex + 1].isImageUrl())
            assertEquals("data:image/jpeg;base64,CQgHBg==", parts.last().imageUrl())
        }

    @Test
    public fun mockedWrongVariantResponseStillParsesAsCatalogSku() =
        runBlocking {
            server.enqueue(MockResponse().setBody(responseBody(sku = "SKU-0002", confidence = 0.41f)))

            val result = recognizer.recognize(counterImage(), lookalikeCatalog())

            assertTrue(result.isSuccess)
            val item = result.getOrThrow().single()
            assertEquals("SKU-0002", item.sku)
            assertEquals(0.41f, item.confidence)
        }

    private fun lookalikeCatalog(): List<CatalogItem> {
        val chocoPhoto = photoStorage.save(jpegBytes(color = 0xFF5D2A00.toInt()), "choco.jpg")
        val vanillaPhoto = photoStorage.save(jpegBytes(color = 0xFFE7C66A.toInt()), "vanilla.jpg")
        return listOf(
            CatalogItem(
                id = 1,
                sku = "SKU-0001",
                name = "Choco Wafer",
                priceMinor = 12_000,
                active = true,
                photos = listOf(ProductPhoto(chocoPhoto, position = 0)),
                createdAtEpochMillis = 0L,
            ),
            CatalogItem(
                id = 2,
                sku = "SKU-0002",
                name = "Vanilla Wafer",
                priceMinor = 12_000,
                active = true,
                photos = listOf(ProductPhoto(vanillaPhoto, position = 0)),
                createdAtEpochMillis = 0L,
            ),
        )
    }

    private fun counterImage(): CapturedImage =
        CapturedImage(bytes = byteArrayOf(9, 8, 7, 6), width = 512, height = 512, mimeType = "image/jpeg")

    private fun responseBody(
        sku: String,
        confidence: Float,
    ): String {
        val content = """{\"items\":[{\"sku\":\"$sku\",\"quantity\":1,\"confidence\":$confidence}]}"""
        return """{"choices":[{"message":{"content":"$content"}}]}"""
    }

    private fun kotlinx.serialization.json.JsonArray.indexOfText(text: String): Int =
        indexOfFirst { part -> part.jsonObject["text"]?.jsonPrimitive?.content == text }

    private fun kotlinx.serialization.json.JsonElement.isImageUrl(): Boolean = imageUrl()?.startsWith("data:image/jpeg;base64,") == true

    private fun kotlinx.serialization.json.JsonElement.imageUrl(): String? =
        jsonObject["image_url"]
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.content

    private fun jpegBytes(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }
}
