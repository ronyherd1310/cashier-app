package com.cashierapp.photocheckout.ui.catalog.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cashierapp.photocheckout.domain.model.ProductPhoto
import com.cashierapp.photocheckout.domain.money.IdrFormat
import com.cashierapp.photocheckout.ui.common.glass.GlassCard
import com.cashierapp.photocheckout.ui.common.glass.glassFieldColors
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.DangerRed
import com.cashierapp.photocheckout.ui.theme.GlassBorderTop
import com.cashierapp.photocheckout.ui.theme.GlassSurfaceBottom
import com.cashierapp.photocheckout.ui.theme.TealContainer
import com.cashierapp.photocheckout.ui.theme.TealPrimary
import java.io.File

private val ActionHeight = 52.dp
private val DetailHorizontalPadding = 24.dp
private val DetailVerticalPadding = 16.dp

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
    resolvePhotoPath: (String) -> String = { it },
    modifier: Modifier = Modifier,
) {
    val product = state.product
    var showStatusConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = DetailHorizontalPadding, vertical = DetailVerticalPadding),
    ) {
        if (product == null) {
            EmptyDetail(onBack = onBack)
            return@Column
        }

        DetailTopBar(onBack = onBack)
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            HeroPhoto(
                active = product.active,
                photos = product.photos,
                resolvePhotoPath = resolvePhotoPath,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            Text(
                text = product.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 31.sp,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceXs))
            Text(
                text = "SKU  •  ${product.sku}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))

            PriceCard(
                price = IdrFormat.format(product.priceMinor),
                onEdit = { showEditDialog = true },
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceLg))

            PhotosSection(
                photos = product.photos,
                onAddPhoto = onAddPhoto,
                onRemovePhoto = onRemovePhoto,
                resolvePhotoPath = resolvePhotoPath,
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        BottomActions(
            active = product.active,
            onEdit = { showEditDialog = true },
            onToggle = { showStatusConfirm = true },
        )
    }

    if (product != null && showEditDialog) {
        EditProductDialog(
            name = state.nameInput,
            price = state.priceInput,
            onNameChange = onNameChange,
            onPriceChange = onPriceChange,
            onDismiss = { showEditDialog = false },
            onSave = {
                onSave()
                showEditDialog = false
            },
        )
    }

    if (product != null && showStatusConfirm) {
        val targetActive = !product.active
        AlertDialog(
            onDismissRequest = { showStatusConfirm = false },
            title = { Text(if (targetActive) "Reactivate product?" else "Deactivate product?") },
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

@Composable
private fun EmptyDetail(onBack: () -> Unit) {
    Text("Product not found")
    Button(onClick = onBack) { Text("Back") }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(GlassSurfaceBottom)
                    .border(1.dp, GlassBorderTop, RoundedCornerShape(18.dp)),
            onClick = onBack,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "Product Detail",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(GlassSurfaceBottom)
                    .border(1.dp, GlassBorderTop, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HeroPhoto(
    active: Boolean,
    photos: List<ProductPhoto>,
    resolvePhotoPath: (String) -> String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.58f)
                .clip(RoundedCornerShape(AppDimens.cardRadius))
                .background(MaterialTheme.colorScheme.surface),
    ) {
        ProductImage(
            photo = photos.firstOrNull(),
            modifier = Modifier.fillMaxSize(),
            contentDescription = "Product photo",
            resolvePhotoPath = resolvePhotoPath,
        )
        StatusBadge(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(AppDimens.spaceMd),
            active = active,
        )
        PhotoCounter(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(AppDimens.spaceMd),
            count = photos.size.coerceAtLeast(1),
        )
    }
}

@Composable
private fun StatusBadge(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(if (active) TealContainer else MaterialTheme.colorScheme.surface)
                .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceSm),
    ) {
        Text(
            text = if (active) "Active" else "Inactive",
            color = if (active) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PhotoCounter(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceXs),
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "1 / $count",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PriceCard(
    price: String,
    onEdit: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.cardRadius),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(AppDimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Price (IDR)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceSm))
                Text(
                    text = price,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(14.dp)) {
                Text("Edit", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PhotosSection(
    photos: List<ProductPhoto>,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    resolvePhotoPath: (String) -> String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = "Photos",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${photos.size} of 3 photos",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
    }
    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)) {
        PhotoThumbnail(photo = photos.firstOrNull(), resolvePhotoPath = resolvePhotoPath)
        AddPhotoTile(
            enabled = photos.size < 3,
            onClick = onAddPhoto,
        )
        if (photos.size > 1) {
            RemovePhotoTile(onClick = onRemovePhoto)
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photo: ProductPhoto?,
    resolvePhotoPath: (String) -> String,
) {
    ProductImage(
        photo = photo,
        modifier =
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
        contentDescription = "Product thumbnail",
        resolvePhotoPath = resolvePhotoPath,
    )
}

@Composable
private fun AddPhotoTile(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = if (enabled) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier =
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(30.dp),
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            Text(
                text = "Add Photo",
                color = color,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RemovePhotoTile(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(AppDimens.spaceSm))
            Text(
                text = "Remove",
                color = DangerRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ProductImage(
    photo: ProductPhoto?,
    contentDescription: String,
    resolvePhotoPath: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val photoFile = photo?.let { File(resolvePhotoPath(it.path)) }
    if (photoFile == null || !photoFile.exists()) {
        MissingImage(modifier = modifier, contentDescription = contentDescription)
    } else {
        AsyncImage(
            model = photoFile,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

@Composable
private fun MissingImage(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun BottomActions(
    active: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
    ) {
        OutlinedButton(
            modifier =
                Modifier
                    .weight(1f)
                    .height(ActionHeight),
            onClick = onEdit,
            shape = RoundedCornerShape(AppDimens.controlRadius),
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.size(AppDimens.spaceSm))
            Text("Edit Product", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        OutlinedButton(
            modifier =
                Modifier
                    .weight(1f)
                    .height(ActionHeight),
            onClick = onToggle,
            shape = RoundedCornerShape(AppDimens.controlRadius),
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = DangerRed,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.size(AppDimens.spaceSm))
            Text(
                text = if (active) "Deactivate" else "Reactivate",
                color = DangerRed,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
private fun EditProductDialog(
    name: String,
    price: String,
    onNameChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Product") },
        text = {
            Column {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Product Name") },
                    singleLine = true,
                    colors = editFieldColors(),
                )
                Spacer(modifier = Modifier.height(AppDimens.spaceMd))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = price,
                    onValueChange = onPriceChange,
                    label = { Text("Price (IDR)") },
                    singleLine = true,
                    colors = editFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun editFieldColors() = glassFieldColors()
