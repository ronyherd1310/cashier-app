package com.cashierapp.photocheckout.ui.catalog.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.cashierapp.photocheckout.ui.theme.TealContainer
import com.cashierapp.photocheckout.ui.theme.TealPrimary
import java.io.File

@Composable
public fun CatalogListScreen(
    state: CatalogListUiState,
    onAddProductClick: () -> Unit,
    onProductClick: (Long) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onStatusFilterChange: (CatalogStatusFilter) -> Unit = {},
    onSortOrderChange: (CatalogSortOrder) -> Unit = {},
    onProductActiveChange: (Long, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var productPendingStatusChange by remember { mutableStateOf<CatalogItem?>(null) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(AppDimens.screenPadding),
    ) {
        CatalogHeader(activeCount = state.activeCount)
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        Button(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            onClick = onAddProductClick,
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
            shape = RoundedCornerShape(AppDimens.controlRadius),
        ) {
            Text(text = "+  Add Product")
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.query,
            onValueChange = onQueryChange,
            enabled = true,
            placeholder = { Text("Search products...") },
            shape = RoundedCornerShape(AppDimens.controlRadius),
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))

        CatalogFilterRow(
            statusFilter = state.statusFilter,
            sortOrder = state.sortOrder,
            onStatusFilterChange = onStatusFilterChange,
            onSortOrderChange = onSortOrderChange,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))

        if (state.visibleProducts.isEmpty()) {
            CatalogEmptyState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
            ) {
                items(
                    items = state.visibleProducts,
                    key = CatalogItem::sku,
                ) { product ->
                    CatalogProductCard(
                        product = product,
                        onClick = { onProductClick(product.id) },
                        onRequestStatusChange = {
                            productPendingStatusChange = product
                        },
                    )
                }
            }
        }
    }

    productPendingStatusChange?.let { product ->
        val targetActive = !product.active
        AlertDialog(
            onDismissRequest = { productPendingStatusChange = null },
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
                TextButton(
                    onClick = {
                        onProductActiveChange(product.id, targetActive)
                        productPendingStatusChange = null
                    },
                ) {
                    Text(if (targetActive) "Reactivate" else "Deactivate")
                }
            },
            dismissButton = {
                TextButton(onClick = { productPendingStatusChange = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CatalogFilterRow(
    statusFilter: CatalogStatusFilter,
    sortOrder: CatalogSortOrder,
    onStatusFilterChange: (CatalogStatusFilter) -> Unit,
    onSortOrderChange: (CatalogSortOrder) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.spaceXs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)) {
            TextButton(onClick = { onStatusFilterChange(CatalogStatusFilter.All) }) {
                Text(if (statusFilter == CatalogStatusFilter.All) "All ✓" else "All")
            }
            TextButton(onClick = { onStatusFilterChange(CatalogStatusFilter.ActiveOnly) }) {
                Text(if (statusFilter == CatalogStatusFilter.ActiveOnly) "Active ✓" else "Active")
            }
            TextButton(onClick = { onStatusFilterChange(CatalogStatusFilter.InactiveOnly) }) {
                Text(if (statusFilter == CatalogStatusFilter.InactiveOnly) "Inactive ✓" else "Inactive")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)) {
            CatalogSortButton(
                label = "Name A-Z",
                sortOrder = CatalogSortOrder.NameAscending,
                selectedSortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
            )
            CatalogSortButton(
                label = "Name Z-A",
                sortOrder = CatalogSortOrder.NameDescending,
                selectedSortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
            )
            CatalogSortButton(
                label = "Price ↑",
                sortOrder = CatalogSortOrder.PriceAscending,
                selectedSortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)) {
            CatalogSortButton(
                label = "Price ↓",
                sortOrder = CatalogSortOrder.PriceDescending,
                selectedSortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
            )
            CatalogSortButton(
                label = "Newest",
                sortOrder = CatalogSortOrder.NewestFirst,
                selectedSortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
            )
            CatalogSortButton(
                label = "Oldest",
                sortOrder = CatalogSortOrder.OldestFirst,
                selectedSortOrder = sortOrder,
                onSortOrderChange = onSortOrderChange,
            )
        }
    }
}

@Composable
private fun CatalogSortButton(
    label: String,
    sortOrder: CatalogSortOrder,
    selectedSortOrder: CatalogSortOrder,
    onSortOrderChange: (CatalogSortOrder) -> Unit,
) {
    TextButton(onClick = { onSortOrderChange(sortOrder) }) {
        Text(if (sortOrder == selectedSortOrder) "$label ✓" else label)
    }
}

@Composable
private fun CatalogHeader(activeCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = "Catalogue",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                modifier = Modifier.testTag("catalog-active-count"),
                text = "$activeCount active items",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = {}) {
            Text(text = "Filter")
        }
    }
}

@Composable
private fun CatalogEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No products yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            Text(
                text = "Add your first product to start checkout.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CatalogProductCard(
    product: CatalogItem,
    onClick: () -> Unit,
    onRequestStatusChange: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("catalog-card-menu-${product.sku}")
                .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(AppDimens.cardRadius),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProductThumbnail(product = product)
            Spacer(modifier = Modifier.width(AppDimens.spaceMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceXs))
                Text(
                    text = product.sku,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Text(
                    text = "IDR ${IdrFormat.format(product.priceMinor)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onRequestStatusChange) {
                    Text(
                        text = if (product.active) "Deactivate" else "Reactivate",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(modifier = Modifier.height(AppDimens.spaceLg))
                Text(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(TealContainer)
                            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceXs),
                    text = if (product.active) "Active" else "Inactive",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TealPrimary,
                )
            }
        }
    }
}

@Composable
private fun ProductThumbnail(product: CatalogItem) {
    val photoPath = product.photos.firstOrNull()?.path

    AsyncImage(
        modifier =
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(AppDimens.spaceMd))
                .background(MaterialTheme.colorScheme.primaryContainer),
        model = photoPath?.let(::File),
        contentDescription = "${product.name} photo",
        contentScale = ContentScale.Crop,
    )
}
