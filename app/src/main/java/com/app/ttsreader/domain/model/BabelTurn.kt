package com.app.ttsreader.domain.model

import androidx.compose.runtime.Immutable

/**
 * One exchange in a Babel conversation session.
 *
 * Annotated [@Immutable] so Compose can skip recomposition for list items
 * whose fields haven't changed.
 *
 * @param id             Monotonically increasing index within the current session.
 * @param spokenText     Text recognized from the speaker, in their own language.
 * @param translatedText Translation played to the other speaker, in the other language.
 * @param isTopSpeaker   true  → top-panel speaker uttered this turn.
 *                       false → bottom-panel speaker uttered this turn.
 */
@Immutable
data class BabelTurn(
    val id: Int,
    val spokenText: String,
    val translatedText: String,
    val isTopSpeaker: Boolean
)
