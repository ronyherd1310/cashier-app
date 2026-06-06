package com.cashierapp.photocheckout.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.cashierapp.photocheckout.ui.catalog.list.CatalogListRoute
import com.cashierapp.photocheckout.ui.theme.AppDimens

@Composable
public fun AppShell(
    modifier: Modifier = Modifier,
    destinations: List<AppDestination> = AppDestinations,
    catalogueContent: @Composable () -> Unit = {
        CatalogListRoute(onAddProductClick = {})
    },
) {
    var selectedLabel by rememberSaveable { mutableStateOf(DEFAULT_DESTINATION_LABEL) }
    val selectedDestination =
        destinations.firstOrNull { it.label == selectedLabel }
            ?: destinations.first()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            AppBottomBar(
                destinations = destinations,
                selectedLabel = selectedDestination.label,
                onDestinationSelected = { selectedLabel = it.label },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        Column(modifier = Modifier.padding(contentPadding)) {
            if (selectedDestination.label == DEFAULT_DESTINATION_LABEL) {
                catalogueContent()
            } else {
                PlaceholderDestination(destination = selectedDestination)
            }
        }
    }
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
                    Text(
                        text = destination.label.take(1),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
}
