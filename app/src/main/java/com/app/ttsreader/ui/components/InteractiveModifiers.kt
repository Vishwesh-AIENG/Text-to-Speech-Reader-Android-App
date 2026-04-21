package com.app.ttsreader.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Composed modifier that scales an element when pressed, giving tactile feel.
 *
 * Pass the same [MutableInteractionSource] to both the button and this modifier
 * so the scale is driven by the button's own press state.
 *
 * @param interactionSource The interaction source from the button/clickable.
 * @param pressedScale Target scale on press (default 0.94f).
 */
fun Modifier.scaleOnPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessHigh
        ),
        label = "scaleOnPress"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Performs a short haptic tap feedback using the Compose [LocalHapticFeedback].
 * Call inside an onClick lambda:
 *
 * ```kotlin
 * val haptic = LocalHapticFeedback.current
 * Button(onClick = { haptic.tap() }) { ... }
 * ```
 *
 * Uses [HapticFeedbackType.LongPress] which maps to VIRTUAL_KEY on most devices —
 * a crisp, short tick sensation appropriate for button taps.
 */
fun androidx.compose.ui.hapticfeedback.HapticFeedback.tap() {
    performHapticFeedback(HapticFeedbackType.LongPress)
}
