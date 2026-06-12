package com.cashierapp.photocheckout.ui.common.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.cashierapp.photocheckout.ui.theme.GlassBackgroundBottom
import com.cashierapp.photocheckout.ui.theme.GlassBackgroundTop
import com.cashierapp.photocheckout.ui.theme.GlassBlobBlue
import com.cashierapp.photocheckout.ui.theme.GlassBlobTeal

/**
 * Ambient backdrop for the glassmorphism look: a soft vertical gradient with
 * large blurred-looking colour blobs. Translucent glass surfaces (cards, the
 * bottom nav bar) need this colour variation behind them to read as glass.
 */
@Composable
public fun GlassBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(GlassBackgroundTop, GlassBackgroundBottom),
                ),
        )
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(GlassBlobTeal, Color.Transparent),
                    center = Offset(x = size.width * 0.9f, y = size.height * 0.1f),
                    radius = size.width * 0.7f,
                ),
            center = Offset(x = size.width * 0.9f, y = size.height * 0.1f),
            radius = size.width * 0.7f,
        )
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(GlassBlobBlue, Color.Transparent),
                    center = Offset(x = size.width * 0.05f, y = size.height * 0.45f),
                    radius = size.width * 0.6f,
                ),
            center = Offset(x = size.width * 0.05f, y = size.height * 0.45f),
            radius = size.width * 0.6f,
        )
        drawCircle(
            brush =
                Brush.radialGradient(
                    colors = listOf(GlassBlobTeal, Color.Transparent),
                    center = Offset(x = size.width * 0.7f, y = size.height * 0.95f),
                    radius = size.width * 0.65f,
                ),
            center = Offset(x = size.width * 0.7f, y = size.height * 0.95f),
            radius = size.width * 0.65f,
        )
    }
}
