package com.app.ttsreader.camera

import android.content.Context
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.app.ttsreader.ocr.SpatialWord
import com.app.ttsreader.ocr.TextRecognitionRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX [ImageAnalysis.Analyzer] that feeds frames to ML Kit Text Recognition.
 *
 * ## Dual-gate throttle
 * Gate 1 — Timestamp ([throttleIntervalMs], default 500 ms): drops frames if less than
 *   [throttleIntervalMs] has elapsed since the last accepted frame.
 * Gate 2 — [AtomicBoolean] ([isProcessing]): skips the frame if ML Kit is still
 *   processing the previous one, preventing concurrent requests.
 *
 * ## ImageProxy lifecycle
 * [ImageProxy.close] is deferred to [addOnCompleteListener] so the underlying
 * [android.media.Image] stays valid for the entire duration of the ML Kit task.
 * Every code path that returns early closes the proxy before returning.
 */
class TextAnalyzer(
    context: Context,
    private val repository: TextRecognitionRepository,
    private val throttleIntervalMs: Long = 500L
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    // Use ContextCompat to support API 24–27 (Context.getMainExecutor requires API 28).
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)

    private var lastAnalyzedTimestamp = 0L
    private val isProcessing = AtomicBoolean(false)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()

        if (now - lastAnalyzedTimestamp < throttleIntervalMs) {
            imageProxy.close()
            return
        }
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastAnalyzedTimestamp = now

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        try {
            recognizer.process(inputImage)
                .addOnSuccessListener(mainExecutor) { mlKitText ->
                    repository.processResult(extractWords(mlKitText))
                }
                .addOnFailureListener(mainExecutor) { e ->
                    repository.reportError(e)
                }
                .addOnCompleteListener(mainExecutor) {
                    imageProxy.close()
                    isProcessing.set(false)
                }
        } catch (t: Throwable) {
            // If process() throws synchronously (e.g., recognizer already closed),
            // the completion listener never runs — close the proxy here so the
            // camera pipeline isn't starved.
            imageProxy.close()
            isProcessing.set(false)
            throw t
        }
    }

    fun close() {
        recognizer.close()
    }

    companion object {
        /**
         * Extracts at word (element) granularity — used by Classic TTS and Dyslexia Focus
         * where per-word position matters.
         */
        fun extractWords(mlKitText: Text): List<SpatialWord> =
            mlKitText.textBlocks
                .flatMap { it.lines }
                .flatMap { it.elements }
                .mapNotNull { element ->
                    val quad = quadFromElement(element) ?: return@mapNotNull null
                    SpatialWord(element.text, quad, 0.95f)
                }

        /**
         * Extracts at ML Kit **line** granularity — use for AR Lens and Instant Indexing.
         *
         * Each returned [SpatialWord] represents one `Text.Line` with ML Kit's own
         * bounding geometry (more accurate than manually re-grouping word-level elements).
         * Blank lines are filtered out.
         */
        fun extractLines(mlKitText: Text): List<SpatialWord> =
            mlKitText.textBlocks
                .flatMap { it.lines }
                .mapNotNull { line ->
                    if (line.text.isBlank()) return@mapNotNull null
                    val quad = quadFromRaw(line.cornerPoints, line.boundingBox)
                        ?: return@mapNotNull null
                    SpatialWord(line.text, quad, 0.95f)
                }

        fun quadFromElement(element: Text.Element): FloatArray? =
            quadFromRaw(element.cornerPoints, element.boundingBox)

        /**
         * Shared quad builder — works for any ML Kit recognised-text level
         * (element, line, or text block). Returns a FloatArray[8] encoding the
         * four corner points TL→TR→BR→BL, or null if no geometry is available.
         */
        private fun quadFromRaw(
            cornerPoints: Array<android.graphics.Point>?,
            boundingBox:  android.graphics.Rect?
        ): FloatArray? {
            if (cornerPoints != null && cornerPoints.size == 4) {
                return floatArrayOf(
                    cornerPoints[0].x.toFloat(), cornerPoints[0].y.toFloat(),
                    cornerPoints[1].x.toFloat(), cornerPoints[1].y.toFloat(),
                    cornerPoints[2].x.toFloat(), cornerPoints[2].y.toFloat(),
                    cornerPoints[3].x.toFloat(), cornerPoints[3].y.toFloat()
                )
            }
            val box = boundingBox ?: return null
            return floatArrayOf(
                box.left.toFloat(),  box.top.toFloat(),
                box.right.toFloat(), box.top.toFloat(),
                box.right.toFloat(), box.bottom.toFloat(),
                box.left.toFloat(),  box.bottom.toFloat()
            )
        }
    }
}
