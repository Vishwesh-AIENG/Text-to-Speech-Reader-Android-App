package com.app.ttsreader.camera

import androidx.compose.runtime.Immutable

/**
 * Coordinate-smoothed bounding box emitted by [BoundingBoxSmoother].
 *
 * Annotated [@Immutable] — all fields are primitive Floats and instances are
 * never mutated after creation, allowing Compose to skip recomposition in the
 * overlay when the same box coordinates are re-emitted.
 *
 * All values are in raw ML Kit image space (portrait 720×1280) — the same
 * coordinate system as [android.graphics.Rect] from [com.google.mlkit.vision.text.Text.TextBlock].
 * The overlay composable is responsible for scaling these to screen pixels.
 */
@Immutable
data class SmoothedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
