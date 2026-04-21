package com.app.ttsreader.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glass palette used across every feature screen.
 *
 * Paired with `GlassBackground()` (aurora) to give the whole app one consistent
 * frosted-glass identity — white-alpha surfaces and borders over a dark animated
 * aurora, with a cyan accent for interactive emphasis.
 *
 * Names retain their historical "NeonGreen*" form for backwards compatibility
 * with existing screen code; the values are now white-alpha variants.
 */
object HubColors {
    /** Pure black — reserved for camera dim scrims. */
    val Black = Color(0xFF000000)

    /** Primary glass foreground — icons, headings, active text. */
    val NeonGreen = Color.White

    /** 70 % white — secondary labels and subtitles. */
    val NeonGreenDim = Color.White.copy(alpha = 0.70f)

    /** 10 % white — pressed / hover tile fill. */
    val NeonGreenFaint = Color.White.copy(alpha = 0.10f)

    /** 22 % white — standard glass border. */
    val NeonGreenBorder = Color.White.copy(alpha = 0.22f)

    /** 8 % white — glass tile surface. */
    val TileSurface = Color.White.copy(alpha = 0.08f)

    /** Cyan accent (matches aurora highlight) for press swirls and active states. */
    val SwirlCenter = Color(0xFF66FFD1).copy(alpha = 0.45f)

    /** Cyan accent color — use for active sliders, chips, and highlighted chrome. */
    val Accent = Color(0xFF66FFD1)
}

/**
 * Soft ambient glow drawn behind the composable — used for headings and focused chrome.
 *
 * Defaults to white so it reads as a diffuse halo over the aurora glass background.
 * Must come before any `clip()` in the modifier chain.
 */
fun Modifier.subtleNeonGlow(
    color: Color = HubColors.NeonGreen,
    glowRadius: Dp = 8.dp,
    cornerRadius: Dp = 0.dp,
    intensity: Float = 0.12f,
    layerCount: Int = 5
): Modifier = this.drawBehind {
    val glowPx   = glowRadius.toPx()
    val cornerPx = cornerRadius.toPx()

    for (i in 1..layerCount) {
        val t         = i.toFloat() / layerCount
        val expansion = glowPx * t
        val alpha     = intensity * (1f - t * 0.75f)

        drawRoundRect(
            color      = color.copy(alpha = alpha),
            topLeft    = Offset(-expansion, -expansion),
            size       = Size(
                width  = size.width  + expansion * 2f,
                height = size.height + expansion * 2f
            ),
            cornerRadius = CornerRadius(
                x = (cornerPx + expansion).coerceAtLeast(0f),
                y = (cornerPx + expansion).coerceAtLeast(0f)
            )
        )
    }
}
