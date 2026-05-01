package com.app.ttsreader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Spring spec — one allocation shared by every ScaleOnPressNode.
private val SCALE_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness    = Spring.StiffnessHigh
)

/**
 * Scales an element when pressed using a spring animation.
 *
 * Pass the same [MutableInteractionSource] to both the button/clickable and this
 * modifier so the scale is driven by the button's own press state.
 *
 * Implemented as a [DrawModifierNode] instead of `composed {}`:
 * - No Modifier wrapper allocation on every recomposition.
 * - Coroutines are tied to node attach/detach, not composition.
 * - [Animatable.value] is a snapshot State; reading it in [DrawModifierNode.draw]
 *   is snapshot-tracked, so the layer redraws automatically every animation frame
 *   without any explicit invalidation call.
 *
 * @param interactionSource The interaction source from the enclosing clickable.
 * @param pressedScale Target scale while pressed (default 0.94f).
 */
fun Modifier.scaleOnPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f
): Modifier = this then ScaleOnPressElement(interactionSource, pressedScale)

// ── ModifierNodeElement ────────────────────────────────────────────────────────

private data class ScaleOnPressElement(
    val interactionSource: MutableInteractionSource,
    val pressedScale: Float
) : ModifierNodeElement<ScaleOnPressNode>() {
    override fun create()                       = ScaleOnPressNode(interactionSource, pressedScale)
    override fun update(node: ScaleOnPressNode) = node.update(interactionSource, pressedScale)
}

// ── Modifier.Node ──────────────────────────────────────────────────────────────

private class ScaleOnPressNode(
    var interactionSource: MutableInteractionSource,
    var pressedScale: Float
) : Modifier.Node(), DrawModifierNode {

    private val animatable     = Animatable(1f)
    private var interactionJob: Job? = null

    override fun onAttach() {
        launchObservers()
    }

    /** Called when the parent recomposes with changed params. */
    fun update(source: MutableInteractionSource, newPressedScale: Float) {
        val sourceChanged = interactionSource !== source
        interactionSource = source
        pressedScale      = newPressedScale
        if (sourceChanged) {
            interactionJob?.cancel()
            launchObservers()
        }
    }

    private fun launchObservers() {
        interactionJob = coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press   -> coroutineScope.launch {
                        animatable.animateTo(pressedScale, SCALE_SPRING)
                    }
                    is PressInteraction.Release,
                    is PressInteraction.Cancel  -> coroutineScope.launch {
                        animatable.animateTo(1f, SCALE_SPRING)
                    }
                }
            }
        }
    }

    /**
     * [animatable.value] is a snapshot [State] — reading it here is tracked by
     * the Compose draw-phase snapshot observer, so this function is automatically
     * re-executed (and the layer redrawn) on every animation frame.
     *
     * [drawContent] is defined on [ContentDrawScope], but [withTransform]'s draw
     * block only exposes [DrawScope] as the implicit receiver, so we capture the
     * outer [ContentDrawScope] reference and call [drawContent] on it explicitly.
     */
    override fun ContentDrawScope.draw() {
        val s = animatable.value  // snapshot-tracked: triggers redraw each frame
        if (s == 1f) {
            drawContent()
            return
        }
        val contentScope = this
        withTransform(
            transformBlock = { scale(s, s, size.center) },
            drawBlock      = { with(contentScope) { drawContent() } }
        )
    }
}

// ── HapticFeedback.tap() ──────────────────────────────────────────────────────

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
