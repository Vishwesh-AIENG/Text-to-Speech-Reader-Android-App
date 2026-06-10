package com.app.ttsreader.utils

/**
 * Offline fuzzy string matcher for the Instant Indexing mode AND the AR Lens
 * tracker. Returns a similarity score in [0, 1].
 *
 * ## Scoring (returns [0, 1])
 * Checks are applied in priority order; the first hit that scores above
 * [MATCH_THRESHOLD] is returned:
 *
 * 1. **Exact** (1.0) — identical strings after lowercase/trim.
 * 2. **Prefix** (0.93) — any word in [target] starts with [query], gated by
 *    `min(|q|,|w|) >= 3 && ||q|-|w|| <= 2` so a 1- or 2-char query cannot
 *    match every long word.
 * 3. **Suffix** (0.72) — word ends with query (kept below threshold so
 *    "search" does NOT spuriously match "research").
 * 4. **Contains** (0.78) — query found anywhere in the full target string.
 * 5. **Levenshtein similarity** — handles OCR typos like "Ntrition" → "Nutrition".
 *
 * ## Symmetry
 * [score] is symmetric: `score(a,b) == score(b,a)`. Internally it computes
 * `max(scoreOneWay(q,t), scoreOneWay(t,q))`. Symmetry matters because the AR
 * Lens tracker compares (new detection, previous tracker) and (previous tracker,
 * new detection) interchangeably.
 *
 * ## Threshold
 * [MATCH_THRESHOLD] = 0.76.
 */
object FuzzyMatcher {

    const val MATCH_THRESHOLD = 0.76f

    /** Precompiled — token-splitter is hit in every score() call. */
    private val WS = Regex("\\s+")

    /**
     * Returns a symmetric similarity score in [0, 1].
     * Both inputs are normalised to lowercase + trim before comparison.
     */
    fun score(a: String, b: String): Float {
        val x = a.lowercase().trim()
        val y = b.lowercase().trim()
        if (x.isEmpty() || y.isEmpty()) return 0f
        if (x == y) return 1.0f
        // Symmetric: try both directions and keep the higher score.
        val ab = scoreOneWay(x, y)
        val ba = scoreOneWay(y, x)
        return if (ab >= ba) ab else ba
    }

    private fun scoreOneWay(query: String, target: String): Float {
        val targetWords = target.split(WS)
        var best = 0f

        for (word in targetWords) {
            when {
                query == word -> return 1.0f
                // Length-aware prefix gate — require ≥3 chars on the shorter side
                // and at most 2 chars of length difference. Prevents "a" matching "apple".
                word.startsWith(query)
                    && min3(query.length, word.length)
                    && diffLeq2(query.length, word.length) -> best = maxOf(best, 0.93f)
                word.endsWith(query) && query.length > 3 -> best = maxOf(best, 0.72f)
            }
            best = maxOf(best, levenshteinSimilarity(query, word))
        }
        if (target.contains(query)) best = maxOf(best, 0.88f)
        return best
    }

    private fun min3(a: Int, b: Int): Boolean = a >= 3 && b >= 3
    private fun diffLeq2(a: Int, b: Int): Boolean = (if (a > b) a - b else b - a) <= 2

    // ── Levenshtein — two-row rolling DP, references swapped (no copyOf) ────────

    private fun levenshteinSimilarity(a: String, b: String): Float {
        val dist = levenshteinDistance(a, b)
        val maxLen = maxOf(a.length, b.length)
        return if (maxLen == 0) 1f else 1f - dist.toFloat() / maxLen
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val shorter: String
        val longer: String
        if (m <= n) { shorter = a; longer = b } else { shorter = b; longer = a }
        val s = shorter.length
        val l = longer.length

        var prev = IntArray(s + 1) { it }
        var curr = IntArray(s + 1)

        for (j in 1..l) {
            curr[0] = j
            for (i in 1..s) {
                curr[i] = if (shorter[i - 1] == longer[j - 1]) {
                    prev[i - 1]
                } else {
                    1 + minOf(prev[i - 1], prev[i], curr[i - 1])
                }
            }
            // Swap references instead of copying — saves an alloc per row.
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[s]
    }
}
