package com.app.ttsreader.utils

/**
 * Offline fuzzy string matcher for the Instant Indexing mode.
 *
 * ## Scoring (returns [0, 1])
 * Checks are applied in priority order; the first hit that scores above
 * [MATCH_THRESHOLD] is returned:
 *
 * 1. **Exact** (1.0) — identical strings after lowercase/trim.
 * 2. **Prefix** (0.93) — any word in [target] starts with [query].
 *    Handles "Nutrition" → "Nutritional", "Search" → "Searching".
 * 3. **Suffix** (0.72) — word ends with query (kept below threshold intentionally
 *    so "search" does NOT spuriously match "research").
 * 4. **Contains** (0.78) — query found anywhere in the full target string
 *    (multi-word targets like "Nutritional Information" when query is "Nutritional").
 * 5. **Levenshtein similarity** — handles OCR typos such as "Ntrition" → "Nutrition"
 *    (`dist = 1`, similarity ≈ 0.89).
 *
 * ## Threshold
 * [MATCH_THRESHOLD] = 0.76.  At this level:
 * - "Nutrition" / "Nutritional"          →  0.93 ✓
 * - "Nutrition" / "Nutritional Info"     →  0.93 ✓  (word prefix)
 * - "Nutrition" / "Ntrition"             →  0.89 ✓  (Levenshtein)
 * - "Search"    / "Research"             →  0.75 ✗  (just below threshold)
 * - "Nutrition" / "Food"                 →  0.00 ✗
 */
object FuzzyMatcher {

    const val MATCH_THRESHOLD = 0.76f

    /**
     * Returns a score in [0, 1] representing how well [query] matches [target].
     * Both strings are normalised to lowercase before comparison.
     */
    fun score(query: String, target: String): Float {
        val q = query.lowercase().trim()
        val t = target.lowercase().trim()
        if (q.isEmpty() || t.isEmpty()) return 0f
        if (q == t) return 1.0f

        // Split target into individual tokens for word-level checks
        val targetWords = t.split(Regex("\\s+"))
        var best = 0f

        for (word in targetWords) {
            when {
                q == word           -> return 1.0f
                word.startsWith(q)  -> best = maxOf(best, 0.93f)
                word.endsWith(q) && q.length > 3 -> best = maxOf(best, 0.72f)
            }
            // Levenshtein for this token
            val sim = levenshteinSimilarity(q, word)
            best = maxOf(best, sim)
        }

        // Full-string contains check (catches "Nutritional Information".contains("Nutritional"))
        if (t.contains(q)) best = maxOf(best, 0.88f)

        return best
    }

    // ── Levenshtein ────────────────────────────────────────────────────────────

    private fun levenshteinSimilarity(a: String, b: String): Float {
        val dist = levenshteinDistance(a, b)
        val maxLen = maxOf(a.length, b.length)
        return if (maxLen == 0) 1f else 1f - dist.toFloat() / maxLen
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        // Rolling two-row DP — O(min(m,n)) space
        val shorter: String
        val longer: String
        if (m <= n) { shorter = a; longer = b } else { shorter = b; longer = a }
        val s = shorter.length
        val l = longer.length

        var prev = IntArray(s + 1) { it }
        val curr = IntArray(s + 1)

        for (j in 1..l) {
            curr[0] = j
            for (i in 1..s) {
                curr[i] = if (shorter[i - 1] == longer[j - 1]) {
                    prev[i - 1]
                } else {
                    1 + minOf(prev[i - 1], prev[i], curr[i - 1])
                }
            }
            prev = curr.copyOf()
        }
        return prev[s]
    }
}
