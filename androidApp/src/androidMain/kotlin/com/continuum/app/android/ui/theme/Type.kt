package com.continuum.app.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Mirrors iosApp/iosApp/Theme/Typography.swift — system sans (Roboto on Android, the
// closest analog to SF), no custom display font, exact iOS pt sizes mapped to sp.

val ContinuumHeroTitle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 40.sp,
)

val ContinuumTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
)

val ContinuumHeadlineStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
)

val ContinuumSubheadlineStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

val ContinuumBodyStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

val ContinuumCaptionStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)

val ContinuumSmallStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)

val ContinuumPinStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 32.sp,
    lineHeight = 36.sp,
)

val ContinuumTypography = Typography(
    displayLarge = ContinuumHeroTitle,
    displayMedium = ContinuumHeroTitle.copy(fontSize = 32.sp, lineHeight = 36.sp),
    displaySmall = ContinuumTitleStyle.copy(fontSize = 24.sp, lineHeight = 28.sp),
    headlineLarge = ContinuumTitleStyle.copy(fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = ContinuumTitleStyle,
    headlineSmall = ContinuumHeadlineStyle,
    titleLarge = ContinuumTitleStyle,
    titleMedium = ContinuumHeadlineStyle,
    titleSmall = ContinuumSubheadlineStyle,
    bodyLarge = ContinuumBodyStyle.copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = ContinuumBodyStyle,
    bodySmall = ContinuumCaptionStyle,
    labelLarge = ContinuumSubheadlineStyle,
    labelMedium = ContinuumCaptionStyle,
    labelSmall = ContinuumSmallStyle,
)
