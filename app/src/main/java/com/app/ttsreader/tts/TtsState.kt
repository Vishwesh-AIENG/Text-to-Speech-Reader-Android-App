package com.app.ttsreader.tts

/**
 * Lifecycle states of the Android [android.speech.tts.TextToSpeech] engine.
 *
 * Transitions:
 *   [Initializing] → [Ready] on successful init
 *   [Initializing] → [Error] on init failure
 *   [Ready] ⇄ [Speaking] on speak / completion
 *   any → [Shutdown] when ViewModel is cleared
 */
sealed class TtsState {
    /** Engine is being initialised — speak button should be disabled. */
    data object Initializing : TtsState()

    /** Engine is ready and idle — speak button enabled. */
    data object Ready : TtsState()

    /** Engine is currently speaking — show stop button. */
    data object Speaking : TtsState()

    /** Unrecoverable init failure. */
    data class Error(val message: String) : TtsState()

    /** [android.speech.tts.TextToSpeech.shutdown] has been called — do not use. */
    data object Shutdown : TtsState()
}
