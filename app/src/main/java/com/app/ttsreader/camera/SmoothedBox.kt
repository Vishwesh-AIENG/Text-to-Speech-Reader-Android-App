package com.app.ttsreader.camera

/**
 * Coordinate-smoothed bounding box emitted by [BoundingBoxSmoother].
 *
 * All values are in raw ML Kit image space (portrait 720×1280) — the same
 * coordinate system as [android.graphics.Rect] from [com.google.mlkit.vision.text.Text.TextBlock].
 * The overlay composable is responsible for scaling these to screen pixels.
 */
data class SmoothedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
