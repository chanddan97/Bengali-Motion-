package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BanglaMotionDarkColorScheme = darkColorScheme(
    primary = StudioAmberPrimary,
    onPrimary = StudioOnAmber,
    primaryContainer = StudioAmberContainer,
    onPrimaryContainer = StudioOnAmberContainer,
    secondary = StudioCoralSecondary,
    onSecondary = StudioOnCoral,
    secondaryContainer = StudioCoralContainer,
    onSecondaryContainer = StudioOnCoralContainer,
    tertiary = StudioTealTertiary,
    onTertiary = StudioOnTeal,
    tertiaryContainer = StudioTealContainer,
    onTertiaryContainer = StudioOnTealContainer,
    background = StudioMidnight,
    onBackground = StudioInkPrimary,
    surface = StudioNavySurface,
    onSurface = StudioInkPrimary,
    surfaceVariant = StudioCardElevated,
    onSurfaceVariant = StudioInkSecondary,
    surfaceContainerLowest = StudioMidnight,
    surfaceContainerLow = StudioNavySurface,
    surfaceContainer = StudioCardElevated,
    surfaceContainerHigh = StudioCardHighest,
    surfaceContainerHighest = StudioCardHighest,
    outline = StudioBorderSubtle,
    outlineVariant = StudioCardHighest,
    error = StudioDanger,
    onError = StudioOnDanger,
    errorContainer = StudioDangerContainer,
    onErrorContainer = StudioOnDangerContainer,
)

private val BanglaMotionShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun BanglaMotionTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BanglaMotionDarkColorScheme,
        typography = BanglaMotionTypography,
        shapes = BanglaMotionShapes,
        content = content,
    )
}
