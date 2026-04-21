package com.app.ttsreader.camera

import com.app.ttsreader.ocr.SpatialWord
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Stabilises native-engine bounding boxes across frames using three complementary techniques:
 *
 * 1. **EMA (Exponential Moving Average)**
 *    Each incoming coordinate is blended with the previous smoothed value:
 *    `smoothed = old × (1 − α) + incoming × α`
 *    α = 0.25 means new data contributes 25 %; the box drifts smoothly rather
 *    than teleporting.
 *
 * 2. **Dead-zone filter**
 *    If the incoming centre moves less than [DEAD_ZONE_PX] from the tracked
 *    centre, the update is treated as noise and the box stays where it is.
 *
 * 3. **Persistence**
 *    When the engine momentarily loses a word, the last smoothed position is
 *    kept on screen for [PERSISTENCE_MS] milliseconds before fading away.
 *
 * ## Block matching
 * Incoming words are matched to tracked boxes by the closest Euclidean centre
 * distance within [MATCH_RADIUS_PX]. Unmatched incoming words start as new
 * entries; unmatched tracked boxes persist until they expire.
 *
 * ## Threading
 * [update] is called from the ViewModel coroutine scope (single-threaded).
 */
class BoundingBoxSmoother {

    companion object {
        private const val ALPHA          = 0.25f
        private const val DEAD_ZONE_PX   = 10f
        private const val MATCH_RADIUS_PX = 150f
        const val PERSISTENCE_MS         = 500L
    }

    private data class TrackedBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val lastSeenMs: Long
    ) {
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    private var tracked: List<TrackedBox> = emptyList()

    /**
     * Processes one frame's worth of native [SpatialWord] detections and returns
     * the stabilised boxes.
     *
     * Calling with an empty list still runs the persistence logic — boxes expire
     * naturally rather than being wiped instantly.
     */
    fun update(words: List<SpatialWord>): List<SmoothedBox> {
        val now  = System.currentTimeMillis()
        val next = mutableListOf<TrackedBox>()
        val matchedTrackedIndices = mutableSetOf<Int>()

        for (word in words) {
            val r        = word.toBoundingRect()
            val inLeft   = r.left.toFloat()
            val inTop    = r.top.toFloat()
            val inRight  = r.right.toFloat()
            val inBottom = r.bottom.toFloat()
            val inCX     = (inLeft + inRight)  / 2f
            val inCY     = (inTop  + inBottom) / 2f

            var bestIndex = -1
            var bestDist  = Float.MAX_VALUE
            // Early-exit: if we find a tracked box already inside the dead-zone,
            // no other candidate can produce a visibly better match — stop
            // scanning to keep the inner loop near-O(n) for stable frames.
            run {
                tracked.forEachIndexed { idx, t ->
                    if (idx in matchedTrackedIndices) return@forEachIndexed
                    val dist = hypot(
                        (t.centerX - inCX).toDouble(),
                        (t.centerY - inCY).toDouble()
                    ).toFloat()
                    if (dist < bestDist && dist < MATCH_RADIUS_PX) {
                        bestDist  = dist
                        bestIndex = idx
                        if (bestDist < DEAD_ZONE_PX) return@run
                    }
                }
            }

            if (bestIndex >= 0) {
                matchedTrackedIndices.add(bestIndex)
                val old = tracked[bestIndex]
                val dx  = abs(inCX - old.centerX)
                val dy  = abs(inCY - old.centerY)

                next.add(
                    if (dx < DEAD_ZONE_PX && dy < DEAD_ZONE_PX) {
                        old.copy(lastSeenMs = now)
                    } else {
                        val inv = 1f - ALPHA
                        TrackedBox(
                            left       = old.left   * inv + inLeft   * ALPHA,
                            top        = old.top    * inv + inTop    * ALPHA,
                            right      = old.right  * inv + inRight  * ALPHA,
                            bottom     = old.bottom * inv + inBottom * ALPHA,
                            lastSeenMs = now
                        )
                    }
                )
            } else {
                next.add(TrackedBox(inLeft, inTop, inRight, inBottom, now))
            }
        }

        tracked.forEachIndexed { idx, t ->
            if (idx !in matchedTrackedIndices) {
                val age = now - t.lastSeenMs
                if (age < PERSISTENCE_MS) next.add(t)
            }
        }

        tracked = next
        return next.map { SmoothedBox(it.left, it.top, it.right, it.bottom) }
    }

    fun clear() {
        tracked = emptyList()
    }
}
