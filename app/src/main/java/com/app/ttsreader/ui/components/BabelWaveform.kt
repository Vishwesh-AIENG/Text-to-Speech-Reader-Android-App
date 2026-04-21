package com.app.ttsreader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.app.ttsreader.ui.theme.HubColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated neon waveform bar graph that responds to audio RMS level.
 *
 * ## Idle behaviour
 * Even when [rmsLevel] is 0, the bars gently pulse at low amplitude using an
 * [InfiniteTransition] phase offset — this signals "listening" without appearing frozen.
 *
 * ## Active behaviour
 * When [rmsLevel] > 0 (microphone picking up audio), bars grow proportionally.
 * The sin-wave envelope creates an organic, non-uniform look rather than all bars
 * moving in lockstep.
 *
 * @param rmsLevel Normalised microphone amplitude in [0, 1]. 0 = silence / idle.
 * @param color    Bar colour — defaults to [HubColors.NeonGreen].
 * @param modifier Sizing is driven by the caller; this composable fills whatever
 *                 space the modifier provides.
 */
@Composable
fun BabelWaveform(
    rmsLevel: Float,
    color: Color = HubColors.NeonGreen,
    modifier: Modifier = Modifier
) {
    val barCount = 22

    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue   = 0f,
        targetValue    = (2.0 * PI).toFloat(),
        animationSpec  = infiniteRepeatable(
            animation  = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Canvas(modifier = modifier) {
        val totalWidth  = size.width
        val maxHeight   = size.height
        val gap         = totalWidth / (barCount * 2f - 1f)  // bar width = gap; spacing = gap
        val barWidth    = gap
        val baseHeight  = maxHeight * 0.12f                   // minimum bar height at silence

        for (i in 0 until barCount) {
            // Envelope: sin wave across bar index + animated phase
            val envelope    = sin(phase + i * 0.45f)           // −1 … +1
            val idlePulse   = envelope * maxHeight * 0.08f      // subtle idle movement
            val activeDrive = rmsLevel * maxHeight * 0.82f *
                              (0.6f + 0.4f * sin(phase + i * 0.8f).coerceAtLeast(0f))

            val barHeight = (baseHeight + idlePulse + activeDrive).coerceIn(4f, maxHeight)
            val x         = i * barWidth * 2f
            val top       = (maxHeight - barHeight) / 2f

            val alpha = if (rmsLevel > 0.05f) 0.92f else 0.38f

            drawRect(
                color   = color.copy(alpha = alpha),
                topLeft = Offset(x, top),
                size    = Size(barWidth, barHeight)
            )
        }
    }
}
