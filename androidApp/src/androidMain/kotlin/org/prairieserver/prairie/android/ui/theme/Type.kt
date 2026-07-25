package org.prairieserver.prairie.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Mirrors iosApp/iosApp/Theme/Typography.swift — system sans (Roboto on Android, the
// closest analog to SF), no custom display font, exact iOS pt sizes mapped to sp.

val PrairieHeroTitle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 40.sp,
)

val PrairieTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
)

val PrairieHeadlineStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
)

val PrairieSubheadlineStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

val PrairieBodyStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

val PrairieCaptionStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

val PrairieSmallStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)

val PrairiePinStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 36.sp,
)

val PrairieTypography = Typography(
    displayLarge = PrairieHeroTitle,
    displayMedium = PrairieHeroTitle.copy(fontSize = 32.sp, lineHeight = 36.sp),
    displaySmall = PrairieTitleStyle.copy(fontSize = 24.sp, lineHeight = 28.sp),
    headlineLarge = PrairieTitleStyle.copy(fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = PrairieTitleStyle,
    headlineSmall = PrairieHeadlineStyle,
    titleLarge = PrairieTitleStyle,
    titleMedium = PrairieHeadlineStyle,
    titleSmall = PrairieSubheadlineStyle,
    bodyLarge = PrairieBodyStyle.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = PrairieBodyStyle,
    bodySmall = PrairieCaptionStyle,
    labelLarge = PrairieSubheadlineStyle,
    labelMedium = PrairieCaptionStyle,
    labelSmall = PrairieSmallStyle,
)
