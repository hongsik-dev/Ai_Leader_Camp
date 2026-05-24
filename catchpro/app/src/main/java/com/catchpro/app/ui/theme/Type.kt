package com.catchpro.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.catchpro.app.R

val PretendardVariable = FontFamily(
    Font(R.font.pretendard_variable, FontWeight.Thin),
    Font(R.font.pretendard_variable, FontWeight.ExtraLight),
    Font(R.font.pretendard_variable, FontWeight.Light),
    Font(R.font.pretendard_variable, FontWeight.Normal),
    Font(R.font.pretendard_variable, FontWeight.Medium),
    Font(R.font.pretendard_variable, FontWeight.SemiBold),
    Font(R.font.pretendard_variable, FontWeight.Bold),
    Font(R.font.pretendard_variable, FontWeight.ExtraBold),
    Font(R.font.pretendard_variable, FontWeight.Black),
)

private val BaseTypography = Typography()

val CatchProTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Black, letterSpacing = 0.sp),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Black, letterSpacing = 0.sp),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Black, letterSpacing = 0.sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Black, letterSpacing = 0.sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = PretendardVariable, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
)

