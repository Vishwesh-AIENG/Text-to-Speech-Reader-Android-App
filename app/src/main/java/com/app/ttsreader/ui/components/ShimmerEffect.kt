package com.app.ttsreader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Constant colour stops — allocated once at class-load time, never re-created
private val SHIMMER_COLORS = listOf(
    Color(0xFFE0E0E0),
    Color(0xFFF5F5F5),
    Color(0xFFE0E0E0)
)

/**
 * Skeleton shimmer placeholder that mimics text lines while content is loading.
 *
 * Uses a [rememberInfiniteTransition] to sweep a highlight gradient across
 * rounded placeholder bars — the standard "loading" pattern in premium apps.
 *
 * ### Performance notes
 * The animated [translateX] value is kept as a [androidx.compose.runtime.State]
 * reference and read only inside [drawWithCache]'s [onDrawBehind] block.
 * This means changes to the animation trigger a **redraw**, not a recomposition,
 * and avoids the per-frame [Brush] allocation that the previous
 * `remember(translateX) { Brush.linearGradient(…) }` pattern incurred.
 *
 * @param lineCount   Number of skeleton lines to show.
 * @param lineHeight  Height of each placeholder bar.
 * @param lineSpacing Vertical space between bars.
 */
@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier,
    lineCount: Int = 3,
    lineHeight: Dp = 14.dp,
    lineSpacing: Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    // State reference — value read only in draw scope to avoid per-frame recompositions
    val translateXState = transition.animateFloat(
        initialValue  = -300f,
        targetValue   = 600f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    Column(modifier = modifier) {
        repeat(lineCount) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == lineCount - 1) 0.6f else 1f)
                    .height(lineHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .drawWithCache {
                        onDrawBehind {
                            // translateXState.value read in draw scope — redraw only
                            val x = translateXState.value
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = SHIMMER_COLORS,
                                    start  = Offset(x, 0f),
                                    end    = Offset(x + 300f, 0f)
                                )
                            )
                        }
                    }
            )
            if (index < lineCount - 1) {
                Spacer(Modifier.height(lineSpacing))
            }
        }
    }
}
