package com.cashierapp.photocheckout.ui.catalog.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashierapp.photocheckout.data.storage.PhotoStorage
import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import com.cashierapp.photocheckout.domain.catalog.SkuGenerator
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
        private val catalogRepository: CatalogRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(AddProductUiState())
        public val uiState: StateFlow<AddProductUiState> = mutableState

        init {
            refreshPreviewSku()
        }

        public fun reset() {
            mutableState.value = AddProductUiState()
            refreshPreviewSku()
        }

        private fun refreshPreviewSku() {
            viewModelScope.launch {
                val previewSku = SkuGenerator.generate(catalogRepository.nextSkuSequence())
                mutableState.update { it.copy(previewSku = previewSku) }
            }
        }

        public fun onNameChange(value: String) {
            mutableState.update { it.copy(name = value) }
        }

        public fun onPriceChange(value: String) {
            mutableState.update { it.copy(price = value) }
        }

        public fun onPhotoCaptured(bytes: ByteArray) {
            val path = photoStorage.save(bytes, "reference.jpg")
            mutableState.update {
                it.copy(photoPath = path, photoAbsolutePath = photoStorage.absolutePath(path))
            }
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
    }

public data class AddProductUiState(
    val step: Int = 1,
    val name: String = "",
    val price: String = "",
    val photoPath: String? = null,
    val photoAbsolutePath: String? = null,
    val previewSku: String = "",
) {
    public val canContinueFromBasic: Boolean = name.isNotBlank() && photoPath != null
    public val canContinueFromPrice: Boolean = runCatching { IdrFormat.parse(price) > 0L }.getOrDefault(false)
}
