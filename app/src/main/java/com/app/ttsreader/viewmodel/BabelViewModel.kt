package com.app.ttsreader.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.ttsreader.domain.model.AppLanguage
import com.app.ttsreader.domain.model.BabelTurn
import com.app.ttsreader.translate.TranslationRepository
import com.app.ttsreader.tts.SpeechController
import com.app.ttsreader.tts.TtsState
import com.app.ttsreader.utils.LanguageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Phase enum ─────────────────────────────────────────────────────────────────
enum class BabelPhase { IDLE, LISTENING, TRANSLATING, SPEAKING }

// ── Language picker target ─────────────────────────────────────────────────────
enum class PickerTarget { TOP, BOTTOM }

// ── UI state ───────────────────────────────────────────────────────────────────
// @Stable (not @Immutable) because List<BabelTurn> is not Compose-verifiable
// as structurally immutable, but the whole object is replaced atomically via
// StateFlow, so Compose can rely on equality comparisons being correct.
@Stable
data class BabelUiState(
    val sessionActive: Boolean = false,
    val phase: BabelPhase = BabelPhase.IDLE,
    val isTopActive: Boolean = true,
    val topLanguage: AppLanguage = LanguageUtils.DEFAULT_SOURCE,
    val bottomLanguage: AppLanguage = LanguageUtils.DEFAULT_TARGET,
    val turns: List<BabelTurn> = emptyList(),
    val rmsLevel: Float = 0f,
    val errorMessage: String? = null,
    val languagePickerTarget: PickerTarget? = null  // null = picker closed
)

/**
 * ViewModel for the Babel Conversation Engine.
 *
 * ## State machine
 * ```
 * IDLE ──startSession()──► LISTENING (top speaker first)
 * LISTENING ──speech recognized──► TRANSLATING
 * TRANSLATING ──translation done──► SPEAKING (TTS plays to other side)
 * SPEAKING ──TTS done──► LISTENING (flipped to other speaker, auto-alternating)
 * any state ──stopSession()──► IDLE
 * ```
 *
 * ## Threading
 * [SpeechRecognizer] must be created and used on the Main thread. Since
 * [AndroidViewModel.init] is always called on Main, the recognizer is safe to
 * create here. Recognition callbacks also arrive on Main.
 *
 * [TranslationRepository.translate] is a suspend function and runs inside
 * [viewModelScope] on the default coroutine dispatcher.
 */
class BabelViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BabelUiState())
    val uiState: StateFlow<BabelUiState> = _uiState.asStateFlow()

    private val speechController = SpeechController(application)
    private val translationRepo   = TranslationRepository()

    private var recognizer: SpeechRecognizer? = null
    private var translateJob: Job? = null

    init {
        // Must be created on Main thread — ViewModel.init satisfies this requirement
        if (SpeechRecognizer.isRecognitionAvailable(application)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(application)
            recognizer?.setRecognitionListener(RecognitionHandler())
        }

        // ── Auto-flip after TTS finishes ──────────────────────────────────────
        // Detect the Speaking → Ready transition in the TTS state flow.
        // When TTS finishes and the session is still active in SPEAKING phase,
        // flip to the other speaker and start listening again.
        viewModelScope.launch {
            var wasSpeaking = false
            speechController.state.collect { ttsState ->
                val isSpeaking = ttsState is TtsState.Speaking
                val justFinished = wasSpeaking && !isSpeaking
                wasSpeaking = isSpeaking

                val state = _uiState.value
                if (justFinished && state.sessionActive && state.phase == BabelPhase.SPEAKING) {
                    startListening(isTop = !state.isTopActive)
                }
            }
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    fun startSession() {
        if (recognizer == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Speech recognition is not available on this device."
            )
            return
        }
        _uiState.value = _uiState.value.copy(
            sessionActive = true,
            turns         = emptyList(),
            errorMessage  = null,
            phase         = BabelPhase.IDLE
        )
        startListening(isTop = true)
    }

    fun stopSession() {
        translateJob?.cancel()
        translateJob = null
        recognizer?.stopListening()
        speechController.stop()
        _uiState.value = _uiState.value.copy(
            sessionActive = false,
            phase         = BabelPhase.IDLE,
            rmsLevel      = 0f
        )
    }

    /** Manually triggers recognition for [isTop] speaker, interrupting current activity. */
    fun manualTrigger(isTop: Boolean) {
        if (!_uiState.value.sessionActive) return
        translateJob?.cancel()
        translateJob = null
        recognizer?.stopListening()
        speechController.stop()
        startListening(isTop = isTop)
    }

    fun openLanguagePicker(forTop: Boolean) {
        if (_uiState.value.sessionActive) return   // locked during active session
        _uiState.value = _uiState.value.copy(
            languagePickerTarget = if (forTop) PickerTarget.TOP else PickerTarget.BOTTOM
        )
    }

    fun closeLanguagePicker() {
        _uiState.value = _uiState.value.copy(languagePickerTarget = null)
    }

    fun selectLanguage(lang: AppLanguage) {
        val target = _uiState.value.languagePickerTarget ?: return
        _uiState.value = _uiState.value.copy(
            topLanguage          = if (target == PickerTarget.TOP)    lang else _uiState.value.topLanguage,
            bottomLanguage       = if (target == PickerTarget.BOTTOM) lang else _uiState.value.bottomLanguage,
            languagePickerTarget = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private fun startListening(isTop: Boolean) {
        val lang = if (isTop) _uiState.value.topLanguage else _uiState.value.bottomLanguage
        _uiState.value = _uiState.value.copy(
            phase      = BabelPhase.LISTENING,
            isTopActive = isTop,
            rmsLevel   = 0f
        )
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    private fun processRecognizedText(text: String, isTop: Boolean) {
        _uiState.value = _uiState.value.copy(phase = BabelPhase.TRANSLATING, rmsLevel = 0f)

        val topLang    = _uiState.value.topLanguage
        val bottomLang = _uiState.value.bottomLanguage
        val sourceLang = if (isTop) topLang    else bottomLang
        val targetLang = if (isTop) bottomLang else topLang

        translateJob = viewModelScope.launch {
            try {
                val translated = translationRepo.translate(
                    text       = text,
                    sourceLang = sourceLang.mlKitCode,
                    targetLang = targetLang.mlKitCode
                )

                val newTurn = BabelTurn(
                    id             = _uiState.value.turns.size,
                    spokenText     = text,
                    translatedText = translated,
                    isTopSpeaker   = isTop
                )

                _uiState.value = _uiState.value.copy(
                    phase = BabelPhase.SPEAKING,
                    turns = _uiState.value.turns + newTurn
                )

                // Speak the translation in the TARGET speaker's language
                speechController.setLanguage(targetLang.locale)
                speechController.speak(translated)
                // Auto-flip is handled by the TTS state collector in init { }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    phase        = BabelPhase.IDLE,
                    errorMessage = "Translation failed. Tap a mic button to retry."
                )
            }
        }
    }

    // ── SpeechRecognizer callbacks ─────────────────────────────────────────────

    private inner class RecognitionHandler : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize typical [-2, 10] dB range to [0, 1]
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _uiState.value = _uiState.value.copy(rmsLevel = normalized)
        }

        override fun onEndOfSpeech() {
            _uiState.value = _uiState.value.copy(rmsLevel = 0f)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()

            if (text.isNullOrEmpty()) {
                // Nothing heard — silently retry same speaker
                if (_uiState.value.sessionActive) startListening(_uiState.value.isTopActive)
                return
            }
            processRecognizedText(text, _uiState.value.isTopActive)
        }

        override fun onError(error: Int) {
            when (error) {
                // Silent retry — user just didn't speak or timed out
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (_uiState.value.sessionActive) startListening(_uiState.value.isTopActive)
                }

                // Permission missing — surface error and stop session
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    _uiState.value = _uiState.value.copy(
                        sessionActive = false,
                        phase         = BabelPhase.IDLE,
                        errorMessage  = "Microphone permission required"
                    )
                }

                // Recognizer busy — wait briefly then retry
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    viewModelScope.launch {
                        delay(600L)
                        if (_uiState.value.sessionActive) {
                            recognizer?.stopListening()
                            startListening(_uiState.value.isTopActive)
                        }
                    }
                }

                // All other errors — silent retry
                else -> {
                    if (_uiState.value.sessionActive) startListening(_uiState.value.isTopActive)
                }
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Called when BabelScreen leaves composition.
     * Stops any active recognition/translation/TTS so the microphone and
     * speakers are released while another mode is in the foreground.
     */
    fun pause() {
        if (_uiState.value.sessionActive) stopSession()
    }

    override fun onCleared() {
        translateJob?.cancel()
        recognizer?.destroy()
        recognizer = null
        speechController.shutdown()
        super.onCleared()
    }
}
