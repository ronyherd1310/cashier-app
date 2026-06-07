package com.cashierapp.photocheckout.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.cashierapp.photocheckout.domain.model.DraftReceipt
import com.cashierapp.photocheckout.ui.catalog.add.AddProductRoute
import com.cashierapp.photocheckout.ui.catalog.detail.ProductDetailRoute
import com.cashierapp.photocheckout.ui.catalog.list.CatalogListRoute
import com.cashierapp.photocheckout.ui.scan.additem.AddItemRoute
import com.cashierapp.photocheckout.ui.scan.capture.ScanCaptureRoute
import com.cashierapp.photocheckout.ui.scan.discarded.DraftDiscardedScreen
import com.cashierapp.photocheckout.ui.scan.draft.DraftRoute
import com.cashierapp.photocheckout.ui.scan.edit.EditItemRoute
import com.cashierapp.photocheckout.ui.settings.SettingsRoute
import com.cashierapp.photocheckout.ui.theme.AppDimens

@Composable
public fun AppShell(
    modifier: Modifier = Modifier,
    destinations: List<AppDestination> = AppDestinations,
    catalogueContent: (@Composable () -> Unit)? = null,
    // Module 3 (Sales) plugs CommitSale in here; default is a no-op stub for the Scan module.
    onConfirmDraft: (DraftReceipt) -> Unit = {},
) {
    var selectedLabel by rememberSaveable { mutableStateOf(DEFAULT_DESTINATION_LABEL) }
    var catalogMode by rememberSaveable { mutableStateOf(CatalogMode.List) }
    var selectedProductId by rememberSaveable { mutableStateOf<Long?>(null) }
    var scanMode by rememberSaveable { mutableStateOf(ScanMode.Capture) }
    var scanDraft by remember { mutableStateOf<DraftReceipt?>(null) }
    var editingScanSku by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDestination =
        destinations.firstOrNull { it.label == selectedLabel }
            ?: destinations.first()
    val showBottomBar =
        when (selectedDestination.label) {
            DEFAULT_DESTINATION_LABEL -> catalogMode == CatalogMode.List
            SCAN_DESTINATION_LABEL -> false
            else -> true
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    destinations = destinations,
                    selectedLabel = selectedDestination.label,
                    onDestinationSelected = { selectedLabel = it.label },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            if (selectedDestination.label == DEFAULT_DESTINATION_LABEL) {
                if (catalogueContent != null) {
                    catalogueContent()
                } else {
                    when (catalogMode) {
                        CatalogMode.List ->
                            CatalogListRoute(
                                onAddProductClick = { catalogMode = CatalogMode.Add },
                                onProductClick = { productId ->
                                    selectedProductId = productId
                                    catalogMode = CatalogMode.Detail
                                },
                            )

                        CatalogMode.Add ->
                            AddProductRoute(
                                onBack = { catalogMode = CatalogMode.List },
                                onSaved = { catalogMode = CatalogMode.List },
                            )

                        CatalogMode.Detail ->
                            ProductDetailRoute(
                                productId = selectedProductId ?: 0L,
                                onBack = { catalogMode = CatalogMode.List },
                            )
                    }
                }
            } else if (selectedDestination.label == SCAN_DESTINATION_LABEL) {
                when (scanMode) {
                    ScanMode.Capture ->
                        ScanCaptureRoute(
                            onClose = { selectedLabel = DEFAULT_DESTINATION_LABEL },
                            onDraftReady = { draft ->
                                scanDraft = draft
                                scanMode = ScanMode.Draft
                            },
                        )

                    ScanMode.Draft -> {
                        val draft = scanDraft
                        if (draft == null) {
                            scanMode = ScanMode.Capture
                        } else {
                            DraftRoute(
                                draft = draft,
                                onBack = {
                                    scanDraft = null
                                    scanMode = ScanMode.Capture
                                },
                                onLineClick = { sku ->
                                    editingScanSku = sku
                                    scanMode = ScanMode.EditLine
                                },
                                onAddItem = { scanMode = ScanMode.AddItem },
                                onConfirm = { finalizedDraft ->
                                    // Module 3 (Sales) hand-off seam: replace this stub with
                                    // CommitSale(finalizedDraft) → Receipt (S5). No auto-commit
                                    // happens before this explicit cashier action (SALE-1).
                                    onConfirmDraft(finalizedDraft)
                                    scanDraft = null
                                    scanMode = ScanMode.Capture
                                },
                                onDiscarded = {
                                    scanDraft = null
                                    scanMode = ScanMode.Discarded
                                },
                            )
                        }
                    }

                    ScanMode.EditLine ->
                        EditItemRoute(
                            sku = editingScanSku.orEmpty(),
                            onDone = {
                                editingScanSku = null
                                scanMode = ScanMode.Draft
                            },
                        )

                    ScanMode.AddItem ->
                        AddItemRoute(
                            onDone = { scanMode = ScanMode.Draft },
                        )

                    ScanMode.Discarded ->
                        DraftDiscardedScreen(
                            onNewScan = { scanMode = ScanMode.Capture },
                            onBackToHome = {
                                scanMode = ScanMode.Capture
                                selectedLabel = DEFAULT_DESTINATION_LABEL
                            },
                        )
                }
            } else if (selectedDestination.label == MORE_DESTINATION_LABEL) {
                SettingsRoute()
            } else {
                PlaceholderDestination(destination = selectedDestination)
            }
        }
    }
}

private enum class CatalogMode {
    List,
    Add,
    Detail,
}

private enum class ScanMode {
    Capture,
    Draft,
    EditLine,
    AddItem,
    Discarded,
}

@Composable
private fun PlaceholderDestination(
    destination: AppDestination,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(AppDimens.screenPadding),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            modifier = Modifier.testTag("shell-title"),
            text = destination.label,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(AppDimens.spaceMd))
        Text(
            text = destination.placeholderText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppBottomBar(
    destinations: List<AppDestination>,
    selectedLabel: String,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        destinations.forEach { destination ->
            NavigationBarItem(
                modifier = Modifier.testTag("tab-${destination.label}"),
                selected = destination.label == selectedLabel,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
}
