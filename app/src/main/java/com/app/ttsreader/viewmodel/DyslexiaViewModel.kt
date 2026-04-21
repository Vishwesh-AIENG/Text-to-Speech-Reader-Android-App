package com.app.ttsreader.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.app.ttsreader.camera.CameraController
import com.app.ttsreader.camera.DyslexiaFocusAnalyzer
import com.app.ttsreader.ocr.SpatialWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

// ── Sub-mode ───────────────────────────────────────────────────────────────────
enum class DyslexiaSubMode { CAMERA, TEXT }

// ── Focus band (camera mode) ───────────────────────────────────────────────────
/**
 * Position of the line-focus window expressed as fractions of the screen height [0, 1].
 * [topFraction] and [bottomFraction] are EMA-smoothed so they update gradually on
 * every analyzed frame, preventing snapping or flickering.
 */
data class FocusBand(
    val topFraction: Float    = 0.40f,
    val bottomFraction: Float = 0.60f,
    val focusedText: String   = ""
)

// ── UI state ───────────────────────────────────────────────────────────────────
data class DyslexiaUiState(
    val subMode: DyslexiaSubMode = DyslexiaSubMode.TEXT,
    // Camera mode
    val focusBand: FocusBand    = FocusBand(),
    val hasDetectedText: Boolean = false,
    // Text mode
    val rawText: String              = "",
    val paragraphs: List<List<String>> = emptyList(),   // [paragraphs][words]
    val activeParagraphIdx: Int      = 0,
    val activeWordIdx: Int           = 0,
    val isEditingText: Boolean       = true,
    val isAutoReading: Boolean       = false,
    val autoSpeedMs: Long            = 400L,
    val errorMessage: String?        = null
)

/**
 * ViewModel for the Dyslexia Focus screen.
 *
 * ## Camera mode — line-focus tracking
 * Each analyzed frame from [DyslexiaFocusAnalyzer] calls [onFrameAnalyzed].
 * The method:
 * 1. Converts the image-space bounding box of the text block nearest the vertical
 *    centre into screen-height fractions.
 * 2. Applies EMA smoothing (α = 0.15) so the focus window drifts smoothly to
 *    the new position rather than jumping.
 * 3. Enforces a minimum window height (8 % of screen) to handle very thin lines.
 *
 * ## Text mode — word-by-word reading
 * [loadText] parses pasted text into paragraphs (split on blank lines) and words.
 * [nextWord] / [prevWord] advance / retreat one word. [toggleAuto] runs an
 * auto-advance coroutine that fires [nextWord] every [DyslexiaUiState.autoSpeedMs].
 */
class DyslexiaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DyslexiaUiState())
    val uiState: StateFlow<DyslexiaUiState> = _uiState.asStateFlow()

    private val cameraController = CameraController(application)
    private val analyzer         = DyslexiaFocusAnalyzer(
        context  = application,
        onResult = ::onFrameAnalyzed
    )

    // EMA state (camera mode)
    private var smoothedTop    = 0.40f
    private var smoothedBottom = 0.60f

    private var autoJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                ttsReady = true
            }
        }
    }

    // ── TTS helper ─────────────────────────────────────────────────────────────

    private fun speakWord(word: String) {
        if (word.isBlank() || !ttsReady) return
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    // ── Sub-mode control ───────────────────────────────────────────────────────

    fun switchMode(mode: DyslexiaSubMode) {
        autoJob?.cancel()
        tts?.stop()
        _uiState.value = _uiState.value.copy(subMode = mode, isAutoReading = false)
    }

    // ── Camera mode ────────────────────────────────────────────────────────────

    /**
     * Binds CameraX to [previewView] scoped to [lifecycleOwner].
     * Called from [DyslexiaScreen] each time the [CameraPreview] composable
     * is (re-)attached to the hierarchy.
     */
    fun startCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                cameraController.startCamera(lifecycleOwner, previewView, analyzer)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Camera failed to start. Please restart the app."
                )
            }
        }
    }

    /**
     * Receives OCR results from [DyslexiaFocusAnalyzer] on the analysis thread.
     * State writes go through [MutableStateFlow] which is thread-safe.
     */
    private fun onFrameAnalyzed(
        words: List<SpatialWord>,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ) {
        if (_uiState.value.subMode != DyslexiaSubMode.CAMERA) return

        // For portrait mode the sensor delivers a landscape frame (e.g. 1280×720).
        // ML Kit returns coordinates in the rotated image space, where height maps to
        // effectiveHeight (imageWidth when rotated 90/270).
        val effectiveHeight = if (rotationDegrees == 90 || rotationDegrees == 270) {
            imageWidth
        } else {
            imageHeight
        }

        if (effectiveHeight == 0) return

        // Restrict scanning to the visible focus band with only a tiny tolerance
        // (4 % of screen height) to account for pixel-level misalignment.
        // This ensures ONLY text within the green box is captured.
        val margin     = 0.04f
        val scanTop    = (smoothedTop    - margin).coerceAtLeast(0f)
        val scanBottom = (smoothedBottom + margin).coerceAtMost(1f)

        val wordsInBand = words.filter { word ->
            val box = word.toBoundingRect()
            val cy  = (box.top + box.bottom) / 2f / effectiveHeight
            cy in scanTop..scanBottom
        }

        if (wordsInBand.isEmpty()) {
            _uiState.value = _uiState.value.copy(hasDetectedText = words.isNotEmpty())
            return
        }

        // Collect ALL words in the band left-to-right as the focused text.
        // This shows the full line the green band is covering.
        val bandText = wordsInBand
            .sortedBy { word -> word.toBoundingRect().left }
            .filter   { it.text.isNotBlank() }
            .joinToString(" ") { it.text }
            .trim()

        // Update the EMA band position using the vertical extent of ALL in-band words,
        // not just the nearest one, so the band tracks the full line height.
        val rawTop    = wordsInBand.minOf { it.toBoundingRect().top  }.toFloat() / effectiveHeight
        val rawBottom = wordsInBand.maxOf { it.toBoundingRect().bottom }.toFloat() / effectiveHeight

        smoothedTop    = smoothedTop    + EMA_ALPHA * (rawTop    - smoothedTop)
        smoothedBottom = smoothedBottom + EMA_ALPHA * (rawBottom - smoothedBottom)

        val midY = (smoothedTop + smoothedBottom) / 2f
        if (smoothedBottom - smoothedTop < MIN_BAND_HEIGHT) {
            smoothedTop    = midY - MIN_BAND_HEIGHT / 2f
            smoothedBottom = midY + MIN_BAND_HEIGHT / 2f
        }

        _uiState.value = _uiState.value.copy(
            focusBand = FocusBand(
                topFraction    = smoothedTop.coerceIn(0.02f, 0.98f),
                bottomFraction = smoothedBottom.coerceIn(0.02f, 0.98f),
                focusedText    = bandText
            ),
            hasDetectedText = true
        )
    }

    // ── Text mode ──────────────────────────────────────────────────────────────

    fun setRawText(text: String) {
        _uiState.value = _uiState.value.copy(rawText = text)
    }

    /** Parses [rawText] into paragraphs and enters reading mode, speaking the first word. */
    fun loadText() {
        val text = _uiState.value.rawText.trim()
        if (text.isEmpty()) return
        val parsed = parseParagraphs(text)
        if (parsed.isEmpty()) return
        _uiState.value = _uiState.value.copy(
            paragraphs         = parsed,
            activeParagraphIdx = 0,
            activeWordIdx      = 0,
            isEditingText      = false,
            isAutoReading      = false
        )
        speakWord(parsed.firstOrNull()?.firstOrNull() ?: "")
    }

    /** Returns from reading mode back to the text editor. */
    fun backToEdit() {
        autoJob?.cancel()
        tts?.stop()
        _uiState.value = _uiState.value.copy(isEditingText = true, isAutoReading = false)
    }

    fun nextWord() {
        val s    = _uiState.value
        val para = s.paragraphs.getOrNull(s.activeParagraphIdx) ?: return
        when {
            s.activeWordIdx < para.size - 1 -> {
                val newIdx = s.activeWordIdx + 1
                _uiState.value = s.copy(activeWordIdx = newIdx)
                speakWord(para[newIdx])
            }
            s.activeParagraphIdx < s.paragraphs.size - 1 -> {
                val newParaIdx = s.activeParagraphIdx + 1
                _uiState.value = s.copy(activeParagraphIdx = newParaIdx, activeWordIdx = 0)
                speakWord(s.paragraphs[newParaIdx].firstOrNull() ?: "")
            }
        }
    }

    fun prevWord() {
        val s = _uiState.value
        when {
            s.activeWordIdx > 0 -> {
                val newIdx = s.activeWordIdx - 1
                _uiState.value = s.copy(activeWordIdx = newIdx)
                val para = s.paragraphs.getOrNull(s.activeParagraphIdx) ?: return
                speakWord(para[newIdx])
            }
            s.activeParagraphIdx > 0 -> {
                val newParaIdx = s.activeParagraphIdx - 1
                val prevPara   = s.paragraphs[newParaIdx]
                val newIdx     = prevPara.size - 1
                _uiState.value = s.copy(activeParagraphIdx = newParaIdx, activeWordIdx = newIdx)
                speakWord(prevPara.getOrElse(newIdx) { "" })
            }
        }
    }

    fun toggleAuto() {
        if (_uiState.value.isAutoReading) {
            tts?.stop()
            autoJob?.cancel()
            _uiState.value = _uiState.value.copy(isAutoReading = false)
        } else {
            _uiState.value = _uiState.value.copy(isAutoReading = true)
            autoJob = viewModelScope.launch {
                while (isActive && _uiState.value.isAutoReading) {
                    delay(_uiState.value.autoSpeedMs)
                    val before = _uiState.value
                    nextWord()
                    val after = _uiState.value
                    // Reached the end — stop auto
                    if (before.activeParagraphIdx == after.activeParagraphIdx &&
                        before.activeWordIdx      == after.activeWordIdx) {
                        _uiState.value = _uiState.value.copy(isAutoReading = false)
                        break
                    }
                }
            }
        }
    }

    /** Steps reading speed up or down through [SPEED_STEPS]. */
    fun adjustSpeed(faster: Boolean) {
        val current = _uiState.value.autoSpeedMs
        val idx = SPEED_STEPS.indices.minByOrNull { i ->
            val diff = SPEED_STEPS[i].first - current
            if (diff < 0L) -diff else diff
        } ?: (SPEED_STEPS.size / 2)
        // SPEED_STEPS[0] = slowest (800 ms), last = fastest (100 ms).
        // "faster" → higher index → lower ms.
        val newIdx = if (faster) (idx + 1).coerceAtMost(SPEED_STEPS.size - 1)
                     else         (idx - 1).coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(autoSpeedMs = SPEED_STEPS[newIdx].first)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Splits [text] on blank lines (≥2 consecutive newlines) to form paragraphs,
     * then splits each paragraph on whitespace to form word lists.
     */
    private fun parseParagraphs(text: String): List<List<String>> =
        text.split(Regex("""\n{2,}"""))
            .map { para ->
                para.trim()
                    .split(Regex("""\s+"""))
                    .filter { it.isNotEmpty() }
            }
            .filter { it.isNotEmpty() }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /**
     * Called when DyslexiaScreen leaves composition.
     * Unbinds camera and pauses auto-read so no background work continues
     * while another mode is active.
     */
    fun pause() {
        cameraController.pauseCamera()
        autoJob?.cancel()
        autoJob = null
        tts?.stop()
    }

    override fun onCleared() {
        autoJob?.cancel()
        tts?.stop()
        tts?.shutdown()
        analyzer.close()
        cameraController.stopCamera()
        super.onCleared()
    }

    // ── Constants ──────────────────────────────────────────────────────────────

    companion object {
        /**
         * Predefined reading speeds — sorted slowest (index 0) to fastest (last).
         * [autoSpeedMs] snaps to the nearest entry when [adjustSpeed] is called.
         */
        val SPEED_STEPS: List<Pair<Long, String>> = listOf(
            800L to "0.5×",
            533L to "0.75×",
            400L to "1×",
            320L to "1.25×",
            267L to "1.5×",
            200L to "2×",
            160L to "2.5×",
            133L to "3×",
            100L to "4×"
        )

        /** Returns the display label (e.g. "1.25×") closest to [ms]. */
        fun speedLabel(ms: Long): String =
            SPEED_STEPS.minByOrNull { (stepMs, _) ->
                val diff = stepMs - ms; if (diff < 0L) -diff else diff
            }?.second ?: "1×"

        private const val EMA_ALPHA       = 0.15f
        private const val MIN_BAND_HEIGHT = 0.08f
    }
}
