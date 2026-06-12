package com.cashierapp.photocheckout.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cashierapp.photocheckout.R

public val AppFontFamily: FontFamily =
    FontFamily(
        Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
        Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
        Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
        Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    )

private val defaults = Typography()

private fun TextStyle.branded(): TextStyle = copy(fontFamily = AppFontFamily)

public val AppTypography: Typography =
    Typography(
        displayLarge = defaults.displayLarge.branded(),
        displayMedium = defaults.displayMedium.branded(),
        displaySmall = defaults.displaySmall.branded(),
        headlineLarge =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                lineHeight = 48.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
            ),
        headlineSmall = defaults.headlineSmall.branded(),
        titleLarge =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        titleMedium = defaults.titleMedium.branded(),
        titleSmall = defaults.titleSmall.branded(),
        bodyLarge =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodySmall = defaults.bodySmall.branded(),
        labelLarge = defaults.labelLarge.branded(),
        labelMedium = defaults.labelMedium.branded(),
        labelSmall = defaults.labelSmall.branded(),
    )
