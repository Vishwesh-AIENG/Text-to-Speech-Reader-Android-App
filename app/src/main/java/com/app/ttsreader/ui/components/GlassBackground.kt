package com.app.ttsreader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import kotlin.math.sin

/**
 * Full-screen aurora background shared by HubScreen and SettingsScreen.
 *
 * Render order (all Screen blend-mode glow layers):
 *  1. Near-black deep gradient base
 *  2. Animated S-curve aurora sweep via cubic-bezier [Path]
 *  3. Upper green flare — top-right
 *  4. Left teal glow
 *  5. Bottom deep glow
 *  6. 120 deterministic grain stars
 *
 * [BlendMode.Screen] on every glow layer ensures additive, luminous
 * compositing — colours only ever get brighter, never clip to grey.
 * A single 14 s [LinearEasing] phase drives all sine-wave wobbles.
 *
 * ### Performance notes
 * [drawWithCache] is used so that all [Brush] objects, grain-star coordinate
 * arrays, and the reusable [Path] are allocated exactly once per canvas-size
 * change (typically once per orientation change), not on every draw frame.
 * The animation state is read inside [onDrawBehind] — draw-scope reads only
 * trigger a redraw, never a recomposition.
 */
@Composable
internal fun GlassBackground() {
    val transition = rememberInfiniteTransition(label = "aurora")
    // Keep as State reference — value read only in draw scope to avoid
    // per-frame recompositions.
    val animState = transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = LinearEasing)
        ),
        label = "auroraPhase"
    )

    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height

                // ── Allocations cached until canvas size changes ────────────────

                // Static base gradient
                val baseGradient = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF103850),
                        Color(0xFF185C80),
                        Color(0xFF0E3040),
                        Color(0xFF0A1E30)
                    )
                )

                // Aurora sweep — start/end bake in canvas-size-relative positions
                val auroraSweepBrush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF00E0A4).copy(alpha = 0.72f),
                        Color(0xFF66FFD1).copy(alpha = 0.62f),
                        Color(0xFF00CFFF).copy(alpha = 0.50f),
                        Color.Transparent
                    ),
                    start = Offset(0f, h * 0.4f),
                    end   = Offset(w,  h * 0.7f)
                )

                // Static radial brushes — no geometry baked in, fully reusable
                val upperFlareBrush = Brush.radialGradient(
                    colors = listOf(Color(0xFF7CFFB2).copy(alpha = 0.52f), Color.Transparent)
                )
                val leftTealBrush = Brush.radialGradient(
                    colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.40f), Color.Transparent)
                )
                val bottomGlowBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF00FFC8).copy(alpha = 0.28f),
                        Color.Transparent
                    )
                )

                // Grain stars — positions precomputed from canvas size; colour is constant
                val grainColor = Color.White.copy(alpha = 0.09f)
                val grainXs = FloatArray(120) { i -> (i * 97 % 1000) / 1000f * w }
                val grainYs = FloatArray(120) { i -> (i * 57 % 1000) / 1000f * h }

                // Reusable path — reset each frame, never re-allocated
                val auroraPath = Path()

                onDrawBehind {
                    // Read animation state in draw scope — skips recomposition entirely
                    val t = animState.value * 2f * Math.PI.toFloat()

                    // ── 1. Base ─────────────────────────────────────────────────
                    drawRect(brush = baseGradient)

                    // ── 2. Aurora S-curve sweep (animated cubic bezier) ─────────
                    auroraPath.reset()
                    auroraPath.moveTo(0f, h * 0.55f)
                    auroraPath.cubicTo(
                        w * 0.25f, h * (0.35f + 0.05f * sin(t)),
                        w * 0.65f, h * (0.65f - 0.05f * sin(t)),
                        w,         h * 0.45f
                    )
                    auroraPath.lineTo(w, h * 0.7f)
                    auroraPath.cubicTo(
                        w * 0.65f, h * (0.85f - 0.04f * sin(t)),
                        w * 0.25f, h * (0.60f + 0.04f * sin(t)),
                        0f,        h * 0.75f
                    )
                    auroraPath.close()
                    drawPath(
                        path      = auroraPath,
                        brush     = auroraSweepBrush,
                        blendMode = BlendMode.Screen
                    )

                    // ── 3. Upper green flare — top-right ────────────────────────
                    drawCircle(
                        brush     = upperFlareBrush,
                        radius    = w * 0.8f,
                        center    = Offset(w * (0.8f + 0.02f * sin(t)), h * 0.2f),
                        blendMode = BlendMode.Screen
                    )

                    // ── 4. Left teal glow ───────────────────────────────────────
                    drawCircle(
                        brush     = leftTealBrush,
                        radius    = w * 0.9f,
                        center    = Offset(w * (0.15f - 0.02f * sin(t)), h * 0.4f),
                        blendMode = BlendMode.Screen
                    )

                    // ── 5. Bottom deep glow ─────────────────────────────────────
                    drawCircle(
                        brush     = bottomGlowBrush,
                        radius    = w * 1.1f,
                        center    = Offset(w * 0.5f, h * 0.95f),
                        blendMode = BlendMode.Screen
                    )

                    // ── 6. Grain stars (120, deterministic — zero heap alloc) ───
                    repeat(120) { i ->
                        drawCircle(
                            color  = grainColor,
                            radius = 1.2f,
                            center = Offset(grainXs[i], grainYs[i])
                        )
                    }
                }
            }
    )
}
