package com.cashierapp.photocheckout.data.recognizer

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.cashierapp.photocheckout.data.config.RecognizerConfig
import com.cashierapp.photocheckout.data.image.ReferenceThumbnailStore
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.model.ProductPhoto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "LookalikeAccuracyCheck"

/**
 * On-demand real-provider A/B check for R1. Normal CI/device runs skip unless a key
 * is supplied:
 *
 * `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew app:connectedDebugAndroidTest \
 * -Pandroid.testInstrumentationRunnerArguments.class=com.cashierapp.photocheckout.data.recognizer.LookalikeAccuracyCheck \
 * -Pandroid.testInstrumentationRunnerArguments.openrouterApiKey=$OPENROUTER_API_KEY`
 *
 * The generated look-alike images are mechanical stand-ins. Replace them with
 * owner-supplied real fixture photos before recording product accuracy numbers.
 */
public class LookalikeAccuracyCheck {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var photoStorage: PhotoStorage
    private lateinit var api: OpenRouterApi
    private lateinit var config: RecognizerConfig

    @Before
    public fun setUp() {
        File(context.filesDir, "product_photos").deleteRecursively()
        File(context.filesDir, "reference_thumbnails").deleteRecursively()
        context.deleteSharedPreferences("recognizer_config")
        photoStorage = PhotoStorage(context)
        api =
            Retrofit
                .Builder()
                .baseUrl(OPENROUTER_BASE_URL)
                .client(
                    OkHttpClient
                        .Builder()
                        .callTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build(),
                ).addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenRouterApi::class.java)
        config = RecognizerConfig(context)
    }

    @Test
    public fun compareLookalikeRecognitionWithReferencesOnAndOff() =
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            val apiKey = args.getString("openrouterApiKey")
            assumeTrue("Set openrouterApiKey instrumentation argument to run real-provider A/B.", !apiKey.isNullOrBlank())
            config.setApiKey(apiKey)
            config.setModelId(args.getString("openrouterModelId"))

            val catalog = lookalikeCatalog()
            val capture = counterImage()

            runArm(name = "references_on", cap = REFERENCE_PHOTO_SKU_CAP, image = capture, catalog = catalog)
            runArm(name = "references_off", cap = 0, image = capture, catalog = catalog)
        }

    private suspend fun runArm(
        name: String,
        cap: Int,
        image: CapturedImage,
        catalog: List<CatalogItem>,
    ) {
        val recognizer = OpenRouterRecognizer(api, config, json, ReferenceThumbnailStore(photoStorage, context), cap)
        val started = System.nanoTime()
        val result = recognizer.recognize(image, catalog).getOrThrow()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val first = result.firstOrNull()
        Log.i(
            TAG,
            "R1_AB arm=$name sku=${first?.sku ?: "none"} confidence=${first?.confidence ?: "none"} " +
                "latencyMs=$elapsedMs requestBytes=see OpenRouterRecognizer debug log",
        )
    }

    private fun lookalikeCatalog(): List<CatalogItem> {
        val chocoPhoto = photoStorage.save(jpegBytes(color = 0xFF5D2A00.toInt()), "choco.jpg")
        val vanillaPhoto = photoStorage.save(jpegBytes(color = 0xFFE7C66A.toInt()), "vanilla.jpg")
        return listOf(
            CatalogItem(1, "SKU-0001", "Choco Wafer", 12_000, true, listOf(ProductPhoto(chocoPhoto, 0)), 0L),
            CatalogItem(2, "SKU-0002", "Vanilla Wafer", 12_000, true, listOf(ProductPhoto(vanillaPhoto, 0)), 0L),
        )
    }

    private fun counterImage(): CapturedImage = CapturedImage(jpegBytes(color = 0xFF5D2A00.toInt()), 320, 180, "image/jpeg")

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
