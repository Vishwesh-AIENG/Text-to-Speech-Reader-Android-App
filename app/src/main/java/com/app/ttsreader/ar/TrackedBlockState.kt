package com.app.ttsreader.ar

/**
 * Per-block tracker state — primitive fields only, no boxing, no ArrayDeque.
 *
 * Owned exclusively by the AR Lens tracker; mutated from a single confined
 * dispatcher so no synchronisation is required.
 *
 * Coordinates are stored in **post-rotation image space** (the same space
 * ML Kit returns its bounding boxes in when `InputImage.fromMediaImage(..., rotation)`
 * is used).
 *
 * @property id              Monotonic atomic id from the ViewModel. Identity is
 *                           decoupled from spatial position — motion never mints a new id.
 * @property cx, cy          Smoothed center in image-space pixels.
 * @property w, h            Smoothed width / height in image-space pixels.
 * @property vx, vy          Smoothed velocity (px / ms). Used to extrapolate
 *                           position on missed frames.
 * @property lastMeasuredCx,
 *           lastMeasuredCy  Last raw measurement (for finite-difference velocity).
 * @property lastSeenNs      `System.nanoTime()` of the last frame this block was matched.
 * @property missedFrames    Consecutive frames since the last match.
 * @property stableFrameCount Saturating counter — how long the block has been judged stable.
 * @property displayAlpha    Target alpha [0,1] published to the renderer.
 * @property sourceText      Raw ML Kit line text.
 * @property translatedText  Latest translation (empty until first translation lands).
 */
class TrackedBlockState(
    @JvmField val id: Long,
    @JvmField var cx: Float,
    @JvmField var cy: Float,
    @JvmField var w: Float,
    @JvmField var h: Float,
    @JvmField var sourceText: String,
) {
    @JvmField var vx: Float = 0f
    @JvmField var vy: Float = 0f
    @JvmField var lastMeasuredCx: Float = cx
    @JvmField var lastMeasuredCy: Float = cy
    @JvmField var lastSeenNs: Long = 0L
    @JvmField var missedFrames: Int = 0
    @JvmField var stableFrameCount: Int = 0
    @JvmField var displayAlpha: Float = 0f
    @JvmField var translatedText: String = ""

    /** Predicted center after `dtMs` milliseconds with constant velocity. */
    fun predictCx(dtMs: Float): Float = cx + vx * dtMs
    fun predictCy(dtMs: Float): Float = cy + vy * dtMs

    /** Image-space bounds, computed on demand. */
    val left:   Float get() = cx - w * 0.5f
    val top:    Float get() = cy - h * 0.5f
    val right:  Float get() = cx + w * 0.5f
    val bottom: Float get() = cy + h * 0.5f
}
