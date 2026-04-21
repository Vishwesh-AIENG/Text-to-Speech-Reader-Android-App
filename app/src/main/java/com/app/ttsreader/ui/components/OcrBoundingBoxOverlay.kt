package com.app.ttsreader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.app.ttsreader.camera.SmoothedBox

/**
 * Draws stabilised bounding rectangles over each detected text block.
 *
 * Receives [SmoothedBox] coordinates from [com.app.ttsreader.camera.BoundingBoxSmoother],
 * which has already applied EMA smoothing, dead-zone filtering, and persistence — so
 * the boxes here drift smoothly rather than flickering every frame.
 *
 * ## Coordinate transform
 * [SmoothedBox] values are in raw ML Kit image space (portrait 720×1280).
 * The PreviewView uses FILL_CENTER, which scales by `max(screenW/imageW, screenH/imageH)`
 * and centres — this composable applies the same transform.
 *
 * @param boxes    Stabilised boxes from [com.app.ttsreader.viewmodel.MainUiState.smoothedBoxes].
 * @param modifier Must be [Modifier.fillMaxSize] layered directly over the camera preview.
 */
@Composable
fun OcrBoundingBoxOverlay(
    boxes: List<SmoothedBox>,
    modifier: Modifier = Modifier
) {
    if (boxes.isEmpty()) return

    // Decorative overlay — hidden from TalkBack
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        // Analysis image dimensions in portrait display space: 720×1280
        val analysisW = 720f
        val analysisH = 1280f

        // FILL_CENTER scale: larger axis covers the screen; the other is cropped.
        val scale   = maxOf(size.width / analysisW, size.height / analysisH)
        val offsetX = (size.width  - analysisW * scale) / 2f
        val offsetY = (size.height - analysisH * scale) / 2f

        boxes.forEach { box ->
            val left   = box.left   * scale + offsetX
            val top    = box.top    * scale + offsetY
            val width  = (box.right  - box.left) * scale
            val height = (box.bottom - box.top)  * scale

            // Skip boxes that are fully outside the visible screen area
            if (left + width < 0f || left > size.width)  return@forEach
            if (top + height < 0f || top  > size.height) return@forEach

            // Filled background tint
            drawRect(
                color   = Color(0x267C3AED),   // purple 15 % opacity
                topLeft = Offset(left, top),
                size    = Size(width, height)
            )

            // Crisp outline stroke
            drawRect(
                color   = Color(0xCC7C3AED),   // purple 80 % opacity
                topLeft = Offset(left, top),
                size    = Size(width, height),
                style   = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
