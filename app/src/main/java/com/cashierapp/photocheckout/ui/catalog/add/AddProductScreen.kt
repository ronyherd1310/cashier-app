package com.cashierapp.photocheckout.ui.catalog.add

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.cashierapp.photocheckout.ui.theme.AppDimens

@Composable
public fun AddProductScreen(
    state: AddProductUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(AppDimens.screenPadding),
    ) {
        Text(
            text = "Add Product",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(text = "Step ${state.step} of 3", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

        when (state.step) {
            1 ->
                BasicStep(
                    state = state,
                    onNameChange = onNameChange,
                    onAddPhoto = onAddPhoto,
                )

            2 ->
                PricingStep(
                    state = state,
                    onPriceChange = onPriceChange,
                )

            else -> ReviewStep(state = state)
        }

        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = if (state.step == 1) onBack else onPrevious,
            ) {
                Text(if (state.step == 1) "Cancel" else "Back")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled =
                    when (state.step) {
                        1 -> state.canContinueFromBasic
                        2 -> state.canContinueFromPrice
                        else -> true
                    },
                onClick = if (state.step == 3) onSave else onNext,
            ) {
                Text(if (state.step == 3) "Save Product" else "Next")
            }
        }
    }
}

@Composable
private fun BasicStep(
    state: AddProductUiState,
    onNameChange: (String) -> Unit,
    onAddPhoto: () -> Unit,
) {
    Text("Basic Information", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
    OutlinedButton(onClick = onAddPhoto) {
        Text(if (state.photoPath == null) "Add Photo" else "Photo Added")
    }
    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.name,
        onValueChange = onNameChange,
        label = { Text("Product Name") },
        placeholder = { Text("e.g. Nasi Goreng Spesial") },
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
    Text("SKU is generated automatically and cannot be changed.")
}

@Composable
private fun PricingStep(
    state: AddProductUiState,
    onPriceChange: (String) -> Unit,
) {
    Text("Pricing", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.price,
        onValueChange = onPriceChange,
        label = { Text("Price (IDR)") },
        placeholder = { Text("25.000") },
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
    Text("Price Preview: IDR ${state.price.ifBlank { "0" }}")
}

@Composable
private fun ReviewStep(state: AddProductUiState) {
    Text("Review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
    Text("Product Name")
    Text(state.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
    Text("Price (IDR)")
    Text(state.price, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}
