package com.cashierapp.photocheckout.ui.catalog.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import com.cashierapp.photocheckout.domain.money.IdrFormat
import com.cashierapp.photocheckout.domain.usecase.EnrollProduct
import com.cashierapp.photocheckout.domain.usecase.EnrollProductInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
public class AddProductViewModel
    @Inject
    constructor(
        private val enrollProduct: EnrollProduct,
        private val photoStorage: PhotoStorage,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(AddProductUiState())
        public val uiState: StateFlow<AddProductUiState> = mutableState

        public fun onNameChange(value: String) {
            mutableState.update { it.copy(name = value) }
        }

        public fun onPriceChange(value: String) {
            mutableState.update { it.copy(price = value) }
        }

        public fun addPlaceholderPhoto() {
            val path = photoStorage.save(PLACEHOLDER_PHOTO_BYTES, "reference.jpg")
            mutableState.update { it.copy(photoPath = path) }
        }

        public fun nextStep() {
            mutableState.update { state -> state.copy(step = (state.step + 1).coerceAtMost(3)) }
        }

        public fun previousStep() {
            mutableState.update { state -> state.copy(step = (state.step - 1).coerceAtLeast(1)) }
        }

        public fun save(onSaved: () -> Unit) {
            val state = mutableState.value
            val photoPath = state.photoPath ?: return
            viewModelScope.launch {
                enrollProduct(
                    EnrollProductInput(
                        name = state.name,
                        priceMinor = IdrFormat.parse(state.price),
                        photoPaths = listOf(photoPath),
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                onSaved()
            }
        }

        private companion object {
            val PLACEHOLDER_PHOTO_BYTES = byteArrayOf(1, 2, 3)
        }
    }

public data class AddProductUiState(
    val step: Int = 1,
    val name: String = "",
    val price: String = "",
    val photoPath: String? = null,
) {
    public val canContinueFromBasic: Boolean = name.isNotBlank() && photoPath != null
    public val canContinueFromPrice: Boolean = runCatching { IdrFormat.parse(price) > 0L }.getOrDefault(false)
}
