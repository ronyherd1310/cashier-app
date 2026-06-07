package com.cashierapp.photocheckout.ui.scan.draft

import androidx.compose.runtime.Immutable
import com.cashierapp.photocheckout.domain.model.DraftLine
import com.cashierapp.photocheckout.domain.model.DraftReceipt
import com.cashierapp.photocheckout.domain.model.UnidentifiedItem

/** UI state for the Draft Review screen (S2). */
@Immutable
public data class DraftUiState(
    val draft: DraftReceipt? = null,
) {
    public val lines: List<DraftLine> get() = draft?.lines.orEmpty()
    public val unidentified: List<UnidentifiedItem> get() = draft?.unidentified.orEmpty()

    /** The live count shown in the top bar ("Draft (N item)"). */
    public val itemCount: Int get() = lines.size
    public val subtotalMinor: Long get() = draft?.subtotalMinor ?: 0L
    public val totalMinor: Long get() = draft?.totalMinor ?: 0L
}
