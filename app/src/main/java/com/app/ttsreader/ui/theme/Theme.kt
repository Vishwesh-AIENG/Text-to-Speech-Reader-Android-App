package com.app.ttsreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary          = Purple80,
    secondary        = PurpleGrey80,
    tertiary         = Mauve80,
    background       = DarkCharcoal,
    surface          = DarkSurface,
    surfaceVariant   = DarkSurfaceVariant
)

@Composable
fun TTSReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography,
        content     = content
    )
}
