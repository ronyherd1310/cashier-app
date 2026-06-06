package com.cashierapp.photocheckout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme =
    lightColorScheme(
        primary = TealPrimary,
        onPrimary = SurfaceWhite,
        primaryContainer = TealContainer,
        onPrimaryContainer = TealPrimaryDark,
        background = SoftBlueBackground,
        onBackground = TextPrimary,
        surface = SurfaceWhite,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        outline = DividerBlue,
        error = DangerRed,
    )

@Composable
public fun PhotoCheckoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
