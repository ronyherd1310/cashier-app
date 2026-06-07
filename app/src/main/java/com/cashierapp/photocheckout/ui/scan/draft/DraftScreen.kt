package com.cashierapp.photocheckout.ui.scan.draft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashierapp.photocheckout.domain.model.UnidentifiedItem
import com.cashierapp.photocheckout.domain.money.IdrFormat
import com.cashierapp.photocheckout.ui.common.dialogs.ConfirmDialog
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.TealPrimary

/**
 * Draft Review screen (S2, mockups 09/13/14): priced lines with numeric confidence
 * and low-confidence flags, unidentified entries, subtotal + total, and the
 * Add Item / Confirm actions. Editing and discard wiring land in T6/T7.
 */
@Composable
public fun DraftScreen(
    state: DraftUiState,
    onBack: () -> Unit,
    onLineClick: (String) -> Unit,
    onAddItem: () -> Unit,
    onConfirm: () -> Unit,
    onDiscardConfirmed: () -> Unit,
    resolvePhotoPath: (String) -> String,
    modifier: Modifier = Modifier,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = AppDimens.screenPadding),
    ) {
        DraftTopBar(itemCount = state.itemCount, onBack = onBack, onDiscard = { showDiscardDialog = true })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val isEmpty = state.lines.isEmpty() && state.unidentified.isEmpty()
        if (isEmpty) {
            DraftEmptyState(onScanAgain = onBack, modifier = Modifier.weight(1f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.lines, key = { it.sku }) { line ->
                    DraftLineRow(
                        line = line,
                        onClick = { onLineClick(line.sku) },
                        resolvePhotoPath = resolvePhotoPath,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                items(state.unidentified.size) { index ->
                    UnidentifiedRow(item = state.unidentified[index], index = index)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            TotalsSection(subtotalMinor = state.subtotalMinor, totalMinor = state.totalMinor)
            Spacer(modifier = Modifier.height(AppDimens.spaceMd))
            DraftActions(onAddItem = onAddItem, onConfirm = onConfirm)
            Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        }
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = "Discard draft?",
            message = "All items will be deleted and no sale is created.",
            confirmLabel = "Discard",
            onConfirm = {
                showDiscardDialog = false
                onDiscardConfirmed()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
}

@Composable
private fun DraftTopBar(
    itemCount: Int,
    onBack: () -> Unit,
    onDiscard: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("draft-back-button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                modifier = Modifier.testTag("draft-title"),
                text = "Draft ($itemCount item)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Review and edit before confirming",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DraftOverflowMenu(onDiscard = onDiscard)
    }
}

@Composable
private fun DraftOverflowMenu(onDiscard: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("draft-overflow-button")) {
        Icon(Icons.Default.MoreVert, contentDescription = "More options")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Discard draft") },
            modifier = Modifier.testTag("draft-discard-menu-item"),
            onClick = {
                expanded = false
                onDiscard()
            },
        )
    }
}

@Composable
private fun DraftEmptyState(
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("draft-empty-state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No items found",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceSm))
        Text(
            text = "We couldn't recognize any catalog items. Try again with the items spread flat.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceLg))
        Button(
            modifier = Modifier.height(52.dp).testTag("draft-scan-again-button"),
            onClick = onScanAgain,
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
            shape = RoundedCornerShape(AppDimens.controlRadius),
        ) {
            Text("Scan again")
        }
    }
}

@Composable
private fun UnidentifiedRow(
    item: UnidentifiedItem,
    index: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("draft-unidentified-$index")
                .padding(vertical = AppDimens.spaceMd),
    ) {
        Text(
            text = "Unidentified item",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text =
                buildString {
                    append("Detected ${item.quantity}× but not in the catalog")
                    item.rawSku?.let { append(" ($it)") }
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TotalsSection(
    subtotalMinor: Long,
    totalMinor: Long,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TotalRow(label = "Subtotal", valueMinor = subtotalMinor, emphasized = false, tag = "draft-subtotal")
        Spacer(modifier = Modifier.height(AppDimens.spaceXs))
        TotalRow(label = "Total", valueMinor = totalMinor, emphasized = true, tag = "draft-total")
    }
}

@Composable
private fun TotalRow(
    label: String,
    valueMinor: Long,
    emphasized: Boolean,
    tag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasized) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            modifier = Modifier.testTag(tag),
            text = "IDR ${IdrFormat.format(valueMinor)}",
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DraftActions(
    onAddItem: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.spaceMd)) {
        OutlinedButton(
            modifier =
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("draft-add-item-button"),
            onClick = onAddItem,
            shape = RoundedCornerShape(AppDimens.controlRadius),
        ) {
            Text("+ Add Item")
        }
        Button(
            modifier =
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .testTag("draft-confirm-button"),
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
            shape = RoundedCornerShape(AppDimens.controlRadius),
        ) {
            Text("Confirm")
        }
    }
}
