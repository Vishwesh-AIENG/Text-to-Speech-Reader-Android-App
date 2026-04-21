package com.app.ttsreader.error

import android.content.Context
import com.app.ttsreader.R

/**
 * Maps raw [Throwable] instances to typed [AppError] values, and maps
 * [AppError] values to localised user-facing strings.
 *
 * ## Classification heuristics
 * ML Kit does not expose a stable public exception hierarchy for translation
 * errors. We inspect the message string for well-known patterns:
 *
 * - "network" / "unable to resolve" / "timeout" / "connection" → [AppError.NetworkUnavailable]
 * - "download" / "model" (but not purely network) → [AppError.ModelDownloadFailed]
 * - anything else during translation → [AppError.TranslationFailed]
 */
object ErrorMapper {

    /**
     * Classifies a translation-pipeline [Throwable] into a typed [AppError].
     *
     * @param isDownloadPhase `true` if the error occurred during model download,
     *                        `false` if during the translate() call itself.
     */
    fun fromTranslationException(e: Throwable, isDownloadPhase: Boolean = false): AppError {
        val msg = e.message?.lowercase() ?: ""
        val isNetworkError = msg.containsAny(
            "network", "unable to resolve", "timeout", "connection refused",
            "no address", "failed to connect", "socket", "unreachable"
        )
        return when {
            isNetworkError                           -> AppError.NetworkUnavailable
            isDownloadPhase                          -> AppError.ModelDownloadFailed(e)
            msg.containsAny("download", "model")    -> AppError.ModelDownloadFailed(e)
            else                                     -> AppError.TranslationFailed(e)
        }
    }

    /** Maps an [AppError] to a localised string for display in the Snackbar. */
    fun toUserMessage(error: AppError, context: Context): String = when (error) {
        is AppError.NetworkUnavailable       -> context.getString(R.string.error_network)
        is AppError.ModelDownloadFailed      -> context.getString(R.string.error_model_download)
        is AppError.TranslationFailed        -> context.getString(R.string.error_translation)
        is AppError.CameraError              -> context.getString(R.string.error_camera)
        is AppError.TtsInitFailed            -> context.getString(R.string.error_tts_init)
        is AppError.TtsUnsupportedLanguage   -> context.getString(R.string.error_tts_language)
        is AppError.OcrFailed                -> context.getString(R.string.error_ocr)
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { this.contains(it) }
}
