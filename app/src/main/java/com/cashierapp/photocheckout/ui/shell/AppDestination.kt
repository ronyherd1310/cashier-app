package com.cashierapp.photocheckout.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
public data class AppDestination(
    val label: String,
    val placeholderText: String,
    val icon: ImageVector,
)

public val AppDestinations: List<AppDestination> =
    listOf(
        AppDestination(label = "Home", placeholderText = "Home placeholder", icon = Icons.Outlined.Home),
        AppDestination(label = "Catalogue", placeholderText = "Catalogue placeholder", icon = Icons.Outlined.GridView),
        AppDestination(label = "Scan", placeholderText = "Scan placeholder", icon = Icons.Outlined.PhotoCamera),
        AppDestination(label = "Sales", placeholderText = "Sales placeholder", icon = Icons.Outlined.ReceiptLong),
        AppDestination(label = "More", placeholderText = "More placeholder", icon = Icons.Outlined.MoreHoriz),
    )

public const val DEFAULT_DESTINATION_LABEL: String = "Catalogue"

public const val SCAN_DESTINATION_LABEL: String = "Scan"
