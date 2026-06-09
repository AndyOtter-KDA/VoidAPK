package com.voidchat.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = VoidBlack,
    secondary = HotPink,
    onSecondary = VoidBlack,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = VoidDarkNavy,
    onSurface = TextPrimary,
    error = ErrorRed,
    onError = TextPrimary,
    outline = BorderDark
)

@Composable
fun VoidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = VoidTypography,
        content = content
    )
}

/**
 * Cyberpunk Scanline overlay for high-immersion Terminal look.
 */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = 1.dp.toPx()
        val spacing = 6.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Green.copy(alpha = 0.03f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth
            )
            y += spacing
        }
    }
}
