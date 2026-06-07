package com.cashierapp.photocheckout.ui.scan.additem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cashierapp.photocheckout.domain.model.CatalogItem
import com.cashierapp.photocheckout.domain.money.IdrFormat
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.TealPrimary
import java.io.File

/**
 * Full-screen Add-Item catalog picker (C2, mockups 11/12). Supports per-row quick
 * add and a multi-select mode with a batch "Add to Draft" action. Prices come from
 * the catalog, never typed (SCAN-8).
 */
@Composable
public fun AddItemScreen(
    state: AddItemUiState,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onQuickAdd: (CatalogItem) -> Unit,
    onAddSelected: (List<CatalogItem>) -> Unit,
    resolvePhotoPath: (String) -> String,
    modifier: Modifier = Modifier,
) {
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.screenPadding),
    ) {
        AddItemTopBar(
            selecting = selecting,
            selectedCount = selected.size,
            onClose = onClose,
            onCancelSelection = {
                selecting = false
                selected.clear()
            },
        )
        OutlinedTextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("add-item-search"),
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            placeholder = { Text("Search products...") },
            shape = RoundedCornerShape(AppDimens.controlRadius),
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.products, key = { it.sku }) { product ->
                AddItemRow(
                    product = product,
                    selecting = selecting,
                    selected = selected.contains(product.sku),
                    resolvePhotoPath = resolvePhotoPath,
                    onQuickAdd = { onQuickAdd(product) },
                    onToggleSelect = {
                        if (selected.contains(product.sku)) selected.remove(product.sku) else selected.add(product.sku)
                    },
                    onLongPress = {
                        selecting = true
                        if (!selected.contains(product.sku)) selected.add(product.sku)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        if (selecting) {
            Button(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("add-item-add-to-draft-button"),
                enabled = selected.isNotEmpty(),
                onClick = {
                    onAddSelected(state.products.filter { selected.contains(it.sku) })
                    selecting = false
                    selected.clear()
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                shape = RoundedCornerShape(AppDimens.controlRadius),
            ) {
                Text("Add to Draft")
            }
            Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        }
    }
}

@Composable
private fun AddItemTopBar(
    selecting: Boolean,
    selectedCount: Int,
    onClose: () -> Unit,
    onCancelSelection: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, modifier = Modifier.testTag("add-item-close-button")) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
        Text(
            modifier = Modifier.weight(1f),
            text = if (selecting) "$selectedCount selected" else "Add Item",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (selecting) {
            TextButton(onClick = onCancelSelection) { Text("Cancel") }
        }
    }
}

@Composable
private fun AddItemRow(
    product: CatalogItem,
    selecting: Boolean,
    selected: Boolean,
    resolvePhotoPath: (String) -> String,
    onQuickAdd: () -> Unit,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { if (selecting) onToggleSelect() else onLongPress() }
                .testTag("add-item-row-${product.sku}")
                .padding(vertical = AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(AppDimens.spaceSm))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            model =
                product.photos
                    .firstOrNull()
                    ?.path
                    ?.let { File(resolvePhotoPath(it)) },
            contentDescription = "${product.name} photo",
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.width(AppDimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = product.sku,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "IDR ${IdrFormat.format(product.priceMinor)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (selecting) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (selected) "Selected" else "Not selected",
                tint = if (selected) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        } else {
            QuickAddButton(onClick = onQuickAdd, sku = product.sku)
        }
    }
}

@Composable
private fun QuickAddButton(
    onClick: () -> Unit,
    sku: String,
) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(AppDimens.spaceSm))
                .background(TealPrimary)
                .clickable(onClick = onClick)
                .testTag("add-item-quick-add-$sku"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add $sku", tint = androidx.compose.ui.graphics.Color.White)
    }
}
