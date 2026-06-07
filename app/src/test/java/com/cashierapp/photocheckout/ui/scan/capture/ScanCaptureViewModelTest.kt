package com.cashierapp.photocheckout.ui.scan.capture

import com.cashierapp.photocheckout.MainDispatcherRule
import com.cashierapp.photocheckout.domain.image.ImageDownscaler
import com.cashierapp.photocheckout.domain.model.CapturedImage
import com.cashierapp.photocheckout.domain.recognizer.RecognizedItem
import com.cashierapp.photocheckout.domain.usecase.FakeCatalogRepository
import com.cashierapp.photocheckout.domain.usecase.ScanCounter
import com.cashierapp.photocheckout.recognizer.FakeRecognizer
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@kotlinx.coroutines.ExperimentalCoroutinesApi
public class ScanCaptureViewModelTest {
    @get:Rule
    public val mainDispatcherRule: MainDispatcherRule = MainDispatcherRule()

    private val downscaled =
        CapturedImage(bytes = byteArrayOf(9, 9), width = 1024, height = 768, mimeType = "image/jpeg")

    private val downscaler =
        object : ImageDownscaler {
            override suspend fun downscale(jpegBytes: ByteArray): CapturedImage = downscaled
        }

    private suspend fun seededScanCounter(recognizer: FakeRecognizer): ScanCounter {
        val repo = FakeCatalogRepository()
        repo.insert(sku = "SKU-0001", name = "Coffee", priceMinor = 15_000, photoPaths = emptyList(), createdAt = 0L)
        return ScanCounter(repo, recognizer)
    }

    @Test
    public fun startsInReadyPhase() {
        val recognizer = FakeRecognizer()
        val viewModel = ScanCaptureViewModel(downscaler, ScanCounter(FakeCatalogRepository(), recognizer))
        assertEquals(ScanCapturePhase.Ready, viewModel.uiState.value.phase)
        assertNull(viewModel.uiState.value.draft)
    }

    @Test
    public fun successfulCaptureProducesDraftFromDownscaledImage() =
        runTest(mainDispatcherRule.dispatcher) {
            val recognizer = FakeRecognizer()
            recognizer.returns(listOf(RecognizedItem("SKU-0001", quantity = 2, confidence = 0.9f)))
            val viewModel = ScanCaptureViewModel(downscaler, seededScanCounter(recognizer))

            viewModel.onPhotoCaptured(byteArrayOf(1, 2, 3))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNotNull(state.draft)
            assertEquals(30_000L, state.draft?.totalMinor)
            // SCAN-2: the recognizer received the downscaled image, not the raw bytes
            assertEquals(downscaled, recognizer.lastImage)
        }

    @Test
    public fun recognizerFailureSurfacesErrorAndNoDraft() =
        runTest(mainDispatcherRule.dispatcher) {
            val recognizer = FakeRecognizer()
            recognizer.fails(RuntimeException("network down"))
            val viewModel = ScanCaptureViewModel(downscaler, seededScanCounter(recognizer))

            viewModel.onPhotoCaptured(byteArrayOf(1, 2, 3))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(ScanCapturePhase.Error, state.phase)
            assertNotNull(state.errorMessage)
            assertNull(state.draft)
        }

    @Test
    public fun draftConsumedClearsDraftAndReturnsToReady() =
        runTest(mainDispatcherRule.dispatcher) {
            val recognizer = FakeRecognizer()
            recognizer.returns(listOf(RecognizedItem("SKU-0001", 1, 0.9f)))
            val viewModel = ScanCaptureViewModel(downscaler, seededScanCounter(recognizer))

            viewModel.onPhotoCaptured(byteArrayOf(1))
            advanceUntilIdle()
            assertNotNull(viewModel.uiState.value.draft)

            viewModel.onDraftConsumed()

            assertNull(viewModel.uiState.value.draft)
            assertEquals(ScanCapturePhase.Ready, viewModel.uiState.value.phase)
        }

    @Test
    public fun errorDismissedReturnsToReady() =
        runTest(mainDispatcherRule.dispatcher) {
            val recognizer = FakeRecognizer()
            recognizer.fails(RuntimeException("boom"))
            val viewModel = ScanCaptureViewModel(downscaler, seededScanCounter(recognizer))

            viewModel.onPhotoCaptured(byteArrayOf(1))
            advanceUntilIdle()
            assertEquals(ScanCapturePhase.Error, viewModel.uiState.value.phase)

            viewModel.onErrorDismissed()

            assertEquals(ScanCapturePhase.Ready, viewModel.uiState.value.phase)
            assertTrue(viewModel.uiState.value.errorMessage == null)
        }
}
