package com.app.smartgarden.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SmartGardenColorScheme = lightColorScheme(
    primary = AccentGreen,
    onPrimary = BackgroundPrimary,
    background = BackgroundPrimary,
    onBackground = TextPrimary,
    surface = BackgroundPrimary,
    onSurface = TextPrimary,
    surfaceVariant = BackgroundSecondary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = BackgroundPrimary,
    outline = DividerGray
)

private val SmartGardenShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SmartGardenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartGardenColorScheme,
        typography = AppTypography,
        shapes = SmartGardenShapes,
        content = content
    )
}