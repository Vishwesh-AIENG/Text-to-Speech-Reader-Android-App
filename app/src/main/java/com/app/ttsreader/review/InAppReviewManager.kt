package com.app.ttsreader.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.ReviewInfo

/**
 * Manages the Google Play In-App Review flow.
 *
 * ## When the prompt triggers
 * The review dialog is surfaced only when ALL conditions are met:
 *   1. The user has triggered TTS playback at least [MIN_SPEAK_COUNT] times.
 *   2. At least [MIN_DAYS_BETWEEN_PROMPTS] days have elapsed since the last prompt.
 *
 * Google Play silently enforces its own per-user quota (typically once every
 * few months), so calling [maybeAskForReview] liberally is safe — Google will
 * suppress the dialog if it was already shown recently.
 *
 * ## Usage
 * ```kotlin
 * // In your ViewModel or Activity:
 * InAppReviewManager.incrementSpeakCount(context)
 * InAppReviewManager.maybeAskForReview(activity)
 * ```
 */
object InAppReviewManager {

    private const val TAG = "InAppReview"
    private const val PREFS_NAME           = "tts_review_prefs"
    private const val KEY_SPEAK_COUNT      = "speak_count"
    private const val KEY_LAST_PROMPT_MS   = "last_prompt_ms"
    private const val MIN_SPEAK_COUNT      = 5          // ask after 5 TTS plays
    private const val MIN_DAYS_BETWEEN     = 30L        // at most once per 30 days
    private const val MS_PER_DAY           = 86_400_000L

    // Cached ReviewInfo — pre-warm early so the dialog shows instantly when needed.
    @Volatile private var cachedReviewInfo: ReviewInfo? = null

    /**
     * Pre-warms the review flow in the background. Call this early (e.g.
     * from MainActivity.onCreate) so [ReviewInfo] is ready by the time the user
     * triggers enough interactions.
     */
    fun preWarm(context: Context) {
        val manager = ReviewManagerFactory.create(context.applicationContext)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cachedReviewInfo = task.result
                Log.d(TAG, "ReviewInfo pre-warmed successfully")
            } else {
                Log.w(TAG, "ReviewInfo pre-warm failed: ${task.exception?.message}")
            }
        }
    }

    /**
     * Increments the cumulative TTS-speak counter.
     * Call this every time the user triggers TTS playback.
     */
    fun incrementSpeakCount(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_SPEAK_COUNT, 0)
        prefs.edit().putInt(KEY_SPEAK_COUNT, current + 1).apply()
        Log.d(TAG, "speak_count → ${current + 1}")
    }

    /**
     * Shows the Play Store in-app review dialog if the usage threshold and
     * cooldown period are both satisfied.
     *
     * Must be called from the UI thread with a valid [Activity] reference
     * (not Application context).
     */
    fun maybeAskForReview(activity: Activity) {
        val prefs = activity.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val speakCount   = prefs.getInt(KEY_SPEAK_COUNT, 0)
        val lastPromptMs = prefs.getLong(KEY_LAST_PROMPT_MS, 0L)
        val daysSinceLast = (System.currentTimeMillis() - lastPromptMs) / MS_PER_DAY

        if (speakCount < MIN_SPEAK_COUNT) {
            Log.d(TAG, "Skipping review: speak_count=$speakCount < $MIN_SPEAK_COUNT")
            return
        }
        if (lastPromptMs > 0L && daysSinceLast < MIN_DAYS_BETWEEN) {
            Log.d(TAG, "Skipping review: only ${daysSinceLast}d since last prompt")
            return
        }

        val manager = ReviewManagerFactory.create(activity.applicationContext)

        val launch = { reviewInfo: ReviewInfo ->
            manager.launchReviewFlow(activity, reviewInfo)
                .addOnCompleteListener {
                    // Record prompt time regardless of whether user actually rated —
                    // Google does not tell us if they submitted a review.
                    prefs.edit().putLong(KEY_LAST_PROMPT_MS, System.currentTimeMillis()).apply()
                    Log.d(TAG, "Review flow completed")
                }
        }

        val cached = cachedReviewInfo
        if (cached != null) {
            launch(cached)
        } else {
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    launch(task.result)
                } else {
                    Log.w(TAG, "requestReviewFlow failed: ${task.exception?.message}")
                }
            }
        }
    }
}
