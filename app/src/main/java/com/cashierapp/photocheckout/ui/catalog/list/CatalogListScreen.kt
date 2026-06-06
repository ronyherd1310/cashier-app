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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(AppDimens.screenPadding),
    ) {
        CatalogHeader(activeCount = state.activeProducts.size)
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
            value = "",
            onValueChange = {},
            enabled = false,
            placeholder = { Text("Search products...") },
            shape = RoundedCornerShape(AppDimens.controlRadius),
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))

        if (state.activeProducts.isEmpty()) {
            CatalogEmptyState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
            ) {
                items(
                    items = state.activeProducts,
                    key = CatalogItem::sku,
                ) { product ->
                    CatalogProductCard(
                        product = product,
                        onClick = { onProductClick(product.id) },
                    )
                }
            }
        }
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
                Text(
                    text = "⋮",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceLg))
                Text(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(TealContainer)
                            .padding(horizontal = AppDimens.spaceSm, vertical = AppDimens.spaceXs),
                    text = "Active",
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
