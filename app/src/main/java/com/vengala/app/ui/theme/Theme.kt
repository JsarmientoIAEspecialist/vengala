package com.vengala.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paleta neón rave — siempre oscura: es una app para usar de noche.
val NeonMagenta = Color(0xFFFF2E88)
val NeonCyan = Color(0xFF00E5FF)
val NeonLime = Color(0xFFB6FF00)
val NightBlack = Color(0xFF0A0A12)
val NightSurface = Color(0xFF15151F)
val NightSurfaceHigh = Color(0xFF1E1E2C)
val TextPrimary = Color(0xFFF2F0F7)
val TextDim = Color(0xFF8A8798)

private val VengalaColors = darkColorScheme(
    primary = NeonMagenta,
    onPrimary = Color.Black,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    tertiary = NeonLime,
    onTertiary = Color.Black,
    background = NightBlack,
    onBackground = TextPrimary,
    surface = NightSurface,
    onSurface = TextPrimary,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = TextDim,
    outline = Color(0xFF35333F),
    error = Color(0xFFFF5470),
)

private val VengalaType = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = 2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 1.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
    ),
)

@Composable
fun VengalaTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme() // la ignoramos: Vengala vive de noche
    MaterialTheme(
        colorScheme = VengalaColors,
        typography = VengalaType,
        content = content,
    )
}
