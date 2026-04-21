package com.app.ttsreader.error

/**
 * Typed error hierarchy for the entire app.
 *
 * Each subtype carries:
 * - [isRetryable] — whether the user can meaningfully retry the operation
 * - Enough context to produce a user-facing message via [ErrorMapper.toUserMessage]
 *
 * The ViewModel owns the mapping from raw [Throwable] → [AppError] via [ErrorMapper].
 * UI only sees the [AppError] and calls [ErrorMapper.toUserMessage] to get display text.
 */
sealed class AppError(val isRetryable: Boolean) {

    /** No internet while a model download was needed. */
    data object NetworkUnavailable : AppError(isRetryable = true)

    /** ML Kit model download finished with a non-network error (e.g. server error, disk full). */
    data class ModelDownloadFailed(val cause: Throwable) : AppError(isRetryable = true)

    /** Translation call failed after the model was already present. */
    data class TranslationFailed(val cause: Throwable) : AppError(isRetryable = true)

    /** CameraX failed to start or bind. Not recoverable without app restart. */
    data class CameraError(val cause: Throwable) : AppError(isRetryable = false)

    /** Android TTS engine returned an error code during init or playback. */
    data class TtsInitFailed(val cause: Throwable) : AppError(isRetryable = false)

    /** The selected language locale is not supported by the device's TTS engine. */
    data class TtsUnsupportedLanguage(val languageName: String) : AppError(isRetryable = false)

    /** ML Kit text recognizer threw during frame analysis. */
    data class OcrFailed(val cause: Throwable) : AppError(isRetryable = false)
}
