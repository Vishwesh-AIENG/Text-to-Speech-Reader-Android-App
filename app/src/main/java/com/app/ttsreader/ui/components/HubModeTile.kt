package com.app.ttsreader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Glassmorphism tile for the Modal Hub dashboard.
 *
 * ## Visual
 * Frosted-glass card with diagonal gradient, thin white border, 20 dp
 * corners. Icon in a 48 dp frosted container (top-start), labels at
 * bottom-start.  Matches the Figma reference exactly.
 *
 * ## Entry animation
 * Fade-in + slide-up triggered after [animationDelay] ms, creating a
 * staggered reveal across the grid (100 ms base + 50 ms per card).
 *
 * ## Press interactions (5 effects)
 * 1. **Glass highlight sweep** — 600 ms light band across the card.
 * 2. **Outer glow** — expanding white shadow behind the card.
 * 3. **Icon micro-bounce** — spring-physics scale 1.15× with Y lift.
 * 4. **Card expand** — scale 1.03× (container-transform hint).
 * 5. **Haptic** — short vibration on touch.
 */
@Composable
fun HubModeTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isBeta: Boolean = false,
    isComingSoon: Boolean = false,
    onClick: () -> Unit,
    animationDelay: Int = 0,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // ── Entry animation ──────────────────────────────────────────────────
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(animationDelay.toLong())
        appeared = true
    }
    val entryAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "entryAlpha"
    )
    val entrySlide by animateFloatAsState(
        targetValue = if (appeared) 0f else 50f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "entrySlide"
    )

    // ── Press interaction state ──────────────────────────────────────────
    val highlightSweep = remember { Animatable(-0.5f) }
    val glowAlpha      = remember { Animatable(0f) }
    val iconScale      = remember { Animatable(1f) }
    val iconLift       = remember { Animatable(0f) }
    val cardScale      = remember { Animatable(1f) }

    val contentAlpha = if (isComingSoon) 0.50f else 1f

    // Card glass gradient: brighter at top-left, dimmer at bottom-right
    // Remembered because it never changes for the lifetime of a tile
    val cardBg = remember {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f),
                Color.White.copy(alpha = 0.04f)
            )
        )
    }
    Box(
        modifier = modifier
            // Layer 1: entry animation + card scale (outer-most transform)
            .graphicsLayer {
                alpha = entryAlpha
                translationY = entrySlide
                scaleX = cardScale.value
                scaleY = cardScale.value
            }
            // Layer 2: outer glow (drawn BEFORE clip so it extends beyond card)
            .drawBehind {
                if (glowAlpha.value > 0.01f) {
                    val expansion = 14.dp.toPx() * glowAlpha.value
                    val cornerPx = 20.dp.toPx()
                    drawRoundRect(
                        color = Color.White.copy(alpha = glowAlpha.value * 0.10f),
                        topLeft = Offset(-expansion, -expansion),
                        size = Size(size.width + expansion * 2, size.height + expansion * 2),
                        cornerRadius = CornerRadius(cornerPx + expansion)
                    )
                }
            }
            // Layer 3: clip + glass fill + border + touch
            .clip(shape)
            .background(brush = cardBg, shape = shape)
            // Border colour read in draw scope — glowAlpha changes only trigger
            // a redraw, not a recomposition of the full tile.
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color        = Color.White.copy(
                        alpha = (0.14f + glowAlpha.value * 0.8f).coerceAtMost(0.50f)
                    ),
                    style        = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(20.dp.toPx())
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // ── Haptic ───────────────────────────────────────
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                        val pressStart = System.currentTimeMillis()

                        // ── Icon bounce (spring physics) ─────────────────
                        scope.launch {
                            iconScale.animateTo(
                                1.15f,
                                spring(dampingRatio = 0.4f, stiffness = 800f)
                            )
                            iconScale.animateTo(
                                1f,
                                spring(dampingRatio = 0.5f, stiffness = 500f)
                            )
                        }
                        scope.launch {
                            iconLift.animateTo(
                                -4f,
                                spring(dampingRatio = 0.4f, stiffness = 800f)
                            )
                            iconLift.animateTo(
                                0f,
                                spring(dampingRatio = 0.5f, stiffness = 500f)
                            )
                        }

                        // ── Glass highlight sweep ────────────────────────
                        scope.launch {
                            highlightSweep.snapTo(-0.5f)
                            highlightSweep.animateTo(
                                1.5f,
                                tween(600, easing = FastOutSlowInEasing)
                            )
                        }

                        // ── Outer glow ───────────────────────────────────
                        scope.launch {
                            glowAlpha.animateTo(0.30f, tween(120))
                        }

                        // ── Card expand ──────────────────────────────────
                        scope.launch {
                            cardScale.animateTo(
                                1.03f,
                                tween(280, easing = FastOutSlowInEasing)
                            )
                        }

                        val confirmed = tryAwaitRelease()

                        if (confirmed) {
                            val elapsed   = System.currentTimeMillis() - pressStart
                            val remaining = (500L - elapsed).coerceAtLeast(0L)
                            delay(remaining)
                            onClick()
                        }

                        // ── Reset ────────────────────────────────────────
                        scope.launch { glowAlpha.animateTo(0f, tween(300)) }
                        scope.launch { cardScale.animateTo(1f, tween(220)) }
                    }
                )
            }
    ) {
        // ── Glass highlight sweep overlay ─────────────────────────────────
        Canvas(modifier = Modifier.matchParentSize()) {
            val sweep = highlightSweep.value
            if (sweep > -0.5f && sweep < 1.5f) {
                val sweepW = size.width * 0.40f
                val cx     = size.width * sweep
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.3f to Color.White.copy(alpha = 0.06f),
                            0.5f to Color.White.copy(alpha = 0.12f),
                            0.7f to Color.White.copy(alpha = 0.06f),
                            1.0f to Color.Transparent
                        ),
                        startX = cx - sweepW / 2f,
                        endX   = cx + sweepW / 2f
                    )
                )
            }
        }

        // ── Content ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Icon container — frosted rounded square with spring bounce
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                        translationY = iconLift.value
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = Color.White.copy(alpha = 0.85f * contentAlpha),
                    modifier           = Modifier.size(24.dp)
                )
            }

            // Push labels to the bottom
            Spacer(modifier = Modifier.weight(1f))

            // Title
            Text(
                text          = title,
                color         = Color.White.copy(alpha = contentAlpha),
                fontWeight    = FontWeight.SemiBold,
                fontSize      = 15.sp,
                lineHeight    = 20.sp,
                letterSpacing = 0.2.sp
            )
            Spacer(Modifier.height(2.dp))
            // Subtitle
            Text(
                text       = subtitle,
                color      = Color.White.copy(alpha = 0.50f * contentAlpha),
                fontSize   = 11.sp,
                lineHeight = 14.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
        }

        // ── Badge (BETA / SOON) ──────────────────────────────────────────
        val badgeText = when {
            isBeta       -> "BETA"
            isComingSoon -> "SOON"
            else         -> null
        }
        if (badgeText != null) {
            Surface(
                modifier       = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .wrapContentSize(),
                color          = Color.White.copy(alpha = 0.10f),
                shape          = RoundedCornerShape(6.dp),
                tonalElevation = 0.dp
            ) {
                Text(
                    text          = badgeText,
                    color         = Color.White.copy(alpha = 0.65f),
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier      = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}
