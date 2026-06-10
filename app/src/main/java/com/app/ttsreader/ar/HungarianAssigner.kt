package com.app.ttsreader.ar

import kotlin.math.min

/**
 * Bipartite minimum-cost assignment via the Jonker-Volgenant algorithm.
 *
 * Used by the AR Lens tracker to globally pair this frame's text detections with
 * the previous frame's tracked blocks. A global assignment removes the identity
 * swaps that a greedy nearest-neighbor matcher produces when two blocks cross.
 *
 * ## API
 * Pass a row-major cost matrix as a `FloatArray` of size `rows * cols`. Forbidden
 * pairs use [INF]. The result writes assignments into [assignment] (`assignment[r] = c`
 * or `-1` if no allowed match was found).
 *
 * Square matrices are solved directly. Rectangular inputs are padded virtually
 * with [INF]-cost rows/columns; the result keeps the rectangular shape.
 *
 * ## Threading / allocation
 * The solver allocates working arrays internally. A `Scratch` holder is exposed
 * for callers who solve many small matrices per second; pass the same scratch
 * to avoid GC churn.
 */
object HungarianAssigner {

    const val INF: Float = 1e9f

    /**
     * Reusable scratch buffers — owned by the caller so the per-frame solve
     * allocates nothing.
     */
    class Scratch {
        var u: FloatArray = FloatArray(0)
        var v: FloatArray = FloatArray(0)
        var p: IntArray = IntArray(0)
        var way: IntArray = IntArray(0)
        var minv: FloatArray = FloatArray(0)
        var used: BooleanArray = BooleanArray(0)
        var padded: FloatArray = FloatArray(0)

        fun ensure(n: Int) {
            val plus1 = n + 1
            if (u.size < plus1) u = FloatArray(plus1)
            if (v.size < plus1) v = FloatArray(plus1)
            if (p.size < plus1) p = IntArray(plus1)
            if (way.size < plus1) way = IntArray(plus1)
            if (minv.size < plus1) minv = FloatArray(plus1)
            if (used.size < plus1) used = BooleanArray(plus1)
            if (padded.size < n * n) padded = FloatArray(n * n)
        }
    }

    /**
     * Solve assignment for a [rows] × [cols] cost matrix stored row-major in [cost].
     *
     * @param assignment Output buffer of length ≥ [rows]. Each entry is the column
     *                   index assigned to that row, or `-1` if the chosen cost
     *                   was [INF] (forbidden).
     * @return Total cost of the assignment (excluding any -1 rows).
     */
    fun solve(
        cost: FloatArray,
        rows: Int,
        cols: Int,
        assignment: IntArray,
        scratch: Scratch = Scratch(),
    ): Float {
        require(cost.size >= rows * cols) { "cost matrix too small" }
        require(assignment.size >= rows) { "assignment buffer too small" }

        if (rows == 0 || cols == 0) {
            for (i in 0 until rows) assignment[i] = -1
            return 0f
        }

        val n = maxOf(rows, cols)
        scratch.ensure(n)

        // Pad to n × n with INF for the virtual cells.
        val padded = scratch.padded
        for (i in 0 until n) {
            val rowBase = i * n
            for (j in 0 until n) {
                padded[rowBase + j] = if (i < rows && j < cols) cost[i * cols + j] else INF
            }
        }

        // Jonker-Volgenant (1-indexed in the canonical formulation).
        val u = scratch.u
        val v = scratch.v
        val p = scratch.p
        val way = scratch.way
        val minv = scratch.minv
        val used = scratch.used

        for (i in 0..n) { u[i] = 0f; v[i] = 0f; p[i] = 0; way[i] = 0 }

        for (i in 1..n) {
            p[0] = i
            var j0 = 0
            for (k in 0..n) { minv[k] = INF; used[k] = false }
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = INF
                var j1 = -1
                for (j in 1..n) {
                    if (!used[j]) {
                        val cur = padded[(i0 - 1) * n + (j - 1)] - u[i0] - v[j]
                        if (cur < minv[j]) {
                            minv[j] = cur
                            way[j] = j0
                        }
                        if (minv[j] < delta) {
                            delta = minv[j]
                            j1 = j
                        }
                    }
                }
                if (j1 == -1) break   // degenerate; shouldn't happen with INF padding
                for (j in 0..n) {
                    if (used[j]) {
                        u[p[j]] += delta
                        v[j] -= delta
                    } else {
                        minv[j] -= delta
                    }
                }
                j0 = j1
            } while (p[j0] != 0)

            // Reconstruct.
            do {
                val j1 = way[j0]
                p[j0] = p[j1]
                j0 = j1
            } while (j0 != 0)
        }

        // Build inverse: ans[row] = col (1-indexed → convert to 0-indexed).
        // p[col_1based] = row_1based.
        for (i in 0 until rows) assignment[i] = -1
        var total = 0f
        for (j in 1..n) {
            val i = p[j] - 1
            val jj = j - 1
            if (i in 0 until rows && jj < cols) {
                val c = cost[i * cols + jj]
                if (c >= INF) {
                    assignment[i] = -1
                } else {
                    assignment[i] = jj
                    total += c
                }
            }
        }
        return total
    }
}
