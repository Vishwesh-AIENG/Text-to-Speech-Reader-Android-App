package com.app.ttsreader.ar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.app.ttsreader.ui.theme.HubColors

/**
 * Animated AR focus reticle — four corner brackets that spring-snap to enclose
 * the bounding rectangle of every currently-stable detected block. When [active]
 * is false, the reticle eases to a centered idle rect and pulses.
 *
 * Pass screen-space pixel coordinates for the union of stable boxes. Values are
 * ignored when [active] is false.
 */
@Composable
fun FocusReticle(
    active: Boolean,
    targetLeft: Float,
    targetTop: Float,
    targetRight: Float,
    targetBottom: Float,
    modifier: Modifier = Modifier,
) {
    // Idle reticle size — centered 45 % of the smaller screen dimension
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val (idleL, idleT, idleR, idleB) = remember(configuration) {
        with(density) {
            val sw = configuration.screenWidthDp.dp.toPx()
            val sh = configuration.screenHeightDp.dp.toPx()
            val s = minOf(sw, sh) * 0.45f
            FloatQuad(
                (sw - s) * 0.5f,
                (sh - s) * 0.5f,
                (sw + s) * 0.5f,
                (sh + s) * 0.5f,
            )
        }
    }

    val l = if (active) targetLeft   else idleL
    val t = if (active) targetTop    else idleT
    val r = if (active) targetRight  else idleR
    val b = if (active) targetBottom else idleB

    val animSpec = spring<Float>(stiffness = 600f, dampingRatio = 0.85f)
    val animL by animateFloatAsState(l, animSpec, label = "ret-l")
    val animT by animateFloatAsState(t, animSpec, label = "ret-t")
    val animR by animateFloatAsState(r, animSpec, label = "ret-r")
    val animB by animateFloatAsState(b, animSpec, label = "ret-b")

    val pulseAlpha by rememberInfiniteTransition(label = "ret-pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = if (active) 0.55f else 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ret-alpha",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val strokePx = 2.dp.toPx()
        val padding  = 8.dp.toPx()
        val color    = HubColors.NeonGreen.copy(alpha = pulseAlpha)

        val left   = (animL - padding).coerceAtLeast(0f)
        val top    = (animT - padding).coerceAtLeast(0f)
        val right  = (animR + padding).coerceAtMost(size.width)
        val bottom = (animB + padding).coerceAtMost(size.height)
        val bracketLen = (minOf(right - left, bottom - top) * (1f / 6f))
            .coerceAtLeast(12.dp.toPx())

        val path = Path().apply {
            // Top-left
            moveTo(left, top + bracketLen); lineTo(left, top); lineTo(left + bracketLen, top)
            // Top-right
            moveTo(right - bracketLen, top); lineTo(right, top); lineTo(right, top + bracketLen)
            // Bottom-right
            moveTo(right, bottom - bracketLen); lineTo(right, bottom); lineTo(right - bracketLen, bottom)
            // Bottom-left
            moveTo(left + bracketLen, bottom); lineTo(left, bottom); lineTo(left, bottom - bracketLen)
        }

        drawPath(path = path, color = color, style = Stroke(width = strokePx))
    }
}

/** Lightweight 4-float bundle so `remember(configuration)` returns a single value. */
private data class FloatQuad(val l: Float, val t: Float, val r: Float, val b: Float)

/** Public 4-float reticle target — destructurable. */
data class ReticleQuad(val l: Float, val t: Float, val r: Float, val b: Float)
