package com.cashierapp.photocheckout.ui.scan.draft

import androidx.lifecycle.ViewModel
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.model.DraftLine
import com.cashierapp.photocheckout.domain.model.DraftReceipt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Owns the editable draft across the Draft Review screen and its Edit/Add/Discard
 * sub-screens (T6/T7 add the mutations). The draft is seeded once via [setDraft]
 * when the capture flow produces it.
 */
@HiltViewModel
public class DraftViewModel
    @Inject
    constructor(
        private val photoStorage: PhotoStorage,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DraftUiState())
        public val uiState: StateFlow<DraftUiState> = _uiState.asStateFlow()

        public fun setDraft(draft: DraftReceipt) {
            _uiState.value = DraftUiState(draft = draft)
        }

        /** Re-price a line's quantity and note, then recompute totals deterministically (X-1, SCAN-8). */
        public fun updateLine(
            sku: String,
            quantity: Int,
            note: String?,
        ) {
            val clamped = quantity.coerceAtLeast(1)
            mutateLines { lines ->
                lines.map { line ->
                    if (line.sku == sku) {
                        line.copy(
                            quantity = clamped,
                            lineTotalMinor = line.unitPriceMinor * clamped,
                            note = note?.takeIf { it.isNotBlank() },
                        )
                    } else {
                        line
                    }
                }
            }
        }

        public fun removeLine(sku: String) {
            mutateLines { lines -> lines.filterNot { it.sku == sku } }
        }

        /** Add a catalog item to the draft, or increment its quantity if already present (SCAN-8). */
        public fun addCatalogItem(item: CatalogItem) {
            mutateLines { lines ->
                val existing = lines.firstOrNull { it.sku == item.sku }
                if (existing != null) {
                    lines.map { line ->
                        if (line.sku == item.sku) {
                            val quantity = line.quantity + 1
                            line.copy(quantity = quantity, lineTotalMinor = line.unitPriceMinor * quantity)
                        } else {
                            line
                        }
                    }
                } else {
                    lines +
                        DraftLine(
                            sku = item.sku,
                            name = item.name,
                            quantity = 1,
                            unitPriceMinor = item.priceMinor,
                            lineTotalMinor = item.priceMinor,
                            confidence = 1f,
                            lowConfidence = false,
                            photoPath = item.photos.firstOrNull()?.path,
                        )
                }
            }
        }

        public fun clear() {
            _uiState.value = DraftUiState()
        }

        public fun resolvePhotoPath(path: String): String = photoStorage.absolutePath(path)

        private fun mutateLines(transform: (List<DraftLine>) -> List<DraftLine>) {
            val current = _uiState.value.draft ?: return
            val lines = transform(current.lines)
            val subtotal = lines.sumOf { it.lineTotalMinor }
            // MVP has no tax (taxRateBps = 0); total equals subtotal.
            _uiState.value =
                DraftUiState(
                    draft = current.copy(lines = lines, subtotalMinor = subtotal, taxMinor = 0L, totalMinor = subtotal),
                )
        }
    }
