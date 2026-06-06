package com.cashierapp.photocheckout.ui.shell

import androidx.compose.runtime.Immutable

@Immutable
public data class AppDestination(
    val label: String,
    val placeholderText: String,
)

public val AppDestinations: List<AppDestination> =
    listOf(
        AppDestination(label = "Home", placeholderText = "Home placeholder"),
        AppDestination(label = "Catalogue", placeholderText = "Catalogue placeholder"),
        AppDestination(label = "Scan", placeholderText = "Scan placeholder"),
        AppDestination(label = "Sales", placeholderText = "Sales placeholder"),
        AppDestination(label = "More", placeholderText = "More placeholder"),
    )

public const val DEFAULT_DESTINATION_LABEL: String = "Catalogue"
