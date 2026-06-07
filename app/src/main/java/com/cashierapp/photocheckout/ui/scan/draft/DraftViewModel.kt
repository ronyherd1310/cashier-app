package com.cashierapp.photocheckout.ui.scan.draft

import androidx.lifecycle.ViewModel
import com.cashierapp.photocheckout.data.storage.PhotoStorage
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

        public fun resolvePhotoPath(path: String): String = photoStorage.absolutePath(path)
    }
