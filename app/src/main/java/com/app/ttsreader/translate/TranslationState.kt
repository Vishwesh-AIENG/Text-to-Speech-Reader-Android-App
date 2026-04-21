package com.app.ttsreader.translate

/**
 * Lifecycle states emitted by [TranslationRepository].
 *
 * [Idle]             — No active translation. Initial state and state after completion.
 * [DownloadingModel] — ML Kit is downloading the required translation model (~30 MB).
 *                      ML Kit's API does not expose incremental progress, so the UI
 *                      shows an indeterminate indicator for this state.
 * [Translating]      — Model is ready; an active translation Task is running.
 */
sealed class TranslationState {
    data object Idle : TranslationState()
    data object DownloadingModel : TranslationState()
    data object Translating : TranslationState()
}
