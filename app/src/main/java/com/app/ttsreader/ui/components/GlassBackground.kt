package com.app.ttsreader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
 */
@Composable
internal fun GlassBackground() {
    val transition = rememberInfiniteTransition(label = "aurora")
    val anim by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14_000, easing = LinearEasing)
        ),
        label = "auroraPhase"
    )

    // Hoist Path allocation outside the draw block — reused every frame
    val auroraPath = remember { Path() }

    // Static base gradient — never changes, so remember once
    val baseGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF103850),
                Color(0xFF185C80),
                Color(0xFF0E3040),
                Color(0xFF0A1E30)
            )
        )
    }

    // Static bottom-glow brush — no animation dependency
    val bottomGlowBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF00FFC8).copy(alpha = 0.28f),
                Color.Transparent
            )
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = anim * 2f * Math.PI.toFloat()

        // ── 1. Base: 2× brightened dark gradient ───────────────────────
        drawRect(brush = baseGradient)

        // ── 2. Aurora S-curve sweep (animated cubic bezier) ────────────
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
            path  = auroraPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF00E0A4).copy(alpha = 0.72f),
                    Color(0xFF66FFD1).copy(alpha = 0.62f),
                    Color(0xFF00CFFF).copy(alpha = 0.50f),
                    Color.Transparent
                ),
                start = Offset(0f, h * 0.4f),
                end   = Offset(w,  h * 0.7f)
            ),
            blendMode = BlendMode.Screen
        )

        // ── 3. Upper green flare — top-right ───────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF7CFFB2).copy(alpha = 0.52f),
                    Color.Transparent
                )
            ),
            radius    = w * 0.8f,
            center    = Offset(w * (0.8f + 0.02f * sin(t)), h * 0.2f),
            blendMode = BlendMode.Screen
        )

        // ── 4. Left teal glow ──────────────────────────────────────────
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = 0.40f),
                    Color.Transparent
                )
            ),
            radius    = w * 0.9f,
            center    = Offset(w * (0.15f - 0.02f * sin(t)), h * 0.4f),
            blendMode = BlendMode.Screen
        )

        // ── 5. Bottom deep glow ────────────────────────────────────────
        drawCircle(
            brush     = bottomGlowBrush,
            radius    = w * 1.1f,
            center    = Offset(w * 0.5f, h * 0.95f),
            blendMode = BlendMode.Screen
        )

        // ── 6. Grain stars (120, deterministic — no allocation) ────────
        repeat(120) { i ->
            val x = (i * 97 % 1000) / 1000f * w
            val y = (i * 57 % 1000) / 1000f * h
            drawCircle(
                color  = Color.White.copy(alpha = 0.09f),
                radius = 1.2f,
                center = Offset(x, y)
            )
        }
    }
}
