package com.cashierapp.photocheckout.ui.catalog.add

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.DividerBlue
import com.cashierapp.photocheckout.ui.theme.NeutralBadge
import com.cashierapp.photocheckout.ui.theme.TealPrimary
import java.io.File

private val ControlHeight: Dp = 56.dp

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
        AddProductTopBar(onBack = onBack)
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        StepIndicator(
            currentStep = state.step,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
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

                else ->
                    ReviewStep(
                        state = state,
                        onEditPhoto = onAddPhoto,
                    )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        AddProductActions(
            state = state,
            onPrevious = onPrevious,
            onNext = onNext,
            onSave = onSave,
        )
    }
}

@Composable
private fun AddProductTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .size(44.dp)
                    .clip(RoundedCornerShape(AppDimens.spaceMd))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(AppDimens.spaceMd),
                    ).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "Add Product",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .width(172.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (step in 1..3) {
            StepCircle(step = step, currentStep = currentStep)
            if (step < 3) {
                StepConnector(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StepCircle(
    step: Int,
    currentStep: Int,
) {
    val isReached = step <= currentStep
    val containerColor = if (step == currentStep) TealPrimary else MaterialTheme.colorScheme.surface
    val borderColor = if (isReached) TealPrimary else MaterialTheme.colorScheme.outline
    val numberColor =
        when {
            step == currentStep -> MaterialTheme.colorScheme.onPrimary
            isReached -> TealPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(containerColor)
                .border(width = 1.5.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = step.toString(),
            color = numberColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun StepConnector(modifier: Modifier = Modifier) {
    val color = TealPrimary.copy(alpha = 0.52f)
    Box(
        modifier =
            modifier
                .padding(horizontal = AppDimens.spaceSm)
                .height(2.dp)
                .drawBehind {
                    drawLine(
                        color = color,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = size.height,
                    )
                },
    )
}

@Composable
private fun StepHeader(
    title: String,
    subtitle: String,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceXs))
    Text(
        text = subtitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = AppDimens.spaceSm),
    )
}

@Composable
private fun BasicStep(
    state: AddProductUiState,
    onNameChange: (String) -> Unit,
    onAddPhoto: () -> Unit,
) {
    StepHeader(
        title = "Basic Information",
        subtitle = "Enter the basic details of your product.",
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceLg))

    PhotoUploadCard(
        photoPath = state.photoAbsolutePath,
        onAddPhoto = onAddPhoto,
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceLg))

    FieldLabel("Product Name")
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.name,
        onValueChange = onNameChange,
        singleLine = true,
        placeholder = {
            Text(
                text = "e.g. Nasi Goreng Spesial",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shape = RoundedCornerShape(AppDimens.controlRadius),
        colors = fieldColors(),
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceLg))

    FieldLabel("SKU (auto-generated)")
    ReadOnlyValueBox(value = state.previewSku)
    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
    Text(
        text = "SKU is generated automatically and cannot be changed.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
    )
}

@Composable
private fun PhotoUploadCard(
    photoPath: String?,
    onAddPhoto: () -> Unit,
) {
    val hasPhoto = photoPath != null
    val borderColor = MaterialTheme.colorScheme.outline
    val radius = AppDimens.cardRadius
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.surface)
                .dashedBorder(color = borderColor, cornerRadius = radius)
                .clickable(onClick = onAddPhoto)
                .padding(vertical = AppDimens.spaceXl, horizontal = AppDimens.spaceLg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hasPhoto) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "Captured product photo",
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(AppDimens.spaceMd)),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(AppDimens.spaceMd))
                        .background(NeutralBadge),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Text(
            text = if (hasPhoto) "Photo Added" else "Add Photo",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        Text(
            text = "Take a photo of the product",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        Text(
            text = "(1 photo required for MVP)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ReadOnlyValueBox(value: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppDimens.controlRadius))
                .background(MaterialTheme.colorScheme.background)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(AppDimens.controlRadius),
                ).padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceMd),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PricingStep(
    state: AddProductUiState,
    onPriceChange: (String) -> Unit,
) {
    StepHeader(
        title = "Pricing",
        subtitle = "Set the price for this product.",
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceLg))

    FieldLabel("Price (IDR)")
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.price,
        onValueChange = onPriceChange,
        singleLine = true,
        placeholder = {
            Text(
                text = "25.000",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shape = RoundedCornerShape(AppDimens.controlRadius),
        colors = fieldColors(),
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceSm))
    Text(
        text = "Prices are stored in integer minor units (IDR). No decimals or floating-point values.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceLg))

    FieldLabel("Price Preview")
    ReadOnlyValueBox(value = "IDR ${state.price.ifBlank { "0" }}")
}

@Composable
private fun ReviewStep(
    state: AddProductUiState,
    onEditPhoto: () -> Unit,
) {
    StepHeader(
        title = "Review",
        subtitle = "Confirm your product details before saving.",
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceLg))

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(AppDimens.cardRadius),
                    ambientColor = DividerBlue.copy(alpha = 0.34f),
                    spotColor = DividerBlue.copy(alpha = 0.34f),
                ).clip(RoundedCornerShape(AppDimens.cardRadius))
                .background(MaterialTheme.colorScheme.surface)
                .padding(AppDimens.spaceMd),
    ) {
        ReviewPhoto(
            photoPath = state.photoAbsolutePath ?: state.photoPath,
            onEditPhoto = onEditPhoto,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        ReviewField(
            label = "Product Name",
            value = state.name,
            valueStyle = MaterialTheme.typography.bodyLarge,
        )
        ReviewDivider()
        ReviewField(
            label = "SKU",
            value = state.previewSku,
            valueStyle = MaterialTheme.typography.bodyLarge,
        )
        ReviewDivider()
        ReviewField(
            label = "Price (IDR)",
            value = state.price,
            valueStyle = MaterialTheme.typography.bodyLarge,
            bottomPadding = AppDimens.spaceXs,
        )
    }
}

