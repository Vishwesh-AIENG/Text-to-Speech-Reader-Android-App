package com.app.ttsreader.domain.model

/**
 * The operating modes exposed by the Modal Hub.
 *
 * [isAvailable] = true  → routes to the mode's functional screen.
 * [isAvailable] = false → routes to [com.app.ttsreader.ui.screens.ComingSoonScreen].
 *
 * UI metadata (titles, subtitles, icons) lives in the composables so that
 * string resources and icon references stay in the UI layer, not the domain layer.
 */
enum class AppMode(val isAvailable: Boolean) {
    CLASSIC_TTS(isAvailable = true),
    BABEL_CONVERSATION(isAvailable = true),
    DYSLEXIA_FOCUS(isAvailable = true),
    AR_MAGIC_LENS(isAvailable = true),
    E_READER(isAvailable = true)
}
