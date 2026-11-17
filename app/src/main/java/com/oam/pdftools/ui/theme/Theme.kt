package com.oam.pdftools.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Calm, neutral palette with one trustworthy blue. Follows the system light/dark setting. */
private val Light = lightColorScheme(
    primary = Color(0xFF2F5BEA), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3FF), onPrimaryContainer = Color(0xFF0A1C5C),
    secondaryContainer = Color(0xFFE3E7F1), onSecondaryContainer = Color(0xFF1B2333),
    background = Color(0xFFF7F8FB), surface = Color(0xFFF7F8FB),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F2F7), surfaceContainerHigh = Color(0xFFE9ECF3),
    onSurfaceVariant = Color(0xFF5A6272), outlineVariant = Color(0xFFE0E3EA)
)
private val Dark = darkColorScheme(
    primary = Color(0xFFB3C4FF), onPrimary = Color(0xFF0A2A86),
    primaryContainer = Color(0xFF223F9E), onPrimaryContainer = Color(0xFFDCE3FF),
    background = Color(0xFF101216), surface = Color(0xFF101216),
    surfaceContainerLowest = Color(0xFF0C0E11), surfaceContainerLow = Color(0xFF181B21),
    surfaceContainer = Color(0xFF1D2027), surfaceContainerHigh = Color(0xFF262A32),
    onSurfaceVariant = Color(0xFFAEB4C2), outlineVariant = Color(0xFF30343D)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = if (isSystemInDarkTheme()) Dark else Light,
    shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp)
    ),
    content = content
)
