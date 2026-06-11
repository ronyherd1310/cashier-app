package com.cashierapp.photocheckout.ui.catalog.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    resolvePhotoPath: (String) -> String = { it },
    modifier: Modifier = Modifier,
) {
    var productPendingStatusChange by remember { mutableStateOf<CatalogItem?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(AppDimens.screenPadding),
    ) {
        CatalogHeader(
            activeCount = state.activeCount,
            onFilterClick = { showFilterSheet = true },
        )
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
        CatalogSearchField(
            query = state.query,
            onQueryChange = onQueryChange,
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
                        resolvePhotoPath = resolvePhotoPath,
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        CatalogFilterSheet(
            statusFilter = state.statusFilter,
            sortOrder = state.sortOrder,
            onApply = { status, sort ->
                onStatusFilterChange(status)
                onSortOrderChange(sort)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        )
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
private fun CatalogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        placeholder = {
            Text(
                text = "Search products...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shape = RoundedCornerShape(AppDimens.controlRadius),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = TealPrimary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogFilterSheet(
    statusFilter: CatalogStatusFilter,
    sortOrder: CatalogSortOrder,
    onApply: (CatalogStatusFilter, CatalogSortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingStatus by remember { mutableStateOf(statusFilter) }
    var pendingSort by remember { mutableStateOf(sortOrder) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(bottom = AppDimens.spaceXl),
        ) {
            Text(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = AppDimens.spaceMd),
                text = "Filter & Sort",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            FilterSectionTitle("Status")
            CatalogStatusFilter.entries.forEach { option ->
                FilterOptionRow(
                    label = statusLabel(option),
                    selected = pendingStatus == option,
                    onSelect = { pendingStatus = option },
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            FilterSectionTitle("Sort By")
            CatalogSortOrder.entries.forEach { option ->
                FilterOptionRow(
                    label = sortLabel(option),
                    selected = pendingSort == option,
                    onSelect = { pendingSort = option },
                )
            }

            Spacer(modifier = Modifier.height(AppDimens.spaceLg))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)) {
                OutlinedButton(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(52.dp),
                    onClick = {
                        pendingStatus = CatalogStatusFilter.All
                        pendingSort = CatalogSortOrder.NameAscending
                    },
                    shape = RoundedCornerShape(AppDimens.controlRadius),
                ) {
                    Text("Reset")
                }
                Button(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(52.dp),
                    onClick = { onApply(pendingStatus, pendingSort) },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(AppDimens.controlRadius),
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun FilterSectionTitle(text: String) {
    Text(
        modifier = Modifier.padding(vertical = AppDimens.spaceSm),
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun FilterOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(vertical = AppDimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(AppDimens.spaceSm))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun statusLabel(status: CatalogStatusFilter): String =
    when (status) {
        CatalogStatusFilter.All -> "All"
        CatalogStatusFilter.ActiveOnly -> "Active Only"
        CatalogStatusFilter.InactiveOnly -> "Inactive Only"
    }

private fun sortLabel(sort: CatalogSortOrder): String =
    when (sort) {
        CatalogSortOrder.NameAscending -> "Name (A–Z)"
        CatalogSortOrder.NameDescending -> "Name (Z–A)"
        CatalogSortOrder.PriceAscending -> "Price (Low → High)"
        CatalogSortOrder.PriceDescending -> "Price (High → Low)"
        CatalogSortOrder.NewestFirst -> "Newest First"
        CatalogSortOrder.OldestFirst -> "Oldest First"
    }

@Composable
private fun CatalogHeader(
    activeCount: Int,
    onFilterClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
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
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm)) {
            HeaderIconButton(
                icon = Icons.Default.Search,
                contentDescription = "Search products",
                onClick = {},
            )
            HeaderIconButton(
                icon = Icons.Default.FilterList,
                contentDescription = "Filter and sort",
                onClick = onFilterClick,
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(AppDimens.spaceMd))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(AppDimens.spaceMd),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    resolvePhotoPath: (String) -> String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
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
            ProductThumbnail(product = product, resolvePhotoPath = resolvePhotoPath)
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
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                CatalogCardMenu(
                    product = product,
                    onEdit = onClick,
                    onRequestStatusChange = onRequestStatusChange,
                )
                StatusBadge(active = product.active)
            }
        }
    }
}

@Composable
private fun CatalogCardMenu(
    product: CatalogItem,
    onEdit: () -> Unit,
    onRequestStatusChange: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            modifier = Modifier.testTag("catalog-card-menu-${product.sku}"),
            onClick = { expanded = true },
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options for ${product.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(if (product.active) "Deactivate" else "Reactivate") },
                onClick = {
                    expanded = false
                    onRequestStatusChange()
                },
            )
        }
    }
}

@Composable
private fun StatusBadge(active: Boolean) {
    Text(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(TealContainer)
                .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceXs),
        text = if (active) "Active" else "Inactive",
        style = MaterialTheme.typography.bodyMedium,
        color = TealPrimary,
    )
}

@Composable
private fun ProductThumbnail(
    product: CatalogItem,
    resolvePhotoPath: (String) -> String,
) {
    val photoPath = product.photos.firstOrNull()?.path

    AsyncImage(
        modifier =
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(AppDimens.spaceMd))
                .background(MaterialTheme.colorScheme.primaryContainer),
        model = photoPath?.let { File(resolvePhotoPath(it)) },
        contentDescription = "${product.name} photo",
        contentScale = ContentScale.Crop,
    )
}
