package com.app.ttsreader.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Skeleton shimmer placeholder that mimics text lines while content is loading.
 *
 * Uses an [rememberInfiniteTransition] to sweep a highlight gradient across
 * rounded placeholder bars — the standard "loading" pattern in premium apps.
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
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )

    val shimmerBrush = remember(translateX) {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFE0E0E0),
                Color(0xFFF5F5F5),
                Color(0xFFE0E0E0)
            ),
            start = Offset(translateX, 0f),
            end = Offset(translateX + 300f, 0f)
        )
    }

    Column(modifier = modifier) {
        repeat(lineCount) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (index == lineCount - 1) 0.6f else 1f)
                    .height(lineHeight)
                    .background(shimmerBrush, RoundedCornerShape(4.dp))
            )
            if (index < lineCount - 1) {
                Spacer(Modifier.height(lineSpacing))
            }
        }
    }
}
