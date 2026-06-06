package com.cashierapp.photocheckout.ui.catalog.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cashierapp.photocheckout.domain.catalog.CatalogRepository
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.money.IdrFormat
import com.cashierapp.photocheckout.domain.usecase.SetProductActive
import com.cashierapp.photocheckout.domain.usecase.UpdateProduct
import com.cashierapp.photocheckout.domain.usecase.UpdateProductInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
public class ProductDetailViewModel
    @Inject
    constructor(
        private val catalogRepository: CatalogRepository,
        private val updateProduct: UpdateProduct,
        private val setProductActive: SetProductActive,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ProductDetailUiState())
        public val uiState: StateFlow<ProductDetailUiState> = mutableState

        private var productId: Long? = null

        public fun load(productId: Long) {
            this.productId = productId
            reload()
        }

        public fun onNameChange(value: String) {
            mutableState.update { it.copy(nameInput = value) }
        }

        public fun onPriceChange(value: String) {
            mutableState.update { it.copy(priceInput = value) }
        }

        public fun saveEdits() {
            val state = mutableState.value
            val product = state.product ?: return
            viewModelScope.launch {
                updateProduct(
                    UpdateProductInput(
                        productId = product.id,
                        name = state.nameInput,
                        priceMinor = IdrFormat.parse(state.priceInput),
                        photoPaths = product.photos.map { it.path },
                    ),
                )
                reload()
            }
        }

        public fun addPlaceholderPhoto() {
            val product = mutableState.value.product ?: return
            if (product.photos.size >= MAX_PHOTOS) return
            viewModelScope.launch {
                updateProduct(
                    UpdateProductInput(
                        productId = product.id,
                        name = mutableState.value.nameInput,
                        priceMinor = IdrFormat.parse(mutableState.value.priceInput),
                        photoPaths = product.photos.map { it.path } + "detail-${product.photos.size + 1}.jpg",
                    ),
                )
                reload()
            }
        }

        public fun removeLastPhoto() {
            val product = mutableState.value.product ?: return
            if (product.photos.size <= 1) return
            viewModelScope.launch {
                updateProduct(
                    UpdateProductInput(
                        productId = product.id,
                        name = mutableState.value.nameInput,
                        priceMinor = IdrFormat.parse(mutableState.value.priceInput),
                        photoPaths = product.photos.dropLast(1).map { it.path },
                    ),
                )
                reload()
            }
        }

        public fun toggleActive() {
            val product = mutableState.value.product ?: return
            viewModelScope.launch {
                setProductActive(
                    productId = product.id,
                    active = !product.active,
                    changedAt = System.currentTimeMillis(),
                )
                reload()
            }
        }

        private fun reload() {
            val id = productId ?: return
            viewModelScope.launch {
                val product = catalogRepository.getById(id)
                mutableState.value =
                    ProductDetailUiState(
                        product = product,
                        nameInput = product?.name.orEmpty(),
                        priceInput = product?.priceMinor?.let(IdrFormat::format).orEmpty(),
                    )
            }
        }

        private companion object {
            const val MAX_PHOTOS = 3
        }
    }

public data class ProductDetailUiState(
    val product: CatalogItem? = null,
    val nameInput: String = "",
    val priceInput: String = "",
)
