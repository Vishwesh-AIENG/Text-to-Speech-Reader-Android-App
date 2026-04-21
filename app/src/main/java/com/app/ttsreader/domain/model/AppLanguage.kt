package com.app.ttsreader.domain.model

import java.util.Locale

/**
 * Domain model representing a language supported by both ML Kit Translation
 * and Android TextToSpeech.
 *
 * @param mlKitCode    The code used by ML Kit — matches [com.google.mlkit.nl.translate.TranslateLanguage].
 * @param displayName  Human-readable name shown in the language selector UI.
 * @param locale       Java [Locale] used by Android TTS to set the speech language (Step 6).
 */
data class AppLanguage(
    val mlKitCode: String,
    val displayName: String,
    val locale: Locale
)
