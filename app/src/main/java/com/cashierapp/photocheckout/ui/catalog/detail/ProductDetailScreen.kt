package com.cashierapp.photocheckout.ui.catalog.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.cashierapp.photocheckout.domain.money.IdrFormat
import com.cashierapp.photocheckout.ui.theme.AppDimens

@Composable
public fun ProductDetailScreen(
    state: ProductDetailUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onSave: () -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onToggleActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val product = state.product
    var showStatusConfirm by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(AppDimens.screenPadding),
    ) {
        if (product == null) {
            Text("Product not found")
            Button(onClick = onBack) { Text("Back") }
            return@Column
        }

        Text("Product Detail", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Text(if (product.active) "Active" else "Inactive")
        Text("Photo 1 / ${product.photos.size.coerceAtLeast(1)}")
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.nameInput,
            onValueChange = onNameChange,
            label = { Text("Product Name") },
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Text("SKU • ${product.sku}")
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.priceInput,
            onValueChange = onPriceChange,
            label = { Text("Price (IDR)") },
        )
        Text("Current: IDR ${IdrFormat.format(product.priceMinor)}")
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Text("Photos: ${product.photos.size} of 3 photos")
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)) {
            OutlinedButton(
                enabled = product.photos.size < 3,
                onClick = onAddPhoto,
            ) {
                Text("Add Photo")
            }
            OutlinedButton(
                enabled = product.photos.size > 1,
                onClick = onRemovePhoto,
            ) {
                Text("Remove Photo")
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onBack,
            ) {
                Text("Back")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onSave,
            ) {
                Text("Edit Product")
            }
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { showStatusConfirm = true },
        ) {
            Text(if (product.active) "Deactivate" else "Reactivate")
        }
    }

    if (product != null && showStatusConfirm) {
        val targetActive = !product.active
        AlertDialog(
            onDismissRequest = { showStatusConfirm = false },
            title = {
                Text(if (targetActive) "Reactivate product?" else "Deactivate product?")
            },
            text = {
                Text(
                    if (targetActive) {
                        "${product.name} will return to the active catalogue."
                    } else {
                        "${product.name} will be hidden from active catalogue results."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleActive()
                        showStatusConfirm = false
                    },
                ) {
                    Text(if (targetActive) "Reactivate" else "Deactivate")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStatusConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
