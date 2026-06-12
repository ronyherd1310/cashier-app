package com.cashierapp.photocheckout.ui.common.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.GlassBorderBottom
import com.cashierapp.photocheckout.ui.theme.GlassBorderTop
import com.cashierapp.photocheckout.ui.theme.GlassSurfaceBottom
import com.cashierapp.photocheckout.ui.theme.GlassSurfaceTop

/**
 * Translucent glass surface: gradient fill plus a light top-edge border so it
 * reads as frosted glass over [GlassBackground]. Deliberately blur-free —
 * cheap enough to use per row in lazy lists; reserve real backdrop blur
 * (Haze) for singleton chrome like the bottom nav bar.
 */
@Composable
public fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(AppDimens.glassRadius),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.97f else 1f,
        label = "glass-card-press-scale",
    )
    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.clip(shape)
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(GlassSurfaceTop, GlassSurfaceBottom),
                        ),
                ).border(
                    width = 1.dp,
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(GlassBorderTop, GlassBorderBottom),
                        ),
                    shape = shape,
                ).then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                ),
        content = content,
    )
}
