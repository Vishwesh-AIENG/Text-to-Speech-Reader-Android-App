package com.app.ttsreader.ocr

/**
 * Sealed class representing all possible outcomes of a single OCR analysis pass.
 *
 * [Empty]   — The engine ran successfully but found no text in the frame.
 * [Success] — Text was detected. [fullText] is the joined string; [words] lets
 *             callers access positional/bounding-box data for overlay work.
 * [Error]   — The engine threw an exception. The camera pipeline continues —
 *             the next frame will be analyzed normally.
 */
sealed class OcrResult {
    data object Empty : OcrResult()
    data class Success(
        val fullText: String,
        val words: List<SpatialWord>
    ) : OcrResult()
    data class Error(val exception: Exception) : OcrResult()
}
