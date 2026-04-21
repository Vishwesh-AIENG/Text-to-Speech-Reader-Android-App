package com.app.ttsreader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.app.ttsreader.ui.theme.HubColors
import com.app.ttsreader.viewmodel.FocusBand

/**
 * Camera-mode overlay that visually isolates a single line/paragraph of text.
 *
 * ## Layout
 * ```
 * ┌────────────────────────────┐
 * │  ████ dim region ████████  │  alpha = 0.76 (above focus band)
 * ├────────────────────────────┤  ← neon green border line
 * │                            │  focus band — clear + inner glow
 * ├────────────────────────────┤  ← neon green border line
 * │  ████ dim region ████████  │  alpha = 0.76 (below focus band)
 * └────────────────────────────┘
 * ```
 *
 * ## Smoothing
 * [animateFloatAsState] provides a second layer of animation (120 ms) on top of
 * the EMA smoothing in the ViewModel.  Together they guarantee the band glides
 * without visible steps even on slow devices where frame analysis is infrequent.
 *
 * ## No-text state
 * When [hasText] is false the overlay draws a gentle scanning guide (a dim
 * centred zone with a pulsing border) so the user knows the camera is live
 * and searching for content.
 *
 * @param focusBand Current focus window from [DyslexiaViewModel].
 * @param hasText   Whether any text was detected in the last analyzed frame.
 */
@Composable
fun DyslexiaFocusOverlay(
    focusBand: FocusBand,
    hasText: Boolean,
    modifier: Modifier = Modifier
) {
    val animTop by animateFloatAsState(
        targetValue   = focusBand.topFraction,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label         = "focusTop"
    )
    val animBottom by animateFloatAsState(
        targetValue   = focusBand.bottomFraction,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label         = "focusBottom"
    )

    Canvas(modifier = modifier) {
        val h         = size.height
        val w         = size.width
        val topPx     = animTop    * h
        val bottomPx  = animBottom * h
        val bandH     = (bottomPx - topPx).coerceAtLeast(1f)
        val borderPx  = 2.dp.toPx()

        if (hasText) {
            // ── Top dim region ────────────────────────────────────────────────
            drawRect(
                color   = Color.Black.copy(alpha = 0.76f),
                topLeft = Offset.Zero,
                size    = Size(w, topPx)
            )

            // ── Bottom dim region ─────────────────────────────────────────────
            drawRect(
                color   = Color.Black.copy(alpha = 0.76f),
                topLeft = Offset(0f, bottomPx),
                size    = Size(w, h - bottomPx)
            )

            // ── Focus band inner vertical glow ────────────────────────────────
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        HubColors.NeonGreen.copy(alpha = 0.12f),
                        Color.Transparent,
                        HubColors.NeonGreen.copy(alpha = 0.12f)
                    ),
                    startY = topPx,
                    endY   = bottomPx
                ),
                topLeft = Offset(0f, topPx),
                size    = Size(w, bandH)
            )

            // ── Neon green border — top edge ──────────────────────────────────
            drawLine(
                color       = HubColors.NeonGreen.copy(alpha = 0.90f),
                start       = Offset(0f, topPx),
                end         = Offset(w, topPx),
                strokeWidth = borderPx
            )

            // ── Outer glow on top edge ────────────────────────────────────────
            drawLine(
                color       = HubColors.NeonGreen.copy(alpha = 0.20f),
                start       = Offset(0f, topPx),
                end         = Offset(w, topPx),
                strokeWidth = borderPx * 5f
            )

            // ── Neon green border — bottom edge ───────────────────────────────
            drawLine(
                color       = HubColors.NeonGreen.copy(alpha = 0.90f),
                start       = Offset(0f, bottomPx),
                end         = Offset(w, bottomPx),
                strokeWidth = borderPx
            )

            // ── Outer glow on bottom edge ─────────────────────────────────────
            drawLine(
                color       = HubColors.NeonGreen.copy(alpha = 0.20f),
                start       = Offset(0f, bottomPx),
                end         = Offset(w, bottomPx),
                strokeWidth = borderPx * 5f
            )

            // ── Left & right neon edge markers ────────────────────────────────
            val markerLen = 20.dp.toPx()
            // Top-left corner
            drawLine(
                color       = HubColors.NeonGreen,
                start       = Offset(0f, topPx),
                end         = Offset(markerLen, topPx),
                strokeWidth = borderPx * 1.5f
            )
            // Top-right corner
            drawLine(
                color       = HubColors.NeonGreen,
                start       = Offset(w - markerLen, topPx),
                end         = Offset(w, topPx),
                strokeWidth = borderPx * 1.5f
            )
            // Bottom-left corner
            drawLine(
                color       = HubColors.NeonGreen,
                start       = Offset(0f, bottomPx),
                end         = Offset(markerLen, bottomPx),
                strokeWidth = borderPx * 1.5f
            )
            // Bottom-right corner
            drawLine(
                color       = HubColors.NeonGreen,
                start       = Offset(w - markerLen, bottomPx),
                end         = Offset(w, bottomPx),
                strokeWidth = borderPx * 1.5f
            )

        } else {
            // ── No text detected — scanning guide ─────────────────────────────
            val centerY = h * 0.50f
            val guideH  = h * 0.18f

            // Dim everything outside the guide
            drawRect(
                color   = Color.Black.copy(alpha = 0.50f),
                size    = Size(w, h)
            )

            // Guide band (very dim neon fill)
            drawRect(
                color   = HubColors.NeonGreenFaint.copy(alpha = 0.40f),
                topLeft = Offset(0f, centerY - guideH / 2f),
                size    = Size(w, guideH)
            )

            // Guide border (dashed-look using short line segments)
            val segW = 24.dp.toPx()
            val gapW = 12.dp.toPx()
            var x = 0f
            val guideTop    = centerY - guideH / 2f
            val guideBottom = centerY + guideH / 2f
            while (x < w) {
                drawLine(
                    color       = HubColors.NeonGreen.copy(alpha = 0.50f),
                    start       = Offset(x, guideTop),
                    end         = Offset((x + segW).coerceAtMost(w), guideTop),
                    strokeWidth = borderPx
                )
                drawLine(
                    color       = HubColors.NeonGreen.copy(alpha = 0.50f),
                    start       = Offset(x, guideBottom),
                    end         = Offset((x + segW).coerceAtMost(w), guideBottom),
                    strokeWidth = borderPx
                )
                x += segW + gapW
            }
        }
    }
}
