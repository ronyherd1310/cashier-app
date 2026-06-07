package com.cashierapp.photocheckout.ui.scan.draft

import com.cashierapp.photocheckout.data.storage.PhotoStorage
import com.cashierapp.photocheckout.domain.model.DraftLine
import com.cashierapp.photocheckout.domain.model.DraftReceipt
import com.cashierapp.photocheckout.domain.model.UnidentifiedItem
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

public class DraftViewModelTest {
    private val photoStorage = mockk<PhotoStorage>(relaxed = true)

    private fun line(sku: String): DraftLine =
        DraftLine(
            sku = sku,
            name = "Item $sku",
            quantity = 1,
            unitPriceMinor = 10_000,
            lineTotalMinor = 10_000,
            confidence = 0.9f,
            lowConfidence = false,
        )

    @Test
    public fun startsWithNoDraft() {
        val viewModel = DraftViewModel(photoStorage)
        assertNull(viewModel.uiState.value.draft)
        assertEquals(0, viewModel.uiState.value.itemCount)
    }

    @Test
    public fun setDraftExposesLinesAndItemCount() {
        val viewModel = DraftViewModel(photoStorage)
        val draft =
            DraftReceipt(
                lines = listOf(line("SKU-0001"), line("SKU-0002")),
                unidentified = listOf(UnidentifiedItem("???", 1, 0.2f)),
                subtotalMinor = 20_000,
                taxMinor = 0,
                totalMinor = 20_000,
            )

        viewModel.setDraft(draft)

        val state = viewModel.uiState.value
        assertEquals(2, state.itemCount)
        assertEquals(1, state.unidentified.size)
        assertEquals(20_000L, state.totalMinor)
    }
}
