package com.offlinemessenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** iOS dark-mode color scheme (Cupertino feel). */
private val ColorScheme = darkColorScheme(
    primary = IOSBlue,
    onPrimary = IOSText,
    primaryContainer = IOSBlueDim,
    onPrimaryContainer = IOSBlue,
    secondary = IOSBlue,
    background = IOSBlack,
    onBackground = IOSText,
    surface = IOSBlack,
    onSurface = IOSText,
    surfaceVariant = IOSGray6,
    onSurfaceVariant = IOSSecondary,
    surfaceContainerHighest = IOSGray5,
    error = IOSRed,
    onError = IOSBlack,
)

/** SF-like typography scale with iOS large titles. */
private val AppTypography = Typography(
    // iOS large title
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.sp, color = IOSSecondary),
    labelSmall = TextStyle(fontSize = 11.sp, color = IOSTertiary),
)

@Composable
fun OfflineMessengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = AppTypography,
        content = content,
    )
}
