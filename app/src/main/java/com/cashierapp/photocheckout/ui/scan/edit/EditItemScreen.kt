package com.cashierapp.photocheckout.ui.scan.edit

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cashierapp.photocheckout.domain.model.DraftLine
import com.cashierapp.photocheckout.domain.money.IdrFormat
import com.cashierapp.photocheckout.ui.common.glass.GradientButton
import com.cashierapp.photocheckout.ui.common.glass.glassFieldColors
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.DangerRed
import com.cashierapp.photocheckout.ui.theme.TealPrimary
import com.cashierapp.photocheckout.ui.theme.TealPrimaryLight
import java.io.File

/**
 * Full-screen Edit-Item view (mockup 10): product header, quantity stepper, an
 * optional note, and Remove / Save actions. Save re-prices the line and returns to
 * the draft; Remove deletes it (SCAN-8).
 */
@Composable
public fun EditItemScreen(
    line: DraftLine,
    onBack: () -> Unit,
    onSave: (quantity: Int, note: String?) -> Unit,
    onRemove: () -> Unit,
    resolvePhotoPath: (String) -> String,
    modifier: Modifier = Modifier,
) {
    var quantity by remember(line.sku) { mutableIntStateOf(line.quantity) }
    var note by remember(line.sku) { mutableStateOf(line.note.orEmpty()) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.screenPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("edit-back-button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Edit Item",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(AppDimens.spaceSm))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                model = line.photoPath?.let { File(resolvePhotoPath(it)) },
                contentDescription = "${line.name} photo",
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(AppDimens.spaceMd))
            Column {
                Text(text = line.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = line.sku,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "IDR ${IdrFormat.format(line.unitPriceMinor)} / pcs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TealPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        Text(text = "Quantity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        QuantityStepper(
            quantity = quantity,
            onDecrement = { if (quantity > 1) quantity-- },
            onIncrement = { quantity++ },
        )

        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        Text(text = "Note (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().testTag("edit-note-field"),
            value = note,
            onValueChange = { note = it },
            placeholder = { Text("Add a note...") },
            shape = RoundedCornerShape(AppDimens.controlRadius),
            colors = glassFieldColors(),
        )

        Spacer(modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)) {
            OutlinedButton(
                modifier = Modifier.weight(1f).height(52.dp).testTag("edit-remove-button"),
                onClick = onRemove,
                shape = RoundedCornerShape(AppDimens.controlRadius),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(AppDimens.spaceXs))
                Text("Remove Item")
            }
            GradientButton(
                modifier = Modifier.weight(1f).height(52.dp).testTag("edit-save-button"),
                label = "Save",
                onClick = { onSave(quantity, note) },
            )
        }
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
    }
}

@Composable
private fun QuantityStepper(
    quantity: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton(onClick = onDecrement, tag = "edit-qty-decrement") {
            Icon(Icons.Default.Remove, contentDescription = "Decrease quantity", tint = Color.White)
        }
        Text(
            modifier =
                Modifier
                    .width(64.dp)
                    .testTag("edit-quantity-value"),
            text = quantity.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton(onClick = onIncrement, tag = "edit-qty-increment") {
            Icon(Icons.Default.Add, contentDescription = "Increase quantity", tint = Color.White)
        }
    }
}

@Composable
private fun StepperButton(
    onClick: () -> Unit,
    tag: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(AppDimens.spaceSm))
                .background(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(TealPrimary, TealPrimaryLight),
                        ),
                ).clickable(onClick = onClick)
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
