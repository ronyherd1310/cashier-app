package com.cashierapp.photocheckout.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cashierapp.photocheckout.domain.model.DraftReceipt
import com.cashierapp.photocheckout.ui.catalog.add.AddProductRoute
import com.cashierapp.photocheckout.ui.catalog.detail.ProductDetailRoute
import com.cashierapp.photocheckout.ui.catalog.list.CatalogListRoute
import com.cashierapp.photocheckout.ui.common.glass.GlassBackground
import com.cashierapp.photocheckout.ui.scan.additem.AddItemRoute
import com.cashierapp.photocheckout.ui.scan.capture.ScanCaptureRoute
import com.cashierapp.photocheckout.ui.scan.discarded.DraftDiscardedScreen
import com.cashierapp.photocheckout.ui.scan.draft.DraftRoute
import com.cashierapp.photocheckout.ui.scan.edit.EditItemRoute
import com.cashierapp.photocheckout.ui.settings.SettingsRoute
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.GlassBorderBottom
import com.cashierapp.photocheckout.ui.theme.GlassBorderTop
import com.cashierapp.photocheckout.ui.theme.TealContainer
import com.cashierapp.photocheckout.ui.theme.TealPrimary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

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

    val hazeState = remember { HazeState() }

    Box(modifier = modifier.fillMaxSize()) {
        GlassBackground(modifier = Modifier.fillMaxSize())
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState)
                    .statusBarsPadding()
                    .then(
                        if (showBottomBar) Modifier else Modifier.navigationBarsPadding(),
                    ),
        ) {
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

        if (showBottomBar) {
            AppBottomBar(
                destinations = destinations,
                selectedLabel = selectedDestination.label,
                onDestinationSelected = { selectedLabel = it.label },
                hazeState = hazeState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun AppBottomBar(
    destinations: List<AppDestination>,
    selectedLabel: String,
    onDestinationSelected: (AppDestination) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppDimens.glassRadius + AppDimens.spaceSm)
    Row(
        modifier =
            modifier
                .navigationBarsPadding()
                .padding(horizontal = AppDimens.spaceLg)
                .padding(bottom = AppDimens.spaceMd)
                .fillMaxWidth()
                .clip(shape)
                .hazeEffect(
                    state = hazeState,
                    style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
                ).border(
                    width = 1.dp,
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(GlassBorderTop, GlassBorderBottom),
                        ),
                    shape = shape,
                ).padding(vertical = AppDimens.spaceSm),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        destinations.forEach { destination ->
            GlassNavItem(
                destination = destination,
                selected = destination.label == selectedLabel,
                onClick = { onDestinationSelected(destination) },
            )
        }
    }
}

@Composable
private fun GlassNavItem(
    destination: AppDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(AppDimens.controlRadius))
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    onClick = onClick,
                ).padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs)
                .testTag("tab-${destination.label}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) TealContainer else Color.Transparent)
                    .padding(horizontal = AppDimens.spaceMd, vertical = AppDimens.spaceXs),
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = destination.label,
                tint = tint,
            )
        }
        Text(
            text = destination.label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
