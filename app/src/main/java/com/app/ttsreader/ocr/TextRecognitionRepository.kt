package com.app.ttsreader.ocr

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the latest OCR result and exposes it as a [StateFlow].
 *
 * Producer: [com.app.ttsreader.camera.TextAnalyzer] (native OpenCV/CRNN engine).
 * Consumer: [com.app.ttsreader.viewmodel.MainViewModel].
 *
 * StateFlow is conflated — rapid frame results collapse to the current value only.
 * [processResult] and [reportError] are thread-safe (StateFlow.value assignment).
 */
class TextRecognitionRepository {

    private val _result = MutableStateFlow<OcrResult>(OcrResult.Empty)
    val result: StateFlow<OcrResult> = _result.asStateFlow()

    /** Called by [com.app.ttsreader.camera.TextAnalyzer] on every successful native pass. */
    fun processResult(words: List<SpatialWord>) {
        // Emit Success whenever the engine detects ANY regions, even if CRNN hasn't
        // assigned text yet (model missing or still loading). Bounding boxes are
        // still valid and useful for the smoother and overlay pipelines.
        // The text display pipeline (MainViewModel.observeOcrResults) independently
        // ignores Success with blank fullText — no double-gating needed here.
        _result.value = if (words.isEmpty()) {
            OcrResult.Empty
        } else {
            OcrResult.Success(
                fullText = words.filter { it.text.isNotBlank() }.joinToString(" ") { it.text },
                words    = words
            )
        }
    }

    /** Called by [com.app.ttsreader.camera.TextAnalyzer] when the engine throws. */
    fun reportError(exception: Exception) {
        _result.value = OcrResult.Error(exception)
    }

    /** Resets to [OcrResult.Empty] — call when the camera is paused or stopped. */
    fun clear() {
        _result.value = OcrResult.Empty
    }
}
