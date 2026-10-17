package com.offlinemessenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Charcoal950,
    primaryContainer = Emerald900,
    onPrimaryContainer = Emerald400,
    secondary = Emerald400,
    background = Charcoal900,
    onBackground = InkHigh,
    surface = Charcoal900,
    onSurface = InkHigh,
    surfaceVariant = Charcoal800,
    onSurfaceVariant = InkMid,
    surfaceContainerHighest = Charcoal700,
    error = RedError,
    onError = Charcoal950,
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp, color = InkMid),
    labelSmall = TextStyle(fontSize = 11.sp, color = InkLow),
)

@Composable
fun OfflineMessengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = AppTypography,
        content = content,
    )
}
