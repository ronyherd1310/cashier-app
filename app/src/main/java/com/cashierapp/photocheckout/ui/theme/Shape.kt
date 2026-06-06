package com.cashierapp.photocheckout.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

public val AppShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(AppDimens.spaceSm),
        small = RoundedCornerShape(AppDimens.spaceSm),
        medium = RoundedCornerShape(AppDimens.controlRadius),
        large = RoundedCornerShape(AppDimens.cardRadius),
        extraLarge = RoundedCornerShape(AppDimens.cardRadius),
    )
