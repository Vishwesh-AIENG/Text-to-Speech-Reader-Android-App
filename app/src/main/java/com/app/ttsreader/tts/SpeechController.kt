package com.app.ttsreader.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Thin wrapper around Android [TextToSpeech].
 *
 * ## Lifecycle
 * Create once per ViewModel lifetime. Call [shutdown] from [onCleared] to release
 * the TTS service connection. After [shutdown] the instance must not be used again.
 *
 * ## Threading
 * [TextToSpeech] callbacks arrive on an internal TTS thread. All state writes go
 * through [MutableStateFlow] which is thread-safe.
 *
 * ## Language support
 * [setLanguage] checks [TextToSpeech.isLanguageAvailable] before applying. If the
 * locale is missing, [TtsState.Error] is emitted and callers can decide whether to
 * disable the speak button or show a toast.
 */
class SpeechController(context: Context) {

    private val _state = MutableStateFlow<TtsState>(TtsState.Initializing)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _currentSentenceIndex = MutableStateFlow(-1)
    val currentSentenceIndex: StateFlow<Int> = _currentSentenceIndex.asStateFlow()

    /**
     * Current word range within the active sentence (start inclusive, end exclusive).
     * Pair(-1, -1) means no word is highlighted.
     * Only emitted on API 26+ via [UtteranceProgressListener.onRangeStart].
     */
    private val _currentWordRange = MutableStateFlow(Pair(-1, -1))
    val currentWordRange: StateFlow<Pair<Int, Int>> = _currentWordRange.asStateFlow()

    private var tts: TextToSpeech? = null
    private var _speechRate = 1.0f
    private var _pitch = 1.0f
    private var sentencesList: List<String> = emptyList()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                _state.value = TtsState.Ready
            } else {
                _state.value = TtsState.Error("TTS engine failed to initialise (status=$status)")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _state.value = TtsState.Speaking
                _currentWordRange.value = Pair(-1, -1) // reset for new utterance
                // Track sentence index from utterance ID
                utteranceId?.let { id ->
                    if (id.startsWith(SENTENCE_PREFIX)) {
                        val index = id.removePrefix(SENTENCE_PREFIX).toIntOrNull()
                        if (index != null) _currentSentenceIndex.value = index
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                // Auto-advance to next sentence if sentence-aware playback
                utteranceId?.let { id ->
                    if (id.startsWith(SENTENCE_PREFIX)) {
                        val index = id.removePrefix(SENTENCE_PREFIX).toIntOrNull() ?: -1
                        val nextIndex = index + 1
                        if (nextIndex < sentencesList.size) {
                            tts?.speak(
                                sentencesList[nextIndex],
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "$SENTENCE_PREFIX$nextIndex"
                            )
                            return
                        }
                    }
                }
                // No more sentences or single utterance — done
                _currentSentenceIndex.value = -1
                _currentWordRange.value = Pair(-1, -1)
                _state.value = TtsState.Ready
            }

            @Deprecated("Required override on older API levels")
            override fun onError(utteranceId: String?) {
                _currentSentenceIndex.value = -1
                _currentWordRange.value = Pair(-1, -1)
                _state.value = TtsState.Ready
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _currentSentenceIndex.value = -1
                _currentWordRange.value = Pair(-1, -1)
                _state.value = TtsState.Error("TTS playback error (code=$errorCode)")
            }

            // API 26+: Called when the TTS engine starts speaking a range of characters.
            // This gives us word-level highlighting.
            override fun onRangeStart(
                utteranceId: String?,
                start: Int,
                end: Int,
                frame: Int
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    _currentWordRange.value = Pair(start, end)
                }
            }
        })
    }

    /**
     * Speaks [text] using the current TTS locale.
     *
     * Does nothing if the engine isn't [TtsState.Ready] or [TtsState.Speaking]
     * (i.e., still initialising or shut down).
     */
    fun speak(text: String) {
        val engine = tts ?: return
        val currentState = _state.value
        if (currentState !is TtsState.Ready && currentState !is TtsState.Speaking) return

        sentencesList = emptyList()
        _currentSentenceIndex.value = -1
        engine.setSpeechRate(_speechRate)
        engine.setPitch(_pitch)
        val uid = "utt_${System.nanoTime()}"
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, uid)
    }

    /**
     * Speaks a list of [sentences] with sentence-level tracking. The
     * [currentSentenceIndex] StateFlow emits the active sentence index so
     * the UI can highlight it.
     */
    fun speakSentences(sentences: List<String>, startIndex: Int = 0) {
        val engine = tts ?: return
        val currentState = _state.value
        if (currentState !is TtsState.Ready && currentState !is TtsState.Speaking) return
        if (sentences.isEmpty() || startIndex !in sentences.indices) return

        sentencesList = sentences
        _currentSentenceIndex.value = startIndex
        engine.setSpeechRate(_speechRate)
        engine.setPitch(_pitch)
        engine.speak(sentences[startIndex], TextToSpeech.QUEUE_FLUSH, null, "$SENTENCE_PREFIX$startIndex")
    }

    /**
     * Interrupts the current utterance. State transitions to [TtsState.Ready]
     * immediately rather than waiting for the [UtteranceProgressListener] callback,
     * so the UI responds without delay.
     */
    fun stop() {
        tts?.stop()
        _currentSentenceIndex.value = -1
        _currentWordRange.value = Pair(-1, -1)
        if (_state.value !is TtsState.Shutdown) {
            _state.value = TtsState.Ready
        }
    }

    /** Sets the playback speed. 0.5f = half speed, 1.0f = normal, 2.0f = double. */
    fun setSpeechRate(rate: Float) {
        _speechRate = rate
        tts?.setSpeechRate(rate)
    }

    /** Sets the voice pitch. 0.5f = lower, 1.0f = normal, 2.0f = higher. */
    fun setPitch(pitch: Float) {
        _pitch = pitch
        tts?.setPitch(pitch)
    }

    /**
     * Sets the TTS output locale.
     *
     * @return `true` if the locale was applied, `false` if unsupported.
     */
    fun setLanguage(locale: Locale): Boolean {
        val engine = tts ?: return false
        val result = engine.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /**
     * Releases the TTS service connection.
     * Must be called from [com.app.ttsreader.viewmodel.MainViewModel.onCleared].
     */
    fun shutdown() {
        _state.value = TtsState.Shutdown
        _currentSentenceIndex.value = -1
        _currentWordRange.value = Pair(-1, -1)
        tts?.stop()
        // Detach our UtteranceProgressListener before shutdown to prevent the
        // TTS engine's internal thread from invoking listener callbacks that
        // reference this (about-to-be-GC'd) controller.
        tts?.setOnUtteranceProgressListener(null)
        tts?.shutdown()
        tts = null
    }

    companion object {
        private const val UTTERANCE_ID = "tts_reader_utterance"
        private const val SENTENCE_PREFIX = "sentence_"
    }
}