@Composable
private fun ReviewPhoto(
    photoPath: String?,
    onEditPhoto: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.58f)
                .clip(RoundedCornerShape(18.dp))
                .background(NeutralBadge),
    ) {
        if (photoPath != null) {
            AsyncImage(
                model = File(photoPath),
                contentDescription = "Captured product photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = AppDimens.spaceMd, end = AppDimens.spaceMd)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .clickable(onClick = onEditPhoto)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Edit Photo",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ReviewField(
    label: String,
    value: String,
    valueStyle: androidx.compose.ui.text.TextStyle,
    bottomPadding: Dp = 0.dp,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.spaceSm)
                .padding(bottom = bottomPadding),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        Text(
            text = value,
            style = valueStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReviewDivider() {
    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.spaceSm)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
    )
    Spacer(modifier = Modifier.height(AppDimens.spaceMd))
}

@Composable
private fun AddProductActions(
    state: AddProductUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
) {
    val primaryEnabled =
        when (state.step) {
            1 -> state.canContinueFromBasic
            2 -> state.canContinueFromPrice
            else -> true
        }
    val primaryLabel = if (state.step == 3) "Save Product" else "Next"
    val onPrimary = if (state.step == 3) onSave else onNext

    if (state.step == 1) {
        PrimaryButton(
            modifier = Modifier.fillMaxWidth(),
            label = primaryLabel,
            enabled = primaryEnabled,
            onClick = onPrimary,
        )
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd),
        ) {
            OutlinedButton(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(ControlHeight),
                onClick = onPrevious,
                shape = RoundedCornerShape(AppDimens.controlRadius),
            ) {
                Text("Back")
            }
            PrimaryButton(
                modifier = Modifier.weight(1f),
                label = primaryLabel,
                enabled = primaryEnabled,
                onClick = onPrimary,
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier.height(ControlHeight),
        enabled = enabled,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
        shape = RoundedCornerShape(AppDimens.controlRadius),
    ) {
        Text(text = label)
    }
}

@Composable
private fun fieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedBorderColor = TealPrimary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )

private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.5.dp,
): Modifier =
    drawBehind {
        val stroke =
            Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
            )
        drawRoundRect(
            color = color,
            style = stroke,
            cornerRadius = CornerRadius(cornerRadius.toPx()),
        )
    }
