package com.app.ttsreader.domain.model

import androidx.compose.runtime.Stable
import java.util.Locale

/**
 * Domain model representing a language supported by both ML Kit Translation
 * and Android TextToSpeech.
 *
 * Annotated [@Stable] instead of [@Immutable] because [Locale] is a Java class
 * Compose cannot introspect — but in practice instances are never mutated after
 * creation, so [Stable] is accurate and allows Compose to skip unnecessary
 * recompositions when the same language is re-observed.
 *
 * @param mlKitCode    The code used by ML Kit — matches [com.google.mlkit.nl.translate.TranslateLanguage].
 * @param displayName  Human-readable name shown in the language selector UI.
 * @param locale       Java [Locale] used by Android TTS to set the speech language (Step 6).
 */
@Stable
data class AppLanguage(
    val mlKitCode: String,
    val displayName: String,
    val locale: Locale
)
