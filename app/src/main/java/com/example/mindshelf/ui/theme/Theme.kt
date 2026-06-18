package com.example.mindshelf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = InkBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4F5),
    onPrimaryContainer = InkBlueDark,
    secondary = SlateBlue,
    onSecondary = Color.White,
    tertiary = SageGreen,
    onTertiary = Color.White,
    background = CanvasLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFEDF0F3),
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFE2E5EA),
    error = Color(0xFFC62828),
    onError = Color.White,
)

private val DarkColorScheme = darkColorScheme(
    primary = InkBlueLight,
    onPrimary = Color(0xFF0A3058),
    primaryContainer = Color(0xFF1A4A7A),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF8FA8BE),
    tertiary = Color(0xFF6DB5A5),
    background = CanvasDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = AiBubbleDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = OutlineDark,
    outlineVariant = Color(0xFF343842),
    error = Color(0xFFEF5350),
    onError = Color(0xFF601410),
)

@Composable
fun MindShelfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = MindShelfTypography,
        shapes = MindShelfShapes,
        content = content,
    )
}

@Composable
fun chatUserBubbleColor(): Color =
    if (isSystemInDarkTheme()) UserBubbleDark else UserBubbleLight
