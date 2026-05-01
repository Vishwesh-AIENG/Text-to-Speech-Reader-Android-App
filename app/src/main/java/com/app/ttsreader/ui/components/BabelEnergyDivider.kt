package com.app.ttsreader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.app.ttsreader.ui.theme.HubColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated neon sine-wave divider between the two Babel speaker panels.
 *
 * ## Glow technique
 * Three draw passes at increasing stroke widths with decreasing alpha approximate
 * a Gaussian glow without [android.graphics.BlurMaskFilter] (unreliable in
 * hardware-accelerated Compose canvases). The innermost pass is the sharp core,
 * outer passes form the soft halo.
 *
 * ## Amplitude animation
 * [isActive] controls whether the wave is energetic (session running) or subdued
 * (ready state). Transitioning between states is animated over 900 ms so the
 * divider "wakes up" and "sleeps" smoothly.
 *
 * @param isActive true while a Babel session is in progress.
 * @param modifier Caller controls width; height is fixed inside this composable
 *                 via the [Canvas] draw area (the modifier height is what matters).
 */
@Composable
fun BabelEnergyDivider(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "energyDivider")

    // Phase advances continuously — drives horizontal wave motion
    val phase by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dividerPhase"
    )

    // Secondary phase at a different rate creates an interference-like shimmer
    val phase2 by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 2700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dividerPhase2"
    )

    // Amplitude transitions smoothly between idle and active states
    val amplitude by animateFloatAsState(
        targetValue   = if (isActive) 14f else 5f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label         = "dividerAmplitude"
    )

    val baseAlpha by animateFloatAsState(
        targetValue   = if (isActive) 0.90f else 0.45f,
        animationSpec = tween(durationMillis = 900),
        label         = "dividerAlpha"
    )

    // Drawing constants
    val steps = 200   // path resolution — higher = smoother curve

    // Pre-allocate once; reset + rebuild inside the draw block each frame.
    val path = remember { Path() }

    Canvas(modifier = modifier) {
        val w       = size.width
        val centerY = size.height / 2f

        // Rebuild the sine-wave path into the pre-allocated object — no GC pressure
        path.reset()
        for (i in 0..steps) {
            val x = w * i / steps
            // Superimpose two sine waves at different frequencies for a complex waveform
            val y = centerY +
                    amplitude * sin(phase  + x / w * 2f * PI.toFloat()) +
                    (amplitude * 0.35f) * sin(phase2 + x / w * 4f * PI.toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // ── Layer 1 — wide outer glow (most transparent) ──────────────────────
        drawPath(
            path  = path,
            color = HubColors.NeonGreen.copy(alpha = baseAlpha * 0.18f),
            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
        )

        // ── Layer 2 — mid glow ─────────────────────────────────────────────────
        drawPath(
            path  = path,
            color = HubColors.NeonGreen.copy(alpha = baseAlpha * 0.38f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )

        // ── Layer 3 — sharp neon core ──────────────────────────────────────────
        drawPath(
            path  = path,
            color = HubColors.NeonGreen.copy(alpha = baseAlpha),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
