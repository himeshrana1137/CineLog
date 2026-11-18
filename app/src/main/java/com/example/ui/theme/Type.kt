package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val CineDisplayFont = FontFamily(
    Font(R.font.bebas_neue, FontWeight.Normal)
)

val CineBrandFont = FontFamily(
    Font(R.font.outfit, FontWeight.Bold),
    Font(R.font.outfit, FontWeight.ExtraBold),
)

val CineBodyFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal)
)

val CineTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = CineDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = 1.5.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = CineDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = 1.2.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = CineDisplayFont,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = 1.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = CineBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = CineBodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = CineBodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = CineBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = CineBodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = CineBodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = CineBodyFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.4.sp,
    ),
)
