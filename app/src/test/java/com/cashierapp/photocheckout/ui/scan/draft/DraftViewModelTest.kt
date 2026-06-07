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

    private fun seededViewModel(): DraftViewModel {
        val viewModel = DraftViewModel(photoStorage)
        viewModel.setDraft(
            DraftReceipt(
                lines = listOf(line("SKU-0001"), line("SKU-0002")),
                unidentified = listOf(UnidentifiedItem("???", 1, 0.2f)),
                subtotalMinor = 20_000,
                taxMinor = 0,
                totalMinor = 20_000,
            ),
        )
        return viewModel
    }

    @Test
    public fun updateLineRepricesQuantityAndRecomputesTotals() {
        val viewModel = seededViewModel()

        viewModel.updateLine(sku = "SKU-0001", quantity = 3, note = null)

        val edited =
            viewModel.uiState.value.lines
                .first { it.sku == "SKU-0001" }
        assertEquals(3, edited.quantity)
        assertEquals(30_000L, edited.lineTotalMinor)
        // 30_000 (SKU-0001 x3) + 10_000 (SKU-0002 x1)
        assertEquals(40_000L, viewModel.uiState.value.totalMinor)
    }

    @Test
    public fun updateLineClampsQuantityToAtLeastOne() {
        val viewModel = seededViewModel()
        viewModel.updateLine(sku = "SKU-0001", quantity = 0, note = null)
        assertEquals(
            1,
            viewModel.uiState.value.lines
                .first { it.sku == "SKU-0001" }
                .quantity,
        )
    }

    @Test
    public fun updateLineStoresNote() {
        val viewModel = seededViewModel()
        viewModel.updateLine(sku = "SKU-0001", quantity = 1, note = "extra spicy")
        assertEquals(
            "extra spicy",
            viewModel.uiState.value.lines
                .first { it.sku == "SKU-0001" }
                .note,
        )
    }

    @Test
    public fun removeLineDropsLineAndRecomputesTotals() {
        val viewModel = seededViewModel()
        viewModel.removeLine("SKU-0001")
        assertEquals(1, viewModel.uiState.value.itemCount)
        assertEquals(10_000L, viewModel.uiState.value.totalMinor)
    }

    @Test
    public fun clearResetsDraft() {
        val viewModel = seededViewModel()
        viewModel.clear()
        assertNull(viewModel.uiState.value.draft)
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
