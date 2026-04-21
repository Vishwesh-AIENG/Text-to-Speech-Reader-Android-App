package com.app.ttsreader.domain.model

/**
 * One exchange in a Babel conversation session.
 *
 * @param id             Monotonically increasing index within the current session.
 * @param spokenText     Text recognized from the speaker, in their own language.
 * @param translatedText Translation played to the other speaker, in the other language.
 * @param isTopSpeaker   true  → top-panel speaker uttered this turn.
 *                       false → bottom-panel speaker uttered this turn.
 */
data class BabelTurn(
    val id: Int,
    val spokenText: String,
    val translatedText: String,
    val isTopSpeaker: Boolean
)
