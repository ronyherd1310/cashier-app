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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

public class OpenRouterRecognizerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private lateinit var recognizer: OpenRouterRecognizer
    private lateinit var api: OpenRouterApi
    private lateinit var config: RecognizerConfig
    private lateinit var photoStorage: PhotoStorage
    private lateinit var thumbnailStore: ReferenceThumbnailStore

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
        File(context.filesDir, "product_photos").deleteRecursively()
        File(context.filesDir, "reference_thumbnails").deleteRecursively()
        server = MockWebServer()
        server.start()
        val client =
            OkHttpClient
                .Builder()
                .readTimeout(1, TimeUnit.SECONDS)
                .build()
        api =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenRouterApi::class.java)
        config = RecognizerConfig(context).apply { setApiKey("test-key") }
        photoStorage = PhotoStorage(context)
        thumbnailStore = ReferenceThumbnailStore(photoStorage, context)
        recognizer = newRecognizer()
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
    public fun requestAttachesLabeledReferencePhotosBeforeCaptureWhenCatalogIsUnderCap() =
        runBlocking {
            val coffeePhoto = photoStorage.save(jpegBytes(color = 0xFF7A3B00.toInt()), "coffee.jpg")
            val teaPhoto = photoStorage.save(jpegBytes(color = 0xFF008855.toInt()), "tea.jpg")
            val catalogWithPhotos =
                listOf(
                    catalog[0].copy(photos = listOf(ProductPhoto(coffeePhoto, position = 0))),
                    catalog[1].copy(photos = listOf(ProductPhoto(teaPhoto, position = 0))),
                )
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{\"items\":[]}"}}]}"""))

            recognizer.recognize(image, catalogWithPhotos)

            val parts = recordedContentParts()
            val texts = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            val imageUrls = parts.mapNotNull { it.imageUrl() }

            assertTrue(texts.any { it.contains("Reference photos follow") })
            assertTrue(texts.any { it == "SKU-0001 \u2014 Coffee:" })
            assertTrue(texts.any { it == "SKU-0002 \u2014 Tea:" })
            assertTrue(texts.any { it == "Counter photo to itemize:" })
            assertEquals(3, imageUrls.size)
            assertTrue(imageUrls[0].startsWith("data:image/jpeg;base64,"))
            assertTrue(imageUrls[1].startsWith("data:image/jpeg;base64,"))
            assertEquals("data:image/jpeg;base64,AQIDBA==", imageUrls.last())
            assertTrue(parts.indexOfText("SKU-0001 \u2014 Coffee:") < parts.indexOfImageUrl(imageUrls[0]))
            assertTrue(parts.indexOfText("SKU-0002 \u2014 Tea:") < parts.indexOfImageUrl(imageUrls[1]))
            assertEquals(parts.lastIndex, parts.indexOfImageUrl("data:image/jpeg;base64,AQIDBA=="))
        }

    @Test
    public fun requestOmitsReferencePartsWhenCatalogIsOverCap() =
        runBlocking {
            val photoPath = photoStorage.save(jpegBytes(), "coffee.jpg")
            val catalogWithPhotos = catalog.map { it.copy(photos = listOf(ProductPhoto(photoPath, position = 0))) }
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{\"items\":[]}"}}]}"""))

            newRecognizer(referencePhotoSkuCap = 1).recognize(image, catalogWithPhotos)

            val parts = recordedContentParts()
            val texts = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            val imageUrls = parts.mapNotNull { it.imageUrl() }
            assertEquals(2, parts.size)
            assertTrue(texts.none { it.contains("Reference photos follow") || it.contains("Counter photo to itemize") })
            assertEquals(listOf("data:image/jpeg;base64,AQIDBA=="), imageUrls)
        }

    @Test
    public fun missingReferencePhotoOmitsOnlyThatImageAndStillSucceeds() =
        runBlocking {
            val coffeePhoto = photoStorage.save(jpegBytes(color = 0xFF7A3B00.toInt()), "coffee.jpg")
            val catalogWithMissingPhoto =
                listOf(
                    catalog[0].copy(photos = listOf(ProductPhoto(coffeePhoto, position = 0))),
                    catalog[1].copy(photos = listOf(ProductPhoto("missing.jpg", position = 0))),
                )
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"{\"items\":[]}"}}]}"""))

            val result = recognizer.recognize(image, catalogWithMissingPhoto)

            assertTrue(result.isSuccess)
            val parts = recordedContentParts()
            val texts = parts.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            val imageUrls = parts.mapNotNull { it.imageUrl() }
            assertTrue(texts.any { it == "SKU-0001 \u2014 Coffee:" })
            assertTrue(texts.any { it == "SKU-0002 \u2014 Tea:" })
            assertEquals(2, imageUrls.size)
            assertEquals("data:image/jpeg;base64,AQIDBA==", imageUrls.last())
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
            val noKeyRecognizer = OpenRouterRecognizer(api, noKeyConfig, json, thumbnailStore)

            assertTrue(noKeyRecognizer.recognize(image, catalog).isFailure)
        }

    private fun newRecognizer(referencePhotoSkuCap: Int = REFERENCE_PHOTO_SKU_CAP): OpenRouterRecognizer =
        OpenRouterRecognizer(api, config, json, thumbnailStore, referencePhotoSkuCap)

    private fun recordedContentParts(): JsonArray {
        val body = server.takeRequest().body.readUtf8()
        return json
            .parseToJsonElement(body)
            .jsonObject["messages"]!!
            .jsonArray[0]
            .jsonObject["content"]!!
            .jsonArray
    }

    private fun JsonArray.indexOfText(text: String): Int =
        indexOfFirst { part -> part.jsonObject["text"]?.jsonPrimitive?.content == text }

    private fun JsonArray.indexOfImageUrl(url: String): Int =
        indexOfFirst { part -> part.jsonObject["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content == url }

    private fun kotlinx.serialization.json.JsonElement.imageUrl(): String? =
        jsonObject["image_url"]?.jsonObject?.get("url")?.jsonPrimitive?.content

    private fun jpegBytes(color: Int = 0xFF00AA00.toInt()): ByteArray {
        val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }
}
