package com.example.docscanner.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF2D5BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE5FF),
    onPrimaryContainer = Color(0xFF0B1F66),
    secondary = Color(0xFF00A88F),
    secondaryContainer = Color(0xFFD2F5EE),
    tertiary = Color(0xFFFF7A2F),
    tertiaryContainer = Color(0xFFFFE3D2),
    background = Color(0xFFF6F7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFECEFF7),
    onSurfaceVariant = Color(0xFF59607A),
    outlineVariant = Color(0xFFD9DEEC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB4FF),
    onPrimary = Color(0xFF0B1F66),
    primaryContainer = Color(0xFF1F3180),
    onPrimaryContainer = Color(0xFFDDE5FF),
    secondary = Color(0xFF5FDCC5),
    secondaryContainer = Color(0xFF0D4F45),
    tertiary = Color(0xFFFFB68F),
    tertiaryContainer = Color(0xFF5C2B0C),
    background = Color(0xFF0F1115),
    surface = Color(0xFF171A21),
    surfaceVariant = Color(0xFF232733),
    onSurfaceVariant = Color(0xFFB4BAD0),
    outlineVariant = Color(0xFF343A4D)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
