package com.app.ttsreader.ar

import android.graphics.RectF

/**
 * Stateless per-frame label collision resolver.
 *
 * For each label, tries slots in priority order:
 *   1. above the box, left-aligned
 *   2. above the box, center-aligned
 *   3. below the box, left-aligned
 *   4. below the box, center-aligned
 *   5. inside the box, top strip
 *
 * Each candidate is tested against a 10×10 spatial-bin index of already-placed
 * labels; the first non-overlapping candidate fully inside the canvas wins.
 * If every slot collides, the label is culled (the box still draws).
 *
 * The engine takes the box and the desired label dimensions as input; it does
 * NOT measure text — pass a pre-measured `labelW × labelH` per call.
 */
class LabelLayoutEngine(
    private val canvasW: Float,
    private val canvasH: Float,
    private val gap: Float = 4f,
) {
    private val binsX = 10
    private val binsY = 10
    private val binW: Float get() = canvasW / binsX
    private val binH: Float get() = canvasH / binsY

    /**
     * Buckets indexed `binY * binsX + binX` → list of label rects in that bucket.
     * Reused across [resolve] calls within the same render frame after [reset].
     */
    private val placed: Array<ArrayList<RectF>> = Array(binsX * binsY) { ArrayList(4) }

    /** Clear all placed rects between frames. */
    fun reset() {
        for (bucket in placed) bucket.clear()
    }

    /**
     * Try to place a label of size [labelW] × [labelH] near [box].
     * Returns the chosen rect (top-left corner is the draw origin), or `null`
     * if every candidate collides and the label should be culled.
     *
     * The returned rect is owned by the caller — the engine keeps the same
     * geometry internally inside its spatial index.
     */
    fun resolve(box: RectF, labelW: Float, labelH: Float): RectF? {
        val candidates = arrayOf(
            // above the box, left-aligned
            rect(box.left, box.top - labelH - gap, labelW, labelH),
            // above, center-aligned with box
            rect(box.centerX() - labelW * 0.5f, box.top - labelH - gap, labelW, labelH),
            // below, left-aligned
            rect(box.left, box.bottom + gap, labelW, labelH),
            // below, center-aligned
            rect(box.centerX() - labelW * 0.5f, box.bottom + gap, labelW, labelH),
            // inside, top strip
            rect(box.left + gap, box.top + gap, labelW, labelH),
        )

        for (candidate in candidates) {
            if (!insideCanvas(candidate)) continue
            if (collides(candidate)) continue
            insert(candidate)
            return candidate
        }
        return null
    }

    private fun rect(x: Float, y: Float, w: Float, h: Float): RectF =
        RectF(x, y, x + w, y + h)

    private fun insideCanvas(r: RectF): Boolean =
        r.left >= 0f && r.top >= 0f && r.right <= canvasW && r.bottom <= canvasH

    private fun collides(r: RectF): Boolean {
        val (x0, y0, x1, y1) = bounds(r)
        for (by in y0..y1) {
            for (bx in x0..x1) {
                val bucket = placed[by * binsX + bx]
                for (existing in bucket) {
                    if (RectF.intersects(existing, r)) return true
                }
            }
        }
        return false
    }

    private fun insert(r: RectF) {
        val (x0, y0, x1, y1) = bounds(r)
        for (by in y0..y1) {
            for (bx in x0..x1) {
                placed[by * binsX + bx].add(r)
            }
        }
    }

    private data class Bounds(val x0: Int, val y0: Int, val x1: Int, val y1: Int)

    private fun bounds(r: RectF): Bounds {
        val x0 = ((r.left / binW).toInt()).coerceIn(0, binsX - 1)
        val y0 = ((r.top / binH).toInt()).coerceIn(0, binsY - 1)
        val x1 = ((r.right / binW).toInt()).coerceIn(0, binsX - 1)
        val y1 = ((r.bottom / binH).toInt()).coerceIn(0, binsY - 1)
        return Bounds(x0, y0, x1, y1)
    }
}
