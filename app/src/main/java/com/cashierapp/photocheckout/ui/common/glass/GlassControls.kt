package com.cashierapp.photocheckout.ui.common.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cashierapp.photocheckout.ui.theme.AppDimens
import com.cashierapp.photocheckout.ui.theme.GlassBorderTop
import com.cashierapp.photocheckout.ui.theme.GlassSurfaceBottom
import com.cashierapp.photocheckout.ui.theme.GlassSurfaceTop
import com.cashierapp.photocheckout.ui.theme.NeutralBadge
import com.cashierapp.photocheckout.ui.theme.TealPrimary
import com.cashierapp.photocheckout.ui.theme.TealPrimaryLight
import com.cashierapp.photocheckout.ui.theme.TextSecondary

/** Primary action button with the teal brand gradient; dims to a flat neutral when disabled. */
@Composable
public fun GradientButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(AppDimens.controlRadius)
    Button(
        modifier =
            modifier.background(
                brush =
                    if (enabled) {
                        Brush.horizontalGradient(colors = listOf(TealPrimary, TealPrimaryLight))
                    } else {
                        SolidColor(NeutralBadge)
                    },
                shape = shape,
            ),
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = TextSecondary,
            ),
        shape = shape,
    ) {
        Text(text = label)
    }
}

/** 44dp square glass icon button used in screen headers. */
@Composable
public fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Box(
        modifier =
            modifier
                .size(44.dp)
                .clip(RoundedCornerShape(AppDimens.spaceMd))
                .background(GlassSurfaceBottom)
                .border(
                    width = 1.dp,
                    color = GlassBorderTop,
                    shape = RoundedCornerShape(AppDimens.spaceMd),
                ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/** Translucent glass container colors for OutlinedTextField. */
@Composable
public fun glassFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = GlassSurfaceTop,
        unfocusedContainerColor = GlassSurfaceBottom,
        focusedBorderColor = TealPrimary,
        unfocusedBorderColor = GlassBorderTop,
    )
